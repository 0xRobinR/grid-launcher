package app.gridlauncher.ui.settings

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.gridlauncher.data.DotStyle
import app.gridlauncher.data.GestureAction
import app.gridlauncher.data.IconPacks
import app.gridlauncher.notifications.NotificationCounts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.gridlauncher.gestures.GestureService
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gridlauncher.BuildConfigInfo
import app.gridlauncher.data.LauncherSettings
import app.gridlauncher.domain.SortMode
import app.gridlauncher.ui.LauncherViewModel
import app.gridlauncher.ui.Screen
import app.gridlauncher.ui.components.NavRow
import app.gridlauncher.ui.components.RowDivider
import app.gridlauncher.ui.components.ScreenHeader
import app.gridlauncher.ui.components.SegmentedRow
import app.gridlauncher.ui.components.SettingsSection
import app.gridlauncher.ui.components.SwitchRow
import app.gridlauncher.ui.components.startSafely
import app.gridlauncher.ui.theme.FontCatalog
import app.gridlauncher.ui.theme.Palette

@Composable
fun SettingsScreen(vm: LauncherViewModel, settings: LauncherSettings) {
    // The microphone permission, for the music effect's audio visualizer: asked only when the user turns it on.
    val micContext = LocalContext.current
    var micGranted by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(app.gridlauncher.notifications.hasAudioPermission(micContext)) }
    val askMicrophone = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
        if (granted) vm.setMusicLive(true) else vm.toast("Without microphone permission, the music effect keeps a steady beat")
    }
    var importingCode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (importingCode) ImportCodeDialog(vm) { importingCode = false }
    val context = LocalContext.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) vm.toast("Alerts need notifications to be allowed")
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }
    // Re-checked whenever Settings comes back from a system settings screen.
    var gesturesOn by remember { mutableStateOf(GestureService.isEnabled) }
    var dotsAccess by remember { mutableStateOf(NotificationCounts.hasAccess(context)) }
    LifecycleResumeEffect(Unit) {
        gesturesOn = GestureService.isEnabled
        dotsAccess = NotificationCounts.hasAccess(context)
        onPauseOrDispose { }
    }
    var showPacks by remember { mutableStateOf(false) }
    val packs by produceState<List<IconPacks.Pack>?>(null) {
        value = withContext(Dispatchers.IO) { IconPacks.installed(context) }
    }
    val packLabel = if (settings.iconPack.isEmpty()) "None" else packs?.firstOrNull { it.packageName == settings.iconPack }?.label ?: "…"
    if (showPacks) {
        AlertDialog(
            onDismissRequest = { showPacks = false },
            containerColor = Palette.Surface3,
            titleContentColor = Palette.Fg,
            title = { Text("Icon pack", fontSize = 18.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                    val options = listOf(IconPacks.Pack("", "None")) + packs.orEmpty()
                    options.forEach { pack ->
                        val isSelected = pack.packageName == settings.iconPack
                        androidx.compose.foundation.layout.Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = isSelected, role = Role.RadioButton) {
                                    vm.setIconPack(pack.packageName)
                                    showPacks = false
                                },
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = isSelected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Palette.Accent))
                            Text(pack.label, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                    if (packs?.isEmpty() == true) {
                        Text(
                            "No icon packs installed. Search the Play Store for \"icon pack\" to find one.",
                            fontSize = 13.sp,
                            color = Palette.Muted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPacks = false }) { Text("Close", color = Palette.Accent) } },
        )
    }
    val blurSupported = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Launcher settings", onBack = vm::back)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = navBottom + 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "grid") {
                SettingsSection("App grid") {
                    SegmentedRow(
                        title = "Grid size",
                        hint = "${settings.gridSize.columns} columns · ${settings.gridSize.iconDp} dp icons. A screen can choose its own in its options.",
                        options = app.gridlauncher.data.GridSize.entries.map { it to it.title },
                        selected = settings.gridSize,
                        onSelect = vm::setGridSize,
                    )
                    RowDivider()
                    SegmentedRow(
                        title = "Quick actions per app",
                        hint = "Shown under each app label",
                        options = listOf(0 to "None", 1 to "1", 2 to "2"),
                        selected = settings.quickActionCount,
                        onSelect = vm::setQuickActionCount,
                    )
                    RowDivider()
                    SegmentedRow(
                        title = "Sort apps",
                        hint = null,
                        options = listOf(SortMode.AZ to "A–Z", SortMode.MOST_USED to "Most used", SortMode.CUSTOM to "Custom"),
                        selected = settings.sortMode,
                        onSelect = vm::setSortMode,
                    )
                    if (settings.sortMode == SortMode.CUSTOM) {
                        RowDivider()
                        NavRow("Arrange apps", value = null, hint = "Drag apps into your own order") {
                            vm.openArrange(fromSettings = true)
                        }
                    }
                    RowDivider()
                    SwitchRow(
                        "Favorites row",
                        "A row of your favorite apps above the grid",
                        settings.favoritesEnabled,
                        vm::setFavoritesEnabled,
                    )
                    RowDivider()
                    SwitchRow("Recent apps row", "Your last-opened apps above the grid", settings.recentsEnabled, vm::setRecentsEnabled)
                    RowDivider()
                    SegmentedRow(
                        title = "Notification dots",
                        hint = if (dotsAccess) null else "Needs notification access",
                        options = listOf(DotStyle.OFF to "Off", DotStyle.DOT to "Dot", DotStyle.COUNT to "Count"),
                        selected = settings.notificationDots,
                        onSelect = { style ->
                            vm.setNotificationDots(style)
                            if (style != DotStyle.OFF && !dotsAccess) NotificationCounts.openSettings(context)
                        },
                    )
                    if (settings.notificationDots != DotStyle.OFF) {
                        RowDivider()
                        NavRow("Notification access", value = if (dotsAccess) "On" else "Off", hint = "Only counts notifications; never reads them") {
                            NotificationCounts.openSettings(context)
                        }
                    }
                    RowDivider()
                    SwitchRow("Show app labels", "Name under every icon", settings.showLabels, vm::setShowLabels)
                    RowDivider()
                    SwitchRow(
                        "Themed icons",
                        "Monochrome icons for apps that support them",
                        settings.themedIcons,
                        vm::setThemedIcons,
                    )
                }
            }
            item(key = "screens") {
                SettingsSection("Screens") {
                    SwitchRow(
                        "Phone monitor",
                        "A live screen to the left of home: battery, network speed, screen time, memory and more",
                        settings.monitorEnabled,
                        vm::setMonitorEnabled,
                    )
                    RowDivider()
                    SwitchRow(
                        "Monitor alerts",
                        "A notification when the phone runs hot or storage is nearly full",
                        settings.monitorAlerts,
                    ) { on ->
                        vm.setMonitorAlerts(on)
                        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    RowDivider()
                    NavStyleRow(settings.navStyle, vm::setNavStyle)
                    RowDivider()
                    NavRow("Make screens from app groups", value = null, hint = "One screen per type (finance, games, news…) or publisher") {
                        vm.open(Screen.GROUPS)
                    }
                    RowDivider()
                    Text(
                        "${settings.pages.size} home ${if (settings.pages.size == 1) "screen" else "screens"}. To add, delete or reorder screens, or choose their apps, long-press empty space on home and tap Screen.",
                        fontSize = 12.sp,
                        color = Palette.Muted,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
            item(key = "appearance") {
                SettingsSection("Appearance") {
                    NavRow("Font", value = FontCatalog.displayName(settings.fontFamily), hint = "From Google Fonts") {
                        vm.open(Screen.FONTS)
                    }
                    RowDivider()
                    NavRow("Icon pack", value = packLabel, hint = "Replaces app icons where the pack has them") { showPacks = true }
                    RowDivider()
                    AccentRow(settings.accentColor, vm::setAccentColor)
                    RowDivider()
                    ChargingStyleRow(settings.chargingStyle, vm::setChargingStyle)
                    RowDivider()
                    SwitchRow(
                        "Full charge celebration",
                        "A burst of confetti at 100%, then a calm green glow while it stays plugged in",
                        settings.fullChargeBurst,
                        vm::setFullChargeBurst,
                    )
                    RowDivider()
                    SwitchRow(
                        "Low battery pulse",
                        "A slow amber glow under 30%, a quicker red one under 15%, over the charging port",
                        settings.lowBatteryPulse,
                        vm::setLowBatteryPulse,
                    )
                    RowDivider()
                    SegmentedRow(
                        title = "Music effect",
                        hint = if (dotsAccess) "Along the bottom of home while music plays, to a steady beat" else "Needs notification access, to see when music is playing",
                        options = app.gridlauncher.data.MusicStyle.entries.map { it to it.title },
                        selected = settings.musicStyle,
                        onSelect = { style ->
                            vm.setMusicStyle(style)
                            if (style != app.gridlauncher.data.MusicStyle.OFF && !dotsAccess) NotificationCounts.openSettings(context)
                        },
                    )
                    if (settings.musicStyle != app.gridlauncher.data.MusicStyle.OFF) {
                        RowDivider()
                        SwitchRow(
                            "Follow the actual sound",
                            if (micGranted) {
                                "Uses Android's audio visualizer to read the sound playing on the phone as levels. Nothing is recorded or saved."
                            } else {
                                "Asks for microphone permission, which Android requires for its audio visualizer. The launcher reads only the sound playing on the phone, as levels, and never records."
                            },
                            settings.musicLive && micGranted,
                        ) { on ->
                            when {
                                !on -> vm.setMusicLive(false)
                                micGranted -> vm.setMusicLive(true)
                                else -> askMicrophone.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        }
                        if (micGranted) {
                            RowDivider()
                            NavRow(
                                "Revoke microphone permission",
                                value = null,
                                hint = "Turns off Follow the actual sound and hands the permission back to Android",
                            ) {
                                vm.setMusicLive(false)
                                micGranted = false
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    context.revokeSelfPermissionOnKill(android.Manifest.permission.RECORD_AUDIO)
                                    vm.toast("Microphone permission revoked. Android removes it fully when the launcher next restarts.")
                                } else {
                                    // Older Android can't revoke from inside the app: open its permission page.
                                    context.startSafely(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)),
                                    )
                                }
                            }
                        }
                    }
                    RowDivider()
                    SwitchRow(
                        "Timer on the edge",
                        "A line drains along the bottom while a Quick timer runs, and the edge flashes when it ends",
                        settings.timerEdge,
                        vm::setTimerEdge,
                    )
                    RowDivider()
                    SwitchRow(
                        "Time of day sky",
                        "A soft sky along the top: dawn pink, daylight blue, sunset orange, and night with stars",
                        settings.timeOfDaySky,
                        vm::setTimeOfDaySky,
                    )
                    RowDivider()
                    SwitchRow(
                        "Notification flash",
                        if (dotsAccess) "The edges of home glow briefly when a notification arrives" else "Needs notification access",
                        settings.notifyFlash,
                    ) { on ->
                        vm.setNotifyFlash(on)
                        if (on && !dotsAccess) NotificationCounts.openSettings(context)
                    }
                    RowDivider()
                    SliderRow(
                        title = "Widget corners",
                        valueText = { "${it.toInt()} dp" },
                        value = settings.widgetCorner,
                        range = 0f..36f,
                        steps = 8,
                        onChange = vm::setWidgetCorner,
                    )
                    RowDivider()
                    SwitchRow(
                        "Show wallpaper",
                        "Off keeps a solid dark background",
                        settings.showWallpaper,
                        vm::setShowWallpaper,
                    )
                    if (settings.showWallpaper) {
                        RowDivider()
                        SliderRow(
                            title = "Wallpaper tint",
                            valueText = { "${(it * 100).toInt()}% dark" },
                            value = settings.wallpaperDim,
                            range = 0f..0.9f,
                            steps = 8,
                            onChange = vm::setWallpaperDim,
                        )
                        if (blurSupported) {
                            RowDivider()
                            SwitchRow("Blur wallpaper", "Softens the wallpaper behind your screens", settings.wallpaperBlur, vm::setWallpaperBlur)
                        }
                    }
                }
            }
            item(key = "gestures") {
                SettingsSection("Gestures") {
                    SegmentedRow(
                        title = "Swipe down",
                        hint = null,
                        options = listOf(GestureAction.NONE to "Off", GestureAction.NOTIFICATIONS to "Alerts", GestureAction.QUICK_SETTINGS to "Quick"),
                        selected = settings.swipeDown,
                        onSelect = vm::setSwipeDown,
                    )
                    RowDivider()
                    SegmentedRow(
                        title = "Swipe up",
                        hint = "At the end of the app list",
                        options = listOf(GestureAction.NONE to "Off", GestureAction.LOCK to "Lock", GestureAction.QUICK_SETTINGS to "Quick"),
                        selected = settings.swipeUp,
                        onSelect = vm::setSwipeUp,
                    )
                    RowDivider()
                    SegmentedRow(
                        title = "Double-tap empty space",
                        hint = null,
                        options = listOf(GestureAction.NONE to "Off", GestureAction.LOCK to "Lock screen"),
                        selected = settings.doubleTap,
                        onSelect = vm::setDoubleTap,
                    )
                    RowDivider()
                    NavRow(
                        "Gesture access",
                        value = if (gesturesOn) "On" else "Off",
                        hint = "Needed to open notifications and lock the screen",
                    ) { GestureService.openSettings(context) }
                }
            }
            item(key = "widgets") {
                SettingsSection("Widgets") {
                    SwitchRow("Snap to grid", "Align widgets on an 8 dp grid", settings.snapToGrid, vm::setSnapToGrid)
                    RowDivider()
                    SwitchRow("Lock layout", "Stop widgets from being moved or resized", settings.lockLayout, vm::setLockLayout)
                }
            }
            item(key = "more") {
                SettingsSection("More") {
                    NavRow("Hidden apps", value = settings.hiddenApps.size.toString()) { vm.open(Screen.HIDDEN_APPS) }
                    RowDivider()
                    NavRow("Back up layout", value = null, hint = "Save screens and settings to a file") {
                        exportLauncher.launch("grid-launcher-backup.json")
                    }
                    RowDivider()
                    NavRow("Restore from backup", value = null, hint = "Replaces your current screens and settings") {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }
                    RowDivider()
                    val savedSetups by vm.setups.collectAsStateWithLifecycle()
                    NavRow("Saved setups", value = savedSetups.size.toString(), hint = "Save this launcher or a screen, and switch back any time") {
                        vm.open(Screen.SETUPS)
                    }
                    RowDivider()
                    NavRow("Share your launcher", value = null, hint = "A code with your settings and screens, nothing personal") {
                        vm.showShareCode(vm.launcherShareCode(), "My launcher", "launcher")
                    }
                    RowDivider()
                    NavRow("Import a shared code", value = null, hint = "Add someone's screens, or use their whole launcher") {
                        importingCode = true
                    }
                    RowDivider()
                    NavRow("Tutorials", value = null, hint = "How every gesture and feature works, with pictures") {
                        vm.open(Screen.TUTORIALS)
                    }
                    RowDivider()
                    NavRow("Default home app", value = null, hint = "Choose which launcher opens on Home") {
                        if (!context.startSafely(Intent(Settings.ACTION_HOME_SETTINGS))) {
                            context.startSafely(Intent(Settings.ACTION_SETTINGS))
                        }
                    }
                }
            }
            item(key = "version") {
                Text(
                    "Grid Launcher ${BuildConfigInfo.versionName(context)}",
                    fontSize = 12.sp,
                    color = Palette.Subtle,
                    modifier = Modifier,
                )
            }
        }
    }
}

/** The charging effect, each choice drawn as a small live preview. */
@Composable
private fun ChargingStyleRow(selected: app.gridlauncher.data.ChargingStyle, onSelect: (app.gridlauncher.data.ChargingStyle) -> Unit) {
    Column(Modifier.padding(vertical = 14.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Charging effect", fontSize = 15.sp)
            Text("Plays on home over the charging port while the phone charges, on whichever edge the port is", fontSize = 12.sp, color = Palette.Muted)
        }
        androidx.compose.foundation.lazy.LazyRow(
            Modifier.selectableGroup(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        ) {
            items(app.gridlauncher.data.ChargingStyle.entries.size) { i ->
                val style = app.gridlauncher.data.ChargingStyle.entries[i]
                val isSelected = style == selected
                Column(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) Palette.Accent else Palette.Line, RoundedCornerShape(18.dp))
                        .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(style) }
                        .semantics { contentDescription = "${style.title} charging effect" }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(width = 112.dp, height = 132.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Ground),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (style == app.gridlauncher.data.ChargingStyle.OFF) {
                            Text("None", fontSize = 12.sp, color = Palette.Muted)
                        } else {
                            app.gridlauncher.ui.home.ChargingCanvas(style, app.gridlauncher.ui.home.PortEdge.BOTTOM, 0.7f, Modifier.fillMaxSize())
                        }
                    }
                    Text(style.title, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, color = if (isSelected) Palette.Fg else Palette.Muted)
                }
            }
        }
    }
}

/** The screen switcher's look, each choice drawn as a small live preview. */
@Composable
private fun NavStyleRow(selected: app.gridlauncher.data.NavStyle, onSelect: (app.gridlauncher.data.NavStyle) -> Unit) {
    val sample = remember {
        listOf(
            app.gridlauncher.ui.home.NavItem(app.gridlauncher.ui.theme.IconLibrary.vector("home"), "Home"),
            app.gridlauncher.ui.home.NavItem(app.gridlauncher.ui.theme.IconLibrary.vector("work"), "Work"),
            app.gridlauncher.ui.home.NavItem(app.gridlauncher.ui.theme.IconLibrary.vector("moon"), "Night"),
        )
    }
    Column(Modifier.padding(vertical = 14.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Screen switcher", fontSize = 15.sp)
            Text("The bar at the bottom of home for moving between screens", fontSize = 12.sp, color = Palette.Muted)
        }
        androidx.compose.foundation.lazy.LazyRow(
            Modifier.selectableGroup(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        ) {
            items(app.gridlauncher.data.NavStyle.entries.size) { i ->
                val style = app.gridlauncher.data.NavStyle.entries[i]
                val isSelected = style == selected
                Column(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) Palette.Accent else Palette.Line, RoundedCornerShape(18.dp))
                        .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(style) }
                        .semantics { contentDescription = "${style.title} switcher" }
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                ) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(width = 176.dp, height = 56.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Ground),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (style == app.gridlauncher.data.NavStyle.OFF) {
                            Text("Swipe only", fontSize = 12.sp, color = Palette.Muted)
                        } else {
                            // The preview isn't tappable itself; the whole card picks the style.
                            app.gridlauncher.ui.home.ScreenNavBar(
                                items = sample,
                                current = 1,
                                style = style,
                                progress = { 1f },
                                onSelect = {},
                                compact = true,
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                        }
                    }
                    Text(style.title, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, color = if (isSelected) Palette.Fg else Palette.Muted)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AccentRow(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
        Text("Accent color", fontSize = 15.sp)
        androidx.compose.foundation.layout.FlowRow(
            Modifier.selectableGroup(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            Palette.accentChoices.forEach { (argb, name) ->
                val isSelected = argb == selected
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(if (isSelected) 3.dp else 1.dp, if (isSelected) Palette.Fg else Palette.Line, CircleShape)
                        .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(argb) }
                        .semantics { contentDescription = name }
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Color(argb)),
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueText: (Float) -> String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    // Moves smoothly under the finger and saves once, when the finger lifts.
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        androidx.compose.foundation.layout.Row {
            Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(valueText(local), fontSize = 13.sp, color = Palette.Muted)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local) },
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Palette.Accent,
                activeTrackColor = Palette.Accent,
                inactiveTrackColor = Palette.Line,
                activeTickColor = Palette.Ground,
                inactiveTickColor = Palette.LineStrong,
            ),
        )
    }
}
