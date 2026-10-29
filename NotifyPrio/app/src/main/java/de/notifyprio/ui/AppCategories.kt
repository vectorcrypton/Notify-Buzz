package de.notifyprio.ui

enum class AppCategory(val label: String, private val keywords: List<String>) {
    MUSIC(
        "Musik",
        listOf("music", "musik", "spotify", "soundcloud", "deezer", "tidal", "shazam", "bandcamp", "podcast", "audible"),
    ),
    SHOPPING(
        "Shopping",
        listOf("shopping", "shop", "store", "amazon", "ebay", "etsy", "temu", "aliexpress", "zalando", "vinted", "shein"),
    ),
    COMMUNICATION(
        "Kommunikation",
        listOf("whatsapp", "telegram", "signal", "messenger", "messages", "sms", "mail", "email", "discord", "slack", "teams", "zoom"),
    ),
    SOCIAL(
        "Social",
        listOf("instagram", "tiktok", "facebook", "snapchat", "pinterest", "reddit", "twitter", "threads", "mastodon", "linkedin"),
    ),
    VIDEO(
        "Video & Streaming",
        listOf("youtube", "netflix", "prime video", "disney", "twitch", "crunchyroll", "plex", "dazn", "joyn", "mediathek"),
    ),
    PRODUCTIVITY(
        "Produktivität",
        listOf("notion", "onenote", "office", "docs", "sheets", "drive", "calendar", "kalender", "todo", "tasks", "keep", "evernote"),
    ),
    FINANCE(
        "Finanzen",
        listOf("bank", "banking", "paypal", "wallet", "finance", "finanzen", "sparkasse", "volksbank", "revolut", "wise", "n26", "klarna"),
    ),
    TRAVEL(
        "Reisen & Navigation",
        listOf("maps", "navigation", "waze", "uber", "bolt", "bahn", "db navigator", "flixbus", "booking", "airbnb", "trip"),
    ),
    GAMES(
        "Spiele",
        listOf("game", "gaming", "roblox", "minecraft", "clash", "pubg", "genshin", "pokemon", "steam", "supercell", "playrix"),
    ),
    NEWS(
        "Nachrichten",
        listOf("news", "nachrichten", "tagesschau", "spiegel", "zeit", "faz", "bild", "n-tv", "stern", "heise"),
    ),
    HEALTH(
        "Gesundheit & Fitness",
        listOf("fitness", "health", "gesundheit", "strava", "adidas running", "google fit", "samsung health", "headspace", "calm"),
    ),
    TOOLS(
        "Tools",
        listOf("settings", "einstellungen", "calculator", "taschenrechner", "file manager", "dateimanager", "authenticator", "cleaner"),
    ),
    OTHER("Sonstiges", emptyList()),
    ;

    companion object {
        fun forApp(app: AppEntry): Set<AppCategory> {
            val searchable = "${app.label} ${app.pkg}".lowercase()
            val matched = entries.filter { category ->
                category != OTHER && category.keywords.any(searchable::contains)
            }.toSet()
            return matched.ifEmpty { setOf(OTHER) }
        }
    }
}
