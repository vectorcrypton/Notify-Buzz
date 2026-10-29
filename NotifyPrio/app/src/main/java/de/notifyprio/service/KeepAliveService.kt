package de.notifyprio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import de.notifyprio.R
import de.notifyprio.system.ListenerGuard
import de.notifyprio.ui.MainActivity

/**
 * Foreground Service (Typ specialUse) – hält den Prozess gegenüber den
 * HyperOS-Akkusparmechanismen am Leben und prüft minütlich, ob der
 * Benachrichtigungs-Listener noch verbunden ist.
 */
class KeepAliveService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val watchdog = object : Runnable {
        override fun run() {
            ListenerGuard.ensureEnabled(this@KeepAliveService)
            ListenerGuard.rebindIfNeeded(this@KeepAliveService)
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        running = true
        handler.post(watchdog)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(watchdog)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_keepalive)
            .setContentTitle("NotifyPrio läuft")
            .setContentText("Dauerbetrieb gegen HyperOS-Akkusparmodus aktiv")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "keepalive"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "de.notifyprio.action.STOP_KEEPALIVE"
        private const val WATCHDOG_INTERVAL_MS = 60_000L
        private const val PREFS = "service"
        private const val KEY_ENABLED = "keepalive_enabled"

        @Volatile
        var running: Boolean = false
            private set

        private fun createChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Dauerbetrieb", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Hält NotifyPrio im Hintergrund aktiv"
                        setShowBadge(false)
                    }
                )
            }
        }

        fun isEnabled(ctx: Context): Boolean =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

        fun setEnabled(ctx: Context, enabled: Boolean) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
            if (enabled) start(ctx) else ctx.stopService(Intent(ctx, KeepAliveService::class.java))
        }

        /** Startet den Dienst; schlägt bewusst still fehl, wenn Android einen Start aus dem Hintergrund verbietet. */
        fun start(ctx: Context) {
            if (!isEnabled(ctx)) return
            runCatching {
                ContextCompat.startForegroundService(ctx, Intent(ctx, KeepAliveService::class.java))
            }
        }
    }
}
