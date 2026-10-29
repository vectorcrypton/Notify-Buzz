package de.notifyprio.system

import android.app.NotificationManager
import android.content.Context
import de.notifyprio.data.FocusMode

/**
 * Optionaler Spiegel zum System-"Nicht stören".
 * Braucht "Nicht stören"-Zugriff (Einstellungen oder ADB: cmd notification allow_dnd).
 *
 * FOCUS -> Nur Priorität (System-Einstellung "Priorität" gilt)
 * QUIET -> Nur Wecker
 * OFF   -> vorherigen Filter wiederherstellen (nur wenn WIR ihn gesetzt haben)
 */
object DndController {
    private const val PREFS = "dnd"
    private const val KEY_PREVIOUS = "previous_filter"

    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    fun hasPolicyAccess(ctx: Context): Boolean = nm(ctx).isNotificationPolicyAccessGranted

    fun apply(ctx: Context, mode: FocusMode, mirror: Boolean) {
        val nm = nm(ctx)
        if (!nm.isNotificationPolicyAccessGranted) return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        runCatching {
            if (mirror && mode != FocusMode.OFF) {
                if (!prefs.contains(KEY_PREVIOUS)) {
                    prefs.edit().putInt(KEY_PREVIOUS, nm.currentInterruptionFilter).apply()
                }
                nm.setInterruptionFilter(
                    if (mode == FocusMode.FOCUS) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    else NotificationManager.INTERRUPTION_FILTER_ALARMS
                )
            } else if (prefs.contains(KEY_PREVIOUS)) {
                val previous = prefs.getInt(KEY_PREVIOUS, NotificationManager.INTERRUPTION_FILTER_ALL)
                nm.setInterruptionFilter(
                    if (previous == NotificationManager.INTERRUPTION_FILTER_UNKNOWN)
                        NotificationManager.INTERRUPTION_FILTER_ALL
                    else previous
                )
                prefs.edit().remove(KEY_PREVIOUS).apply()
            }
        }
    }
}
