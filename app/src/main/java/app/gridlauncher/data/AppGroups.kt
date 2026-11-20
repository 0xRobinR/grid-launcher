package app.gridlauncher.data

import android.content.pm.ApplicationInfo

/** A suggested screen: a set of apps that belong together, by type or by publisher. */
data class AppGroup(val id: String, val title: String, val icon: String, val apps: List<AppEntry>)

/**
 * Sorts apps into groups for "Make screens from app groups". Types come from the category each
 * app declares, helped by its name and package for the ones Android has no category for (such as
 * finance and shopping). Publishers come from the package name's owner, like com.google.
 */
object AppGroups {
    private class Type(val id: String, val title: String, val icon: String, val category: Int?, val words: List<String>)

    // Checked in order: the first that matches wins. Word matches come first, as an app's own
    // category is often missing or too broad.
    private val TYPES = listOf(
        Type("finance", "Finance", "wallet", null, listOf("bank", "finance", "wallet", "money", "invest", "trading", "trade", "stock", "crypto", "upi", "paytm", "phonepe", "gpay", "paypal", "zerodha", "groww", "broker", "credit", "loan", "tax")),
        Type("shopping", "Shopping", "cart", null, listOf("shop", "cart", "amazon", "flipkart", "myntra", "ebay", "meesho", "ajio", "etsy", "aliexpress", "grocery", "blinkit", "zepto", "instamart", "bigbasket")),
        Type("chat", "Chat & mail", "chat", null, listOf("whatsapp", "telegram", "signal", "messag", "messenger", "mail", "gmail", "outlook", "sms", "chat", "discord", "slack", "dialer", "contacts")),
        Type("health", "Health & fitness", "heart", null, listOf("fitness", "fitbit", "health", "workout", "strava", "yoga", "meditat", "sleep", "calorie")),
        Type("games", "Games", "game", ApplicationInfo.CATEGORY_GAME, listOf("game", "games", "chess", "puzzle")),
        Type("social", "Social", "users", ApplicationInfo.CATEGORY_SOCIAL, listOf("instagram", "facebook", "twitter", "threads", "reddit", "snapchat", "linkedin", "pinterest", "tiktok")),
        Type("music", "Music & audio", "music", ApplicationInfo.CATEGORY_AUDIO, listOf("music", "spotify", "podcast", "radio", "audio", "soundcloud")),
        Type("video", "Video", "film", ApplicationInfo.CATEGORY_VIDEO, listOf("video", "youtube", "netflix", "prime video", "hotstar", "twitch", "jiocinema")),
        Type("photos", "Photos & camera", "camera", ApplicationInfo.CATEGORY_IMAGE, listOf("photo", "camera", "gallery", "lightroom", "snapseed")),
        Type("news", "News & reading", "book", ApplicationInfo.CATEGORY_NEWS, listOf("news", "reader", "kindle", "medium", "feed", "magazine", "books")),
        Type("travel", "Maps & travel", "compass", ApplicationInfo.CATEGORY_MAPS, listOf("map", "maps", "uber", "olacabs", "lyft", "travel", "flight", "airline", "rail", "metro", "booking", "airbnb", "trip")),
        Type("work", "Work & productivity", "work", ApplicationInfo.CATEGORY_PRODUCTIVITY, listOf("docs", "sheets", "slides", "office", "drive", "notes", "notion", "calendar", "todo", "task", "zoom", "teams", "meet")),
    )

    /** Package owners shown by a nicer name than the one in their package. */
    private val PUBLISHER_NAMES = mapOf(
        "google" to "Google", "microsoft" to "Microsoft", "samsung" to "Samsung", "sec" to "Samsung",
        "adobe" to "Adobe", "amazon" to "Amazon", "facebook" to "Meta", "instagram" to "Meta", "whatsapp" to "Meta",
        "android" to "Android", "mozilla" to "Mozilla", "spotify" to "Spotify", "netflix" to "Netflix",
    )

    /** Owners too generic to make a useful publisher screen. */
    private val SKIP_OWNERS = setOf("android", "app", "apps", "mobile", "example")

    /**
     * Groups by type. [category] gives an app's declared category for a package, or null when it
     * declares none. Each app lands in one type at most; types with fewer than [min] apps are left out.
     */
    fun byType(apps: List<AppEntry>, category: (String) -> Int?, min: Int = 2): List<AppGroup> {
        val buckets = linkedMapOf<String, MutableList<AppEntry>>()
        apps.forEach { app ->
            val text = (app.packageName + " " + app.label).lowercase()
            val declared = category(app.packageName)
            val type = TYPES.firstOrNull { t -> t.words.any { w -> w in text } }
                ?: TYPES.firstOrNull { t -> t.category != null && t.category == declared }
                ?: return@forEach
            buckets.getOrPut(type.id) { mutableListOf() } += app
        }
        return TYPES.mapNotNull { t ->
            val list = buckets[t.id] ?: return@mapNotNull null
            if (list.size < min) null else AppGroup("type:${t.id}", t.title, t.icon, list.sortedBy { it.label.lowercase() })
        }
    }

    /** Groups by publisher: apps whose packages share an owner, like com.google, with at least [min] apps. */
    fun byPublisher(apps: List<AppEntry>, min: Int = 3): List<AppGroup> =
        apps.groupBy { owner(it.packageName) }
            .filterKeys { it != null }
            .mapNotNull { (owner, list) ->
                if (owner == null || list.size < min) null
                else AppGroup("publisher:$owner", PUBLISHER_NAMES[owner] ?: owner.replaceFirstChar { it.uppercase() }, "layers", list.sortedBy { it.label.lowercase() })
            }
            .sortedWith(compareByDescending<AppGroup> { it.apps.size }.thenBy { it.title })

    /** The owner part of a package name: "google" in com.google.android.gm. */
    fun owner(packageName: String): String? {
        val parts = packageName.split('.')
        if (parts.size < 2) return null
        val owner = parts[1].lowercase()
        return if (owner.length < 2 || owner in SKIP_OWNERS) null else owner
    }
}
