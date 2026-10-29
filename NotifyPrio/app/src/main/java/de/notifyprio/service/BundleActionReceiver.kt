package de.notifyprio.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.notifyprio.data.NotificationStore

/** "Leeren"-Aktion bzw. Wegwischen der Gruppen-Zusammenfassung: gesamtes Bündel verwerfen. */
class BundleActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BundleNotifier.ACTION_CLEAR) return
        val groupId = intent.getStringExtra(BundleNotifier.EXTRA_GROUP_ID)
        if (groupId == null) {
            NotificationStore.get(context).clearBundled(null)
            BundleNotifier.cancelAll(context)
        } else {
            NotificationStore.get(context).clearBundledGroup(groupId)
                .forEach { BundleNotifier.cancelChild(context, it.key) }
            BundleNotifier.updateSummary(context)
        }
    }
}
