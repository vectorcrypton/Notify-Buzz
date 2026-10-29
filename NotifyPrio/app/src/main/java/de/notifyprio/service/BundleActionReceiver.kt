package de.notifyprio.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.notifyprio.data.NotificationStore

/** "Leeren"-Aktion bzw. Wegwischen der Gruppen-Zusammenfassung: gesamtes Bündel verwerfen. */
class BundleActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BundleNotifier.ACTION_CLEAR) return
        NotificationStore.get(context).clearBundled(null)
        BundleNotifier.cancelAll(context)
    }
}
