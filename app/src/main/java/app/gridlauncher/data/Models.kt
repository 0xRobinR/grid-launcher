package app.gridlauncher.data

import android.content.ComponentName
import android.os.UserHandle
import app.gridlauncher.domain.SortMode
import app.gridlauncher.domain.WidgetBounds
import app.gridlauncher.ui.theme.FontCatalog

/** One launchable activity. [key] is stable across restarts: component plus user serial number. */
data class AppEntry(
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val isPrimaryUser: Boolean,
    val isSystemApp: Boolean,
) {
    val packageName: String get() = component.packageName
}

enum class WidgetKind {
    APP_WIDGET,
    CLOCK,
    TEXT,

    /** Charge ring, charging state and temperature. */
    BATTERY,

    /** One-tap flashlight, internet, Bluetooth and volume. */
    CONTROLS,

    /** The next calendar events today and tomorrow. */
    AGENDA,

    /** Starts a 1, 5, 10 or 25 minute timer in one tap. */
    TIMER,

    /** A short note, edited in place. Its text lives in [PlacedWidget.text]. */
    NOTE,

    /** How much of the day, week, month and year has gone. */
    PROGRESS,

    /** An app pinned among the widgets, with its label and quick actions. Its key is [PlacedWidget.appKey]. */
    APP,

    /** A photo the user picked, in a shape. The file is [PlacedWidget.image]. */
    IMAGE,

    /**
     * A place for another app's widget, from a share code: which widget it was
     * ([PlacedWidget.provider], [PlacedWidget.label]), waiting to be set up on this phone.
     */
    WIDGET_SLOT;

    /** Widgets drawn by the launcher itself, as opposed to other apps' widgets. */
    val builtIn: Boolean get() = this != APP_WIDGET
}

enum class TextAlignment { START, CENTER, END }

/** The outline a photo widget is cut to. */
enum class ImageShape(val title: String) { ROUNDED("Rounded"), OVAL("Oval"), ARCH("Arch"), SCALLOP("Scallop"), CLOVER("Clover") }

enum class TextTone { PRIMARY, MUTED, ACCENT }

/** A text label widget. The text scales to fill whatever frame the user gives it. */
data class TextSpec(
    val text: String = "Label",
    /** A Google Fonts family name, or an empty string to use the launcher's font. */
    val font: String = "",
    /** CSS-style weight: 300, 400, 500 or 700. */
    val weight: Int = 500,
    val align: TextAlignment = TextAlignment.START,
    val tone: TextTone = TextTone.PRIMARY,
)

data class PlacedWidget(
    val uid: String,
    val kind: WidgetKind,
    /** The system app widget id for [WidgetKind.APP_WIDGET], otherwise -1. */
    val appWidgetId: Int,
    val bounds: WidgetBounds,
    /** Set for [WidgetKind.TEXT] and, holding just the note, [WidgetKind.NOTE]. */
    val text: TextSpec? = null,
    /** [WidgetKind.TIMER]: when the last timer started from it ends (wall clock ms), or 0. */
    val timerEndsAt: Long = 0L,
    /** [WidgetKind.TIMER]: that timer's length in ms, for the progress bar. */
    val timerLengthMs: Long = 0L,
    /** [WidgetKind.APP]: the pinned app's [AppEntry.key]. */
    val appKey: String = "",
    /** [WidgetKind.IMAGE]: the photo's file, relative to the app's files folder; empty until one is chosen. */
    val image: String = "",
    /** [WidgetKind.IMAGE]: the outline the photo is cut to. */
    val shape: ImageShape = ImageShape.ROUNDED,
    /** The widget area it sits in: empty for the one at the top, else a [Board.uid]. */
    val board: String = "",
    /** [WidgetKind.WIDGET_SLOT]: the widget provider's component, flattened. */
    val provider: String = "",
    /** [WidgetKind.WIDGET_SLOT]: what to call it, like "Spotify · Now playing". */
    val label: String = "",
)

/**
 * A widget area between rows of a screen's apps, after row [afterRow] (0 = straight after the top
 * area). Its widgets are the screen's widgets whose [PlacedWidget.board] is [uid].
 */
data class Board(val uid: String, val afterRow: Int)

/** Which apps a home screen lists under its widgets. */
enum class PageApps {
    /** Every app that isn't hidden, in the global sort order. */
    ALL,
    /** Only the apps the user chose for this screen, in their order. */
    CUSTOM,
    /** No app grid: a widgets-only screen. */
    NONE,
}

/**
 * When a screen is on home. Outside its rule the screen is hidden (it still shows while editing),
 * and when the rule starts to match it comes up by itself as you come back to home. Time and Wi-Fi
 * can each be on; when both are on, both must match.
 */
data class ScreenRule(
    val timeEnabled: Boolean = false,
    /** Minutes after midnight. A window that ends earlier than it starts runs overnight; equal means all day. */
    val fromMinute: Int = 9 * 60,
    val toMinute: Int = 17 * 60,
    val wifiEnabled: Boolean = false,
    /** The Wi-Fi network name to match, without quotes. */
    val wifiName: String = "",
) {
    val active: Boolean get() = timeEnabled || (wifiEnabled && wifiName.isNotBlank())

    fun matches(minuteOfDay: Int, currentWifi: String?): Boolean {
        if (!active) return false
        val timeOk = !timeEnabled || fromMinute == toMinute || if (fromMinute < toMinute) {
            minuteOfDay in fromMinute until toMinute
        } else {
            minuteOfDay >= fromMinute || minuteOfDay < toMinute
        }
        val wifiOk = !wifiEnabled || (currentWifi != null && currentWifi.equals(wifiName, ignoreCase = false))
        return timeOk && wifiOk
    }
}

enum class GestureAction { NONE, NOTIFICATIONS, QUICK_SETTINGS, LOCK }

enum class DotStyle { OFF, DOT, COUNT }

/** How big the app grid is: how many columns, and how large icons and labels are. */
enum class GridSize(val title: String, val columns: Int, val iconDp: Int, val labelSp: Int) {
    XS("XS", 5, 44, 11),
    S("S", 4, 52, 12),
    M("M", 3, 60, 13),
    L("L", 3, 72, 14),
    XL("XL", 2, 88, 15),
}

/**
 * A screen's own look for its app grid. A screen without one ([LauncherPage.look] null) follows
 * the launcher's settings.
 */
data class PageLook(
    val quickActions: Int = 2,
    val dots: DotStyle = DotStyle.DOT,
    val labels: Boolean = true,
    val themedIcons: Boolean = true,
    val gridSize: GridSize = GridSize.M,
)

/** The animation along the charging-port edge of home while the phone charges. */
enum class ChargingStyle(val title: String) {
    OFF("Off"),
    GLOW("Glow"),
    FIRE("Fire"),
    WAVE("Wave"),
    RINGS("Rings"),
    BUBBLES("Bubbles"),
    ELECTRIC("Electric"),
    AURORA("Aurora"),
    NEON("Neon"),
    ORB("Orb"),
    FOUNTAIN("Fountain"),
    HEARTBEAT("Heartbeat"),
    RAINBOW("Rainbow"),
}

/** The effect along the bottom edge of home while music plays. */
enum class MusicStyle(val title: String) {
    OFF("Off"),
    BARS("Bars"),
    PULSE("Pulse"),
    WAVE("Waveform"),
}

/** How the bar that switches screens looks. */
enum class NavStyle(val title: String) {
    /** Icons; the screen in view grows into a pill with its name. */
    PILL("Pill"),
    /** Icons only; the screen in view sits in a filled circle. */
    ICONS("Icons"),
    /** Screen names only, as tabs. */
    LABELS("Labels"),
    /** Small dots; the one in view stretches wide. */
    DOTS("Dots"),
    /** Dots joined by an indicator that stretches and follows your finger as you swipe. */
    SNAKE("Snake"),
    /** A thin segmented line with a sliding marker. */
    LINE("Line"),
    /** No bar; swipe between screens. */
    OFF("Off"),
}

/** A folder inside a screen's custom app set. The set lists it as [marker]. */
data class AppFolder(val id: String, val name: String, val apps: List<String>) {
    val marker: String get() = MARKER_PREFIX + id

    companion object {
        const val MARKER_PREFIX = "folder:"
        fun idOf(marker: String): String? = marker.takeIf { it.startsWith(MARKER_PREFIX) }?.removePrefix(MARKER_PREFIX)
    }
}

/** One swipeable home screen: its own free-form widgets and its own apps. */
data class LauncherPage(
    val uid: String,
    /** Back to front: the last widget draws on top. */
    val widgets: List<PlacedWidget> = emptyList(),
    val appsMode: PageApps = PageApps.ALL,
    /** App keys for [PageApps.CUSTOM], in display order. */
    val apps: List<String> = emptyList(),
    /** Shown in the screen navbar; empty means "Screen N". */
    val name: String = "",
    /** A key from the icon library for the screen navbar; empty means the default for its position. */
    val icon: String = "",
    /** Shows this screen automatically at certain times or on a certain Wi-Fi network. */
    val rule: ScreenRule = ScreenRule(),
    /** Folders in this screen's custom set; [apps] lists each by its marker. */
    val folders: List<AppFolder> = emptyList(),
    /**
     * Width in dp of the widget area the widgets were laid out in (0 = not known yet). Widgets are
     * scaled sideways from it, so a layout keeps its proportions in landscape and on tablets.
     */
    val layoutWidth: Float = 0f,
    /** This screen's own grid look, or null to follow the launcher's settings. */
    val look: PageLook? = null,
    /** More widget areas, each between rows of the apps. */
    val boards: List<Board> = emptyList(),
)

data class LauncherSettings(
    val quickActionCount: Int = 2,
    val sortMode: SortMode = SortMode.AZ,
    val showLabels: Boolean = true,
    val themedIcons: Boolean = true,
    val favoritesEnabled: Boolean = false,
    val showWallpaper: Boolean = false,
    val snapToGrid: Boolean = true,
    val lockLayout: Boolean = false,
    /** A Google Fonts family name, or an empty string for the system font. */
    val fontFamily: String = FontCatalog.DEFAULT,
    val hiddenApps: Set<String> = emptySet(),
    val favorites: List<String> = emptyList(),
    val customOrder: List<String> = emptyList(),
    val launchCounts: Map<String, Int> = emptyMap(),
    /** App key to the shortcut ids the user pinned; a missing key means "use the app's defaults". */
    val pinnedActions: Map<String, List<String>> = emptyMap(),
    /** The home screens, left to right. Never empty once loaded. */
    val pages: List<LauncherPage> = emptyList(),
    /** Adds the phone monitor screen to the left of the home screens. */
    val monitorEnabled: Boolean = false,
    /** The one accent color, as ARGB. */
    val accentColor: Int = DEFAULT_ACCENT,
    /** Corner radius of widget cards, in dp. */
    val widgetCorner: Float = 22f,
    /** How much the dark overlay hides the wallpaper, 0 to 0.9, when the wallpaper is shown. */
    val wallpaperDim: Float = 0.55f,
    /** Blurs the wallpaper behind the home screen, where the phone supports it. */
    val wallpaperBlur: Boolean = false,
    val tutorialDone: Boolean = false,
    val swipeDown: GestureAction = GestureAction.NOTIFICATIONS,
    val swipeUp: GestureAction = GestureAction.NONE,
    val doubleTap: GestureAction = GestureAction.LOCK,
    val notificationDots: DotStyle = DotStyle.DOT,
    /** Columns and icon size of the app grid, for screens without their own. */
    val gridSize: GridSize = GridSize.M,
    /** The look of the screen switcher at the bottom of home. */
    val navStyle: NavStyle = NavStyle.PILL,
    /** The animation over the charging port while charging. */
    val chargingStyle: ChargingStyle = ChargingStyle.GLOW,
    /** The effect at the bottom of home while music plays. */
    val musicStyle: MusicStyle = MusicStyle.OFF,
    /** The music effect follows the actual sound, through Android's audio visualizer (needs the microphone permission). */
    val musicLive: Boolean = false,
    /** A flash around the edges of home when a notification arrives. */
    val notifyFlash: Boolean = false,
    /** A red or amber pulse when the battery runs low. */
    val lowBatteryPulse: Boolean = true,
    /** A burst, then a calm glow, when charging reaches 100%. */
    val fullChargeBurst: Boolean = true,
    /** A draining line along the bottom while a Quick timer runs, and a flash when it ends. */
    val timerEdge: Boolean = true,
    /** A sky tint along the top that follows the time of day, with stars at night. */
    val timeOfDaySky: Boolean = false,
    /** A row of recently opened apps on the first screen. */
    val recentsEnabled: Boolean = false,
    /** Most recent first. */
    val recents: List<String> = emptyList(),
    /** Apps that need a fingerprint or screen lock to open from the launcher. */
    val lockedApps: Set<String> = emptySet(),
    /** Package of the icon pack in use, or empty for none. */
    val iconPack: String = "",
    /** Notifications when the phone runs hot or storage is nearly full. */
    val monitorAlerts: Boolean = false,
    /** The user said "Not now" to turning on gestures: stop asking. */
    val gesturePromptDeclined: Boolean = false,
) {
    companion object {
        const val DEFAULT_ACCENT = 0xFF9DB8FF.toInt()
    }
}

/** The launcher's look as a screen's own starting point. */
fun LauncherSettings.lookNow() = PageLook(quickActionCount, notificationDots, showLabels, themedIcons, gridSize)

/** The settings a screen's app grid uses: the launcher's, with the screen's own look on top. */
fun LauncherSettings.forPage(page: LauncherPage?): LauncherSettings {
    val look = page?.look ?: return this
    return copy(
        quickActionCount = look.quickActions,
        notificationDots = look.dots,
        showLabels = look.labels,
        themedIcons = look.themedIcons,
        gridSize = look.gridSize,
    )
}
