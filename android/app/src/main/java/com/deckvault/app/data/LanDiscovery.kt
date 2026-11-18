package com.deckvault.app.data

import android.content.Context
import android.net.wifi.WifiManager
import com.deckvault.app.model.DiscoveredHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object LanDiscovery {
    private const val HTTP_PORT = 48720
    private const val UDP_PORT = 48721
    private val MAGIC = "DECKVAULT_DISCOVER_V1".toByteArray(Charsets.UTF_8)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun discover(context: Context, timeoutMs: Int = 2200): List<DiscoveredHost> = withContext(Dispatchers.IO) {
        val found = ConcurrentHashMap<String, DiscoveredHost>()
        val pool = Executors.newFixedThreadPool(48)

        val udpTask = pool.submit {
            discoverViaUdp(context, timeoutMs, found)
        }

        // Parallel TCP subnet /api/ping sweep so devices appear even if router blocks UDP broadcast
        val candidateIps = collectLocalSubnetIps()
        for (ip in candidateIps) {
            pool.execute {
                probeTcpHost(ip)?.let { host ->
                    found.merge(host.hostId, host) { prev, next ->
                        val mergedIps = (next.ips + prev.ips).distinct()
                        next.copy(
                            ips = mergedIps,
                            freeBytes = maxOf(prev.freeBytes, next.freeBytes),
                            totalBytes = maxOf(prev.totalBytes, next.totalBytes),
                        )
                    }
                }
            }
        }

        runCatching { udpTask.get() }
        pool.shutdown()
        runCatching { pool.awaitTermination(2500, TimeUnit.MILLISECONDS) }
        pool.shutdownNow()

        found.values.sortedWith(
            compareBy<DiscoveredHost> {
                when (it.os.lowercase()) {
                    "windows" -> 0
                    "android" -> 1
                    else -> 2
                }
            }.thenBy { it.deviceName.lowercase() },
        )
    }

    /** Fast UDP ping targeted at a single paired hostId so DHCP IP changes heal automatically in ~100ms. */
    suspend fun resolveHostIpsById(context: Context, targetHostId: String, timeoutMs: Int = 900): List<String> =
        withContext(Dispatchers.IO) {
            if (targetHostId.isBlank()) return@withContext emptyList()
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lock = wifi?.createMulticastLock("DeckVaultHeal")?.apply {
                setReferenceCounted(false)
                runCatching { acquire() }
            }
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 250
                }
                val targets = collectBroadcastAddresses()
                for (target in targets) {
                    runCatching { socket.send(DatagramPacket(MAGIC, MAGIC.size, target, UDP_PORT)) }
                }
                val deadline = System.currentTimeMillis() + timeoutMs
                val buf = ByteArray(4096)
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val resp = DatagramPacket(buf, buf.size)
                        socket.receive(resp)
                        val text = String(resp.data, 0, resp.length, Charsets.UTF_8)
                        val parsed = runCatching { json.decodeFromString<DiscoveredHost>(text) }.getOrNull()
                        if (parsed != null && parsed.service == "DeckVault" && parsed.hostId == targetHostId) {
                            val senderIp = resp.address.hostAddress ?: ""
                            return@withContext (listOf(senderIp) + parsed.ips)
                                .filter { it.isNotBlank() && it != "127.0.0.1" }
                                .distinct()
                                .sortedByDescending { it.startsWith("192.168.") || it.startsWith("10.") || it.startsWith("172.") }
                        }
                    } catch (_: SocketTimeoutException) {
                        for (target in targets) {
                            runCatching { socket.send(DatagramPacket(MAGIC, MAGIC.size, target, UDP_PORT)) }
                        }
                    }
                }
                emptyList()
            } catch (_: Exception) {
                emptyList()
            } finally {
                runCatching { socket?.close() }
                runCatching { if (lock?.isHeld == true) lock.release() }
            }
        }

    private fun discoverViaUdp(
        context: Context,
        timeoutMs: Int,
        found: ConcurrentHashMap<String, DiscoveredHost>,
    ) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("DeckVaultDiscover")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 400
            }
            val targets = collectBroadcastAddresses()
            repeat(2) {
                for (target in targets) {
                    runCatching {
                        socket.send(DatagramPacket(MAGIC, MAGIC.size, target, UDP_PORT))
                    }
                }
                Thread.sleep(150)
            }
            val deadline = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(4096)
            while (System.currentTimeMillis() < deadline) {
                try {
                    val resp = DatagramPacket(buf, buf.size)
                    socket.receive(resp)
                    val text = String(resp.data, 0, resp.length, Charsets.UTF_8)
                    val parsed = runCatching { json.decodeFromString<DiscoveredHost>(text) }.getOrNull()
                    if (parsed != null && parsed.service == "DeckVault") {
                        val senderIp = resp.address.hostAddress ?: ""
                        val mergedIps = (listOf(senderIp) + parsed.ips)
                            .filter { it.isNotBlank() && it != "127.0.0.1" }
                            .distinct()
                        val item = parsed.copy(ips = mergedIps.ifEmpty { listOf(senderIp) })
                        found[item.hostId] = item
                    }
                } catch (_: SocketTimeoutException) {
                    // Continue waiting until deadline
                }
            }
        } catch (_: Exception) {
            // Fall through cleanly; TCP sweep still provides results
        } finally {
            runCatching { socket?.close() }
            runCatching { if (lock?.isHeld == true) lock.release() }
        }
    }

    private fun probeTcpHost(ip: String): DiscoveredHost? {
        return runCatching {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(ip, HTTP_PORT), 320)
            }
            val conn = (URL("http://$ip:$HTTP_PORT/api/ping").openConnection() as HttpURLConnection).apply {
                connectTimeout = 500
                readTimeout = 700
                requestMethod = "GET"
            }
            try {
                if (conn.responseCode != 200) return@runCatching null
                val body = conn.inputStream.bufferedReader().readText()
                val ping = json.decodeFromString<com.deckvault.app.model.PingResponse>(body)
                if (ping.service != "DeckVault" || ping.hostId.isBlank()) return@runCatching null
                DiscoveredHost(
                    service = ping.service,
                    version = ping.version,
                    hostId = ping.hostId,
                    deviceName = ping.deviceName.ifBlank { ip },
                    port = HTTP_PORT,
                    ips = listOf(ip),
                    ffmpeg = ping.ffmpeg,
                    os = ping.os,
                )
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun collectLocalSubnetIps(): List<String> {
        val prefixes = linkedSetOf<String>()
        val ownIps = hashSetOf<String>()
        runCatching {
            val ifaces = NetworkInterface.getNetworkInterfaces()
            while (ifaces.hasMoreElements()) {
                val iface = ifaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        ownIps.add(ip)
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            val parts = ip.split('.')
                            if (parts.size == 4) {
                                prefixes.add("${parts[0]}.${parts[1]}.${parts[2]}")
                            }
                        }
                    }
                }
            }
        }
        val result = ArrayList<String>(prefixes.size * 254)
        for (prefix in prefixes) {
            for (host in 1..254) {
                val candidate = "$prefix.$host"
                if (candidate !in ownIps) result.add(candidate)
            }
        }
        return result
    }

    private fun collectBroadcastAddresses(): List<InetAddress> {
        val result = linkedSetOf<InetAddress>()
        runCatching { result.add(InetAddress.getByName("255.255.255.255")) }
        runCatching {
            val ifaces = NetworkInterface.getNetworkInterfaces()
            while (ifaces.hasMoreElements()) {
                val iface = ifaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.interfaceAddresses) {
                    val bcast = addr.broadcast
                    if (bcast is Inet4Address) {
                        result.add(bcast)
                    }
                }
            }
        }
        return result.toList()
    }
}
