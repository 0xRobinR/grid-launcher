package app.gridlauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.gridlauncher.domain.QuickActions
import app.gridlauncher.domain.SortMode
import app.gridlauncher.ui.theme.FontCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_settings")

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.launcherDataStore

    private object Keys {
        val quickActions = intPreferencesKey("quick_actions")
        val sortMode = stringPreferencesKey("sort_mode")
        val showLabels = booleanPreferencesKey("show_labels")
        val themedIcons = booleanPreferencesKey("themed_icons")
        val favoritesEnabled = booleanPreferencesKey("favorites_enabled")
        val showWallpaper = booleanPreferencesKey("show_wallpaper")
        val snapToGrid = booleanPreferencesKey("snap_to_grid")
        val lockLayout = booleanPreferencesKey("lock_layout")
        val fontFamily = stringPreferencesKey("font_family")
        val hiddenApps = stringSetPreferencesKey("hidden_apps")
        val favorites = stringPreferencesKey("favorites")
        val customOrder = stringPreferencesKey("custom_order")
        val launchCounts = stringPreferencesKey("launch_counts")
        val pinnedActions = stringPreferencesKey("pinned_actions")
        /** Widgets of the single home screen, before screens existed. Read once to migrate. */
        val legacyWidgets = stringPreferencesKey("widgets")
        val legacyWidgetsInitialized = booleanPreferencesKey("widgets_initialized")
        val pages = stringPreferencesKey("pages")
        val monitorEnabled = booleanPreferencesKey("monitor_enabled")
        val accentColor = intPreferencesKey("accent_color")
        val widgetCorner = floatPreferencesKey("widget_corner")
        val wallpaperDim = floatPreferencesKey("wallpaper_dim")
        val wallpaperBlur = booleanPreferencesKey("wallpaper_blur")
        val tutorialDone = booleanPreferencesKey("tutorial_done")
        val swipeDown = stringPreferencesKey("gesture_swipe_down")
        val swipeUp = stringPreferencesKey("gesture_swipe_up")
        val doubleTap = stringPreferencesKey("gesture_double_tap")
        val notificationDots = stringPreferencesKey("notification_dots")
        val navStyle = stringPreferencesKey("nav_style")
        val gridSize = stringPreferencesKey("grid_size")
        val chargingStyle = stringPreferencesKey("charging_style")
        val musicStyle = stringPreferencesKey("music_style")
        val musicLive = booleanPreferencesKey("music_live")
        val notifyFlash = booleanPreferencesKey("notify_flash")
        val lowBatteryPulse = booleanPreferencesKey("low_battery_pulse")
        val fullChargeBurst = booleanPreferencesKey("full_charge_burst")
        val timerEdge = booleanPreferencesKey("timer_edge")
        val timeOfDaySky = booleanPreferencesKey("time_of_day_sky")
        val recentsEnabled = booleanPreferencesKey("recents_enabled")
        val recents = stringPreferencesKey("recents")
        val lockedApps = stringSetPreferencesKey("locked_apps")
        val iconPack = stringPreferencesKey("icon_pack")
        val monitorAlerts = booleanPreferencesKey("monitor_alerts")
        val gesturePromptDeclined = booleanPreferencesKey("gesture_prompt_declined")
    }

    private fun gesture(value: String?, default: GestureAction) =
        GestureAction.entries.firstOrNull { it.name == value } ?: default

    val settings: Flow<LauncherSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::read)
        .distinctUntilChanged()

    private fun read(p: Preferences): LauncherSettings {
        val defaults = LauncherSettings()
        return LauncherSettings(
            quickActionCount = (p[Keys.quickActions] ?: defaults.quickActionCount).coerceIn(0, QuickActions.MAX_PER_APP),
            sortMode = SortMode.parse(p[Keys.sortMode]),
            showLabels = p[Keys.showLabels] ?: defaults.showLabels,
            themedIcons = p[Keys.themedIcons] ?: defaults.themedIcons,
            favoritesEnabled = p[Keys.favoritesEnabled] ?: defaults.favoritesEnabled,
            showWallpaper = p[Keys.showWallpaper] ?: defaults.showWallpaper,
            snapToGrid = p[Keys.snapToGrid] ?: defaults.snapToGrid,
            lockLayout = p[Keys.lockLayout] ?: defaults.lockLayout,
            fontFamily = p[Keys.fontFamily] ?: FontCatalog.DEFAULT,
            hiddenApps = p[Keys.hiddenApps] ?: emptySet(),
            favorites = Codec.decodeList(p[Keys.favorites]),
            customOrder = Codec.decodeList(p[Keys.customOrder]),
            launchCounts = Codec.decodeCounts(p[Keys.launchCounts]),
            pinnedActions = Codec.decodePins(p[Keys.pinnedActions]),
            pages = readPages(p),
            monitorEnabled = p[Keys.monitorEnabled] ?: false,
            accentColor = p[Keys.accentColor] ?: LauncherSettings.DEFAULT_ACCENT,
            widgetCorner = (p[Keys.widgetCorner] ?: defaults.widgetCorner).coerceIn(0f, 36f),
            wallpaperDim = (p[Keys.wallpaperDim] ?: defaults.wallpaperDim).coerceIn(0f, 0.9f),
            wallpaperBlur = p[Keys.wallpaperBlur] ?: false,
            tutorialDone = p[Keys.tutorialDone] ?: false,
            swipeDown = gesture(p[Keys.swipeDown], defaults.swipeDown),
            swipeUp = gesture(p[Keys.swipeUp], defaults.swipeUp),
            doubleTap = gesture(p[Keys.doubleTap], defaults.doubleTap),
            gridSize = GridSize.entries.firstOrNull { it.name == p[Keys.gridSize] } ?: defaults.gridSize,
            chargingStyle = ChargingStyle.entries.firstOrNull { it.name == p[Keys.chargingStyle] } ?: defaults.chargingStyle,
            musicStyle = MusicStyle.entries.firstOrNull { it.name == p[Keys.musicStyle] } ?: defaults.musicStyle,
            musicLive = p[Keys.musicLive] ?: defaults.musicLive,
            notifyFlash = p[Keys.notifyFlash] ?: defaults.notifyFlash,
            lowBatteryPulse = p[Keys.lowBatteryPulse] ?: defaults.lowBatteryPulse,
            fullChargeBurst = p[Keys.fullChargeBurst] ?: defaults.fullChargeBurst,
            timerEdge = p[Keys.timerEdge] ?: defaults.timerEdge,
            timeOfDaySky = p[Keys.timeOfDaySky] ?: defaults.timeOfDaySky,
            navStyle = NavStyle.entries.firstOrNull { it.name == p[Keys.navStyle] } ?: defaults.navStyle,
            notificationDots = DotStyle.entries.firstOrNull { it.name == p[Keys.notificationDots] } ?: defaults.notificationDots,
            recentsEnabled = p[Keys.recentsEnabled] ?: false,
            recents = Codec.decodeList(p[Keys.recents]),
            lockedApps = p[Keys.lockedApps] ?: emptySet(),
            iconPack = p[Keys.iconPack] ?: "",
            monitorAlerts = p[Keys.monitorAlerts] ?: false,
            gesturePromptDeclined = p[Keys.gesturePromptDeclined] ?: false,
        )
    }

    /** The saved screens; an install from before screens existed becomes one screen with its widgets. */
    private fun readPages(p: Preferences): List<LauncherPage> {
        val pages = Codec.decodePages(p[Keys.pages])
        if (pages.isNotEmpty()) return pages
        val legacy = Codec.decodeWidgets(p[Keys.legacyWidgets])
        val wasSetUp = p[Keys.legacyWidgetsInitialized] ?: false
        return if (legacy.isNotEmpty() || wasSetUp) listOf(LauncherPage(uid = LEGACY_PAGE_UID, widgets = legacy)) else emptyList()
    }

    private suspend fun update(block: (MutablePreferences, LauncherSettings) -> Unit) {
        store.edit { prefs -> block(prefs, read(prefs)) }
    }

    suspend fun setQuickActionCount(count: Int) = update { p, _ -> p[Keys.quickActions] = count.coerceIn(0, QuickActions.MAX_PER_APP) }
    suspend fun setSortMode(mode: SortMode) = update { p, _ -> p[Keys.sortMode] = mode.name }
    suspend fun setShowLabels(value: Boolean) = update { p, _ -> p[Keys.showLabels] = value }
    suspend fun setThemedIcons(value: Boolean) = update { p, _ -> p[Keys.themedIcons] = value }
    suspend fun setFavoritesEnabled(value: Boolean) = update { p, _ -> p[Keys.favoritesEnabled] = value }
    suspend fun setShowWallpaper(value: Boolean) = update { p, _ -> p[Keys.showWallpaper] = value }
    suspend fun setSnapToGrid(value: Boolean) = update { p, _ -> p[Keys.snapToGrid] = value }
    suspend fun setLockLayout(value: Boolean) = update { p, _ -> p[Keys.lockLayout] = value }
    suspend fun setFontFamily(name: String) = update { p, _ -> p[Keys.fontFamily] = name.trim() }

    suspend fun setHidden(key: String, hidden: Boolean) = update { p, s ->
        p[Keys.hiddenApps] = if (hidden) s.hiddenApps + key else s.hiddenApps - key
    }

    suspend fun setFavorite(key: String, favorite: Boolean) = update { p, s ->
        val list = s.favorites.filterNot { it == key }
        p[Keys.favorites] = Codec.encodeList(if (favorite) list + key else list)
    }

    suspend fun setFavorites(keys: List<String>) = update { p, _ -> p[Keys.favorites] = Codec.encodeList(keys.distinct()) }

    suspend fun setCustomOrder(keys: List<String>) = update { p, _ -> p[Keys.customOrder] = Codec.encodeList(keys.distinct()) }

    /** Saves [keys] as the custom order and switches the grid to it, in one write. */
    suspend fun applyCustomOrder(keys: List<String>) = update { p, _ ->
        p[Keys.customOrder] = Codec.encodeList(keys.distinct())
        p[Keys.sortMode] = SortMode.CUSTOM.name
    }

    suspend fun recordLaunch(key: String) = update { p, s ->
        val counts = s.launchCounts.toMutableMap()
        counts[key] = (counts[key] ?: 0) + 1
        p[Keys.launchCounts] = Codec.encodeCounts(counts)
        p[Keys.recents] = Codec.encodeList((listOf(key) + s.recents.filterNot { it == key }).take(MAX_RECENTS))
    }

    suspend fun setNotificationDots(style: DotStyle) = update { p, _ -> p[Keys.notificationDots] = style.name }
    suspend fun setNavStyle(style: NavStyle) = update { p, _ -> p[Keys.navStyle] = style.name }
    suspend fun setGridSize(size: GridSize) = update { p, _ -> p[Keys.gridSize] = size.name }
    suspend fun setChargingStyle(style: ChargingStyle) = update { p, _ -> p[Keys.chargingStyle] = style.name }
    suspend fun setMusicStyle(style: MusicStyle) = update { p, _ -> p[Keys.musicStyle] = style.name }
    suspend fun setMusicLive(on: Boolean) = update { p, _ -> p[Keys.musicLive] = on }
    suspend fun setNotifyFlash(on: Boolean) = update { p, _ -> p[Keys.notifyFlash] = on }
    suspend fun setLowBatteryPulse(on: Boolean) = update { p, _ -> p[Keys.lowBatteryPulse] = on }
    suspend fun setFullChargeBurst(on: Boolean) = update { p, _ -> p[Keys.fullChargeBurst] = on }
    suspend fun setTimerEdge(on: Boolean) = update { p, _ -> p[Keys.timerEdge] = on }
    suspend fun setTimeOfDaySky(on: Boolean) = update { p, _ -> p[Keys.timeOfDaySky] = on }
    suspend fun setRecentsEnabled(value: Boolean) = update { p, _ -> p[Keys.recentsEnabled] = value }
    suspend fun setIconPack(pkg: String) = update { p, _ -> p[Keys.iconPack] = pkg }
    suspend fun setMonitorAlerts(value: Boolean) = update { p, _ -> p[Keys.monitorAlerts] = value }
    suspend fun setGesturePromptDeclined(value: Boolean) = update { p, _ -> p[Keys.gesturePromptDeclined] = value }
    suspend fun setLocked(key: String, locked: Boolean) = update { p, s ->
        p[Keys.lockedApps] = if (locked) s.lockedApps + key else s.lockedApps - key
    }

    suspend fun setPinnedActions(appKey: String, ids: List<String>) = update { p, s ->
        p[Keys.pinnedActions] = Codec.encodePins(s.pinnedActions + (appKey to ids))
    }

    /** Read and write in one transaction, so two quick taps can't overwrite each other. */
    suspend fun togglePin(appKey: String, available: List<String>, shortcutId: String) = update { p, s ->
        val next = QuickActions.togglePin(available, s.pinnedActions[appKey], s.quickActionCount, shortcutId)
        p[Keys.pinnedActions] = Codec.encodePins(s.pinnedActions + (appKey to next))
    }

    suspend fun setMonitorEnabled(value: Boolean) = update { p, _ -> p[Keys.monitorEnabled] = value }
    suspend fun setAccentColor(argb: Int) = update { p, _ -> p[Keys.accentColor] = argb }
    suspend fun setWidgetCorner(dp: Float) = update { p, _ -> p[Keys.widgetCorner] = dp.coerceIn(0f, 36f) }
    suspend fun setWallpaperDim(value: Float) = update { p, _ -> p[Keys.wallpaperDim] = value.coerceIn(0f, 0.9f) }
    suspend fun setWallpaperBlur(value: Boolean) = update { p, _ -> p[Keys.wallpaperBlur] = value }
    suspend fun setTutorialDone(value: Boolean) = update { p, _ -> p[Keys.tutorialDone] = value }
    suspend fun setSwipeDown(action: GestureAction) = update { p, _ -> p[Keys.swipeDown] = action.name }
    suspend fun setSwipeUp(action: GestureAction) = update { p, _ -> p[Keys.swipeUp] = action.name }
    suspend fun setDoubleTap(action: GestureAction) = update { p, _ -> p[Keys.doubleTap] = action.name }

    /** Replaces every setting and screen with a restored backup, in one write. */
    suspend fun restore(s: LauncherSettings) = store.edit { p ->
        p[Keys.quickActions] = s.quickActionCount.coerceIn(0, QuickActions.MAX_PER_APP)
        p[Keys.sortMode] = s.sortMode.name
        p[Keys.showLabels] = s.showLabels
        p[Keys.themedIcons] = s.themedIcons
        p[Keys.favoritesEnabled] = s.favoritesEnabled
        p[Keys.showWallpaper] = s.showWallpaper
        p[Keys.snapToGrid] = s.snapToGrid
        p[Keys.lockLayout] = s.lockLayout
        p[Keys.fontFamily] = s.fontFamily
        p[Keys.hiddenApps] = s.hiddenApps
        p[Keys.favorites] = Codec.encodeList(s.favorites)
        p[Keys.customOrder] = Codec.encodeList(s.customOrder)
        p[Keys.launchCounts] = Codec.encodeCounts(s.launchCounts)
        p[Keys.pinnedActions] = Codec.encodePins(s.pinnedActions)
        if (s.pages.isNotEmpty()) p[Keys.pages] = Codec.encodePages(s.pages)
        p[Keys.monitorEnabled] = s.monitorEnabled
        p[Keys.accentColor] = s.accentColor
        p[Keys.widgetCorner] = s.widgetCorner
        p[Keys.wallpaperDim] = s.wallpaperDim
        p[Keys.wallpaperBlur] = s.wallpaperBlur
        p[Keys.tutorialDone] = true
        p[Keys.swipeDown] = s.swipeDown.name
        p[Keys.swipeUp] = s.swipeUp.name
        p[Keys.doubleTap] = s.doubleTap.name
        p[Keys.notificationDots] = s.notificationDots.name
        p[Keys.navStyle] = s.navStyle.name
        p[Keys.gridSize] = s.gridSize.name
        p[Keys.chargingStyle] = s.chargingStyle.name
        p[Keys.musicStyle] = s.musicStyle.name
        p[Keys.musicLive] = s.musicLive
        p[Keys.notifyFlash] = s.notifyFlash
        p[Keys.lowBatteryPulse] = s.lowBatteryPulse
        p[Keys.fullChargeBurst] = s.fullChargeBurst
        p[Keys.timerEdge] = s.timerEdge
        p[Keys.timeOfDaySky] = s.timeOfDaySky
        p[Keys.recentsEnabled] = s.recentsEnabled
        p[Keys.lockedApps] = s.lockedApps
        p[Keys.iconPack] = s.iconPack
        p[Keys.monitorAlerts] = s.monitorAlerts
    }

    /** Replaces every screen at once, for undo and for restoring a backup. */
    suspend fun replacePages(pages: List<LauncherPage>) = update { p, _ ->
        if (pages.isNotEmpty()) p[Keys.pages] = Codec.encodePages(pages)
    }

    /** Applies [transform] to the screens in one transaction. The result is never left empty. */
    suspend fun updatePages(transform: (List<LauncherPage>) -> List<LauncherPage>) = update { p, s ->
        val next = transform(s.pages)
        if (next.isNotEmpty()) p[Keys.pages] = Codec.encodePages(next)
    }

    /**
     * Runs on every start. A new install gets one screen with the built-in clock; an install from
     * before screens existed has its widgets moved onto its first screen.
     */
    suspend fun initializePagesIfNeeded(defaults: () -> List<LauncherPage>) = update { p, s ->
        if (p[Keys.pages] == null) {
            p[Keys.pages] = Codec.encodePages(s.pages.ifEmpty(defaults))
            p.remove(Keys.legacyWidgets)
            p.remove(Keys.legacyWidgetsInitialized)
        }
    }

    companion object {
        private const val LEGACY_PAGE_UID = "page-main"
        const val MAX_RECENTS = 8
    }
}
