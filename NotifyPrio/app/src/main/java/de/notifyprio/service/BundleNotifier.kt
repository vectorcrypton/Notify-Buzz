package de.notifyprio.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.LruCache
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import de.notifyprio.R
import de.notifyprio.data.CapturedNotification
import de.notifyprio.data.NotificationStore
import de.notifyprio.data.Outcome
import de.notifyprio.ui.MainActivity

/**
 * App-übergreifendes Bündel nach dem Muster "Gruppe mit Kindern":
 *
 *  - Jede abgefangene Benachrichtigung wird als EIGENE Kind-Benachrichtigung neu gepostet
 *    (App-Icon, Titel, Text, Zeit, Original-Tipp-Aktion).
 *  - Alle Kinder (egal von welcher App) hängen unter EINER Gruppen-Zusammenfassung.
 *  - Auf-/Zuklappen übernimmt HyperOS selbst.
 *
 * Die Original-PendingIntent wird direkt als Tipp-Ziel des Kindes verwendet. Sie bleibt
 * deshalb auch nach einem Prozess-Neustart gültig, solange die Kind-Meldung in der Leiste steht.
 */
object BundleNotifier {

    const val ACTION_CLEAR = "de.notifyprio.action.CLEAR_BUNDLE"
    const val EXTRA_ORIG_KEY = "de.notifyprio.orig_key"

    private const val CHANNEL_ID = "bundle"
    private const val GROUP_KEY = "de.notifyprio.BUNDLE_GROUP"
    private const val ID_SUMMARY = 1001
    private const val ID_CHILD = 1002
    private const val TAG_PREFIX = "bundle:"
    private const val MAX_LINES = 7

    private val iconCache = LruCache<String, Bitmap>(32)

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Gebündelte Benachrichtigungen", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Abgefangene Meldungen aller Apps, gesammelt in einer Gruppe"
                    setShowBadge(true)
                }
            )
        }
    }

    // ------------------------------------------------------------------ Kinder

    /** Postet (oder aktualisiert) die Kind-Benachrichtigung zu einem abgefangenen Eintrag. */
    fun postChild(ctx: Context, item: CapturedNotification, contentIntent: PendingIntent?) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        ensureChannel(ctx)

        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bundle)
            .setContentTitle(item.title.ifBlank { item.appLabel })
            .setSubText(item.appLabel)
            .setWhen(item.postTime)
            .setShowWhen(true)
            .setGroup(GROUP_KEY)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent ?: fallbackIntent(ctx, item))
            .addExtras(Bundle().apply { putString(EXTRA_ORIG_KEY, item.key) })

        if (item.text.isNotBlank()) {
            builder.setContentText(item.text)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(item.text))
        }
        appIcon(ctx, item.packageName)?.let { builder.setLargeIcon(it) }

        nm.notify(TAG_PREFIX + item.key, ID_CHILD, builder.build())
    }

    fun cancelChild(ctx: Context, key: String) {
        ctx.getSystemService(NotificationManager::class.java).cancel(TAG_PREFIX + key, ID_CHILD)
    }

    /** Entfernt alle Kinder und die Zusammenfassung. */
    fun cancelAll(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        runCatching {
            nm.activeNotifications
                .filter { it.tag?.startsWith(TAG_PREFIX) == true }
                .forEach { nm.cancel(it.tag, it.id) }
        }
        nm.cancel(ID_SUMMARY)
    }

    // ------------------------------------------------------------------ Zusammenfassung

    /** Aktualisiert die Gruppen-Zusammenfassung anhand des Stores (oder entfernt sie, wenn leer). */
    fun updateSummary(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        ensureChannel(ctx)

        val items = NotificationStore.get(ctx).items.value.filter { it.outcome == Outcome.BUNDLED }
        if (items.isEmpty()) {
            nm.cancel(ID_SUMMARY)
            return
        }

        val inbox = NotificationCompat.InboxStyle()
            .setBigContentTitle("${items.size} gebündelte Benachrichtigungen")
        items.take(MAX_LINES).forEach { inbox.addLine(lineOf(it)) }
        if (items.size > MAX_LINES) inbox.setSummaryText("+${items.size - MAX_LINES} weitere")

        val summary = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bundle)
            .setContentTitle("${items.size} gebündelte Benachrichtigungen")
            .setContentText(items.map { it.appLabel }.distinct().joinToString(", "))
            .setNumber(items.size)
            .setWhen(items.first().postTime)
            .setStyle(inbox)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setContentIntent(openIntent(ctx))
            .setDeleteIntent(clearIntent(ctx))
            .addAction(0, "Leeren", clearIntent(ctx))
            .build()
        nm.notify(ID_SUMMARY, summary)
    }

    /**
     * Gleicht Store und Leiste ab (z. B. nach Neustart): Einträge, deren Kind-Meldung
     * nicht mehr in der Leiste steht, werden aus dem Store entfernt.
     */
    fun reconcile(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        val activeKeys = runCatching { nm.activeNotifications }.getOrNull()
            ?.mapNotNull { it.tag?.takeIf { tag -> tag.startsWith(TAG_PREFIX) }?.removePrefix(TAG_PREFIX) }
            ?.toSet() ?: return
        NotificationStore.get(ctx).retainBundled(activeKeys)
        updateSummary(ctx)
    }

    // ------------------------------------------------------------------ Helfer

    private fun lineOf(i: CapturedNotification): CharSequence {
        val body = listOf(i.title, i.text).filter { it.isNotBlank() }.joinToString(": ")
        return if (body.isEmpty()) i.appLabel else "${i.appLabel} · $body"
    }

    private fun appIcon(ctx: Context, pkg: String): Bitmap? {
        iconCache.get(pkg)?.let { return it }
        return runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(128, 128) }
            .getOrNull()
            ?.also { iconCache.put(pkg, it) }
    }

    /** Hatte die Original-Meldung keine Tipp-Aktion: App starten, sonst unsere Bündel-Liste öffnen. */
    private fun fallbackIntent(ctx: Context, item: CapturedNotification): PendingIntent {
        val launch = ctx.packageManager.getLaunchIntentForPackage(item.packageName)
            ?: return openIntent(ctx)
        return PendingIntent.getActivity(
            ctx, item.packageName.hashCode(), launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun openIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_TAB, "bundle")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            ctx, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun clearIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, BundleActionReceiver::class.java).setAction(ACTION_CLEAR)
        return PendingIntent.getBroadcast(
            ctx, 2, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
