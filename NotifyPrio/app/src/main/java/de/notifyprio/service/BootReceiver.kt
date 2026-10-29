package de.notifyprio.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.notifyprio.system.ListenerGuard

/** Nach Neustart oder App-Update: Listener und Dauerbetrieb wiederherstellen. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                ListenerGuard.ensureEnabled(context)
                ListenerGuard.rebindIfNeeded(context)
                KeepAliveService.start(context)
            }
        }
    }
}
