package de.notifyprio.service

import android.app.Notification
import android.app.NotificationChannel
import android.content.ComponentName
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import de.notifyprio.data.CapturedNotification
import de.notifyprio.data.DEFAULT_GROUP_ID
import de.notifyprio.data.Decision
import de.notifyprio.data.FilterManager
import de.notifyprio.data.NotificationStore
import de.notifyprio.data.Outcome

/**
 * Fängt Benachrichtigungen aller Apps ab und wendet die Regeln aus [FilterManager] an:
 *  - PASS      -> unverändert lassen
 *  - BUNDLE    -> entfernen, merken, als Sammel-Benachrichtigung neu ausgeben
 *  - SUPPRESS  -> entfernen, nur im Protokoll behalten
 *
 * Wichtig: Laufende/nicht entfernbare Benachrichtigungen (Anrufe, Wecker, Navigation,
 * Musik, Fortschritt, Foreground-Services) werden nie angefasst.
 */
class PriorityListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        KeepAliveService.start(this)
        BundleNotifier.ensureChannel(this)

        // Kanäle bereits sichtbarer Benachrichtigungen lernen
        val ranking = currentRanking
        runCatching { activeNotifications }.getOrNull()?.forEach { learn(it, ranking) }
        BundleNotifier.reconcile(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected = false
        // HyperOS trennt den Listener gelegentlich -> sofort Neuanbindung anfordern
        runCatching {
            NotificationListenerService.requestRebind(ComponentName(this, PriorityListenerService::class.java))
        }
    }

    override fun onDestroy() {
        connected = false
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        runCatching { handle(sbn, rankingMap) }
    }

    /**
     * Der Nutzer hat eine unserer Kind-Meldungen angetippt oder weggewischt
     * -> Eintrag aus dem Bündel nehmen. Eigene Cancels (APP_CANCEL) ignorieren wir.
     */
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (sbn.packageName != packageName) return
        val key = sbn.notification.extras.getString(BundleNotifier.EXTRA_ORIG_KEY) ?: return
        val store = NotificationStore.get(this)
        when (reason) {
            NotificationListenerService.REASON_CLICK,
            NotificationListenerService.REASON_CANCEL -> {
                store.remove(key)
                BundleNotifier.updateSummary(this)
            }
            NotificationListenerService.REASON_CANCEL_ALL,
            NotificationListenerService.REASON_GROUP_SUMMARY_CANCELED -> store.remove(key)
        }
    }

    // ------------------------------------------------------------------ Logik

    private fun handle(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        if (sbn.packageName == packageName) return

        val n = sbn.notification
        val channel = channelOf(sbn, rankingMap)
        val channelId = n.channelId ?: channel?.id
        val filters = FilterManager.get(this)

        filters.learnChannel(sbn.packageName, channelId, channel?.name?.toString())

        // Gruppen-Zusammenfassungen räumt das System selbst weg, sobald die Kinder fehlen.
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (isProtected(sbn)) return

        when (filters.decide(sbn.packageName, channelId)) {
            Decision.PASS -> Unit
            Decision.BUNDLE -> capture(sbn, channel, channelId, Outcome.BUNDLED)
            Decision.SUPPRESS -> capture(sbn, channel, channelId, Outcome.SUPPRESSED)
        }
    }

    private fun capture(
        sbn: StatusBarNotification,
        channel: NotificationChannel?,
        channelId: String?,
        outcome: Outcome,
    ) {
        val n = sbn.notification
        val extras = n.extras
        val title = (extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE))?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()

        val item = CapturedNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = labelFor(sbn.packageName),
            channelId = channelId,
            channelName = channel?.name?.toString(),
            title = title.take(200),
            text = text.take(400),
            postTime = sbn.postTime,
            outcome = outcome,
            groupId = if (outcome == Outcome.BUNDLED) FilterManager.get(this).groupForApp(sbn.packageName) else DEFAULT_GROUP_ID,
        )
        NotificationStore.get(this).upsert(item, n.contentIntent)
        cancelNotification(sbn.key)
        if (outcome == Outcome.BUNDLED) {
            BundleNotifier.postChild(this, item, n.contentIntent)
        } else {
            BundleNotifier.cancelChild(this, item.key) // war evtl. vorher gebündelt
        }
        BundleNotifier.updateSummary(this)
    }

    private fun learn(sbn: StatusBarNotification, ranking: RankingMap?) {
        if (sbn.packageName == packageName) return
        val channel = channelOf(sbn, ranking)
        FilterManager.get(this).learnChannel(
            sbn.packageName,
            sbn.notification.channelId ?: channel?.id,
            channel?.name?.toString(),
        )
    }

    /** Der Kanal (inkl. Name) steht im Ranking – das geht ohne Spezialrechte. */
    private fun channelOf(sbn: StatusBarNotification, rankingMap: RankingMap?): NotificationChannel? {
        if (rankingMap == null) return null
        val ranking = Ranking()
        return if (rankingMap.getRanking(sbn.key, ranking)) ranking.channel else null
    }

    private fun isProtected(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (!sbn.isClearable) return true                                   // laufend / nicht löschbar
        if ((n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return true
        if (n.fullScreenIntent != null) return true                         // eingehender Anruf/Wecker
        return n.category in PROTECTED_CATEGORIES
    }

    private fun labelFor(pkg: String): String = runCatching {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))).toString()
    }.getOrDefault(pkg)

    companion object {
        @Volatile
        var connected: Boolean = false
            private set

        private val PROTECTED_CATEGORIES = setOf(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_STOPWATCH,
        )
    }
}
