package de.notifyprio.data

/** Regel pro App bzw. pro Kanal. DEFAULT = keine eigene Regel (Kanal: "wie App"). */
enum class RuleAction { DEFAULT, PRIORITY, BUNDLE, SUPPRESS }

/** Globaler Fokus-Modus. */
enum class FocusMode { OFF, FOCUS, QUIET }

/** Ergebnis der Filter-Entscheidung für eine eingehende Benachrichtigung. */
enum class Decision { PASS, BUNDLE, SUPPRESS }

/** Was mit einer abgefangenen Benachrichtigung passiert ist. */
enum class Outcome { BUNDLED, SUPPRESSED }

data class ChannelInfo(val id: String, val name: String)

data class NotificationGroup(val id: String, val name: String)

data class RulesState(
    val focusMode: FocusMode = FocusMode.OFF,
    /** Fokus/Ruhe zusätzlich im System-"Nicht stören" spiegeln (braucht DND-Zugriff). */
    val mirrorSystemDnd: Boolean = false,
    val appActions: Map<String, RuleAction> = emptyMap(),
    /** Schlüssel: "paket|kanalId" */
    val channelActions: Map<String, RuleAction> = emptyMap(),
    /** Aus eingehenden Benachrichtigungen gelernte Kanäle: paket -> Kanäle */
    val knownChannels: Map<String, List<ChannelInfo>> = emptyMap(),
    /** Manuelle Ergänzungen zu den automatisch erkannten Kategorien: paket -> Kategorienamen */
    val manualAppCategories: Map<String, Set<String>> = emptyMap(),
    val notificationGroups: List<NotificationGroup> = emptyList(),
    /** Paket -> eigene Gruppe. Nicht zugeordnete Apps verwenden die Standardgruppe. */
    val appGroups: Map<String, String> = emptyMap(),
)

data class CapturedNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val channelId: String?,
    val channelName: String?,
    val title: String,
    val text: String,
    val postTime: Long,
    val outcome: Outcome,
    val groupId: String = DEFAULT_GROUP_ID,
)

const val DEFAULT_GROUP_ID = "default"
const val DEFAULT_GROUP_NAME = "Weitere Apps"
