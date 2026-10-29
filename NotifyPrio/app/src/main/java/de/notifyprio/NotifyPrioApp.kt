package de.notifyprio

import android.app.Application
import de.notifyprio.data.FilterManager
import de.notifyprio.data.NotificationStore
import de.notifyprio.service.BundleNotifier

class NotifyPrioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FilterManager.get(this)
        NotificationStore.get(this)
        BundleNotifier.ensureChannel(this)
    }
}
