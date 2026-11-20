package app.gridlauncher.data

import app.gridlauncher.domain.SortMode
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** The layout backup file: every setting and screen, as readable JSON. */
object Backup {
    private const val FORMAT = "grid-launcher-backup"
    private const val VERSION = 1

    fun encode(s: LauncherSettings): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("quickActions", s.quickActionCount)
        .put("sortMode", s.sortMode.name)
        .put("showLabels", s.showLabels)
        .put("themedIcons", s.themedIcons)
        .put("favoritesEnabled", s.favoritesEnabled)
        .put("showWallpaper", s.showWallpaper)
        .put("snapToGrid", s.snapToGrid)
        .put("lockLayout", s.lockLayout)
        .put("fontFamily", s.fontFamily)
        .put("hiddenApps", JSONArray(s.hiddenApps.sorted()))
        .put("favorites", JSONArray(Codec.encodeList(s.favorites)))
        .put("customOrder", JSONArray(Codec.encodeList(s.customOrder)))
        .put("launchCounts", JSONObject(Codec.encodeCounts(s.launchCounts)))
        .put("pinnedActions", JSONObject(Codec.encodePins(s.pinnedActions)))
        .put("pages", JSONArray(Codec.encodePages(s.pages)))
        .put("monitorEnabled", s.monitorEnabled)
        .put("accentColor", s.accentColor)
        .put("widgetCorner", s.widgetCorner.toDouble())
        .put("wallpaperDim", s.wallpaperDim.toDouble())
        .put("wallpaperBlur", s.wallpaperBlur)
        .put("swipeDown", s.swipeDown.name)
        .put("swipeUp", s.swipeUp.name)
        .put("doubleTap", s.doubleTap.name)
        .put("notificationDots", s.notificationDots.name)
        .put("navStyle", s.navStyle.name)
        .put("gridSize", s.gridSize.name)
        .put("chargingStyle", s.chargingStyle.name)
        .put("musicStyle", s.musicStyle.name)
        .put("musicLive", s.musicLive)
        .put("notifyFlash", s.notifyFlash)
        .put("lowBatteryPulse", s.lowBatteryPulse)
        .put("fullChargeBurst", s.fullChargeBurst)
        .put("timerEdge", s.timerEdge)
        .put("timeOfDaySky", s.timeOfDaySky)
        .put("recentsEnabled", s.recentsEnabled)
        .put("lockedApps", JSONArray(s.lockedApps.sorted()))
        .put("iconPack", s.iconPack)
        .put("monitorAlerts", s.monitorAlerts)
        .toString(2)

    /** Reads a backup, or returns null if the file isn't one. Unknown or missing values fall back to defaults. */
    fun decode(raw: String): LauncherSettings? {
        val o = try {
            JSONObject(raw)
        } catch (_: JSONException) {
            return null
        }
        if (o.optString("format") != FORMAT) return null
        val d = LauncherSettings()
        fun gesture(key: String, default: GestureAction) =
            GestureAction.entries.firstOrNull { it.name == o.optString(key) } ?: default
        val pages = Codec.decodePages(o.optJSONArray("pages")?.toString())
        if (pages.isEmpty()) return null
        return LauncherSettings(
            quickActionCount = o.optInt("quickActions", d.quickActionCount),
            sortMode = SortMode.parse(o.optString("sortMode")),
            showLabels = o.optBoolean("showLabels", d.showLabels),
            themedIcons = o.optBoolean("themedIcons", d.themedIcons),
            favoritesEnabled = o.optBoolean("favoritesEnabled", d.favoritesEnabled),
            showWallpaper = o.optBoolean("showWallpaper", d.showWallpaper),
            snapToGrid = o.optBoolean("snapToGrid", d.snapToGrid),
            lockLayout = o.optBoolean("lockLayout", d.lockLayout),
            fontFamily = o.optString("fontFamily", d.fontFamily),
            hiddenApps = Codec.decodeList(o.optJSONArray("hiddenApps")?.toString()).toSet(),
            favorites = Codec.decodeList(o.optJSONArray("favorites")?.toString()),
            customOrder = Codec.decodeList(o.optJSONArray("customOrder")?.toString()),
            launchCounts = Codec.decodeCounts(o.optJSONObject("launchCounts")?.toString()),
            pinnedActions = Codec.decodePins(o.optJSONObject("pinnedActions")?.toString()),
            pages = pages,
            monitorEnabled = o.optBoolean("monitorEnabled", d.monitorEnabled),
            accentColor = o.optInt("accentColor", d.accentColor),
            widgetCorner = o.optDouble("widgetCorner", d.widgetCorner.toDouble()).toFloat(),
            wallpaperDim = o.optDouble("wallpaperDim", d.wallpaperDim.toDouble()).toFloat(),
            wallpaperBlur = o.optBoolean("wallpaperBlur", d.wallpaperBlur),
            tutorialDone = true,
            swipeDown = gesture("swipeDown", d.swipeDown),
            swipeUp = gesture("swipeUp", d.swipeUp),
            doubleTap = gesture("doubleTap", d.doubleTap),
            gridSize = GridSize.entries.firstOrNull { it.name == o.optString("gridSize") } ?: d.gridSize,
            chargingStyle = ChargingStyle.entries.firstOrNull { it.name == o.optString("chargingStyle") } ?: d.chargingStyle,
            musicStyle = MusicStyle.entries.firstOrNull { it.name == o.optString("musicStyle") } ?: d.musicStyle,
            musicLive = o.optBoolean("musicLive", d.musicLive),
            notifyFlash = o.optBoolean("notifyFlash", d.notifyFlash),
            lowBatteryPulse = o.optBoolean("lowBatteryPulse", d.lowBatteryPulse),
            fullChargeBurst = o.optBoolean("fullChargeBurst", d.fullChargeBurst),
            timerEdge = o.optBoolean("timerEdge", d.timerEdge),
            timeOfDaySky = o.optBoolean("timeOfDaySky", d.timeOfDaySky),
            navStyle = NavStyle.entries.firstOrNull { it.name == o.optString("navStyle") } ?: d.navStyle,
            notificationDots = DotStyle.entries.firstOrNull { it.name == o.optString("notificationDots") } ?: d.notificationDots,
            recentsEnabled = o.optBoolean("recentsEnabled", d.recentsEnabled),
            lockedApps = Codec.decodeList(o.optJSONArray("lockedApps")?.toString()).toSet(),
            iconPack = o.optString("iconPack", d.iconPack),
            monitorAlerts = o.optBoolean("monitorAlerts", d.monitorAlerts),
        )
    }
}
