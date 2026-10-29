package de.notifyprio.ui

import android.content.pm.ApplicationInfo

private fun normalizeForMatching(value: String): String =
    value.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")

enum class AppCategory(val label: String, private val keywords: List<String>) {
    MUSIC(
        "Musik",
        listOf(
            "music", "musik", "spotify", "soundcloud", "deezer", "tidal", "shazam", "bandcamp",
            "podcast", "audible", "audiobook", "amazon music", "youtube music", "apple music",
            "pandora", "anghami", "pocket casts", "antenna pod", "castbox", "radio",
        ),
    ),
    SHOPPING(
        "Shopping",
        listOf(
            "shopping", "shop", "store", "amazon", "ebay", "etsy", "temu", "aliexpress", "zalando",
            "vinted", "shein", "wish", "otto", "about you", "thomann", "ikea", "mercari",
            "marketplace", "asos", "mediamarkt", "saturn", "walmart", "target", "shopify",
        ),
    ),
    COMMUNICATION(
        "Kommunikation",
        listOf(
            "whatsapp", "telegram", "signal", "messenger", "messages", "sms", "gmail", "email",
            "e-mail", "mail", "outlook", "protonmail", "threema", "viber", "wechat", "kakaotalk",
            "line", "imo", "element", "matrix", "discord", "slack", "teams", "zoom", "meet",
            "jitsi", "phone", "dialer", "contacts", "skype", "webex", "nextcloud talk",
        ),
    ),
    SOCIAL(
        "Social",
        listOf(
            "instagram", "tiktok", "facebook", "snapchat", "pinterest", "reddit", "twitter",
            "threads", "mastodon", "linkedin", "bluesky", "tumblr", "bereal", "vk.com",
            "social", "community", "lemmy",
        ),
    ),
    VIDEO(
        "Video & Streaming",
        listOf(
            "youtube", "netflix", "prime video", "disney", "twitch", "crunchyroll", "plex",
            "dazn", "joyn", "mediathek", "video", "streaming", "hulu", "max", "paramount",
            "peacock", "vimeo", "curiositystream", "nebula", "kick.com", "iplayer", "ard",
            "zdf", "waipu", "magenta tv",
        ),
    ),
    PRODUCTIVITY(
        "Produktivität",
        listOf(
            "notion", "onenote", "office", "docs", "sheets", "drive", "calendar", "kalender",
            "todo", "tasks", "keep", "evernote", "trello", "asana", "clickup", "monday.com",
            "todoist", "ticktick", "obsidian", "notability", "workflow", "project management",
            "libreoffice", "dropbox", "owncloud", "nextcloud", "syncthing",
        ),
    ),
    FINANCE(
        "Finanzen",
        listOf(
            "bank", "banking", "paypal", "wallet", "finance", "finanzen", "sparkasse", "volksbank",
            "revolut", "wise", "n26", "klarna", "commerzbank", "deutsche bank", "ing diba",
            "dkb", "trade republic", "scalable", "broker", "trading", "coinbase", "binance",
            "crypto", "cryptocurrency", "budget", "tax", "steuer", "money", "überweisung",
        ),
    ),
    TRAVEL(
        "Reisen & Navigation",
        listOf(
            "maps", "navigation", "waze", "uber", "bolt", "bahn", "db navigator", "flixbus",
            "booking", "airbnb", "trip", "travel", "reise", "flight", "flug", "lufthansa",
            "ryanair", "easyjet", "trainline", "omio", "komoot", "outdooractive", "park",
            "parking", "taxi", "transit", "oepnv", "bvg", "vbb", "mvv", "hvv",
        ),
    ),
    GAMES(
        "Spiele",
        listOf(
            "game", "gaming", "roblox", "minecraft", "clash", "pubg", "genshin", "pokemon",
            "steam", "supercell", "playrix", "fortnite", "epic games", "xbox", "playstation",
            "nintendo", "king.com", "candy crush", "riot games", "riotgames", "ea games",
            "electronic arts", "zynga", "gamehouse",
        ),
    ),
    NEWS(
        "Nachrichten",
        listOf(
            "news", "nachrichten", "tagesschau", "spiegel", "zeit", "faz", "bild", "n-tv",
            "stern", "heise", "bbc", "cnn", "guardian", "reuters", "associated press",
            "new york times", "washington post", "euronews", "dw.com", "ard-aktuell",
            "journal", "newspaper", "zeitung",
        ),
    ),
    HEALTH(
        "Gesundheit & Fitness",
        listOf(
            "fitness", "health", "gesundheit", "strava", "adidas running", "google fit",
            "samsung health", "headspace", "calm", "workout", "running", "runkeeper", "nike run",
            "garmin", "fitbit", "komoot", "sleep", "meditation", "yoga", "myfitnesspal",
            "lifesum", "doctor", "arzt", "pharmacy", "apotheke", "medical", "patient",
        ),
    ),
    FOOD(
        "Essen & Trinken",
        listOf(
            "food", "restaurant", "lieferando", "wolt", "uber eats", "doordash", "just eat",
            "mcdonald", "burger king", "kfc", "too good to go", "yuka", "openfoodfacts",
            "recipe", "rezept", "kitchen", "cookbook", "cooking", "delivery", "lieferdienst",
            "starbucks", "coffee", "kaffee", "pizza",
        ),
    ),
    EDUCATION(
        "Bildung",
        listOf(
            "duolingo", "language", "learn", "learning", "lernen", "schule", "school", "study",
            "studium", "university", "universität", "coursera", "udemy", "khan academy",
            "anki", "quizlet", "photomath", "babbel", "busuu", "translate", "übersetzer",
            "dictionary", "wörterbuch", "mooc", "educational",
        ),
    ),
    PHOTOGRAPHY(
        "Fotos & Kamera",
        listOf(
            "camera", "kamera", "photo", "foto", "gallery", "galerie", "lightroom", "snapseed",
            "picsart", "photoshop", "google photos", "google fotos", "flickr", "vsco",
            "photography", "image editor", "picture", "bilder",
        ),
    ),
    HOME(
        "Smart Home",
        listOf(
            "smart home", "smarthome", "home assistant", "homekit", "smartthings", "alexa",
            "google home", "philips hue", "hue", "tapo", "tuya", "eufy", "ring", "nest",
            "ikea home", "matter", "home connect", "roborock", "ecovacs", "sonos",
        ),
    ),
    WEATHER(
        "Wetter",
        listOf(
            "weather", "wetter", "forecast", "wetteronline", "accuweather", "weatherunderground",
            "yr.no", "meteoblue", "windy", "weather channel", "meteoswiss", "dwd", "rain radar",
        ),
    ),
    BROWSER(
        "Browser",
        listOf(
            "browser", "chrome", "firefox", "edge", "opera", "brave", "vivaldi", "duckduckgo",
            "samsung internet", "kiwi browser", "tor browser", "web browser",
        ),
    ),
    READING(
        "Bücher & Lesen",
        listOf(
            "kindle", "ebook", "e-book", "book", "books", "bücher", "reader", "reading",
            "readera", "moon+ reader", "kobo", "tolino", "google play books", "play books",
            "libby", "instapaper", "pocket", "readwise", "webtoon", "manga",
        ),
    ),
    SECURITY(
        "Sicherheit",
        listOf(
            "antivirus", "anti-virus", "security", "sicherheit", "password manager", "passwords",
            "bitwarden", "1password", "keepass", "lastpass", "nordvpn", "expressvpn", "proton vpn",
            "vpn", "authenticator", "2fa", "malwarebytes", "avast", "norton", "eset",
        ),
    ),
    PERSONALIZATION(
        "Personalisierung",
        listOf(
            "launcher", "wallpaper", "hintergrund", "icon pack", "icons", "ringtones", "klingelton",
            "font", "schriftart", "theme", "thema", "widget", "customization", "personalization",
        ),
    ),
    TOOLS(
        "Tools",
        listOf(
            "settings", "einstellungen", "calculator", "taschenrechner", "file manager",
            "dateimanager", "files", "cleaner", "scanner", "qr code", "bluetooth", "remote",
            "utility", "toolbox", "flashlight", "taschenlampe", "recorder", "screen recorder",
            "system ui", "permission controller", "package installer", "print service",
        ),
    ),
    OTHER("Sonstiges", emptyList()),
    ;

    private val normalizedKeywords = keywords.map(::normalizeForMatching)

    companion object {
        fun forApp(app: AppEntry): Set<AppCategory> {
            val searchable = " ${normalizeForMatching("${app.label} ${app.pkg}")} "
            val matched = entries.filterTo(mutableSetOf()) { category ->
                category != OTHER && category.normalizedKeywords.any { keyword -> " $keyword " in searchable }
            }

            when (app.platformCategory) {
                ApplicationInfo.CATEGORY_AUDIO -> matched += MUSIC
                ApplicationInfo.CATEGORY_VIDEO -> matched += VIDEO
                ApplicationInfo.CATEGORY_GAME -> matched += GAMES
                ApplicationInfo.CATEGORY_IMAGE -> matched += PHOTOGRAPHY
                ApplicationInfo.CATEGORY_SOCIAL -> matched += SOCIAL
                ApplicationInfo.CATEGORY_NEWS -> matched += NEWS
                ApplicationInfo.CATEGORY_MAPS -> matched += TRAVEL
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> matched += PRODUCTIVITY
                ApplicationInfo.CATEGORY_ACCESSIBILITY -> matched += TOOLS
            }

            return matched.ifEmpty { setOf(OTHER) }
        }
    }
}
