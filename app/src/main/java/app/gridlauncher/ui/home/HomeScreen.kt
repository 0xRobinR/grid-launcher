package app.gridlauncher.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import app.gridlauncher.gestures.GestureService
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gridlauncher.data.AppEntry
import app.gridlauncher.data.AppFolder
import app.gridlauncher.data.LauncherPage
import app.gridlauncher.data.LauncherSettings
import app.gridlauncher.data.lookNow
import app.gridlauncher.data.forPage
import app.gridlauncher.data.PageApps
import app.gridlauncher.data.PageOps
import app.gridlauncher.data.TextSpec
import app.gridlauncher.data.WidgetKind
import app.gridlauncher.domain.HorizontalAlign
import app.gridlauncher.ui.HomeEvent
import app.gridlauncher.ui.LauncherViewModel
import app.gridlauncher.ui.Screen
import app.gridlauncher.ui.components.startSafely
import app.gridlauncher.ui.monitor.MonitorScreen
import app.gridlauncher.ui.theme.IconLibrary
import app.gridlauncher.ui.theme.LauncherIcons
import kotlinx.coroutines.launch
import app.gridlauncher.ui.theme.Palette
import app.gridlauncher.widgets.WidgetController

private const val MONITOR_KEY = "monitor"
private const val PLUS_KEY = "add-screen"

/** Room left under the widget area, in edit mode, for the "Add apps" card of an empty screen. */
private const val EMPTY_CARD_SPACE_DP = 200f

/**
 * The home screens, swiped left and right. With the phone monitor on, it sits to the left of the
 * first screen. Each screen has its own free-form widgets and its own apps.
 */
@Composable
fun HomeScreen(vm: LauncherViewModel, settings: LauncherSettings, controller: WidgetController) {
    val context = LocalContext.current
    val editing by vm.editing.collectAsStateWithLifecycle()
    val selected by vm.selectedWidget.collectAsStateWithLifecycle()
    val pickerOpen by vm.pickerOpen.collectAsStateWithLifecycle()
    val pageSheetOpen by vm.pageSheetOpen.collectAsStateWithLifecycle()
    val editingText by vm.editingText.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val allPages = settings.pages
    val ruleContext by vm.ruleContext.collectAsStateWithLifecycle()
    // Screens with a time or Wi-Fi rule are hidden outside it, except while you manage screens.
    val managing = editing || pageSheetOpen
    val pages = remember(allPages, ruleContext, managing) {
        if (managing) allPages else PageOps.visible(allPages, ruleContext.minuteOfDay, ruleContext.wifi)
    }
    // Check the rules again at each new minute, and the Wi-Fi network with them.
    LaunchedEffect(vm) {
        while (true) {
            val now = System.currentTimeMillis()
            delay(60_000 - now % 60_000 + 50)
            vm.refreshRuleContext(app.gridlauncher.data.currentWifiName(context))
        }
    }
    val offset = if (settings.monitorEnabled) 1 else 0
    // Past the last screen sits a page that adds a new one, until the limit is reached.
    val plusIndex = if (allPages.size < PageOps.MAX_PAGES) pages.size + offset else -1
    val pageCount = pages.size + offset + if (plusIndex >= 0) 1 else 0
    // Come back to the screen that was in view before Settings or a picker opened.
    val pagerState = rememberPagerState(
        initialPage = pages.indexOfFirst { it.uid == vm.currentPage.value }.let { if (it >= 0) it + offset else offset },
    ) { pageCount }
    val gridStates = vm.gridStates
    var openMenu by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingShow by remember { mutableStateOf<String?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingApp by rememberSaveable { mutableStateOf(false) }

    fun pageAt(index: Int): LauncherPage? = pages.getOrNull(index - offset)

    val latestPages by rememberUpdatedState(pages)
    val latestOffset by rememberUpdatedState(offset)
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                HomeEvent.CloseMenus -> openMenu = null
                HomeEvent.GoHome -> {
                    latestPages.firstOrNull()?.let { gridStates[it.uid]?.animateScrollToItem(0) }
                    pagerState.animateScrollToPage(latestOffset)
                }
                is HomeEvent.ShowPage -> pendingShow = event.uid
            }
        }
    }
    // A screen that was just added or moved may not be in the list until the next save lands.
    LaunchedEffect(pendingShow, pages) {
        val uid = pendingShow ?: return@LaunchedEffect
        val index = pages.indexOfFirst { it.uid == uid }
        if (index >= 0) {
            pagerState.animateScrollToPage(index + offset)
            pendingShow = null
        }
    }
    // When screens show or hide, stay on the screen that was in view.
    LaunchedEffect(pages.map { it.uid }) {
        val index = pages.indexOfFirst { it.uid == vm.currentPage.value }
        if (index >= 0 && index + offset != pagerState.currentPage) pagerState.scrollToPage(index + offset)
    }
    LaunchedEffect(pagerState.settledPage, pages, offset) {
        // The add-screen page isn't a screen: keep the last real screen as the current one, and
        // leave edit mode, which has nothing to edit there.
        if (pagerState.settledPage == plusIndex) vm.exitEdit() else vm.onPageShown(pageAt(pagerState.settledPage)?.uid)
    }

    val currentPage = pageAt(pagerState.currentPage)
    val currentIndex = pagerState.currentPage - offset
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val showSwitcher = !editing && pages.size + offset > 1 && settings.navStyle != app.gridlauncher.data.NavStyle.OFF
    val customPages = allPages.mapIndexedNotNull { i, p -> if (p.appsMode == PageApps.CUSTOM) p.uid to "Screen ${i + 1}" else null }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            key = { index -> if (index == plusIndex) PLUS_KEY else pageAt(index)?.uid ?: MONITOR_KEY },
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            val page = pageAt(index)
            if (index == plusIndex) {
                AddScreenPage(count = allPages.size, max = PageOps.MAX_PAGES) {
                    vm.addPage(after = allPages.lastOrNull()?.uid)
                    vm.enterEdit()
                }
            } else if (page == null) {
                MonitorScreen(vm = vm, active = pagerState.settledPage == index)
            } else {
                PageContent(
                    vm = vm,
                    page = page,
                    pageIndex = index - offset,
                    baseSettings = settings,
                    controller = controller,
                    gridState = gridStates.getOrPut(page.uid) { LazyGridState() },
                    editing = editing,
                    selected = selected,
                    openMenu = openMenu,
                    onOpenMenu = { openMenu = it },
                    customPages = customPages,
                    statusTop = statusTop,
                    navBottom = navBottom,
                    onClear = { confirmClear = page.uid },
                    showSwitcher = showSwitcher,
                )
            }
        }

        // Charging, battery, music and notification effects: part of home only, and they take no touches.
        EdgeEffectsOverlay(settings, Modifier.fillMaxSize())

        if (showSwitcher) {
            val items = buildList {
                if (offset == 1) add(NavItem(LauncherIcons.Pulse, "Monitor"))
                pages.forEach { p ->
                    // Name and icon follow the screen's place among all screens, as in edit mode.
                    val i = allPages.indexOfFirst { it.uid == p.uid }
                    add(NavItem(IconLibrary.vector(IconLibrary.keyFor(p.icon, i)), p.name.ifBlank { "Screen ${i + 1}" }))
                }
            }
            ScreenNavBar(
                items = items,
                current = pagerState.currentPage,
                style = settings.navStyle,
                // Read while drawing, so the indicator follows the swipe without recomposing.
                progress = { (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, (items.size - 1).toFloat()) },
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 10.dp),
            )
        }

        UndoBar(
            vm = vm,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (editing) 104.dp else 72.dp),
        )

        if (editing && currentPage != null) {
            val canUndo by vm.canUndoLayout.collectAsStateWithLifecycle()
            EditHeader(
                title = if (pages.size > 1) "Edit Screen ${currentIndex + 1} of ${pages.size}" else "Edit home screen",
                locked = settings.lockLayout,
                canUndo = canUndo,
                onUndo = vm::undoLayout,
                onDone = vm::exitEdit,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            EditBar(
                onAddWidget = vm::openPicker,
                onScreen = vm::openPageSheet,
                onWallpaper = {
                    val chooser = Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Choose wallpaper")
                    if (context.startSafely(chooser)) vm.setShowWallpaper(true) else vm.toast("No wallpaper app found")
                },
                onSettings = { vm.open(Screen.SETTINGS) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (!settings.tutorialDone) TutorialOverlay(onDone = { vm.setTutorialDone(true) })

    val gesturePrompt by vm.gesturePrompt.collectAsStateWithLifecycle()
    if (gesturePrompt) {
        AlertDialog(
            onDismissRequest = vm::dismissGesturePrompt,
            containerColor = Palette.Surface3,
            titleContentColor = Palette.Fg,
            textContentColor = Palette.FgSoft,
            title = { Text("Turn on gestures", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Android lets launchers open notifications and lock the screen only through an accessibility service. " +
                        "Turn on \"Grid Launcher gestures\" in the list. It doesn't read anything on your screen.\n\n" +
                        "If the switch is greyed out, open App info for Grid Launcher, tap ⋮ and choose Allow restricted settings first.",
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.dismissGesturePrompt()
                    GestureService.openSettings(context)
                }) { Text("Open settings", color = Palette.Accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { vm.declineGesturePrompt() }) { Text("Not now", color = Palette.FgSoft) } },
        )
    }

    if (pickerOpen) {
        WidgetPickerSheet(
            controller = controller,
            onDismiss = vm::closePicker,
            onPickBuiltIn = { kind ->
                vm.closePicker()
                when (kind) {
                    WidgetKind.IMAGE -> vm.requestPhoto(null)
                    WidgetKind.APP -> choosingApp = true
                    else -> vm.addBuiltIn(kind)
                }
            },
            onPick = { info ->
                vm.closePicker()
                controller.requestAdd(info)
            },
        )
    }

    // The system photo picker: needs no storage permission, and shows only what the user picks.
    val photoRequest by vm.photoRequest.collectAsStateWithLifecycle()
    var photoTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = photoTarget
        photoTarget = null
        if (uri != null && target != null) {
            if (target == app.gridlauncher.ui.NEW_PHOTO) vm.addImage(uri) else vm.setImage(target, uri)
        }
    }
    LaunchedEffect(photoRequest) {
        val request = photoRequest ?: return@LaunchedEffect
        vm.consumePhotoRequest()
        photoTarget = request
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    if (choosingApp) {
        AppChooserDialog(
            vm = vm,
            themed = settings.themedIcons,
            onDismiss = { choosingApp = false },
            onPick = { app ->
                choosingApp = false
                vm.placeAppOnBoard(currentPage?.uid, app)
            },
        )
    }

    if (pageSheetOpen && currentPage != null) {
        PageSheet(
            page = currentPage,
            index = currentIndex,
            count = allPages.size,
            onDismiss = vm::closePageSheet,
            onMode = { mode -> vm.setPageAppsMode(currentPage.uid, mode) },
            onName = { name -> vm.setPageName(currentPage.uid, name) },
            onIcon = { icon -> vm.setPageIcon(currentPage.uid, icon) },
            onChooseApps = {
                vm.closePageSheet()
                vm.openAppPicker(currentPage.uid)
            },
            onMove = { delta -> vm.movePage(currentPage.uid, delta) },
            onRule = { rule -> vm.setPageRule(currentPage.uid, rule) },
            launcherLook = settings.lookNow(),
            onAddBoard = { vm.addBoard(currentPage.uid) },
            onShare = { vm.showShareCode(vm.screenShareCode(currentPage.uid), currentPage.name.ifBlank { "Screen ${currentIndex + 1}" }, "screen") },
            onSave = { name -> vm.saveScreenSetup(currentPage.uid, name) },
            onLook = { look -> vm.setPageLook(currentPage.uid, look) },
            onDuplicate = { vm.duplicatePage(currentPage.uid) },
            onAdd = vm::addPage,
            onDelete = { confirmDelete = currentPage.uid },
            canAdd = allPages.size < PageOps.MAX_PAGES,
        )
    }

    val textWidget = editingText?.let { uid -> allPages.firstNotNullOfOrNull { p -> p.widgets.firstOrNull { it.uid == uid } } }
    if (textWidget != null && textWidget.kind == WidgetKind.NOTE) {
        NoteEditorSheet(
            initial = textWidget.text?.text.orEmpty(),
            onDismiss = vm::closeTextEditor,
            onSave = { note ->
                vm.updateText(textWidget.uid, (textWidget.text ?: TextSpec(text = "")).copy(text = note))
                vm.closeTextEditor()
            },
        )
    } else if (textWidget != null) {
        TextEditorSheet(
            initial = textWidget.text ?: TextSpec(),
            onDismiss = vm::closeTextEditor,
            onSave = { spec ->
                vm.updateText(textWidget.uid, spec)
                vm.closeTextEditor()
            },
        )
    }

    confirmClear?.let { uid ->
        val index = allPages.indexOfFirst { it.uid == uid }
        ConfirmDialog(
            title = "Clear apps from Screen ${index + 1}?",
            body = "Every app is removed from this screen, leaving only its widgets. Nothing is uninstalled. To add your own set, long-press empty space and tap Add apps.",
            confirm = "Clear apps",
            onConfirm = {
                vm.clearPageApps(uid)
                confirmClear = null
            },
            onDismiss = { confirmClear = null },
        )
    }

    confirmDelete?.let { uid ->
        val page = allPages.firstOrNull { it.uid == uid }
        val index = allPages.indexOfFirst { it.uid == uid }
        ConfirmDialog(
            title = "Delete Screen ${index + 1}?",
            body = "Its widgets and app set are removed. Your apps stay installed.",
            confirm = "Delete screen",
            onConfirm = {
                vm.deletePage(uid)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun PageContent(
    vm: LauncherViewModel,
    page: LauncherPage,
    pageIndex: Int,
    baseSettings: LauncherSettings,
    controller: WidgetController,
    gridState: LazyGridState,
    editing: Boolean,
    selected: String?,
    openMenu: String?,
    onOpenMenu: (String?) -> Unit,
    customPages: List<Pair<String, String>>,
    statusTop: Dp,
    navBottom: Dp,
    onClear: () -> Unit,
    /** Whether the screen switcher sits at the bottom, so the last apps stay clear of it. */
    showSwitcher: Boolean,
) {
    // The launcher's settings, with this screen's own grid look (if it has one) on top.
    val settings = remember(baseSettings, page.look) { baseSettings.forPage(page) }
    val context = LocalContext.current
    val gridApps by vm.gridApps.collectAsStateWithLifecycle()
    val allApps by vm.allApps.collectAsStateWithLifecycle()
    val favorites by vm.favoriteApps.collectAsStateWithLifecycle()
    val permission by vm.shortcutPermission.collectAsStateWithLifecycle()
    val version by vm.contentVersion.collectAsStateWithLifecycle()

    val recents by vm.recentApps.collectAsStateWithLifecycle()
    val dots by vm.notificationCounts.collectAsStateWithLifecycle()
    var openFolder by rememberSaveable(page.uid) { mutableStateOf<String?>(null) }
    // What the grid shows: apps, and on a custom screen, folders in their place in the set.
    val apps: List<GridEntry> = when (page.appsMode) {
        PageApps.ALL -> remember(gridApps) { gridApps.map(GridEntry::App) }
        PageApps.CUSTOM -> remember(page.apps, page.folders, allApps) {
            val byKey = allApps.associateBy(AppEntry::key)
            page.apps.mapNotNull { key ->
                val folderId = AppFolder.idOf(key)
                if (folderId == null) {
                    byKey[key]?.let(GridEntry::App)
                } else {
                    page.folders.firstOrNull { it.id == folderId }
                        ?.let { f -> GridEntry.Folder(f, f.apps.mapNotNull(byKey::get)) }
                        ?.takeIf { it.apps.isNotEmpty() }
                }
            }
        }
        PageApps.NONE -> emptyList()
    }
    val custom = page.appsMode == PageApps.CUSTOM
    // A custom screen with no apps chosen is a widgets-only screen. While editing, it still offers
    // "Add apps" so the user can bring apps back.
    val emptyCustom = custom && apps.isEmpty()
    val showApps = page.appsMode == PageApps.ALL || (custom && apps.isNotEmpty())
    val ctx = CellContext(
        vm, settings, version, permission, editing,
        customPageUid = if (custom) page.uid else null,
        customPages = customPages,
        dots = dots,
        // Only folders that still have their place in the set.
        folders = if (custom) page.folders.filter { it.marker in page.apps } else emptyList(),
        screenUid = page.uid,
    )
    val appsByKey = remember(allApps) { allApps.associateBy(AppEntry::key) }
    val enterEdit: (String?) -> Unit = { uid -> vm.enterEdit(uid) }
    val widgetActions = remember(vm, controller) {
        WidgetActions(
            onAlign = { uid, mode -> vm.alignWidget(uid, mode) },
            onEdit = vm::editText,
            onFront = { uid -> vm.bringToFront(uid) },
            // The system widget id is freed only after Undo has passed.
            onRemove = { w -> vm.removeWidget(w.uid) },
            onTimer = { uid, minutes -> vm.setTimer(uid, minutes) },
            onTimerExtend = { uid -> vm.extendTimer(uid) },
            onMessage = vm::toast,
            onChangeImage = { uid -> vm.requestPhoto(uid) },
            onShape = vm::cycleImageShape,
            onFillSlot = { w ->
                val info = controller.findProvider(w.provider)
                if (info == null) {
                    vm.toast("${w.label.ifBlank { "That app" }.substringBefore(" · ")} isn't installed. Install it, then tap here again.")
                } else {
                    vm.fillSlot(w.uid)
                    controller.requestAdd(info)
                }
            },
        )
    }
    val topPad = statusTop + if (editing) 84.dp else 32.dp
    // Leave room for the edit bar, or for the screen navbar.
    val bottomPad = navBottom + when {
        editing -> 120.dp
        showSwitcher -> 76.dp
        else -> 16.dp
    }

    // Swipe down past the top (or up past the bottom) runs the chosen gesture, once per swipe.
    val latestSettings by rememberUpdatedState(settings)
    val latestEditing by rememberUpdatedState(editing)
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    val gestures = remember(vm) {
        object : NestedScrollConnection {
            var pull = 0f
            var fired = false

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (latestEditing || source != NestedScrollSource.UserInput || fired) return Offset.Zero
                if (available.y == 0f || (pull != 0f && kotlin.math.sign(available.y) != kotlin.math.sign(pull))) pull = 0f
                pull += available.y
                if (pull > threshold) {
                    fired = true
                    vm.performGesture(latestSettings.swipeDown)
                } else if (pull < -threshold) {
                    fired = true
                    vm.performGesture(latestSettings.swipeUp)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pull = 0f
                fired = false
                return Velocity.Zero
            }
        }
    }

    val folderEntry = apps.firstNotNullOfOrNull { (it as? GridEntry.Folder)?.takeIf { f -> f.folder.id == openFolder } }
    if (folderEntry != null) {
        FolderSheet(
            folder = folderEntry.folder,
            apps = folderEntry.apps,
            ctx = ctx,
            onRename = { name -> vm.renameFolder(page.uid, folderEntry.folder.id, name) },
            onDismiss = { openFolder = null },
        )
    }

    // One widget area: the top one ("") or one between rows of apps.
    val board: @Composable (String, Float, Float) -> Unit = { boardUid, minHeight, minEditHeight ->
        WidgetArea(
            widgets = page.widgets.filter { it.board == boardUid },
            editing = editing,
            selectedUid = selected,
            snap = settings.snapToGrid,
            locked = settings.lockLayout,
            controller = controller,
            onEnterEdit = enterEdit,
            onSelect = vm::selectWidget,
            onCommit = { frames, front, layoutWidth -> vm.updateWidgetFrames(frames, front, layoutWidth) },
            layoutWidth = page.layoutWidth,
            appContent = { w ->
                val app = appsByKey[w.appKey]
                if (app != null) {
                    val key = "board:${w.uid}"
                    AppGridCell(
                        app = app,
                        ctx = ctx.onBoard(w.uid),
                        menuOpen = openMenu == key,
                        onOpenMenu = { onOpenMenu(key) },
                        onCloseMenu = { onOpenMenu(null) },
                    )
                } else if (editing) {
                    // Its app was uninstalled: visible only while editing, to remove it.
                    Text("App removed", fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.padding(top = 24.dp))
                }
            },
            onAreaWidth = { vm.widgetAreaWidthDp = it },
            actions = widgetActions,
            onDoubleTap = { vm.performGesture(settings.doubleTap) },
            minHeightDp = minHeight,
            minEditHeightDp = minEditHeight,
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // A widgets-only screen gives the whole screen to its widgets.
        val pageHeight = (maxHeight - topPad - bottomPad).value
        val fullHeight = when {
            showApps -> 0f
            emptyCustom && editing -> (pageHeight - EMPTY_CARD_SPACE_DP).coerceAtLeast(0f)
            else -> pageHeight
        }
        // The grid size's columns on a phone in portrait; proportionally more on wider screens.
        val columns = settings.gridSize.columns.let { cols ->
            if (maxWidth < 600.dp) cols else (maxWidth.value / (400f / cols)).toInt().coerceIn(cols + 1, 10)
        }
        val rows = if (showApps) (apps.size + columns - 1) / columns else 0
        // Widget areas between apps, top to bottom; each splits the apps after its row.
        val boards = page.boards.sortedBy { it.afterRow }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topPad, bottom = bottomPad),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(gestures)
                // Empty space anywhere on the screen: long-press to edit, double-tap for the gesture.
                // Apps and widgets handle their own touches first, so this only gets the gaps.
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { enterEdit(null) },
                        onDoubleTap = { vm.performGesture(latestSettings.doubleTap) },
                    )
                },
        ) {
            item(key = "widgets", span = { GridItemSpan(maxLineSpan) }, contentType = "widgets") {
                board("", fullHeight, 300f)
            }
            if (showApps && !permission && settings.quickActionCount > 0 && !editing && pageIndex == 0) {
                item(key = "permission", span = { GridItemSpan(maxLineSpan) }, contentType = "banner") {
                    HomeAppBanner { context.startSafely(Intent(Settings.ACTION_HOME_SETTINGS)) }
                }
            }
            if (settings.recentsEnabled && pageIndex == 0 && recents.isNotEmpty()) {
                item(key = "recents", span = { GridItemSpan(maxLineSpan) }, contentType = "favorites") {
                    FavoritesRow(
                        favorites = recents,
                        ctx = ctx.forAllApps(),
                        openMenuKey = openMenu,
                        onOpenMenu = { onOpenMenu(it) },
                        onCloseMenu = { onOpenMenu(null) },
                        title = "Recent",
                        keyPrefix = "recent",
                    )
                }
            }
            if (settings.favoritesEnabled && pageIndex == 0) {
                item(key = "favorites", span = { GridItemSpan(maxLineSpan) }, contentType = "favorites") {
                    FavoritesRow(
                        favorites = favorites,
                        ctx = ctx.forAllApps(),
                        openMenuKey = openMenu,
                        onOpenMenu = { onOpenMenu(it) },
                        onCloseMenu = { onOpenMenu(null) },
                    )
                }
            }
            if (emptyCustom && editing) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                    EmptyAppsCard(
                        onAdd = { vm.openAppPicker(page.uid) },
                        onShowAll = { vm.setPageAppsMode(page.uid, PageApps.ALL) },
                    )
                }
            }
            if (showApps) {
                // The Apps header, with Edit and Clear, only shows while editing the screen.
                if (editing) item(key = "apps-header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                    AppsHeader(
                        count = apps.size,
                        custom = custom,
                        onLongPress = { enterEdit(null) },
                        onEdit = { vm.openAppPicker(page.uid) },
                        onClear = onClear,
                    )
                }
            }
            run {
                // Apps in parts: each part ends where the next widget area sits. On a screen
                // without apps, the widget areas simply stack.
                val shown = if (showApps) apps else emptyList()
                var start = 0
                (boards + listOf<app.gridlauncher.data.Board?>(null)).forEachIndexed { i, b ->
                    val end = if (b == null) shown.size else (b.afterRow * columns).coerceIn(start, shown.size)
                    val partCtx = ctx.inSection(boards.getOrNull(i - 1)?.uid ?: "")
                    items(shown.subList(start, end), key = { it.key }, contentType = { if (it is GridEntry.Folder) "folder" else "app" }) { entry ->
                        when (entry) {
                            is GridEntry.App -> {
                                val key = "${page.uid}:${entry.app.key}"
                                AppGridCell(
                                    app = entry.app,
                                    ctx = partCtx,
                                    menuOpen = openMenu == key,
                                    onOpenMenu = { onOpenMenu(key) },
                                    onCloseMenu = { onOpenMenu(null) },
                                )
                            }
                            is GridEntry.Folder -> FolderCell(entry.folder, entry.apps, partCtx) { openFolder = entry.folder.id }
                        }
                    }
                    start = end
                    if (b != null) {
                        item(key = "board-${b.uid}", span = { GridItemSpan(maxLineSpan) }, contentType = "board") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (editing) {
                                    BoardHeader(
                                        row = minOf(b.afterRow, rows),
                                        onUp = { vm.moveBoard(page.uid, b.uid, -1) },
                                        onDown = { vm.moveBoard(page.uid, b.uid, 1) },
                                        onAdd = { vm.openPicker(b.uid) },
                                        onRemove = { vm.removeBoard(page.uid, b.uid) },
                                        canUp = b.afterRow > 0,
                                        canDown = b.afterRow < rows,
                                    )
                                }
                                board(b.uid, 0f, 160f)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** In edit mode, above a widget area between apps: where it sits, and its controls. */
@Composable
private fun BoardHeader(
    row: Int,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    canUp: Boolean,
    canDown: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("WIDGET AREA", fontSize = 12.sp, letterSpacing = 0.08.em, color = Palette.Muted)
            Text(if (row == 0) "Above the apps" else "After row $row of apps", fontSize = 12.sp, color = Palette.Subtle)
        }
        BoardButton("Move up a row", LauncherIcons.Chevron, -90f, canUp, onUp)
        BoardButton("Move down a row", LauncherIcons.Chevron, 90f, canDown, onDown)
        BoardButton("Add a widget here", LauncherIcons.AddWidget, 0f, true, onAdd)
        BoardButton("Remove this widget area", LauncherIcons.Trash, 0f, true, onRemove, danger = true)
    }
}

@Composable
private fun BoardButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, rotation: Float, enabled: Boolean, onClick: () -> Unit, danger: Boolean = false) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = when {
                !enabled -> Palette.Line
                danger -> Palette.Danger
                else -> Palette.Fg
            },
            modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotation },
        )
    }
}

@Composable
private fun AppsHeader(count: Int, custom: Boolean, onLongPress: () -> Unit, onEdit: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("APPS", fontSize = 12.sp, letterSpacing = 0.08.em, color = Palette.Muted)
        Text(
            "$count",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = Palette.Subtle,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        if (custom) HeaderButton("Edit", onEdit)
        if (count > 0) HeaderButton("Clear", onClear)
    }
}

@Composable
private fun HeaderButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Palette.Accent)
    }
}

@Composable
private fun EmptyAppsCard(onAdd: () -> Unit, onShowAll: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, Palette.Line, shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("This screen shows widgets only", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            "Add your own set of apps to list them here, or copy a set from another screen.",
            fontSize = 13.sp,
            color = Palette.Muted,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(Palette.Accent)
                    .clickable(role = Role.Button, onClick = onAdd)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Add apps", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ground)
            }
            Box(
                Modifier
                    .height(44.dp)
                    .clip(CircleShape)
                    .border(1.dp, Palette.Line, CircleShape)
                    .clickable(role = Role.Button, onClick = onShowAll)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Show all apps", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface3,
        titleContentColor = Palette.Fg,
        textContentColor = Palette.FgSoft,
        title = { Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = { Text(body, fontSize = 14.sp) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = Palette.Danger, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.FgSoft) } },
    )
}

@Composable
private fun EditHeader(
    title: String,
    locked: Boolean,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Palette.Ground)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (locked) "Layout is locked. Turn off Lock layout in Settings to move widgets."
                else "Drag to move · Pull corners to resize · Swipe for other screens",
                fontSize = 12.sp,
                color = Palette.Muted,
            )
        }
        // Steps back the last move, resize or align; dimmed when there's nothing to undo.
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(1.dp, if (canUndo) Palette.LineStrong else Palette.Line, CircleShape)
                .clickable(enabled = canUndo, role = Role.Button, onClickLabel = "Undo last widget change", onClick = onUndo)
                .semantics { contentDescription = "Undo" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(LauncherIcons.Undo, contentDescription = null, tint = if (canUndo) Palette.Fg else Palette.Subtle, modifier = Modifier.size(20.dp))
        }
        Box(
            Modifier
                .height(44.dp)
                .clip(CircleShape)
                .background(Palette.Accent)
                .clickable(role = Role.Button, onClick = onDone)
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Done", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ground)
        }
    }
}

@Composable
private fun EditBar(
    onAddWidget: () -> Unit,
    onScreen: () -> Unit,
    onWallpaper: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 16.dp)
            .height(72.dp)
            .clip(shape)
            .background(Palette.Surface3)
            .border(1.dp, Palette.Line, shape),
    ) {
        EditBarButton("Add widget", LauncherIcons.AddWidget, highlighted = true, onClick = onAddWidget, modifier = Modifier.weight(1f))
        EditBarButton("Screen", LauncherIcons.Screens, onClick = onScreen, modifier = Modifier.weight(1f))
        EditBarButton("Wallpaper", LauncherIcons.Wallpaper, onClick = onWallpaper, modifier = Modifier.weight(1f))
        EditBarButton("Settings", LauncherIcons.Sliders, onClick = onSettings, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EditBarButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val color = if (highlighted) Palette.Accent else Palette.Fg
    Column(
        modifier.fillMaxSize().clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 12.sp, fontWeight = if (highlighted) FontWeight.Medium else FontWeight.Normal, color = color)
    }
}

@Composable
internal fun PillButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    val shape = CircleShape
    Row(
        Modifier
            .height(44.dp)
            .clip(shape)
            .background(Palette.Surface)
            .border(1.dp, Palette.Line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Palette.Fg, modifier = Modifier.size(16.dp))
        Text(label, fontSize = 13.sp)
    }
}

@Composable
private fun HomeAppBanner(onSetDefault: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Surface)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Quick actions appear once Grid Launcher is your home app.",
            fontSize = 13.sp,
            color = Palette.FgSoft,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .heightIn(min = 44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onSetDefault)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Set as home", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Accent)
        }
    }
}

@Composable
private fun UndoBar(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val action by vm.undo.collectAsStateWithLifecycle()
    // The view model times the offer out, so leaving home doesn't keep it alive.
    val current = action ?: return
    Row(
        modifier
            .padding(horizontal = 16.dp)
            .clip(CircleShape)
            .background(Palette.Surface3)
            .border(1.dp, Palette.LineStrong, CircleShape)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(current.message, fontSize = 14.sp)
        Box(
            Modifier
                .height(40.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = vm::undoLast)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Undo", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.Accent)
        }
    }
}


private class TutorialStep(val title: String, val body: String)

private val TUTORIAL = listOf(
    TutorialStep("Your apps, one tap away", "Every app is on your home screen. The buttons under an app are its quick actions: Selfie under Camera, Compose under Mail."),
    TutorialStep("Long-press for more", "Hold an app for all its shortcuts, favorites, folders and more. Hold empty space to edit the screen."),
    TutorialStep("Make it yours", "In edit mode, add widgets and text labels, drag them anywhere, and hold one over another for 2 seconds to make room."),
    TutorialStep("Many screens", "Swipe between screens, or tap the icons in the bar at the bottom. Swipe past the last screen to add one."),
)

/** A short first-launch walkthrough, shown once. */
@Composable
private fun TutorialOverlay(onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val current = TUTORIAL[step]
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Ground.copy(alpha = 0.82f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Palette.Surface)
                .border(1.dp, Palette.Line, RoundedCornerShape(28.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("${step + 1} of ${TUTORIAL.size}", fontSize = 12.sp, color = Palette.Muted, fontFamily = FontFamily.Monospace)
            Text(current.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(current.body, fontSize = 15.sp, lineHeight = 22.sp, color = Palette.FgSoft)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(TUTORIAL.size) { i ->
                    Box(Modifier.size(width = if (i == step) 18.dp else 6.dp, height = 6.dp).clip(CircleShape).background(if (i == step) Palette.Accent else Palette.LineStrong))
                }
                Box(Modifier.weight(1f))
                TextButton(onClick = onDone) { Text("Skip", color = Palette.Muted) }
                Box(
                    Modifier
                        .height(44.dp)
                        .clip(CircleShape)
                        .background(Palette.Accent)
                        .clickable(role = Role.Button) { if (step < TUTORIAL.lastIndex) step++ else onDone() }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (step < TUTORIAL.lastIndex) "Next" else "Get started", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ground)
                }
            }
        }
    }
}

/** One cell of a screen's app grid: an app, or a folder of apps on a custom screen. */
sealed interface GridEntry {
    val key: String

    class App(val app: AppEntry) : GridEntry {
        override val key: String get() = app.key
    }

    class Folder(val folder: AppFolder, val apps: List<AppEntry>) : GridEntry {
        override val key: String get() = folder.marker
    }
}
