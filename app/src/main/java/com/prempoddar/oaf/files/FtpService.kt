package com.prempoddar.oaf.files

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.prempoddar.oaf.MainActivity
import com.prempoddar.oaf.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.net.BindException

/**
 * Runs the FTP share while it's on, with a notification showing the address and a Stop button.
 * Files that arrive or change are passed to the media library so galleries and players see them.
 */
class FtpService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: FtpServer? = null
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val settings = FtpShare.settings(this).value ?: FtpSettings()
        val notification = notification(settings, FtpShare.addresses(settings.port))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID, notification)
        }
        if (server == null) scope.launch { serve(settings) }
        return START_NOT_STICKY
    }

    private suspend fun serve(settings: FtpSettings) {
        val root = FtpShare.root(settings)
        val config = FtpServer.Config(
            root = root, port = settings.port, readOnly = settings.readOnly,
            user = if (settings.anonymous) null else settings.user, password = if (settings.anonymous) null else FtpShare.password(settings),
        )
        val s = FtpServer(config, object : FtpServer.Listener {
            override fun clients(count: Int) = FtpShare.update { it.copy(clients = count) }
            override fun changed(file: File) { MediaScannerConnection.scanFile(applicationContext, arrayOf(file.path), null, null) }
            override fun log(line: String) = FtpShare.log(line)
        })
        try {
            s.start()
        } catch (e: BindException) {
            fail("Port ${settings.port} is already in use. Pick another one.")
            return
        } catch (e: Exception) {
            fail("Couldn't start sharing: ${e.message ?: e.javaClass.simpleName}")
            return
        }
        server = s
        if (settings.keepAwake) hold()
        FtpShare.update { it.copy(running = true, starting = false, error = null) }
        FtpShare.log("Sharing ${root.path}")
        // Networks come and go (Wi-Fi changes, hotspot on): keep the addresses current.
        var shown = emptyList<String>()
        while (scope.isActive) {
            val now = FtpShare.addresses(s.port)
            if (now != shown) {
                shown = now
                FtpShare.update { it.copy(addresses = now) }
                getSystemService(NotificationManager::class.java).notify(ID, notification(settings, now))
            }
            delay(5_000)
        }
    }

    private fun fail(message: String) {
        FtpShare.update { it.copy(running = false, starting = false, error = message) }
        stopSelf()
    }

    @Suppress("DEPRECATION")
    private fun hold() {
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "oaf:ftp").apply { acquire() }
        val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
        wifi = applicationContext.getSystemService(WifiManager::class.java).createWifiLock(mode, "oaf:ftp").apply { acquire() }
    }

    override fun onDestroy() {
        scope.cancel()
        server?.stop(); server = null
        wake?.takeIf { it.isHeld }?.release(); wake = null
        wifi?.takeIf { it.isHeld }?.release(); wifi = null
        FtpShare.update { it.copy(running = false, starting = false, clients = 0, addresses = emptyList()) }
        FtpShare.log("Stopped sharing")
        super.onDestroy()
    }

    private fun notification(settings: FtpSettings, addresses: List<String>): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Sharing over Wi-Fi", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).setAction(OPEN).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(this, 1, Intent(this, FtpService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val text = addresses.firstOrNull() ?: "Connect this phone to Wi-Fi to share"
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (settings.readOnly) "Sharing files (read only)" else "Sharing files")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    companion object {
        const val STOP = "com.prempoddar.oaf.ftp.STOP"
        /** The notification opens the share screen. */
        const val OPEN = "com.prempoddar.oaf.ftp.OPEN"
        private const val CHANNEL = "ftp"
        private const val ID = 42
    }
}
