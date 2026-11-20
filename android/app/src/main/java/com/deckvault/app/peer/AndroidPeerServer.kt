package com.deckvault.app.peer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Size
import com.deckvault.app.data.AllFilesPermissionRequiredException
import com.deckvault.app.data.LocalStorageRepository
import com.deckvault.app.model.CreateFolderRequest
import com.deckvault.app.model.CreateFolderResponse
import com.deckvault.app.model.DeleteRequest
import com.deckvault.app.model.DeleteResponse
import com.deckvault.app.model.DiscoveredHost
import com.deckvault.app.model.DriveInfo
import com.deckvault.app.model.FileItem
import com.deckvault.app.model.FolderItem
import com.deckvault.app.model.IndexerStatus
import com.deckvault.app.model.LargestResponse
import com.deckvault.app.model.ListResponse
import com.deckvault.app.model.MediaFilter
import com.deckvault.app.model.PairRequest
import com.deckvault.app.model.PairResponse
import com.deckvault.app.model.PingResponse
import com.deckvault.app.model.QuickFolder
import com.deckvault.app.model.RemotePullRequest
import com.deckvault.app.model.RenameRequest
import com.deckvault.app.model.RootsResponse
import com.deckvault.app.model.SearchResponse
import com.deckvault.app.model.SortBy
import com.deckvault.app.model.TransferRequest
import com.deckvault.app.model.TransferResponse
import com.deckvault.app.model.TransferredItemResult
import com.deckvault.app.model.TreeResponse
import com.deckvault.app.model.TrimRequest
import com.deckvault.app.model.TrimResponse
import com.deckvault.app.model.UploadResponse
import com.deckvault.app.model.VideoMeta
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Pure-Kotlin HTTP + UDP Peer Server embedded in the DeckVault APK so every Android
 * tablet and phone acts as a full DeckVault host discoverable by PCs, other Androids,
 * and iPhone Safari.
 */
class AndroidPeerServer(private val context: Context) {

    companion object {
        const val HTTP_PORT = 48720
        const val UDP_PORT = 48721
        private const val DISCOVERY_MAGIC = "DECKVAULT_DISCOVER_V1"
        private const val PREFS_NAME = "deckvault_peer_identity"

        private val VIDEO_EXTS = setOf("mp4", "mkv", "mov", "avi", "webm", "m4v", "wmv", "flv", "ts", "3gp", "mpg", "mpeg")
        private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")
        private val AUDIO_EXTS = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma")
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val localRepo = LocalStorageRepository(context)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val pool: ExecutorService = Executors.newCachedThreadPool()
    private val metaCache = ConcurrentHashMap<String, VideoMeta?>()
    private val mediaSem = Semaphore(2)
    private val pullClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.MINUTES)
        .writeTimeout(20, TimeUnit.MINUTES)
        .build()

    @Volatile
    private var running = false
    private var serverSocket: ServerSocket? = null
    private var udpSocket: DatagramSocket? = null

    val hostId: String by lazy {
        prefs.getString("host_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("host_id", it).apply()
        }
    }

    val deviceName: String by lazy {
        val raw = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    var pin: String
        get() = prefs.getString("pin", null) ?: rotatePin()
        private set(value) {
            prefs.edit().putString("pin", value).apply()
        }

    fun rotatePin(): String {
        val next = "%06d".format(Random.nextInt(100_000, 999_999))
        pin = next
        return next
    }

    private fun tokens(): Set<String> = prefs.getStringSet("tokens", emptySet()) ?: emptySet()

    private fun issueToken(): String {
        val tok = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").take(16)
        val next = tokens().toMutableSet().apply { add(tok) }
        prefs.edit().putStringSet("tokens", next).apply()
        return tok
    }

    private fun isValidToken(token: String?): Boolean = !token.isNullOrBlank() && token in tokens()

    private var spaceSavedBytes: Long
        get() = prefs.getLong("space_saved_bytes", 0L)
        set(value) {
            prefs.edit().putLong("space_saved_bytes", value).apply()
        }

    fun start() {
        if (running) return
        running = true
        // Ensure pin is initialized
        pin

        thread(name = "DeckVaultAndroidHttp", isDaemon = true) {
            try {
                val ss = ServerSocket(HTTP_PORT, 256)
                serverSocket = ss
                while (running) {
                    val sock = try {
                        ss.accept()
                    } catch (_: Exception) {
                        break
                    }
                    pool.execute {
                        try {
                            handleSocket(sock)
                        } catch (_: Throwable) {
                            runCatching { sock.close() }
                        }
                    }
                }
            } catch (_: Exception) {
                running = false
            }
        }

        thread(name = "DeckVaultAndroidUdp", isDaemon = true) {
            try {
                val ds = DatagramSocket(UDP_PORT, InetAddress.getByName("0.0.0.0")).apply {
                    broadcast = true
                }
                udpSocket = ds
                val buf = ByteArray(1024)
                while (running) {
                    val pkt = DatagramPacket(buf, buf.size)
                    try {
                        ds.receive(pkt)
                    } catch (_: Exception) {
                        break
                    }
                    val msg = String(pkt.data, 0, pkt.length, Charsets.UTF_8).trim()
                    if (msg == DISCOVERY_MAGIC) {
                        val drives = detectStorageDrives()
                        val free = drives.sumOf { it.freeBytes }
                        val total = drives.sumOf { it.totalBytes }
                        val replyObj = DiscoveredHost(
                            service = "DeckVault",
                            version = "1.2.0",
                            hostId = hostId,
                            deviceName = deviceName,
                            port = HTTP_PORT,
                            ips = getLocalIpv4Addresses(),
                            freeBytes = free,
                            totalBytes = total,
                            ffmpeg = false,
                            os = "Android",
                        )
                        val bytes = json.encodeToString(replyObj).toByteArray(Charsets.UTF_8)
                        runCatching { ds.send(DatagramPacket(bytes, bytes.size, pkt.address, pkt.port)) }
                    }
                }
            } catch (_: Exception) {
                // UDP port might already be held if another peer instance runs on same host
            }
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        runCatching { udpSocket?.close() }
        serverSocket = null
        udpSocket = null
    }

    fun getLocalIpv4Addresses(): List<String> {
        val result = mutableListOf<String>()
        runCatching {
            val ifaces = NetworkInterface.getNetworkInterfaces()
            while (ifaces.hasMoreElements()) {
                val iface = ifaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val ifName = iface.name.lowercase(Locale.US)
                if (ifName.startsWith("tun") || ifName.startsWith("rmnet") || ifName.startsWith("ccmni")) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddr = addr.hostAddress ?: continue
                        if (hostAddr !in result) result.add(hostAddr)
                    }
                }
            }
        }
        return result.sortedByDescending {
            it.startsWith("192.168.") || it.startsWith("10.") || it.startsWith("172.")
        }
    }

    fun detectStorageDrives(): List<DriveInfo> = localRepo.detectStorageDrives()

    fun detectQuickFolders(): List<QuickFolder> = localRepo.detectQuickFolders()

    private fun statVolume(dir: File): Pair<Long, Long> = runCatching {
        val sf = StatFs(dir.absolutePath)
        sf.totalBytes to sf.availableBytes
    }.getOrDefault(0L to 0L)

    private fun resolveSafePath(raw: String): File = localRepo.resolveSafePath(raw)

    private fun handleSocket(socket: Socket) {
        socket.use { sock ->
            sock.soTimeout = 25_000
            runCatching { sock.tcpNoDelay = true }
            val input = BufferedInputStream(sock.getInputStream(), 32 * 1024)
            val output = BufferedOutputStream(sock.getOutputStream(), 64 * 1024)

            var keepAlive = true
            while (running && keepAlive && !sock.isClosed) {
                var requestLine = readHttpLine(input) ?: break
                while (requestLine.isEmpty()) {
                    requestLine = readHttpLine(input) ?: return
                }
                val parts = requestLine.split(" ")
                if (parts.size < 2) break
                val method = parts[0].uppercase(Locale.US)
                val rawTarget = parts[1]
                val httpVersion = parts.getOrNull(2)?.uppercase(Locale.US) ?: "HTTP/1.1"

                val headers = linkedMapOf<String, String>()
                while (true) {
                    val line = readHttpLine(input) ?: return
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        headers[line.substring(0, colon).trim().lowercase(Locale.US)] =
                            line.substring(colon + 1).trim()
                    }
                }

                val connHdr = headers["connection"].orEmpty()
                keepAlive = (httpVersion == "HTTP/1.1" && !connHdr.equals("close", ignoreCase = true)) ||
                    connHdr.equals("keep-alive", ignoreCase = true)

                val qIdx = rawTarget.indexOf('?')
                val route = if (qIdx >= 0) rawTarget.substring(0, qIdx) else rawTarget
                val queryStr = if (qIdx >= 0) rawTarget.substring(qIdx + 1) else ""
                val qs = parseQuery(queryStr)

                if (method == "GET" && route == "/") {
                    sendHtml(output, DeckVaultWebPortal.HTML, keepAlive)
                    continue
                }

                if (method == "GET" && route == "/api/ping") {
                    val ping = PingResponse(
                        service = "DeckVault",
                        version = com.deckvault.app.BuildConfig.VERSION_NAME,
                        hostId = hostId,
                        deviceName = deviceName,
                        os = "Android",
                        ffmpeg = false,
                        spaceSavedBytes = spaceSavedBytes,
                        indexer = IndexerStatus(scanning = false, indexedDirs = 0, lastScanEpoch = System.currentTimeMillis() / 1000L),
                    )
                    sendJson(output, 200, json.encodeToString(ping), keepAlive = keepAlive)
                    continue
                }

                if (method == "POST" && route == "/api/pair") {
                    val bodyStr = readBodyString(input, headers["content-length"]?.toIntOrNull() ?: 0)
                    val req = json.decodeFromString(PairRequest.serializer(), bodyStr)
                    if (req.pin.trim() != pin.trim()) {
                        sendJson(output, 401, """{"error":"Invalid PIN"}""", keepAlive = false)
                        break
                    }
                    val resp = PairResponse(
                        token = issueToken(),
                        hostId = hostId,
                        deviceName = deviceName,
                        ffmpeg = false,
                        spaceSavedBytes = spaceSavedBytes,
                        os = "Android",
                    )
                    sendJson(output, 200, json.encodeToString(resp), keepAlive = keepAlive)
                    continue
                }

                val token = headers["x-deckvault-token"] ?: qs["token"]
                if (!isValidToken(token)) {
                    sendJson(output, 401, """{"error":"Unauthorized"}""", keepAlive = false)
                    break
                }

                try {
                    when (method) {
                        "GET", "HEAD" -> handleGet(method == "HEAD", route, qs, headers, output, keepAlive)
                        "POST" -> {
                            handlePost(route, qs, headers, input, output, keepAlive)
                        }
                        else -> sendJson(output, 405, """{"error":"Method not allowed"}""", keepAlive = keepAlive)
                    }
                } catch (e: AllFilesPermissionRequiredException) {
                    val safeKa = if (method == "POST") false else keepAlive
                    if (!safeKa) keepAlive = false
                    runCatching { sendJson(output, 403, json.encodeToString(mapOf("error" to "Grant All Files Access on $deviceName or open All Device Videos/Photos")), keepAlive = safeKa) }
                } catch (e: SecurityException) {
                    keepAlive = false
                    runCatching { sendJson(output, 403, json.encodeToString(mapOf("error" to (e.message ?: "Forbidden"))), keepAlive = false) }
                } catch (e: java.io.FileNotFoundException) {
                    val safeKa = if (method == "POST") false else keepAlive
                    if (!safeKa) keepAlive = false
                    runCatching { sendJson(output, 404, json.encodeToString(mapOf("error" to (e.message ?: "Not found"))), keepAlive = safeKa) }
                } catch (e: Throwable) {
                    val safeKa = if (method == "POST") false else keepAlive
                    if (!safeKa) keepAlive = false
                    runCatching { sendJson(output, 400, json.encodeToString(mapOf("error" to (e.message ?: "Bad request"))), keepAlive = safeKa) }
                }
            }
        }
    }

    private fun handleGet(
        headOnly: Boolean,
        route: String,
        qs: Map<String, String>,
        headers: Map<String, String>,
        output: OutputStream,
        keepAlive: Boolean,
    ) {
        when (route) {
            "/api/roots" -> {
                val resp = RootsResponse(
                    deviceName = deviceName,
                    hostId = hostId,
                    os = "Android",
                    ffmpeg = false,
                    spaceSavedBytes = spaceSavedBytes,
                    indexer = IndexerStatus(scanning = false, indexedDirs = 0, lastScanEpoch = System.currentTimeMillis() / 1000L),
                    drives = detectStorageDrives(),
                    quickFolders = detectQuickFolders(),
                )
                sendJson(output, 200, json.encodeToString(resp), headOnly, keepAlive)
            }
            "/api/tree" -> {
                val rawPath = qs["path"].orEmpty()
                val sort = qs["sort"] ?: "size"
                val sortBySize = sort != "name"
                val baseTree = runBlocking { localRepo.getTree(rawPath, sortBySize = sortBySize) }
                val sorted = baseTree.children
                    .filterNot { it.name == "Android" }
                    .let { list ->
                        if (sortBySize) list.sortedWith(compareByDescending<FolderItem> { it.sizeBytes }.thenBy { it.name.lowercase(Locale.US) })
                        else list.sortedBy { it.name.lowercase(Locale.US) }
                    }
                val resp = baseTree.copy(children = sorted)
                sendJson(output, 200, json.encodeToString(resp), headOnly, keepAlive)
            }
            "/api/list" -> {
                val rawPath = qs["path"].orEmpty()
                val sortKey = qs["sort"] ?: "size"
                val desc = (qs["order"] ?: "desc") != "asc"
                val filterKey = qs["filter"] ?: "all"
                val q = (qs["q"] ?: "").trim().lowercase(Locale.US)

                val sortEnum = SortBy.entries.firstOrNull { it.apiKey == sortKey } ?: SortBy.SIZE
                val filterEnum = MediaFilter.entries.firstOrNull { it.apiKey == filterKey } ?: MediaFilter.ALL
                val base = runBlocking { localRepo.listDirectory(rawPath, sortEnum, desc, filterEnum) }
                val filtered = if (q.isEmpty()) base else {
                    base.copy(
                        folders = base.folders.filter { it.name.lowercase(Locale.US).contains(q) },
                        files = base.files.filter { it.name.lowercase(Locale.US).contains(q) },
                    )
                }

                // Queue up to 12 uncached local video metadata probes silently in background for future opens
                var queued = 0
                filtered.files.forEach { item ->
                    if (item.isVideo && item.meta == null && !LocalStorageRepository.isContentUriPath(item.path) && queued < 12) {
                        queued++
                        pool.execute { runCatching { localRepo.probeVideoMeta(item.path) } }
                    }
                }
                sendJson(output, 200, json.encodeToString(filtered), headOnly, keepAlive)
            }
            "/api/search" -> {
                val rawPath = qs["path"].orEmpty()
                val q = (qs["q"] ?: "").trim().lowercase(Locale.US)
                if (LocalStorageRepository.isVirtualMediaStorePath(rawPath) || !localRepo.hasAllFilesAccess()) {
                    val found = runBlocking { localRepo.searchFiles(q, rawPath, MediaFilter.ALL, 120) }
                    sendJson(output, 200, json.encodeToString(found), headOnly, keepAlive)
                    return
                }
                val safe = resolveSafePath(rawPath)
                val matches = mutableListOf<FileItem>()
                if (q.isNotEmpty()) {
                    walkFilesLimited(safe, maxResults = 160) { f ->
                        if (f.name.lowercase(Locale.US).contains(q)) {
                            matches.add(toFileItem(f, probeVideo = false))
                        }
                    }
                }
                sendJson(output, 200, json.encodeToString(SearchResponse(files = matches)), headOnly, keepAlive)
            }
            "/api/largest" -> {
                val rawPath = qs["path"].orEmpty()
                val filterKey = qs["filter"] ?: "video"
                val limit = (qs["limit"]?.toIntOrNull() ?: 80).coerceIn(1, 200)
                if (LocalStorageRepository.isVirtualMediaStorePath(rawPath) || !localRepo.hasAllFilesAccess()) {
                    val found = runBlocking { localRepo.findLargest(rawPath, limit) }
                    sendJson(output, 200, json.encodeToString(found), headOnly, keepAlive)
                    return
                }
                val safe = resolveSafePath(rawPath)
                val all = mutableListOf<FileItem>()
                walkFilesLimited(safe, maxResults = 3000) { f ->
                    val item = toFileItem(f, probeVideo = false)
                    val keep = when (filterKey) {
                        "video" -> item.isVideo
                        "image" -> item.isImage
                        "media" -> item.isVideo || item.isImage
                        else -> true
                    }
                    if (keep) all.add(item)
                }
                val top = all.sortedByDescending { it.sizeBytes }.take(limit).map {
                    if ((it.isVideo || it.isAudio) && it.meta == null) {
                        it.copy(meta = localRepo.getCachedVideoMeta(it.path, it.sizeBytes) ?: probeMediaMeta(File(it.path)))
                    } else it
                }
                sendJson(output, 200, json.encodeToString(LargestResponse(path = safe.canonicalPath, files = top)), headOnly, keepAlive)
            }
            "/api/video/thumbnail" -> {
                val rawPath = qs["path"].orEmpty()
                val w = (qs["w"]?.toIntOrNull() ?: 480).coerceIn(120, 1280)
                val thumb = if (LocalStorageRepository.isContentUriPath(rawPath)) {
                    localRepo.getOrBuildThumbnailFile(rawPath, w)
                        ?: throw java.io.FileNotFoundException("Thumbnail unavailable for $rawPath")
                } else {
                    val safe = resolveSafePath(rawPath)
                    getOrBuildVideoThumb(safe, w)
                }
                sendFileBytes(output, thumb, "image/jpeg", headers, headOnly, keepAlive, cacheSeconds = 604_800)
            }
            "/api/video/storyboard" -> {
                val rawPath = qs["path"].orEmpty()
                val cols = (qs["cols"]?.toIntOrNull() ?: 4).coerceIn(2, 6)
                val rows = (qs["rows"]?.toIntOrNull() ?: 4).coerceIn(2, 6)
                val sb = if (LocalStorageRepository.isContentUriPath(rawPath)) {
                    localRepo.getOrBuildStoryboardFile(rawPath, cols, rows)
                        ?: throw java.io.FileNotFoundException("Storyboard unavailable for $rawPath")
                } else {
                    val safe = resolveSafePath(rawPath)
                    getOrBuildStoryboard(safe, cols, rows)
                }
                sendFileBytes(output, sb, "image/jpeg", headers, headOnly, keepAlive, cacheSeconds = 604_800)
            }
            "/api/video/storyboard-info" -> {
                val rawPath = qs["path"].orEmpty()
                val cols = (qs["cols"]?.toIntOrNull() ?: 4).coerceIn(2, 6)
                val rows = (qs["rows"]?.toIntOrNull() ?: 4).coerceIn(2, 6)
                val targetKey = if (LocalStorageRepository.isContentUriPath(rawPath)) rawPath else resolveSafePath(rawPath).canonicalPath
                val meta = localRepo.probeVideoMeta(targetKey)
                val info = com.deckvault.app.model.StoryboardInfoResponse(
                    cols = cols,
                    rows = rows,
                    frameCount = cols * rows,
                    cellWidth = 320,
                    cellHeight = 180,
                    durationMs = meta?.durationMs ?: 0L,
                    width = meta?.width ?: 0,
                    height = meta?.height ?: 0,
                )
                sendJson(output, 200, json.encodeToString(info), headOnly, keepAlive)
            }
            "/api/video/frame" -> {
                val rawPath = qs["path"].orEmpty()
                val ms = qs["ms"]?.toLongOrNull() ?: 0L
                val w = (qs["w"]?.toIntOrNull() ?: 360).coerceIn(120, 1280)
                val targetKey = if (LocalStorageRepository.isContentUriPath(rawPath)) rawPath else resolveSafePath(rawPath).canonicalPath
                val frame = localRepo.getOrBuildVideoFrameFile(targetKey, ms, w)
                    ?: throw java.io.FileNotFoundException("No frame available for $rawPath")
                sendFileBytes(output, frame, "image/jpeg", headers, headOnly, keepAlive, cacheSeconds = 604_800)
            }
            "/api/image" -> {
                val rawPath = qs["path"].orEmpty()
                val w = (qs["w"]?.toIntOrNull() ?: 0)
                if (LocalStorageRepository.isContentUriPath(rawPath)) {
                    if (w > 0) {
                        val thumb = getOrBuildContentImageThumb(rawPath, w)
                        if (thumb != null) {
                            sendFileBytes(output, thumb, "image/jpeg", headers, headOnly, keepAlive, cacheSeconds = 604_800)
                            return
                        }
                    }
                    sendContentUriStream(output, rawPath, headers, headOnly, keepAlive)
                    return
                }
                val safe = resolveSafePath(rawPath)
                if (w > 0) {
                    val cached = getOrBuildImageThumb(safe, w)
                    if (cached != null) {
                        sendFileBytes(output, cached, "image/jpeg", headers, headOnly, keepAlive, cacheSeconds = 604_800)
                        return
                    }
                }
                sendFileBytes(output, safe, guessMime(safe), headers, headOnly, keepAlive, cacheSeconds = if (w > 0) 604_800 else 0)
            }
            "/api/stream" -> {
                val rawPath = qs["path"].orEmpty()
                if (LocalStorageRepository.isContentUriPath(rawPath)) {
                    sendContentUriStream(output, rawPath, headers, headOnly, keepAlive)
                    return
                }
                val safe = resolveSafePath(rawPath)
                if (!safe.isFile) throw java.io.FileNotFoundException(safe.path)
                sendFileBytes(output, safe, guessMime(safe), headers, headOnly, keepAlive)
            }
            else -> sendJson(output, 404, """{"error":"Unknown route"}""", headOnly, keepAlive)
        }
    }

    private fun handlePost(
        route: String,
        qs: Map<String, String>,
        headers: Map<String, String>,
        input: BufferedInputStream,
        output: OutputStream,
        keepAlive: Boolean,
    ) {
        if (route == "/api/file/upload") {
            val destDir = resolveSafePath(qs["dir"].orEmpty())
            if (!destDir.isDirectory) throw IllegalArgumentException("Destination is not a folder")
            val rawName = qs["name"] ?: headers["x-file-name"] ?: "upload_${System.currentTimeMillis()}"
            val conflict = qs["conflict"] ?: "rename"
            val target = resolveUniqueDest(destDir, rawName, conflict)
            if (target == null) {
                sendJson(output, 200, json.encodeToString(UploadResponse(ok = true, skipped = true)), keepAlive = keepAlive)
                return
            }
            resolveSafePath(target.absolutePath)
            val contentLen = headers["content-length"]?.toLongOrNull() ?: 0L
            val tmp = File(destDir, ".deckvault_up_${UUID.randomUUID()}")
            var written = 0L
            try {
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(128 * 1024)
                    var remaining = contentLen
                    while (remaining > 0L) {
                        val toRead = min(buf.size.toLong(), remaining).toInt()
                        val read = input.read(buf, 0, toRead)
                        if (read < 0) break
                        out.write(buf, 0, read)
                        written += read
                        remaining -= read
                    }
                }
                if (contentLen > 0L && written < contentLen) {
                    throw IllegalStateException("Incomplete upload ($written/$contentLen bytes)")
                }
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
            } catch (e: Exception) {
                tmp.delete()
                throw e
            }
            sendJson(output, 200, json.encodeToString(UploadResponse(ok = true, path = target.canonicalPath, name = target.name, sizeBytes = written)), keepAlive = keepAlive)
            return
        }

        val bodyLen = headers["content-length"]?.toIntOrNull() ?: 0
        val bodyStr = readBodyString(input, bodyLen)

        when (route) {
            "/api/rescan" -> sendJson(output, 200, """{"started":true}""", keepAlive = keepAlive)
            "/api/folder/create" -> {
                val req = json.decodeFromString(CreateFolderRequest.serializer(), bodyStr)
                val parent = resolveSafePath(req.parentDir)
                val clean = File(req.name.trim()).name
                if (clean.isBlank() || clean == "." || clean == "..") throw IllegalArgumentException("Invalid folder name")
                val created = File(parent, clean)
                resolveSafePath(created.absolutePath)
                created.mkdirs()
                sendJson(output, 200, json.encodeToString(CreateFolderResponse(ok = true, path = created.canonicalPath, name = clean)), keepAlive = keepAlive)
            }
            "/api/file/rename" -> {
                val req = json.decodeFromString(RenameRequest.serializer(), bodyStr)
                val src = resolveSafePath(req.path)
                val clean = File(req.newName.trim()).name
                if (clean.isBlank()) throw IllegalArgumentException("Name cannot be empty")
                val dst = File(src.parentFile, clean)
                resolveSafePath(dst.absolutePath)
                if (!src.renameTo(dst)) throw IllegalStateException("Rename failed")
                metaCache.remove(src.canonicalPath)
                localRepo.metaDb.invalidate(src.canonicalPath)
                sendJson(output, 200, """{"ok":true}""", keepAlive = keepAlive)
            }
            "/api/file/delete" -> {
                val req = json.decodeFromString(DeleteRequest.serializer(), bodyStr)
                var freed = 0L
                var count = 0
                val errors = mutableListOf<String>()
                req.paths.forEach { raw ->
                    runCatching {
                        val safe = resolveSafePath(raw)
                        val sz = if (safe.isFile) safe.length() else 0L
                        val ok = if (safe.isDirectory) safe.deleteRecursively() else safe.delete()
                        if (!ok) throw IllegalStateException("Could not delete ${safe.name}")
                        freed += sz
                        count++
                    }.onFailure { errors.add("$raw: ${it.message}") }
                }
                spaceSavedBytes += freed
                sendJson(output, 200, json.encodeToString(DeleteResponse(deletedCount = count, freedBytes = freed, totalSpaceSavedBytes = spaceSavedBytes, errors = errors)), keepAlive = keepAlive)
            }
            "/api/file/transfer" -> {
                val req = json.decodeFromString(TransferRequest.serializer(), bodyStr)
                val destDir = resolveSafePath(req.destinationDir)
                if (!destDir.isDirectory) throw IllegalArgumentException("Destination is not a directory")
                val move = req.mode != "copy"
                val results = mutableListOf<TransferredItemResult>()
                val errors = mutableListOf<String>()
                var totalBytes = 0L

                req.paths.forEach { raw ->
                    runCatching {
                        val src = resolveSafePath(raw)
                        if (!src.exists()) throw java.io.FileNotFoundException(raw)
                        if (move && src.parentFile?.canonicalPath == destDir.canonicalPath) return@runCatching
                        val target = resolveUniqueDest(destDir, src.name, req.conflict) ?: return@runCatching
                        resolveSafePath(target.absolutePath)
                        val sz = if (src.isFile) src.length() else 0L
                        if (move) {
                            if (!src.renameTo(target)) {
                                if (src.isDirectory) {
                                    src.copyRecursively(target, overwrite = true)
                                    src.deleteRecursively()
                                } else {
                                    src.copyTo(target, overwrite = true)
                                    src.delete()
                                }
                            }
                            metaCache.remove(src.canonicalPath)
                        } else {
                            if (src.isDirectory) src.copyRecursively(target, overwrite = true)
                            else src.copyTo(target, overwrite = true)
                        }
                        totalBytes += sz
                        results.add(TransferredItemResult(src = src.canonicalPath, dest = target.canonicalPath, sizeBytes = sz))
                    }.onFailure { errors.add("$raw: ${it.message}") }
                }
                sendJson(output, 200, json.encodeToString(TransferResponse(transferredCount = results.size, transferredBytes = totalBytes, results = results, errors = errors)), keepAlive = keepAlive)
            }
            "/api/file/pull" -> {
                val req = json.decodeFromString(RemotePullRequest.serializer(), bodyStr)
                val destDir = resolveSafePath(req.destinationDir)
                if (!destDir.isDirectory) throw IllegalArgumentException("Destination is not a directory")
                val results = mutableListOf<TransferredItemResult>()
                val errors = mutableListOf<String>()
                var totalBytes = 0L

                req.paths.forEach { srcPath ->
                    val tmp = File(destDir, ".deckvault_pull_${UUID.randomUUID()}")
                    runCatching {
                        val base = req.sourceBaseUrl.trimEnd('/')
                        val url = "$base/api/stream?path=${URLEncoder.encode(srcPath, "UTF-8")}"
                        val httpReq = Request.Builder().url(url).header("X-DeckVault-Token", req.sourceToken).get().build()
                        val target = resolveUniqueDest(destDir, File(srcPath).name, req.conflict) ?: return@runCatching
                        resolveSafePath(target.absolutePath)

                        var written = 0L
                        pullClient.newCall(httpReq).execute().use { resp ->
                            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                            val body = resp.body ?: throw IllegalStateException("Empty stream")
                            body.byteStream().use { srcStream ->
                                FileOutputStream(tmp).use { out ->
                                    val buf = ByteArray(128 * 1024)
                                    while (true) {
                                        val r = srcStream.read(buf)
                                        if (r < 0) break
                                        out.write(buf, 0, r)
                                        written += r
                                    }
                                }
                            }
                        }
                        if (!tmp.renameTo(target)) {
                            tmp.copyTo(target, overwrite = true)
                            tmp.delete()
                        }
                        totalBytes += written
                        results.add(TransferredItemResult(src = srcPath, dest = target.canonicalPath, sizeBytes = written))
                    }.onFailure { err ->
                        tmp.delete()
                        errors.add("$srcPath: ${err.message}")
                    }
                }
                sendJson(output, 200, json.encodeToString(TransferResponse(transferredCount = results.size, transferredBytes = totalBytes, results = results, errors = errors)), keepAlive = keepAlive)
            }
            "/api/video/trim" -> {
                val req = json.decodeFromString(TrimRequest.serializer(), bodyStr)
                val safe = resolveSafePath(req.path)
                val ext = safe.extension.lowercase(Locale.US)
                if (ext !in setOf("mp4", "m4v", "mov")) {
                    throw IllegalArgumentException("On-device lossless trim supports MP4/MOV/M4V; transfer MKV/AVI to PC to cut with FFmpeg.")
                }
                val resp = trimMp4Lossless(safe, req.startMs, req.endMs)
                spaceSavedBytes += resp.savedBytes
                metaCache.remove(safe.canonicalPath)
                sendJson(output, 200, json.encodeToString(resp.copy(totalSpaceSavedBytes = spaceSavedBytes)), keepAlive = keepAlive)
            }
            else -> sendJson(output, 404, """{"error":"Unknown route"}""", keepAlive = keepAlive)
        }
    }

    private fun trimMp4Lossless(src: File, startMs: Long, endMs: Long): TrimResponse {
        val oldSize = src.length()
        val tmp = File(src.parentFile, ".deckvault_trim_${UUID.randomUUID()}.mp4")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(src.absolutePath)
            val trackCount = extractor.trackCount
            muxer = MediaMuxer(tmp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackMap = HashMap<Int, Int>(trackCount)
            var maxBuf = 1024 * 1024
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    trackMap[i] = muxer.addTrack(format)
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        maxBuf = max(maxBuf, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                    }
                }
            }
            muxer.start()
            val startUs = startMs.coerceAtLeast(0L) * 1000L
            val endUs = endMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val buffer = ByteBuffer.allocate(maxBuf)
            val info = MediaCodec.BufferInfo()
            while (true) {
                info.offset = 0
                info.size = extractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                val sampleTime = extractor.sampleTime
                if (endUs in 1 until sampleTime) break
                info.presentationTimeUs = (sampleTime - startUs).coerceAtLeast(0L)
                info.flags = extractor.sampleFlags
                val outIdx = trackMap[extractor.sampleTrackIndex]
                if (outIdx != null) {
                    muxer.writeSampleData(outIdx, buffer, info)
                }
                extractor.advance()
            }
            muxer.stop()
        } catch (e: Exception) {
            tmp.delete()
            throw e
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }

        val newSize = tmp.length()
        if (!tmp.renameTo(src)) {
            tmp.copyTo(src, overwrite = true)
            tmp.delete()
        }
        val newDur = probeMediaMeta(src)?.durationMs ?: (endMs - startMs).coerceAtLeast(0L)
        return TrimResponse(
            ok = true,
            path = src.canonicalPath,
            oldSizeBytes = oldSize,
            newSizeBytes = newSize,
            savedBytes = (oldSize - newSize).coerceAtLeast(0L),
            sizeDeltaBytes = newSize - oldSize,
            newDurationMs = newDur,
            recycledOriginal = false,
            totalSpaceSavedBytes = spaceSavedBytes,
        )
    }

    private fun resolveUniqueDest(destDir: File, rawName: String, conflict: String): File? {
        val clean = File(rawName.trim()).name
        if (clean.isBlank() || clean == "." || clean == "..") throw IllegalArgumentException("Invalid filename")
        var candidate = File(destDir, clean)
        if (!candidate.exists() || conflict == "overwrite") return candidate
        if (conflict == "skip") return null
        val dot = clean.lastIndexOf('.')
        val stem = if (dot > 0) clean.substring(0, dot) else clean
        val ext = if (dot > 0) clean.substring(dot) else ""
        var idx = 1
        while (candidate.exists()) {
            candidate = File(destDir, "$stem ($idx)$ext")
            idx++
        }
        return candidate
    }

    private fun quickFolderSizeBytes(dir: File): Long {
        val list = dir.listFiles() ?: return -1L
        var sum = 0L
        for (f in list) {
            if (f.isFile) sum += f.length()
        }
        return sum
    }

    private fun walkFilesLimited(dir: File, maxResults: Int, onFile: (File) -> Unit) {
        var visited = 0
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        while (stack.isNotEmpty() && visited < maxResults * 4) {
            val cur = stack.removeLast()
            val children = cur.listFiles() ?: continue
            for (child in children) {
                if (child.name.startsWith(".") || child.name == "Android") continue
                if (child.isDirectory) {
                    stack.addLast(child)
                } else if (child.isFile) {
                    visited++
                    onFile(child)
                    if (visited >= maxResults * 4) break
                }
            }
        }
    }

    private fun toFileItem(file: File, probeVideo: Boolean): FileItem {
        val ext = file.extension.lowercase(Locale.US)
        val type = when {
            ext in VIDEO_EXTS -> "video"
            ext in IMAGE_EXTS -> "image"
            ext in AUDIO_EXTS -> "audio"
            else -> "other"
        }
        val meta = if (probeVideo && (type == "video" || type == "audio")) probeMediaMeta(file) else metaCache[file.canonicalPath]
        return FileItem(
            name = file.name,
            path = file.canonicalPath,
            sizeBytes = file.length(),
            modifiedMs = file.lastModified(),
            mediaType = type,
            extension = if (ext.isNotEmpty()) ".$ext" else "",
            meta = meta,
        )
    }

    private fun probeMediaMeta(file: File): VideoMeta? {
        val key = file.canonicalPath
        if (metaCache.containsKey(key)) return metaCache[key]
        val meta = runCatching {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(file.absolutePath)
                val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val br = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
                VideoMeta(durationMs = dur, width = w, height = h, bitrate = br)
            } finally {
                mmr.release()
            }
        }.getOrNull()
        metaCache[key] = meta
        return meta
    }

    private fun cacheKeyForFile(file: File, suffix: String): String {
        val raw = "${file.canonicalPath}|${file.length()}|${file.lastModified()}|$suffix"
        val digest = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun getOrBuildVideoThumb(file: File, width: Int): File {
        val dir = File(context.cacheDir, "peer_thumbs").apply { mkdirs() }
        val key = cacheKeyForFile(file, "t_$width")
        val out = File(dir, "$key.jpg")
        if (out.exists() && out.length() > 100) return out

        if (!mediaSem.tryAcquire(10, TimeUnit.SECONDS)) throw java.io.IOException("Thumbnail busy")
        return try {
            if (out.exists() && out.length() > 100) return out
            val tmp = File(dir, "$key.tmp.jpg")
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(file.absolutePath)
                val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val targetUs = ((durMs * 0.15).toLong().coerceIn(0L, max(0L, durMs - 100L))) * 1000L
                val bmp = mmr.getFrameAtTime(targetUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: mmr.frameAtTime
                    ?: throw IllegalStateException("Cannot extract video frame")
                val scaled = scaleBitmapToWidth(bmp, width)
                FileOutputStream(tmp).use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                if (scaled !== bmp) scaled.recycle()
                bmp.recycle()
                if (!tmp.renameTo(out)) tmp.copyTo(out, overwrite = true)
            } finally {
                runCatching { mmr.release() }
                tmp.delete()
            }
            out
        } finally {
            mediaSem.release()
        }
    }

    private fun getOrBuildStoryboard(file: File, cols: Int, rows: Int): File {
        val dir = File(context.cacheDir, "peer_storyboards").apply { mkdirs() }
        val key = cacheKeyForFile(file, "sb_${cols}x${rows}")
        val out = File(dir, "$key.jpg")
        if (out.exists() && out.length() > 100) return out

        if (!mediaSem.tryAcquire(10, TimeUnit.SECONDS)) throw java.io.IOException("Thumbnail busy")
        return try {
            if (out.exists() && out.length() > 100) return out
            val tmp = File(dir, "$key.tmp.jpg")
            val cellW = 320
            val cellH = 180
            val sheet = Bitmap.createBitmap(cols * cellW, rows * cellH, Bitmap.Config.RGB_565)
            val canvas = Canvas(sheet).apply { drawColor(Color.BLACK) }
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)

            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(file.absolutePath)
                val durMs = (mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 1000L).coerceAtLeast(1000L)
                val total = cols * rows
                for (idx in 0 until total) {
                    val frac = (idx + 0.5) / total.toDouble()
                    val us = (durMs * frac * 1000.0).toLong()
                    val frame = mmr.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                    val c = idx % cols
                    val r = idx / cols
                    val dst = Rect(c * cellW, r * cellH, (c + 1) * cellW, (r + 1) * cellH)
                    canvas.drawBitmap(frame, null, dst, paint)
                    frame.recycle()
                }
            } finally {
                runCatching { mmr.release() }
            }
            FileOutputStream(tmp).use { sheet.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            sheet.recycle()
            if (!tmp.renameTo(out)) tmp.copyTo(out, overwrite = true)
            tmp.delete()
            out
        } finally {
            mediaSem.release()
        }
    }

    private fun scaleBitmapToWidth(src: Bitmap, targetWidth: Int): Bitmap {
        if (src.width <= targetWidth) return src
        val h = (src.height.toFloat() * (targetWidth.toFloat() / src.width.toFloat())).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, targetWidth, h, true)
    }

    private fun decodeScaledBitmap(file: File, maxWidth: Int): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        var sample = 1
        while (opts.outWidth / sample > maxWidth * 2) sample *= 2
        val decOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = BitmapFactory.decodeFile(file.absolutePath, decOpts) ?: return null
        val scaled = scaleBitmapToWidth(raw, maxWidth)
        if (scaled !== raw) raw.recycle()
        scaled
    }.getOrNull()

    private fun getOrBuildImageThumb(file: File, width: Int): File? {
        if (!file.isFile) return null
        val w = width.coerceIn(120, 1280)
        val dir = File(context.cacheDir, "peer_img_thumbs").apply { mkdirs() }
        val key = cacheKeyForFile(file, "img_$w")
        val out = File(dir, "$key.jpg")
        if (out.exists() && out.length() > 64L) return out

        if (!mediaSem.tryAcquire(10, TimeUnit.SECONDS)) throw java.io.IOException("Thumbnail busy")
        return try {
            if (out.exists() && out.length() > 64L) return out
            val tmp = File(dir, "$key.tmp.jpg")
            runCatching {
                // Try fast embedded EXIF JPEG thumbnail first (<2ms, avoids 50MP Bitmap decode)
                val exifThumb = if (w <= 640 && file.extension.lowercase(Locale.US) in setOf("jpg", "jpeg", "heic", "heif")) {
                    runCatching { ExifInterface(file.absolutePath).thumbnailBytes }.getOrNull()
                } else null

                val bmp = if (exifThumb != null && exifThumb.isNotEmpty()) {
                    BitmapFactory.decodeByteArray(exifThumb, 0, exifThumb.size)?.let { raw ->
                        scaleBitmapToWidth(raw, w).also { if (it !== raw) raw.recycle() }
                    }
                } else {
                    decodeScaledBitmap(file, w)
                } ?: return@runCatching null

                FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                bmp.recycle()
                if (!tmp.renameTo(out)) tmp.copyTo(out, overwrite = true)
                out
            }.getOrNull().also {
                tmp.delete()
            }
        } finally {
            mediaSem.release()
        }
    }

    private fun getOrBuildContentImageThumb(uriStr: String, width: Int): File? {
        val w = width.coerceIn(120, 1280)
        val dir = File(context.cacheDir, "peer_img_thumbs").apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-1").digest("$uriStr|w$w".toByteArray()).joinToString("") { "%02x".format(it) }
        val out = File(dir, "$digest.jpg")
        if (out.exists() && out.length() > 64L) return out

        if (!mediaSem.tryAcquire(10, TimeUnit.SECONDS)) throw java.io.IOException("Thumbnail busy")
        return try {
            if (out.exists() && out.length() > 64L) return out
            val tmp = File(dir, "$digest.tmp.jpg")
            runCatching {
                val uri = Uri.parse(uriStr)
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    runCatching { context.contentResolver.loadThumbnail(uri, Size(w, w), null) }.getOrNull()
                } else null
                val finalBmp = bmp ?: context.contentResolver.openInputStream(uri)?.use { ins ->
                    BitmapFactory.decodeStream(ins)?.let { raw ->
                        scaleBitmapToWidth(raw, w).also { if (it !== raw) raw.recycle() }
                    }
                } ?: return@runCatching null
                FileOutputStream(tmp).use { finalBmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                finalBmp.recycle()
                if (!tmp.renameTo(out)) tmp.copyTo(out, overwrite = true)
                out
            }.getOrNull().also {
                tmp.delete()
            }
        } finally {
            mediaSem.release()
        }
    }

    private fun parseByteRange(rangeHeader: String?, size: Long): Triple<Int, Long, Long> {
        var start = 0L
        var end = (size - 1L).coerceAtLeast(0L)
        var status = 200
        if (rangeHeader != null && rangeHeader.startsWith("bytes=") && size > 0L) {
            val spec = rangeHeader.substring(6).split(",")[0].trim()
            val dash = spec.indexOf('-')
            if (dash >= 0) {
                val a = spec.substring(0, dash).trim()
                val b = spec.substring(dash + 1).trim()
                if (a.isNotEmpty()) {
                    start = a.toLongOrNull()?.coerceIn(0L, size - 1L) ?: 0L
                    end = b.toLongOrNull()?.coerceIn(start, size - 1L) ?: (size - 1L)
                    status = 206
                } else if (b.isNotEmpty()) {
                    val suffix = b.toLongOrNull() ?: size
                    start = (size - suffix).coerceAtLeast(0L)
                    end = size - 1L
                    status = 206
                }
            }
        }
        return Triple(status, start, end)
    }

    private fun connectionHeader(keepAlive: Boolean): String =
        if (keepAlive) "Connection: keep-alive\r\nKeep-Alive: timeout=20, max=100\r\n" else "Connection: close\r\n"

    private fun guessMime(file: File): String = when (file.extension.lowercase(Locale.US)) {
        "mp4", "m4v" -> "video/mp4"
        "mov" -> "video/quicktime"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "wmv" -> "video/x-ms-wmv"
        "flv" -> "video/x-flv"
        "ts", "mts", "m2ts" -> "video/mp2t"
        "mpg", "mpeg" -> "video/mpeg"
        "3gp" -> "video/3gpp"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "heic", "heif" -> "image/heic"
        "avif" -> "image/avif"
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "m4a", "aac" -> "audio/mp4"
        "ogg", "opus" -> "audio/ogg"
        "wma" -> "audio/x-ms-wma"
        "srt" -> "application/x-subrip"
        "vtt" -> "text/vtt"
        "pdf" -> "application/pdf"
        "txt", "log", "md", "kt", "py", "js", "csv" -> "text/plain; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "html", "htm" -> "text/html; charset=utf-8"
        else -> "application/octet-stream"
    }

    private fun sendHtml(out: OutputStream, html: String, keepAlive: Boolean = true) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        sendBytes(out, bytes, "text/html; charset=utf-8", headOnly = false, keepAlive = keepAlive)
    }

    private fun sendJson(
        out: OutputStream,
        status: Int,
        body: String,
        headOnly: Boolean = false,
        keepAlive: Boolean = true,
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $status ${statusText(status)}\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append(connectionHeader(keepAlive))
            append("\r\n")
        }
        out.write(header.toByteArray(Charsets.US_ASCII))
        if (!headOnly) out.write(bytes)
        out.flush()
    }

    private fun sendBytes(
        out: OutputStream,
        bytes: ByteArray,
        mime: String,
        headOnly: Boolean,
        keepAlive: Boolean = true,
        cacheSeconds: Int = 0,
    ) {
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: $mime\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            if (cacheSeconds > 0) append("Cache-Control: public, max-age=$cacheSeconds\r\n")
            append(connectionHeader(keepAlive))
            append("\r\n")
        }
        out.write(header.toByteArray(Charsets.US_ASCII))
        if (!headOnly) out.write(bytes)
        out.flush()
    }

    private fun sendFileBytes(
        out: OutputStream,
        file: File,
        mime: String,
        headers: Map<String, String>,
        headOnly: Boolean,
        keepAlive: Boolean = true,
        cacheSeconds: Int = 0,
    ) {
        if (!file.isFile) throw java.io.FileNotFoundException(file.path)
        val size = file.length()
        val (status, start, end) = parseByteRange(headers["range"], size)
        val length = if (size == 0L) 0L else (end - start + 1L).coerceAtLeast(0L)
        val respHeaders = buildString {
            append("HTTP/1.1 $status ${statusText(status)}\r\n")
            append("Content-Type: $mime\r\n")
            append("Accept-Ranges: bytes\r\n")
            append("Content-Length: $length\r\n")
            if (cacheSeconds > 0) append("Cache-Control: public, max-age=$cacheSeconds\r\n")
            if (status == 206) append("Content-Range: bytes $start-$end/$size\r\n")
            append(connectionHeader(keepAlive))
            append("\r\n")
        }
        out.write(respHeaders.toByteArray(Charsets.US_ASCII))
        if (!headOnly && length > 0L) {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(start)
                val buf = ByteArray(128 * 1024)
                var rem = length
                while (rem > 0L) {
                    val read = raf.read(buf, 0, min(buf.size.toLong(), rem).toInt())
                    if (read < 0) break
                    out.write(buf, 0, read)
                    rem -= read
                }
            }
        }
        out.flush()
    }

    private fun sendContentUriStream(
        out: OutputStream,
        uriStr: String,
        headers: Map<String, String>,
        headOnly: Boolean,
        keepAlive: Boolean,
    ) {
        val uri = Uri.parse(uriStr)
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val size = localRepo.getFileLengthAny(uriStr).coerceAtLeast(0L)
        val (status, start, end) = if (size > 0L) parseByteRange(headers["range"], size) else Triple(200, 0L, 0L)
        val length = if (size > 0L) (end - start + 1L).coerceAtLeast(0L) else -1L
        val useKeepAlive = keepAlive && length >= 0L
        val respHeaders = buildString {
            append("HTTP/1.1 $status ${statusText(status)}\r\n")
            append("Content-Type: $mime\r\n")
            if (size > 0L) {
                append("Accept-Ranges: bytes\r\n")
                append("Content-Length: $length\r\n")
                if (status == 206) append("Content-Range: bytes $start-$end/$size\r\n")
            }
            append(connectionHeader(useKeepAlive))
            append("\r\n")
        }
        out.write(respHeaders.toByteArray(Charsets.US_ASCII))
        if (!headOnly && (length != 0L)) {
            val afd = context.contentResolver.openAssetFileDescriptor(uri, "r")
                ?: throw java.io.FileNotFoundException("Cannot open $uriStr")
            afd.use { fd ->
                FileInputStream(fd.fileDescriptor).use { fis ->
                    if (start > 0L) fis.channel.position(fd.startOffset + start)
                    val buf = ByteArray(128 * 1024)
                    var rem = if (length > 0L) length else Long.MAX_VALUE
                    while (rem > 0L) {
                        val read = fis.read(buf, 0, min(buf.size.toLong(), rem).toInt())
                        if (read < 0) break
                        out.write(buf, 0, read)
                        if (length > 0L) rem -= read
                    }
                }
            }
        }
        out.flush()
    }

    private fun statusText(code: Int): String = when (code) {
        200 -> "OK"
        206 -> "Partial Content"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        else -> "Error"
    }

    private fun readHttpLine(input: InputStream): String? {
        val baos = ByteArrayOutputStream()
        var b = input.read()
        if (b < 0) return null
        while (b >= 0) {
            if (b == '\n'.code) break
            if (b != '\r'.code) baos.write(b)
            b = input.read()
        }
        return baos.toString("UTF-8")
    }

    private fun readBodyString(input: InputStream, contentLen: Int): String {
        if (contentLen <= 0 || contentLen > 2_000_000) return "{}"
        val buf = ByteArray(contentLen)
        var off = 0
        while (off < contentLen) {
            val r = input.read(buf, off, contentLen - off)
            if (r < 0) break
            off += r
        }
        return String(buf, 0, off, Charsets.UTF_8)
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        val map = linkedMapOf<String, String>()
        query.split("&").forEach { pair ->
            val eq = pair.indexOf('=')
            if (eq > 0) {
                val k = URLDecoder.decode(pair.substring(0, eq), "UTF-8")
                val v = URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
                map[k] = v
            }
        }
        return map
    }
}
