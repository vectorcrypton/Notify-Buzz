package de.notifyprio.system

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import de.notifyprio.service.PriorityListenerService

/** Hält den Benachrichtigungs-Listener aktiv und angebunden. */
object ListenerGuard {

    fun component(ctx: Context) = ComponentName(ctx, PriorityListenerService::class.java)

    fun isEnabledInSettings(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    /**
     * Trägt den Listener selbst in die aktivierten Listener ein.
     * Funktioniert nur, wenn per ADB WRITE_SECURE_SETTINGS erteilt wurde.
     */
    fun ensureEnabled(ctx: Context): Boolean {
        if (isEnabledInSettings(ctx)) return true
        if (ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return runCatching {
            val cr = ctx.contentResolver
            val flat = Settings.Secure.getString(cr, "enabled_notification_listeners").orEmpty()
            val me = component(ctx).flattenToString()
            if (flat.split(":").contains(me)) {
                true
            } else {
                Settings.Secure.putString(
                    cr, "enabled_notification_listeners",
                    if (flat.isEmpty()) me else "$flat:$me"
                )
            }
        }.getOrDefault(false)
    }

    /** Fordert vom System eine Neuanbindung an, falls der Listener aktiv, aber nicht verbunden ist. */
    fun rebindIfNeeded(ctx: Context) {
        if (isEnabledInSettings(ctx) && !PriorityListenerService.connected) {
            runCatching { NotificationListenerService.requestRebind(component(ctx)) }
        }
    }
}
