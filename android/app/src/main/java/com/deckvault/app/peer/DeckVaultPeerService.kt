package com.deckvault.app.peer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.deckvault.app.MainActivity
import com.deckvault.app.model.DriveInfo
import com.deckvault.app.model.QuickFolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PeerServiceStatus(
    val running: Boolean = false,
    val hostId: String = "",
    val deviceName: String = "",
    val pin: String = "",
    val port: Int = AndroidPeerServer.HTTP_PORT,
    val ips: List<String> = emptyList(),
)

class DeckVaultPeerService : Service() {

    private var server: AndroidPeerServer? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    @Suppress("DEPRECATION")
    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return
        runCatching {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wm.createWifiLock(mode, "DeckVaultPeerWifi").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        acquireWifiLock()
        if (server == null) {
            server = AndroidPeerServer(applicationContext).also { it.start() }
        }
        val currentServer = server!!
        activeServer = currentServer
        publishStatus(running = true, srv = currentServer)

        val notification = buildNotification(currentServer)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        }
        wifiLock = null
        server?.stop()
        server = null
        activeServer = null
        publishStatus(running = false, srv = null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java) ?: return
            val existing = mgr.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "DeckVault Device Sharing",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Allows other DeckVault devices and browsers to browse and transfer files."
                    setShowBadge(false)
                }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    private fun buildNotification(srv: AndroidPeerServer): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = Intent(this, DeckVaultPeerService::class.java).apply { action = ACTION_STOP }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val ip = srv.getLocalIpv4Addresses().firstOrNull() ?: "LAN"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("DeckVault Sharing Active")
            .setContentText("$ip:${AndroidPeerServer.HTTP_PORT}  •  PIN ${srv.pin}")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStop)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "deckvault_peer"
        private const val NOTIFICATION_ID = 48720
        private const val ACTION_STOP = "com.deckvault.app.peer.STOP"

        @Volatile
        var activeServer: AndroidPeerServer? = null
            private set

        private val _status = MutableStateFlow(PeerServiceStatus())
        val status: StateFlow<PeerServiceStatus> = _status.asStateFlow()

        private fun publishStatus(running: Boolean, srv: AndroidPeerServer?) {
            _status.value = if (running && srv != null) {
                PeerServiceStatus(
                    running = true,
                    hostId = srv.hostId,
                    deviceName = srv.deviceName,
                    pin = srv.pin,
                    port = AndroidPeerServer.HTTP_PORT,
                    ips = srv.getLocalIpv4Addresses(),
                )
            } else {
                PeerServiceStatus(running = false)
            }
        }

        fun start(context: Context) {
            val intent = Intent(context, DeckVaultPeerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DeckVaultPeerService::class.java))
        }

        fun rotatePin(): String? {
            val srv = activeServer ?: return null
            val newPin = srv.rotatePin()
            publishStatus(running = true, srv = srv)
            return newPin
        }

        fun refreshIps() {
            val srv = activeServer ?: return
            publishStatus(running = true, srv = srv)
        }

        fun localDrives(context: Context): List<DriveInfo> {
            return (activeServer ?: AndroidPeerServer(context.applicationContext)).detectStorageDrives()
        }

        fun localQuickFolders(context: Context): List<QuickFolder> {
            return (activeServer ?: AndroidPeerServer(context.applicationContext)).detectQuickFolders()
        }

        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
            return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)
        }

        fun batteryOptimizationIntent(context: Context): Intent =
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
