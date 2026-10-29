package de.notifyprio.data

import android.content.Context
import de.notifyprio.system.DndController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Zentrale Regel-Logik: speichert App-/Kanal-Regeln und den Fokus-Modus
 * und entscheidet pro Benachrichtigung, ob sie durchgelassen, gebündelt
 * oder unterdrückt wird. Gleichzeitig steuert sie den optionalen System-DND.
 */
class FilterManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("rules", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<RulesState> = _state.asStateFlow()

    // ---------------------------------------------------------------- Entscheidung

    /** Kanal-Regel schlägt App-Regel. */
    fun effectiveAction(pkg: String, channelId: String?): RuleAction {
        val s = _state.value
        if (channelId != null) {
            val channelRule = s.channelActions[channelKey(pkg, channelId)]
            if (channelRule != null && channelRule != RuleAction.DEFAULT) return channelRule
        }
        return s.appActions[pkg] ?: RuleAction.DEFAULT
    }

    /**
     * OFF   : nur eigene Regeln (Wichtig/Normal -> durch, Bündeln, Blocken)
     * FOCUS : Wichtig -> durch, Blocken -> unterdrücken, alles andere -> still bündeln
     * QUIET : Wichtig -> durch, alles andere -> unterdrücken (im Protokoll abrufbar)
     */
    fun decide(pkg: String, channelId: String?): Decision {
        val action = effectiveAction(pkg, channelId)
        return when (_state.value.focusMode) {
            FocusMode.OFF -> when (action) {
                RuleAction.BUNDLE -> Decision.BUNDLE
                RuleAction.SUPPRESS -> Decision.SUPPRESS
                else -> Decision.PASS
            }
            FocusMode.FOCUS -> when (action) {
                RuleAction.PRIORITY -> Decision.PASS
                RuleAction.SUPPRESS -> Decision.SUPPRESS
                else -> Decision.BUNDLE
            }
            FocusMode.QUIET ->
                if (action == RuleAction.PRIORITY) Decision.PASS else Decision.SUPPRESS
        }
    }

    // ---------------------------------------------------------------- Änderungen

    fun setAppAction(pkg: String, action: RuleAction) = update { s ->
        s.copy(appActions = if (action == RuleAction.DEFAULT) s.appActions - pkg else s.appActions + (pkg to action))
    }

    fun setAppsAction(pkgs: Set<String>, action: RuleAction) = update { s ->
        val actions = s.appActions.toMutableMap()
        pkgs.forEach { pkg ->
            if (action == RuleAction.DEFAULT) actions.remove(pkg) else actions[pkg] = action
        }
        s.copy(appActions = actions)
    }

    fun setAppsGroup(pkgs: Set<String>, groupId: String) = update { s ->
        val actions = s.appActions + pkgs.associateWith { RuleAction.BUNDLE }
        val groups = s.appGroups.toMutableMap()
        pkgs.forEach { pkg ->
            if (groupId == DEFAULT_GROUP_ID) groups.remove(pkg) else groups[pkg] = groupId
        }
        s.copy(appActions = actions, appGroups = groups)
    }

    fun createGroup(name: String): String? {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || cleanName.equals(DEFAULT_GROUP_NAME, ignoreCase = true) ||
            _state.value.notificationGroups.any { it.name.equals(cleanName, ignoreCase = true) }
        ) return null
        val id = UUID.randomUUID().toString()
        update { s -> s.copy(notificationGroups = s.notificationGroups + NotificationGroup(id, cleanName)) }
        return id
    }

    fun deleteGroup(groupId: String) = update { s ->
        if (groupId == DEFAULT_GROUP_ID) return@update s
        s.copy(
            notificationGroups = s.notificationGroups.filterNot { it.id == groupId },
            appGroups = s.appGroups.filterValues { it != groupId },
        )
    }

    fun groupForApp(pkg: String): String =
        _state.value.appGroups[pkg]?.takeIf { id ->
            _state.value.notificationGroups.any { it.id == id }
        } ?: DEFAULT_GROUP_ID

    fun setChannelAction(pkg: String, channelId: String, action: RuleAction) = update { s ->
        val key = channelKey(pkg, channelId)
        s.copy(channelActions = if (action == RuleAction.DEFAULT) s.channelActions - key else s.channelActions + (key to action))
    }

    fun setManualAppCategories(pkg: String, categories: Set<String>) = update { s ->
        val updated = if (categories.isEmpty()) {
            s.manualAppCategories - pkg
        } else {
            s.manualAppCategories + (pkg to categories)
        }
        s.copy(manualAppCategories = updated)
    }

    fun setFocusMode(mode: FocusMode) {
        update { it.copy(focusMode = mode) }
        DndController.apply(appContext, mode, _state.value.mirrorSystemDnd)
    }

    fun setMirrorSystemDnd(enabled: Boolean) {
        update { it.copy(mirrorSystemDnd = enabled) }
        DndController.apply(appContext, _state.value.focusMode, enabled)
    }

    /** Merkt sich Kanäle, die in eingehenden Benachrichtigungen vorkommen. */
    fun learnChannel(pkg: String, id: String?, name: String?) {
        if (id.isNullOrBlank()) return
        val displayName = name?.takeIf { it.isNotBlank() } ?: id
        val current = _state.value.knownChannels[pkg].orEmpty()
        val existing = current.firstOrNull { it.id == id }
        if (existing != null && existing.name == displayName) return
        val updated = (current.filterNot { it.id == id } + ChannelInfo(id, displayName))
            .sortedBy { it.name.lowercase() }
        update { it.copy(knownChannels = it.knownChannels + (pkg to updated)) }
    }

    // ---------------------------------------------------------------- Persistenz

    private fun update(transform: (RulesState) -> RulesState) {
        val next = transform(_state.value)
        _state.value = next
        persist(next)
    }

    private fun persist(s: RulesState) {
        val known = JSONObject()
        s.knownChannels.forEach { (pkg, list) ->
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name)) }
            known.put(pkg, arr)
        }
        val apps = JSONObject()
        s.appActions.forEach { (k, v) -> apps.put(k, v.name) }
        val channels = JSONObject()
        s.channelActions.forEach { (k, v) -> channels.put(k, v.name) }
        val manualCategories = JSONObject()
        s.manualAppCategories.forEach { (pkg, categories) ->
            manualCategories.put(pkg, JSONArray(categories.toList()))
        }
        val groups = JSONArray()
        s.notificationGroups.forEach { group ->
            groups.put(JSONObject().put("id", group.id).put("name", group.name))
        }
        val appGroups = JSONObject()
        s.appGroups.forEach { (pkg, groupId) -> appGroups.put(pkg, groupId) }

        val root = JSONObject()
            .put("focus", s.focusMode.name)
            .put("mirror", s.mirrorSystemDnd)
            .put("apps", apps)
            .put("channels", channels)
            .put("known", known)
            .put("manualCategories", manualCategories)
            .put("notificationGroups", groups)
            .put("appGroups", appGroups)
        prefs.edit().putString("state", root.toString()).apply()
    }

    private fun load(): RulesState {
        val raw = prefs.getString("state", null) ?: return RulesState()
        return runCatching {
            val o = JSONObject(raw)
            val known = LinkedHashMap<String, List<ChannelInfo>>()
            o.optJSONObject("known")?.let { k ->
                k.keys().forEach { pkg ->
                    val arr = k.optJSONArray(pkg) ?: return@forEach
                    known[pkg] = List(arr.length()) { i ->
                        val c = arr.getJSONObject(i)
                        ChannelInfo(c.getString("id"), c.optString("name", c.getString("id")))
                    }
                }
            }
            val manualCategories = LinkedHashMap<String, Set<String>>()
            o.optJSONObject("manualCategories")?.let { categories ->
                categories.keys().forEach { pkg ->
                    val array = categories.optJSONArray(pkg) ?: return@forEach
                    manualCategories[pkg] = buildSet {
                        for (index in 0 until array.length()) {
                            array.optString(index).takeIf(String::isNotBlank)?.let(::add)
                        }
                    }
                }
            }
            val groups = o.optJSONArray("notificationGroups")?.let { array ->
                List(array.length()) { index ->
                    val group = array.getJSONObject(index)
                    NotificationGroup(group.getString("id"), group.getString("name"))
                }
            }.orEmpty()
            val appGroups = LinkedHashMap<String, String>()
            o.optJSONObject("appGroups")?.let { stored ->
                stored.keys().forEach { pkg -> appGroups[pkg] = stored.getString(pkg) }
            }
            RulesState(
                focusMode = enumOr(o.optString("focus"), FocusMode.OFF),
                mirrorSystemDnd = o.optBoolean("mirror", false),
                appActions = o.optJSONObject("apps").toActionMap(),
                channelActions = o.optJSONObject("channels").toActionMap(),
                knownChannels = known,
                manualAppCategories = manualCategories,
                notificationGroups = groups,
                appGroups = appGroups,
            )
        }.getOrDefault(RulesState())
    }

    private fun JSONObject?.toActionMap(): Map<String, RuleAction> {
        if (this == null) return emptyMap()
        val out = LinkedHashMap<String, RuleAction>()
        keys().forEach { k -> out[k] = enumOr(optString(k), RuleAction.DEFAULT) }
        return out
    }

    companion object {
        @Volatile private var instance: FilterManager? = null

        fun get(context: Context): FilterManager =
            instance ?: synchronized(this) {
                instance ?: FilterManager(context).also { instance = it }
            }

        fun channelKey(pkg: String, channelId: String) = "$pkg|$channelId"
    }
}

private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
    runCatching { enumValueOf<E>(name.orEmpty()) }.getOrDefault(fallback)
