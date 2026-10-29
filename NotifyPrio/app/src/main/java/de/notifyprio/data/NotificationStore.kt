package de.notifyprio.data

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Hält abgefangene Benachrichtigungen (gebündelt / unterdrückt).
 * Metadaten werden persistiert; die Original-PendingIntents nur im Speicher
 * (nach einem Prozess-Neustart öffnet ein Tipp die jeweilige App).
 */
class NotificationStore private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("captured", Context.MODE_PRIVATE)
    private val intents = ConcurrentHashMap<String, PendingIntent>()

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<CapturedNotification>> = _items.asStateFlow()

    fun upsert(item: CapturedNotification, contentIntent: PendingIntent?) {
        if (contentIntent != null) intents[item.key] = contentIntent else intents.remove(item.key)
        _items.update { old -> trim(listOf(item) + old.filterNot { it.key == item.key }) }
        persist()
    }

    fun remove(key: String) {
        intents.remove(key)
        _items.update { old -> old.filterNot { it.key == key } }
        persist()
    }

    /** Leert alle gebündelten Einträge (optional nur einer App). */
    fun clearBundled(pkg: String?) {
        val gone = _items.value
            .filter { it.outcome == Outcome.BUNDLED && (pkg == null || it.packageName == pkg) }
            .map { it.key }
            .toSet()
        gone.forEach { intents.remove(it) }
        _items.update { old -> old.filterNot { it.key in gone } }
        persist()
    }

    fun clearBundledGroup(groupId: String): List<CapturedNotification> {
        val gone = _items.value.filter { it.outcome == Outcome.BUNDLED && it.groupId == groupId }
        gone.forEach { intents.remove(it.key) }
        val keys = gone.mapTo(mutableSetOf()) { it.key }
        _items.update { old -> old.filterNot { it.key in keys } }
        persist()
        return gone
    }

    fun moveBundledGroup(fromGroupId: String, toGroupId: String): List<CapturedNotification> {
        val moved = mutableListOf<CapturedNotification>()
        _items.update { old ->
            old.map { item ->
                if (item.outcome == Outcome.BUNDLED && item.groupId == fromGroupId) {
                    item.copy(groupId = toGroupId).also(moved::add)
                } else {
                    item
                }
            }
        }
        if (moved.isNotEmpty()) persist()
        return moved
    }

    fun moveBundledAppsToGroups(
        packages: Set<String>,
        groupForPackage: (String) -> String,
    ): List<CapturedNotification> {
        val moved = mutableListOf<CapturedNotification>()
        _items.update { old ->
            old.map { item ->
                if (item.outcome == Outcome.BUNDLED && item.packageName in packages) {
                    val groupId = groupForPackage(item.packageName)
                    if (item.groupId != groupId) item.copy(groupId = groupId).also(moved::add) else item
                } else {
                    item
                }
            }
        }
        if (moved.isNotEmpty()) persist()
        return moved
    }

    fun contentIntent(key: String): PendingIntent? = intents[key]

    /** Behält nur gebündelte Einträge, deren Schlüssel in [keys] vorkommt (Abgleich mit der Leiste). */
    fun retainBundled(keys: Set<String>) {
        val before = _items.value
        val after = before.filterNot { it.outcome == Outcome.BUNDLED && it.key !in keys }
        if (after.size == before.size) return
        before.filter { it !in after }.forEach { intents.remove(it.key) }
        _items.value = after
        persist()
    }

    fun clear(outcome: Outcome) {
        if (outcome == Outcome.BUNDLED) {
            clearBundled(null)
        } else {
            _items.update { old -> old.filterNot { it.outcome == Outcome.SUPPRESSED } }
            persist()
        }
    }

    /** Feuert die Original-Aktion der Benachrichtigung oder startet ersatzweise die App. */
    fun open(context: Context, item: CapturedNotification) {
        val pi = intents[item.key]
        val sent = pi != null && runCatching {
            pi.send(context, 0, null, null, null, null, backgroundStartOptions())
        }.isSuccess
        if (!sent) {
            context.packageManager.getLaunchIntentForPackage(item.packageName)?.let {
                runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }

    private fun backgroundStartOptions(): Bundle? =
        if (Build.VERSION.SDK_INT >= 34) {
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        } else null

    private fun trim(list: List<CapturedNotification>): List<CapturedNotification> {
        var bundled = 0
        var suppressed = 0
        return list.filter {
            if (it.outcome == Outcome.BUNDLED) ++bundled <= MAX_PER_OUTCOME else ++suppressed <= MAX_PER_OUTCOME
        }
    }

    private fun persist() {
        val arr = JSONArray()
        _items.value.forEach {
            arr.put(
                JSONObject()
                    .put("key", it.key)
                    .put("pkg", it.packageName)
                    .put("app", it.appLabel)
                    .put("chId", it.channelId ?: JSONObject.NULL)
                    .put("chName", it.channelName ?: JSONObject.NULL)
                    .put("title", it.title)
                    .put("text", it.text)
                    .put("time", it.postTime)
                    .put("outcome", it.outcome.name)
                    .put("groupId", it.groupId)
            )
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    private fun load(): List<CapturedNotification> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                CapturedNotification(
                    key = o.getString("key"),
                    packageName = o.getString("pkg"),
                    appLabel = o.optString("app"),
                    channelId = if (o.isNull("chId")) null else o.getString("chId"),
                    channelName = if (o.isNull("chName")) null else o.getString("chName"),
                    title = o.optString("title"),
                    text = o.optString("text"),
                    postTime = o.optLong("time"),
                    outcome = runCatching { Outcome.valueOf(o.getString("outcome")) }.getOrDefault(Outcome.BUNDLED),
                    groupId = o.optString("groupId", DEFAULT_GROUP_ID),
                )
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val MAX_PER_OUTCOME = 100
        @Volatile private var instance: NotificationStore? = null

        fun get(context: Context): NotificationStore =
            instance ?: synchronized(this) {
                instance ?: NotificationStore(context).also { instance = it }
            }
    }
}
