package app.gridlauncher.ui

import android.app.Application
import android.appwidget.AppWidgetManager
import android.net.Uri
import app.gridlauncher.gestures.GestureService
import kotlinx.coroutines.withContext
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.gridlauncher.data.AppEntry
import app.gridlauncher.data.LauncherPage
import app.gridlauncher.data.LauncherSettings
import app.gridlauncher.data.PageApps
import app.gridlauncher.data.PageOps
import app.gridlauncher.data.PlacedWidget
import app.gridlauncher.data.Backup
import app.gridlauncher.data.DotStyle
import app.gridlauncher.data.SavedSetup
import app.gridlauncher.data.SetupStore
import app.gridlauncher.data.AppGroup
import app.gridlauncher.data.ShareCode
import app.gridlauncher.data.ImageShape
import app.gridlauncher.data.ImageStore
import app.gridlauncher.data.GridSize
import app.gridlauncher.data.forPage
import app.gridlauncher.notifications.NotificationCounts
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import app.gridlauncher.data.GestureAction
import app.gridlauncher.data.ScreenRule
import app.gridlauncher.data.TextSpec
import app.gridlauncher.data.WidgetKind
import app.gridlauncher.domain.AppOrdering
import app.gridlauncher.domain.SortMode
import app.gridlauncher.domain.WidgetBounds
import app.gridlauncher.domain.HorizontalAlign
import app.gridlauncher.domain.WidgetGeometry
import app.gridlauncher.domain.WidgetLayout
import app.gridlauncher.launcherApp
import app.gridlauncher.timer.Timers
import app.gridlauncher.monitor.MonitorWorker
import app.gridlauncher.data.IconPacks
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class Screen { HOME, SETTINGS, FONTS, HIDDEN_APPS, ARRANGE_APPS, PICK_APPS, TUTORIALS, GROUPS, SETUPS }

sealed interface HomeEvent {
    /** Close long-press menus and dialogs. */
    data object CloseMenus : HomeEvent

    /** Show the first home screen, scrolled to the top. */
    data object GoHome : HomeEvent

    /** Swipe to the screen with this uid. */
    data class ShowPage(val uid: String) : HomeEvent
}

private const val UNDO_WINDOW_MS = 6_000L

private const val LAYOUT_HISTORY = 30

/** [LauncherViewModel.photoRequest] for a new photo widget rather than an existing one. */
const val NEW_PHOTO = "new-photo"

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val launcher = application.launcherApp
    private val repo = launcher.apps
    private val store = launcher.settings
    val icons = launcher.icons

    /** Null until the first read from disk, so the home screen never flashes default values. */
    val settings: StateFlow<LauncherSettings?> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val allApps: StateFlow<List<AppEntry>> = repo.apps

    /** Every visible app in the global sort order: what an "All apps" screen shows. */
    val gridApps: StateFlow<List<AppEntry>> = combine(repo.apps, store.settings) { apps, s ->
        AppOrdering.sort(
            items = apps.filter { it.key !in s.hiddenApps },
            key = AppEntry::key,
            label = AppEntry::label,
            mode = s.sortMode,
            launchCounts = s.launchCounts,
            customOrder = s.customOrder,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Recently opened apps, most recent first, for the recents row. */
    val recentApps: StateFlow<List<AppEntry>> = combine(repo.apps, store.settings) { apps, s ->
        val byKey = apps.associateBy(AppEntry::key)
        s.recents.mapNotNull(byKey::get).filter { it.key !in s.hiddenApps }.take(6)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Notification counts per app, from the notification-dots listener. */
    val notificationCounts: StateFlow<Map<String, Int>> = NotificationCounts.counts

    val favoriteApps: StateFlow<List<AppEntry>> = combine(repo.apps, store.settings) { apps, s ->
        val byKey = apps.associateBy(AppEntry::key)
        s.favorites.mapNotNull(byKey::get).filter { it.key !in s.hiddenApps }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Changes whenever app icons or shortcuts should be reloaded. */
    val contentVersion: StateFlow<Int> = repo.version

    private val _shortcutPermission = MutableStateFlow(repo.hasShortcutPermission())
    val shortcutPermission: StateFlow<Boolean> = _shortcutPermission.asStateFlow()

    private val _screen = MutableStateFlow(Screen.HOME)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    private val _selectedWidget = MutableStateFlow<String?>(null)
    val selectedWidget: StateFlow<String?> = _selectedWidget.asStateFlow()

    private val _pickerOpen = MutableStateFlow(false)
    val pickerOpen: StateFlow<Boolean> = _pickerOpen.asStateFlow()

    private val _pageSheetOpen = MutableStateFlow(false)
    val pageSheetOpen: StateFlow<Boolean> = _pageSheetOpen.asStateFlow()

    /** The text widget whose editor is open. */
    private val _editingText = MutableStateFlow<String?>(null)
    val editingText: StateFlow<String?> = _editingText.asStateFlow()

    /** The home screen in view, or null while the phone monitor is in view. */
    private val _currentPage = MutableStateFlow<String?>(null)
    val currentPage: StateFlow<String?> = _currentPage.asStateFlow()

    /** The screen the app picker or arrange screen works on; null means the global order. */
    private val _targetPage = MutableStateFlow<String?>(null)
    val targetPage: StateFlow<String?> = _targetPage.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    /** Each screen's grid scroll position, kept while Settings or a picker is open. */
    val gridStates = HashMap<String, LazyGridState>()

    /** Width of the widget area in dp, measured by the home screen. */
    @Volatile
    var widgetAreaWidthDp: Float = 360f

    init {
        // The 15-minute monitor check runs only while the monitor or its alerts are on.
        viewModelScope.launch {
            store.settings.map { it.monitorEnabled || it.monitorAlerts }.distinctUntilChanged().collect { on ->
                if (on) MonitorWorker.schedule(getApplication()) else MonitorWorker.cancel(getApplication())
            }
        }
        // Photos left behind by widgets removed in an earlier run.
        viewModelScope.launch {
            val first = store.settings.first()
            val used = first.pages.flatMap { p -> p.widgets.mapNotNull { it.image.takeIf(String::isNotEmpty) } }.toSet()
            ImageStore.deleteUnused(getApplication(), used + SetupStore.photosInUse(getApplication()))
            _setups.value = SetupStore.list(getApplication())
        }
        // Icon packs: switching one reloads every icon.
        viewModelScope.launch {
            store.settings.map { it.iconPack }.distinctUntilChanged().collect { pack ->
                if (pack.isNotEmpty()) IconPacks.forget(pack)
                icons.iconPack = pack
                repo.reloadContent()
            }
        }
        viewModelScope.launch {
            store.initializePagesIfNeeded {
                listOf(LauncherPage(uid = newUid(), widgets = listOf(defaultClock())))
            }
        }
    }

    private fun newUid() = UUID.randomUUID().toString()

    private fun defaultClock() = PlacedWidget(newUid(), WidgetKind.CLOCK, -1, WidgetBounds(0f, 0f, 264f, 112f))

    // ---- Lifecycle and navigation ------------------------------------------------------------

    fun onResume() {
        val granted = repo.hasShortcutPermission()
        if (granted != _shortcutPermission.value) {
            _shortcutPermission.value = granted
            repo.invalidateAllShortcuts()
        }
    }

    private fun closeTransient() {
        _events.tryEmit(HomeEvent.CloseMenus)
        _pickerOpen.value = false
        _pageSheetOpen.value = false
        _editingText.value = null
    }

    /**
     * [alreadyOnTop] is true when Home was pressed while the launcher was already in front. Coming
     * back from another app only closes transient UI; pressing Home again goes to the first screen.
     */
    /** Back to the first screen, scrolled to the top, closing any open menu. */
    fun goToFirstScreen() {
        _events.tryEmit(HomeEvent.CloseMenus)
        _events.tryEmit(HomeEvent.GoHome)
    }

    fun onHomePressed(alreadyOnTop: Boolean) {
        val hadSheet = _pickerOpen.value || _pageSheetOpen.value || _editingText.value != null
        closeTransient()
        if (!alreadyOnTop) {
            _screen.value = Screen.HOME
            exitEdit()
            return
        }
        when {
            hadSheet -> Unit
            _screen.value != Screen.HOME -> _screen.value = Screen.HOME
            _editing.value -> exitEdit()
            else -> _events.tryEmit(HomeEvent.GoHome)
        }
    }

    fun open(screen: Screen) {
        closeTransient()
        _screen.value = screen
    }

    fun back() {
        _screen.value = when (_screen.value) {
            Screen.FONTS, Screen.HIDDEN_APPS, Screen.TUTORIALS, Screen.GROUPS, Screen.SETUPS -> Screen.SETTINGS
            Screen.ARRANGE_APPS -> if (returnToSettingsFromArrange) Screen.SETTINGS else Screen.HOME
            Screen.SETTINGS, Screen.HOME, Screen.PICK_APPS -> Screen.HOME
        }
    }

    private var returnToSettingsFromArrange = false

    /** Opens drag-to-arrange for [pageUid]'s own apps, or for the global order when null. */
    fun openArrange(fromSettings: Boolean, pageUid: String? = null) {
        returnToSettingsFromArrange = fromSettings
        _targetPage.value = pageUid
        open(Screen.ARRANGE_APPS)
    }

    fun openAppPicker(pageUid: String) {
        _targetPage.value = pageUid
        open(Screen.PICK_APPS)
    }

    fun onPageShown(uid: String?) {
        // A screen just added opens its options as soon as it's in view.
        if (uid != null && uid == sheetForNewPage) {
            sheetForNewPage = null
            _currentPage.value = uid
            _editing.value = true
            _pageSheetOpen.value = true
            return
        }
        if (_currentPage.value == uid) return
        _currentPage.value = uid
        // The phone monitor has nothing to edit.
        if (uid == null && _editing.value) exitEdit()
    }

    // ---- Edit mode ---------------------------------------------------------------------------

    fun enterEdit(selectUid: String? = null) {
        _events.tryEmit(HomeEvent.CloseMenus)
        _editing.value = true
        _selectedWidget.value = selectUid
    }

    fun exitEdit() {
        clearLayoutHistory()
        _editing.value = false
        _selectedWidget.value = null
        _pickerOpen.value = false
        _pageSheetOpen.value = false
    }

    fun selectWidget(uid: String?) {
        _selectedWidget.value = uid
    }

    /** The widget area new widgets go into: empty for the top one, else a board's uid. */
    private var targetBoard = ""

    /**
     * Opens Add widget for widget area [board]; without one, the area of the selected widget, or
     * the top area.
     */
    fun openPicker(board: String? = null) {
        val selectedBoard = _selectedWidget.value?.let { uid ->
            settings.value?.pages.orEmpty().firstNotNullOfOrNull { p -> p.widgets.firstOrNull { it.uid == uid } }?.board
        }
        targetBoard = board ?: selectedBoard ?: ""
        slotToFill = null
        _pickerOpen.value = true
    }

    // ---- Widget areas between apps ----------------------------------------------------------

    /** Adds a widget area to screen [pageUid], two rows of apps below the last one. */
    fun addBoard(pageUid: String) = viewModelScope.launch {
        val page = settings.value?.pages?.firstOrNull { it.uid == pageUid } ?: return@launch
        val row = (page.boards.maxOfOrNull { it.afterRow } ?: 0) + 2
        store.updatePages { PageOps.addBoard(it, pageUid, app.gridlauncher.data.Board(newUid(), row)) }
        _pageSheetOpen.value = false
        _editing.value = true
        toast("Widget area added after row $row. Move it with its arrows.")
    }

    fun moveBoard(pageUid: String, boardUid: String, delta: Int) = viewModelScope.launch {
        store.updatePages { PageOps.moveBoard(it, pageUid, boardUid, delta) }
    }

    /** Removes a widget area and its widgets, with Undo. */
    fun removeBoard(pageUid: String, boardUid: String) = viewModelScope.launch {
        var removed: Pair<app.gridlauncher.data.Board, List<PlacedWidget>>? = null
        store.updatePages { pages ->
            val page = pages.firstOrNull { it.uid == pageUid } ?: return@updatePages pages
            val board = page.boards.firstOrNull { it.uid == boardUid } ?: return@updatePages pages
            removed = board to page.widgets.filter { it.board == boardUid }
            PageOps.removeBoard(pages, pageUid, boardUid)
        }
        val (board, widgets) = removed ?: return@launch
        widgets.filter { it.kind == WidgetKind.TIMER }.forEach { Timers.cancel(getApplication(), it.uid) }
        val restored = widgets.map { it.copy(timerEndsAt = 0L, timerLengthMs = 0L) }
        offerUndo(
            "Widget area removed",
            restore = { PageOps.reinsertBoard(it, pageUid, board, restored) },
            widgetIds = widgets.filter { it.kind == WidgetKind.APP_WIDGET }.map { it.appWidgetId }.toSet(),
        )
    }

    fun closePicker() {
        _pickerOpen.value = false
    }

    fun openPageSheet() {
        _pageSheetOpen.value = true
    }

    fun closePageSheet() {
        _pageSheetOpen.value = false
    }

    // ---- Widgets -----------------------------------------------------------------------------

    fun updateWidgetBounds(uid: String, bounds: WidgetBounds) = viewModelScope.launch {
        store.updatePages { PageOps.updateWidget(it, uid) { w -> w.copy(bounds = bounds) } }
    }

    fun bringToFront(uid: String) = viewModelScope.launch {
        store.updatePages { PageOps.bringToFront(it, uid) }
    }

    /**
     * Removes a widget, with Undo. Its system widget id is freed only once Undo has passed, so an
     * undone app widget comes back intact. A running Quick timer on it is stopped.
     */
    fun removeWidget(uid: String) = viewModelScope.launch {
        if (_selectedWidget.value == uid) _selectedWidget.value = null
        var removed: Triple<String, Int, PlacedWidget>? = null
        // Read what's removed inside the same write, so it matches exactly what was saved.
        store.updatePages { pages ->
            val page = PageOps.pageOf(pages, uid) ?: return@updatePages pages
            val index = page.widgets.indexOfFirst { it.uid == uid }
            removed = Triple(page.uid, index, page.widgets[index])
            PageOps.removeWidget(pages, uid)
        }
        val (pageUid, index, widget) = removed ?: return@launch
        if (widget.kind == WidgetKind.TIMER) Timers.cancel(getApplication(), uid)
        val restored = widget.copy(timerEndsAt = 0L, timerLengthMs = 0L)
        offerUndo(
            when (widget.kind) {
                WidgetKind.APP -> "Removed from the board"
                WidgetKind.IMAGE -> "Photo removed"
                else -> "Widget removed"
            },
            restore = { PageOps.reinsertWidget(it, pageUid, index, restored) },
            widgetIds = if (widget.kind == WidgetKind.APP_WIDGET) setOf(widget.appWidgetId) else emptySet(),
        )
    }

    // ---- Undo --------------------------------------------------------------------------------

    /**
     * The last undoable change. [restore] puts back only what that change removed, so anything
     * done since is kept; [widgetIds] are app widget ids held back until Undo has passed.
     */
    class UndoAction(
        val message: String,
        val restore: (List<LauncherPage>) -> List<LauncherPage>,
        val widgetIds: Set<Int>,
    )

    private val _undo = MutableStateFlow<UndoAction?>(null)
    val undo: StateFlow<UndoAction?> = _undo.asStateFlow()
    private var undoTimeout: Job? = null

    /** App widget ids that a pending Undo may still bring back; startup cleanup must keep them. */
    val heldWidgetIds: Set<Int> get() = _undo.value?.widgetIds.orEmpty()

    private fun offerUndo(message: String, restore: (List<LauncherPage>) -> List<LauncherPage>, widgetIds: Set<Int> = emptySet()) {
        // A newer change replaces the offer; the older one becomes final.
        finalizeUndo()
        val action = UndoAction(message, restore, widgetIds)
        _undo.value = action
        undoTimeout = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            if (_undo.value === action) finalizeUndo()
        }
    }

    fun undoLast() {
        val action = _undo.value ?: return
        _undo.value = null
        undoTimeout?.cancel()
        viewModelScope.launch { store.updatePages(action.restore) }
    }

    /** Makes the pending change final: frees any app widget ids it was holding. */
    private fun finalizeUndo() {
        undoTimeout?.cancel()
        val action = _undo.value ?: return
        _undo.value = null
        val host = launcher.widgetHost
        val ids = action.widgetIds
        viewModelScope.launch {
            // A widget a saved setup still holds stays set up, for when that setup is applied.
            val saved = if (ids.isEmpty()) emptySet() else SetupStore.widgetIdsInUse(getApplication())
            ids.filter { it !in saved }.forEach { id -> runCatching { host.deleteAppWidgetId(id) } }
        }
        cleanImages()
    }

    /** Adds one of the launcher's own widgets. A text label or note opens its editor straight away. */
    fun addBuiltIn(kind: WidgetKind): Job {
        val (w, h) = builtInSize(kind)
        val text = when (kind) {
            WidgetKind.TEXT -> TextSpec()
            WidgetKind.NOTE -> TextSpec(text = "")
            else -> null
        }
        val job = addWidget(kind, -1, w, h, text)
        if (text != null) {
            job.invokeOnCompletion { cause -> if (cause == null) _editingText.value = _selectedWidget.value }
        }
        return job
    }

    private fun builtInSize(kind: WidgetKind): Pair<Float, Float> = when (kind) {
        WidgetKind.CLOCK -> 264f to 112f
        WidgetKind.TEXT -> 240f to 64f
        WidgetKind.BATTERY -> 168f to 112f
        WidgetKind.CONTROLS, WidgetKind.TIMER -> widgetAreaWidthDp to 88f
        WidgetKind.AGENDA -> widgetAreaWidthDp to 152f
        WidgetKind.NOTE -> widgetAreaWidthDp to 120f
        WidgetKind.PROGRESS -> widgetAreaWidthDp to 136f
        WidgetKind.APP_WIDGET -> 200f to 100f
        WidgetKind.APP -> 120f to 140f
        WidgetKind.IMAGE -> 200f to 200f
        WidgetKind.WIDGET_SLOT -> 200f to 100f
    }

    // ---- The board: apps and photos among the widgets ----------------------------------------

    private val _photoRequest = MutableStateFlow<String?>(null)
    /** A photo the home screen should ask for: [NEW_PHOTO] for a new widget, or a widget's uid. */
    val photoRequest: StateFlow<String?> = _photoRequest.asStateFlow()

    fun requestPhoto(forUid: String?) {
        _photoRequest.value = forUid ?: NEW_PHOTO
    }

    fun consumePhotoRequest() {
        _photoRequest.value = null
    }

    /** Pins [app] among the widgets of screen [pageUid], sized like one cell of its grid. */
    fun placeAppOnBoard(pageUid: String?, app: AppEntry, board: String = targetBoard): Job {
        val s = settings.value
        val page = s?.pages?.firstOrNull { it.uid == pageUid }
        val look = s?.forPage(page)
        val size = look?.gridSize ?: GridSize.M
        val chips = look?.quickActionCount ?: 0
        val width = (widgetAreaWidthDp / size.columns - 8f).coerceAtLeast(72f)
        val height = size.iconDp + 8f + (if (look?.showLabels != false) 22f else 0f) + chips * 38f + 8f
        return addWidget(WidgetKind.APP, -1, width, height, pageUid = pageUid ?: _currentPage.value, appKey = app.key, board = board)
    }

    /** Adds a photo widget showing [uri], copied into the launcher's own files first. */
    fun addImage(uri: android.net.Uri) = viewModelScope.launch {
        val uid = newUid()
        val path = ImageStore.import(getApplication(), uri, uid)
        if (path == null) {
            toast("Couldn't open that photo")
            return@launch
        }
        val (w, h) = builtInSize(WidgetKind.IMAGE)
        addWidget(WidgetKind.IMAGE, -1, w, h, image = path, uid = uid)
    }

    /** Swaps the photo in widget [uid] for [uri]. */
    fun setImage(uid: String, uri: android.net.Uri) = viewModelScope.launch {
        val path = ImageStore.import(getApplication(), uri, uid)
        if (path == null) {
            toast("Couldn't open that photo")
            return@launch
        }
        store.updatePages { PageOps.updateWidget(it, uid) { w -> w.copy(image = path) } }
        cleanImages()
    }

    /** Moves photo widget [uid] on to its next outline. */
    fun cycleImageShape(uid: String) = viewModelScope.launch {
        store.updatePages { pages ->
            PageOps.updateWidget(pages, uid) { w ->
                val all = ImageShape.entries
                w.copy(shape = all[(w.shape.ordinal + 1) % all.size])
            }
        }
    }

    /** Deletes stored photos no widget uses, unless an Undo could still bring one back. */
    private fun cleanImages() = viewModelScope.launch {
        if (_undo.value != null) return@launch
        val used = settings.value?.pages.orEmpty().flatMap { p -> p.widgets.mapNotNull { it.image.takeIf(String::isNotEmpty) } }.toSet()
        ImageStore.deleteUnused(getApplication(), used + SetupStore.photosInUse(getApplication()))
    }

    // ---- Saved setups ------------------------------------------------------------------------

    private val _setups = MutableStateFlow<List<SavedSetup>>(emptyList())
    /** Setups saved on this phone, newest first. */
    val setups: StateFlow<List<SavedSetup>> = _setups.asStateFlow()

    fun refreshSetups() = viewModelScope.launch { _setups.value = SetupStore.list(getApplication()) }

    /** Saves the whole launcher, as it is now, under [name]. */
    fun saveLauncherSetup(name: String) = viewModelScope.launch {
        val s = settings.value ?: return@launch
        SetupStore.saveLauncher(getApplication(), newUid(), name.trim().ifEmpty { "My launcher" }.take(40), s)
        _setups.value = SetupStore.list(getApplication())
        toast("Saved \"${name.trim().ifEmpty { "My launcher" }.take(40)}\"")
    }

    /** Saves screen [pageUid], as it is now, under [name]. */
    fun saveScreenSetup(pageUid: String, name: String) = viewModelScope.launch {
        val page = settings.value?.pages?.firstOrNull { it.uid == pageUid } ?: return@launch
        val label = name.trim().ifEmpty { page.name.ifBlank { "My screen" } }.take(40)
        SetupStore.saveScreen(getApplication(), newUid(), label, page)
        _setups.value = SetupStore.list(getApplication())
        toast("Saved \"$label\"")
    }

    /**
     * Applies a saved setup. A launcher either replaces everything ([replace]) or adds its
     * screens; a screen is always added. Other apps' widgets come back if this phone still holds them.
     */
    fun applySetup(setup: SavedSetup, replace: Boolean) = viewModelScope.launch {
        val app = getApplication<Application>()
        val manager = AppWidgetManager.getInstance(app)
        val live: (PlacedWidget) -> Boolean = { w ->
            w.kind != WidgetKind.APP_WIDGET || runCatching { manager.getAppWidgetInfo(w.appWidgetId) }.getOrNull() != null
        }
        when (setup.kind) {
            SavedSetup.Kind.SCREEN -> {
                val page = SetupStore.readScreen(app, setup.id) ?: return@launch toast("That setup can't be read")
                addSavedScreens(listOf(page.copy(widgets = page.widgets.filter(live))))
            }
            SavedSetup.Kind.LAUNCHER -> {
                val saved = SetupStore.readLauncher(app, setup.id) ?: return@launch toast("That setup can't be read")
                val pages = saved.pages.map { p -> p.copy(widgets = p.widgets.filter(live).map { it.copy(timerEndsAt = 0L, timerLengthMs = 0L) }) }
                if (replace) {
                    exitEdit()
                    finalizeUndo()
                    store.restore(saved.copy(pages = pages, tutorialDone = true))
                    _screen.value = Screen.HOME
                    toast("Switched to \"${setup.name}\"")
                } else {
                    addSavedScreens(pages)
                }
            }
        }
    }

    /** Adds screens from a saved setup, with fresh ids so a screen can be added more than once. */
    private suspend fun addSavedScreens(pages: List<LauncherPage>) {
        val have = settings.value?.pages?.size ?: 0
        val room = (PageOps.MAX_PAGES - have).coerceAtLeast(0)
        if (room == 0) return toast("You already have ${PageOps.MAX_PAGES} screens. Delete one to add more.")
        val fresh = pages.take(room).map { p ->
            // Other apps' widgets keep their ids: an id can show on one screen only, so a second
            // copy of the same screen leaves them out.
            val placed = PageOps.appWidgetIds(settings.value?.pages.orEmpty())
            p.copy(uid = newUid(), widgets = p.widgets.filter { it.kind != WidgetKind.APP_WIDGET || it.appWidgetId !in placed }.map { it.copy(uid = newUid(), timerEndsAt = 0L, timerLengthMs = 0L) })
        }
        store.updatePages { all ->
            var after = _currentPage.value
            fresh.fold(all) { acc, p -> PageOps.addPage(acc, after, p).also { after = p.uid } }
        }
        _screen.value = Screen.HOME
        toast(if (fresh.size == 1) "Screen added" else "Added ${fresh.size} screens")
        fresh.firstOrNull()?.let { _events.tryEmit(HomeEvent.ShowPage(it.uid)) }
    }

    fun renameSetup(id: String, name: String) = viewModelScope.launch {
        SetupStore.rename(getApplication(), id, name.trim().take(40))
        _setups.value = SetupStore.list(getApplication())
    }

    fun deleteSetup(id: String) = viewModelScope.launch {
        SetupStore.delete(getApplication(), id)
        _setups.value = SetupStore.list(getApplication())
        cleanImages()
    }

    /** A share code for a saved setup, without its personal parts, like any share code. */
    suspend fun setupShareCode(setup: SavedSetup): String? {
        val app = getApplication<Application>()
        return when (setup.kind) {
            SavedSetup.Kind.LAUNCHER -> SetupStore.readLauncher(app, setup.id)?.let { ShareCode.launcherCode(it, slotLookup) }
            SavedSetup.Kind.SCREEN -> SetupStore.readScreen(app, setup.id)?.let { ShareCode.screenCode(it, slotLookup) }
        }
    }

    /** Saves a drop in one write: moved frames, and optionally which widget to bring to the front. */
    // ---- Layout undo: every move, resize and align while editing can be stepped back ----------

    private val layoutHistory = ArrayDeque<PageOps.LayoutStep>()
    private val _canUndoLayout = MutableStateFlow(false)
    val canUndoLayout: StateFlow<Boolean> = _canUndoLayout.asStateFlow()

    /** Remembers the layout of the screen holding [uid], just before it changes. */
    private fun recordLayout(pages: List<LauncherPage>, uid: String) {
        val page = PageOps.pageOf(pages, uid) ?: return
        val step = PageOps.layoutOf(page)
        if (layoutHistory.lastOrNull() == step) return
        layoutHistory.addLast(step)
        while (layoutHistory.size > LAYOUT_HISTORY) layoutHistory.removeFirst()
        _canUndoLayout.value = true
    }

    private fun clearLayoutHistory() {
        layoutHistory.clear()
        _canUndoLayout.value = false
    }

    /** Steps back the last widget move, resize or align. */
    fun undoLayout() = viewModelScope.launch {
        val step = layoutHistory.removeLastOrNull() ?: return@launch
        _canUndoLayout.value = layoutHistory.isNotEmpty()
        store.updatePages { PageOps.restoreLayout(it, step) }
        _events.tryEmit(HomeEvent.ShowPage(step.pageUid))
    }

    fun updateWidgetFrames(frames: Map<String, WidgetBounds>, bringToFront: String? = null, layoutWidth: Float = 0f) = viewModelScope.launch {
        store.updatePages { pages ->
            (frames.keys.firstOrNull() ?: bringToFront)?.let { recordLayout(pages, it) }
            val moved = if (frames.isEmpty()) pages else PageOps.updateFrames(pages, frames, layoutWidth)
            if (bringToFront == null) moved else PageOps.bringToFront(moved, bringToFront)
        }
    }

    /** Starts a Quick timer of [minutes] for widget [uid], or stops it (null). The launcher runs it. */
    fun setTimer(uid: String, minutes: Int?) = viewModelScope.launch {
        val context = getApplication<Application>()
        if (minutes == null) {
            Timers.cancel(context, uid)
            store.updatePages { PageOps.updateWidget(it, uid) { w -> w.copy(timerEndsAt = 0L, timerLengthMs = 0L) } }
            return@launch
        }
        val length = minutes * 60_000L
        val endsAt = System.currentTimeMillis() + length
        store.updatePages { PageOps.updateWidget(it, uid) { w -> w.copy(timerEndsAt = endsAt, timerLengthMs = length) } }
        Timers.schedule(context, uid, endsAt, length)
    }

    /** Adds a minute to a running Quick timer. */
    fun extendTimer(uid: String) = viewModelScope.launch {
        val widget = settings.value?.pages?.let { PageOps.findWidget(it, uid) } ?: return@launch
        val now = System.currentTimeMillis()
        if (widget.timerEndsAt <= now) return@launch
        val endsAt = widget.timerEndsAt + 60_000L
        val length = widget.timerLengthMs + 60_000L
        store.updatePages { PageOps.updateWidget(it, uid) { w -> w.copy(timerEndsAt = endsAt, timerLengthMs = length) } }
        Timers.schedule(getApplication(), uid, endsAt, length)
    }

    /** Lines a widget up with the screen's left edge, center or right edge, making room below. */
    fun alignWidget(uid: String, mode: HorizontalAlign) = viewModelScope.launch {
        store.updatePages { pages ->
            val page = PageOps.pageOf(pages, uid) ?: return@updatePages pages
            // Saved frames are in the screen's layout width, so align within that width.
            val area = page.layoutWidth.takeIf { it > 0f } ?: widgetAreaWidthDp
            val widget = page.widgets.first { it.uid == uid }
            recordLayout(pages, uid)
            val target = WidgetLayout.alignTo(widget.bounds, mode, area)
            // Only widgets in the same widget area make room.
            val others = page.widgets.filter { it.uid != uid && it.board == widget.board }.associate { it.uid to it.bounds }
            PageOps.updateFrames(pages, WidgetLayout.pushDown(target, others) + (uid to target))
        }
    }

    fun setPageRule(uid: String, rule: ScreenRule) = viewModelScope.launch {
        store.updatePages { PageOps.setRule(it, uid, rule) }
    }

    /** Copies a screen right after itself and shows the copy. */
    fun duplicatePage(uid: String) = viewModelScope.launch {
        if ((settings.value?.pages?.size ?: 0) >= PageOps.MAX_PAGES) {
            toast("You can have up to ${PageOps.MAX_PAGES} screens")
            return@launch
        }
        val copyUid = newUid()
        store.updatePages { PageOps.duplicate(it, uid, copyUid, ::newUid) }
        _pageSheetOpen.value = false
        _events.tryEmit(HomeEvent.ShowPage(copyUid))
    }

    /** The time and Wi-Fi network that screen rules are checked against. */
    data class RuleContext(val minuteOfDay: Int, val wifi: String?)

    private val _ruleContext = MutableStateFlow(RuleContext(minuteNow(), null))
    val ruleContext: StateFlow<RuleContext> = _ruleContext.asStateFlow()

    /** Re-checks which screens are on home, e.g. each minute and on coming back to home. */
    fun refreshRuleContext(wifiName: String?) {
        val next = RuleContext(minuteNow(), wifiName)
        if (_ruleContext.value != next) _ruleContext.value = next
    }

    private fun minuteNow(): Int {
        val now = java.util.Calendar.getInstance()
        return now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
    }

    /** Does any screen switch on Wi-Fi? Only then is the network name worth reading. */
    val wantsWifiName: Boolean get() = settings.value?.pages?.any { it.rule.wifiEnabled } == true

    /**
     * On coming back to home: show the screen whose rule matches now, or, when the screen in view
     * was shown by a rule that no longer matches, go back to a screen without one.
     */
    fun applyScreenRules(wifiName: String?) {
        refreshRuleContext(wifiName)
        val pages = settings.value?.pages ?: return
        if (_screen.value != Screen.HOME || _editing.value || pages.none { it.rule.active }) return
        val minute = _ruleContext.value.minuteOfDay
        val match = PageOps.ruleMatch(pages, minute, wifiName)
        val current = pages.firstOrNull { it.uid == _currentPage.value }
        when {
            match != null && match.uid != current?.uid -> _events.tryEmit(HomeEvent.ShowPage(match.uid))
            match == null && current?.rule?.active == true ->
                _events.tryEmit(HomeEvent.ShowPage((pages.firstOrNull { !it.rule.active } ?: pages.first()).uid))
        }
    }

    // ---- Gestures ----------------------------------------------------------------------------

    private val _gesturePrompt = MutableStateFlow(false)
    /** True when a gesture needs the accessibility service, which is off. */
    val gesturePrompt: StateFlow<Boolean> = _gesturePrompt.asStateFlow()

    fun performGesture(action: GestureAction) {
        if (action == GestureAction.NONE || _editing.value) return
        if (!GestureService.perform(action) && settings.value?.gesturePromptDeclined != true) _gesturePrompt.value = true
    }

    fun dismissGesturePrompt() {
        _gesturePrompt.value = false
    }

    /** "Not now": stop asking; gestures stay quiet until the service is turned on in Settings. */
    fun declineGesturePrompt() = viewModelScope.launch {
        _gesturePrompt.value = false
        store.setGesturePromptDeclined(true)
    }

    fun setSwipeDown(action: GestureAction) = viewModelScope.launch { store.setSwipeDown(action) }
    fun setSwipeUp(action: GestureAction) = viewModelScope.launch { store.setSwipeUp(action) }
    fun setDoubleTap(action: GestureAction) = viewModelScope.launch { store.setDoubleTap(action) }

    // ---- Backup ------------------------------------------------------------------------------

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        val s = settings.value ?: return@launch
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(Backup.encode(s).toByteArray()) } != null
            }.getOrDefault(false)
        }
        toast(if (ok) "Layout backed up" else "Couldn't save the backup")
    }

    // ---- Share codes ---------------------------------------------------------------------------

    /** A share code being shown as QR codes: the code, a title, and what it is ("launcher" or "screen"). */
    data class ShareSheet(val code: String, val title: String, val what: String)

    private val _shareSheet = MutableStateFlow<ShareSheet?>(null)
    val shareSheet: StateFlow<ShareSheet?> = _shareSheet.asStateFlow()

    fun showShareCode(code: String?, title: String, what: String) {
        if (code != null) _shareSheet.value = ShareSheet(code, title, what)
    }

    fun closeShareCode() {
        _shareSheet.value = null
    }

    /** A share code for the whole launcher: settings and screens, with nothing personal in it. */
    fun launcherShareCode(): String? = settings.value?.let { ShareCode.launcherCode(it, slotLookup) }

    /** A share code for one screen. */
    fun screenShareCode(pageUid: String): String? =
        settings.value?.pages?.firstOrNull { it.uid == pageUid }?.let { ShareCode.screenCode(it, slotLookup) }

    /** Names other apps' widgets for share codes: provider component and a label like "Spotify · Now playing". */
    private val slotLookup = ShareCode.SlotLookup { id ->
        val app = getApplication<Application>()
        val info = runCatching { AppWidgetManager.getInstance(app).getAppWidgetInfo(id) }.getOrNull() ?: return@SlotLookup null
        val pm = app.packageManager
        val appName = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }.getOrNull()
        val widgetName = runCatching { info.loadLabel(pm) }.getOrNull()
        val label = listOfNotNull(appName, widgetName?.takeIf { it != appName }).joinToString(" · ").ifEmpty { info.provider.packageName }
        info.provider.flattenToString() to label
    }

    // ---- Widget placeholders from share codes ----

    /** The placeholder being set up: the next app widget added takes its place. */
    private var slotToFill: String? = null

    /** Starts setting up placeholder [uid]; the activity then runs Android's usual add flow. */
    fun fillSlot(uid: String) {
        slotToFill = uid
    }

    /** Reads a pasted code, matching its apps to the ones on this phone. */
    fun readShareCode(text: String): ShareCode.Shared? {
        val apps = allApps.value
        // Prefer the main profile's copy of an app over a work profile's.
        val byComponent = apps.sortedBy { it.isPrimaryUser }.associateBy { ShareCode.portableKey(it.key) }
        val byPackage = apps.sortedBy { it.isPrimaryUser }.associateBy { it.packageName }
        return ShareCode.read(text) { ref ->
            (byComponent[ref] ?: byPackage[ref.substringBefore('/')])?.key
        }
    }

    /** Replaces the launcher with a shared one, keeping this phone's private lists. */
    fun applySharedLauncher(shared: LauncherSettings) = viewModelScope.launch {
        val mine = settings.value ?: return@launch
        exitEdit()
        finalizeUndo()
        store.restore(
            shared.copy(
                hiddenApps = mine.hiddenApps,
                lockedApps = mine.lockedApps,
                launchCounts = mine.launchCounts,
                recents = mine.recents,
                tutorialDone = true,
                pages = shared.pages.map(::freshCopy),
            ),
        )
        toast("Launcher imported")
    }

    /** Adds shared screens after the current one, as many as there's room for. */
    fun addSharedScreens(pages: List<LauncherPage>) = viewModelScope.launch {
        val have = settings.value?.pages?.size ?: 0
        val room = (PageOps.MAX_PAGES - have).coerceAtLeast(0)
        if (room == 0) {
            toast("You already have ${PageOps.MAX_PAGES} screens. Delete one to add more.")
            return@launch
        }
        val fresh = pages.take(room).map(::freshCopy)
        store.updatePages { all ->
            var after = _currentPage.value
            fresh.fold(all) { acc, p -> PageOps.addPage(acc, after, p).also { after = p.uid } }
        }
        val skipped = pages.size - fresh.size
        toast(if (skipped > 0) "Added ${fresh.size} screens; no room for $skipped more" else if (fresh.size == 1) "Screen added" else "Added ${fresh.size} screens")
        fresh.firstOrNull()?.let { _events.tryEmit(HomeEvent.ShowPage(it.uid)) }
    }

    /** A copy with new ids for the screen and its widgets, so it never clashes with one already here. */
    private fun freshCopy(page: LauncherPage): LauncherPage =
        page.copy(uid = newUid(), widgets = page.widgets.map { it.copy(uid = newUid(), timerEndsAt = 0L, timerLengthMs = 0L) })

    // ---- Screens from app groups --------------------------------------------------------------

    /** Every app's declared category (games, social, news...), read from the system. */
    suspend fun appCategories(): Map<String, Int> = withContext(Dispatchers.IO) {
        val pm = getApplication<Application>().packageManager
        allApps.value.map { it.packageName }.distinct().associateWith { pkg ->
            runCatching { pm.getApplicationInfo(pkg, 0).category }.getOrDefault(android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED)
        }
    }

    /** Makes one screen per group, each listing just that group's apps, and shows the first. */
    fun createGroupScreens(groups: List<AppGroup>) = viewModelScope.launch {
        val have = settings.value?.pages?.size ?: 0
        val take = groups.take((PageOps.MAX_PAGES - have).coerceAtLeast(0))
        if (take.isEmpty()) return@launch
        val pages = take.map { g ->
            LauncherPage(uid = newUid(), appsMode = PageApps.CUSTOM, apps = g.apps.map { it.key }, name = g.title.take(24), icon = g.icon)
        }
        store.updatePages { all -> pages.fold(all) { acc, p -> PageOps.addPage(acc, null, p) } }
        _screen.value = Screen.HOME
        toast(if (pages.size == 1) "Made the ${pages[0].name} screen" else "Made ${pages.size} screens")
        _events.tryEmit(HomeEvent.ShowPage(pages.first().uid))
    }

    /**
     * Restores a backup. Other apps' widgets only come back if this phone still holds them (for
     * example, restoring an older backup on the same phone); the rest are left out.
     */
    fun importBackup(uri: Uri) = viewModelScope.launch {
        val app = getApplication<Application>()
        val restored = withContext(Dispatchers.IO) {
            runCatching {
                app.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            }.getOrNull()?.let(Backup::decode)
        }
        if (restored == null) {
            toast("That file isn't a Grid Launcher backup")
            return@launch
        }
        val manager = AppWidgetManager.getInstance(app)
        val pages = restored.pages.map { page ->
            page.copy(
                widgets = page.widgets.filter { w ->
                    w.kind != WidgetKind.APP_WIDGET || runCatching { manager.getAppWidgetInfo(w.appWidgetId) }.getOrNull() != null
                },
            )
        }
        exitEdit()
        // A restore replaces everything: an older Undo can't apply to it, and timers don't carry over.
        finalizeUndo()
        val clean = pages.map { p -> p.copy(widgets = p.widgets.map { it.copy(timerEndsAt = 0L, timerLengthMs = 0L) }) }
        store.restore(restored.copy(pages = clean))
        val dropped = restored.pages.sumOf { it.widgets.size } - pages.sumOf { it.widgets.size }
        toast(if (dropped > 0) "Layout restored. $dropped app widgets need adding again." else "Layout restored")
        _events.tryEmit(HomeEvent.GoHome)
    }

    fun setPageName(uid: String, name: String) = viewModelScope.launch {
        store.updatePages { PageOps.setName(it, uid, name) }
    }

    fun setPageIcon(uid: String, icon: String) = viewModelScope.launch {
        store.updatePages { PageOps.setIcon(it, uid, icon) }
    }

    /** The returned job completes once the widget is saved to disk. */
    fun addAppWidget(appWidgetId: Int, widthDp: Float, heightDp: Float, pageUid: String?): Job {
        // Setting up a placeholder: the widget takes its place, size and area.
        val slot = slotToFill
        slotToFill = null
        if (slot != null && settings.value?.pages.orEmpty().any { p -> p.widgets.any { it.uid == slot && it.kind == WidgetKind.WIDGET_SLOT } }) {
            return viewModelScope.launch {
                store.updatePages { pages ->
                    PageOps.updateWidget(pages, slot) { it.copy(kind = WidgetKind.APP_WIDGET, appWidgetId = appWidgetId, provider = "", label = "") }
                }
            }
        }
        return addWidget(WidgetKind.APP_WIDGET, appWidgetId, widthDp, heightDp, pageUid = pageUid, board = targetBoard)
    }

    private fun addWidget(
        kind: WidgetKind,
        appWidgetId: Int,
        widthDp: Float,
        heightDp: Float,
        text: TextSpec? = null,
        pageUid: String? = _currentPage.value,
        appKey: String = "",
        image: String = "",
        uid: String = newUid(),
        board: String = targetBoard,
    ): Job {
        return viewModelScope.launch {
            store.updatePages { pages ->
                val screenArea = widgetAreaWidthDp
                val target = pages.firstOrNull { it.uid == pageUid } ?: pages.firstOrNull()
                // Work in the screen's saved layout width; a screen new to widgets takes today's width.
                val area = target?.layoutWidth?.takeIf { it > 0f } ?: screenArea
                val k = area / screenArea
                // A widget area that no longer exists falls back to the top one.
                val area0 = board.takeIf { b -> target?.boards?.any { it.uid == b } == true } ?: ""
                val existing = target?.widgets.orEmpty().filter { it.board == area0 }.map { it.bounds }
                val frame = WidgetGeometry.placeNew(existing, widthDp * k, heightDp, area)
                val added = PageOps.addWidget(pages, target?.uid, PlacedWidget(uid, kind, appWidgetId, WidgetGeometry.settle(frame, true, area), text, appKey = appKey, image = image, board = area0))
                added.map { if (it.uid == target?.uid && it.layoutWidth <= 0f) it.copy(layoutWidth = area) else it }
            }
            _editing.value = true
            _selectedWidget.value = uid
        }
    }

    fun editText(uid: String) {
        _editingText.value = uid
    }

    fun closeTextEditor() {
        _editingText.value = null
    }

    fun updateText(uid: String, spec: TextSpec) = viewModelScope.launch {
        store.updatePages { PageOps.updateWidget(it, uid) { w -> w.copy(text = spec) } }
    }

    // ---- Screens -----------------------------------------------------------------------------

    /** Adds an empty screen after [after] (the current screen by default) and swipes to it. */
    /** The screen just added, whose options open once it's in view. */
    private var sheetForNewPage: String? = null

    fun addPage(after: String? = _currentPage.value) {
        val uid = newUid()
        viewModelScope.launch {
            val before = settings.value?.pages?.size ?: 0
            if (before >= PageOps.MAX_PAGES) {
                toast("You can have up to ${PageOps.MAX_PAGES} screens")
                return@launch
            }
            sheetForNewPage = uid
            store.updatePages { PageOps.addPage(it, after, LauncherPage(uid = uid, appsMode = PageApps.NONE)) }
            _pageSheetOpen.value = false
            _events.tryEmit(HomeEvent.ShowPage(uid))
        }
    }

    /** Removes a screen, with Undo. Its app widget ids are freed once Undo has passed. */
    fun deletePage(uid: String) = viewModelScope.launch {
        _pageSheetOpen.value = false
        _selectedWidget.value = null
        var removed: Pair<Int, LauncherPage>? = null
        store.updatePages { pages ->
            val index = pages.indexOfFirst { it.uid == uid }
            if (index < 0 || pages.size <= 1) return@updatePages pages
            removed = index to pages[index]
            PageOps.removePage(pages, uid)
        }
        val (index, page) = removed ?: return@launch
        val context = getApplication<Application>()
        page.widgets.filter { it.kind == WidgetKind.TIMER && it.timerEndsAt > 0 }.forEach { Timers.cancel(context, it.uid) }
        val restored = page.copy(widgets = page.widgets.map { it.copy(timerEndsAt = 0L, timerLengthMs = 0L) })
        offerUndo(
            "Screen deleted",
            restore = { PageOps.reinsertPage(it, index, restored) },
            widgetIds = PageOps.appWidgetIds(listOf(page)),
        )
    }

    fun movePage(uid: String, delta: Int) = viewModelScope.launch {
        store.updatePages { PageOps.movePage(it, uid, delta) }
        _events.tryEmit(HomeEvent.ShowPage(uid))
    }

    fun setPageAppsMode(uid: String, mode: PageApps) = viewModelScope.launch {
        store.updatePages { PageOps.setApps(it, uid, mode) }
    }

    /** Empties a screen's app list (folders too), leaving a widgets-only custom screen. Undoable. */
    fun clearPageApps(uid: String) = viewModelScope.launch {
        var previous: LauncherPage? = null
        store.updatePages { pages ->
            previous = pages.firstOrNull { it.uid == uid }
            PageOps.clearApps(pages, uid)
        }
        val before = previous ?: return@launch
        offerUndo("Apps cleared", restore = { pages ->
            pages.map { if (it.uid == uid) it.copy(appsMode = before.appsMode, apps = before.apps, folders = before.folders) else it }
        })
    }

    /** Saves a screen's custom set, keeping any apps that aren't listed right now. */
    fun setPageApps(uid: String, keys: List<String>) = viewModelScope.launch {
        val listed = repo.apps.value.map { it.key }.toSet()
        store.updatePages { pages ->
            val original = pages.firstOrNull { it.uid == uid }?.apps.orEmpty()
            PageOps.setApps(pages, uid, PageApps.CUSTOM, PageOps.mergeKeepingUnlisted(original, keys, listed))
        }
    }

    fun addAppToPage(uid: String, app: AppEntry) = viewModelScope.launch {
        store.updatePages { PageOps.addApp(it, uid, app.key) }
    }

    fun removeAppFromPage(uid: String, app: AppEntry) = viewModelScope.launch {
        store.updatePages { PageOps.removeApp(it, uid, app.key) }
    }

    // ---- Apps --------------------------------------------------------------------------------

    suspend fun shortcuts(app: AppEntry): List<ShortcutInfo> = repo.shortcuts(app)

    fun cachedShortcuts(app: AppEntry): List<ShortcutInfo>? = repo.cachedShortcuts(app)

    // ---- App lock ----------------------------------------------------------------------------

    /** An app that needs the fingerprint or screen lock before it opens, and what to run after. */
    class UnlockRequest(val label: String, val onUnlocked: () -> Unit)

    private val _unlockRequests = MutableSharedFlow<UnlockRequest>(extraBufferCapacity = 1)
    val unlockRequests: SharedFlow<UnlockRequest> = _unlockRequests.asSharedFlow()

    /** Runs [open] now, or after unlocking if [app] is locked. */
    private fun guarded(app: AppEntry, open: () -> Unit) {
        if (app.key in (settings.value?.lockedApps ?: emptySet())) {
            _unlockRequests.tryEmit(UnlockRequest(app.label, open))
        } else {
            open()
        }
    }

    fun setLocked(app: AppEntry, locked: Boolean) = viewModelScope.launch {
        store.setLocked(app.key, locked)
        toast(if (locked) "Grid Launcher will ask for your fingerprint before opening ${app.label}" else "${app.label} is unlocked")
    }

    fun launch(app: AppEntry, bounds: Rect?, options: Bundle?) = guarded(app) { launchNow(app, bounds, options) }

    private fun launchNow(app: AppEntry, bounds: Rect?, options: Bundle?) {
        if (repo.launchApp(app, bounds, options)) {
            viewModelScope.launch { store.recordLaunch(app.key) }
        } else {
            toast("Couldn't open ${app.label}")
        }
    }

    fun launchShortcut(app: AppEntry, shortcut: ShortcutInfo, bounds: Rect?, options: Bundle?) =
        guarded(app) { launchShortcutNow(app, shortcut, bounds, options) }

    private fun launchShortcutNow(app: AppEntry, shortcut: ShortcutInfo, bounds: Rect?, options: Bundle?) {
        if (repo.launchShortcut(shortcut, bounds, options)) {
            viewModelScope.launch { store.recordLaunch(app.key) }
        } else {
            toast("Couldn't open ${shortcut.shortLabel}")
        }
    }

    fun openAppInfo(app: AppEntry, bounds: Rect?) {
        if (!repo.openAppInfo(app, bounds)) toast("Couldn't open app info")
    }

    fun uninstall(app: AppEntry) {
        if (!repo.uninstall(app)) toast("Couldn't start uninstall")
    }

    fun setHidden(app: AppEntry, hidden: Boolean) = viewModelScope.launch { store.setHidden(app.key, hidden) }

    fun unhide(key: String) = viewModelScope.launch { store.setHidden(key, false) }

    fun setFavorite(app: AppEntry, favorite: Boolean) = viewModelScope.launch { store.setFavorite(app.key, favorite) }

    fun togglePin(app: AppEntry, available: List<String>, shortcutId: String) = viewModelScope.launch {
        store.togglePin(app.key, available, shortcutId)
    }

    /** Saves a dragged order: for a screen's own apps, or as the global custom order. */
    fun saveOrder(pageUid: String?, keys: List<String>) {
        if (pageUid == null) viewModelScope.launch { store.applyCustomOrder(keys) } else setPageApps(pageUid, keys)
    }

    // ---- Settings ----------------------------------------------------------------------------

    fun setQuickActionCount(count: Int) = viewModelScope.launch { store.setQuickActionCount(count) }
    fun setSortMode(mode: SortMode) = viewModelScope.launch { store.setSortMode(mode) }
    fun setShowLabels(value: Boolean) = viewModelScope.launch { store.setShowLabels(value) }
    fun setGridSize(size: GridSize) = viewModelScope.launch { store.setGridSize(size) }
    fun setChargingStyle(style: app.gridlauncher.data.ChargingStyle) = viewModelScope.launch { store.setChargingStyle(style) }
    fun setMusicStyle(style: app.gridlauncher.data.MusicStyle) = viewModelScope.launch { store.setMusicStyle(style) }
    fun setMusicLive(on: Boolean) = viewModelScope.launch { store.setMusicLive(on) }
    fun setNotifyFlash(on: Boolean) = viewModelScope.launch { store.setNotifyFlash(on) }
    fun setLowBatteryPulse(on: Boolean) = viewModelScope.launch { store.setLowBatteryPulse(on) }
    fun setFullChargeBurst(on: Boolean) = viewModelScope.launch { store.setFullChargeBurst(on) }
    fun setTimerEdge(on: Boolean) = viewModelScope.launch { store.setTimerEdge(on) }
    fun setTimeOfDaySky(on: Boolean) = viewModelScope.launch { store.setTimeOfDaySky(on) }

    /** Gives a screen its own grid look, or (null) makes it follow the launcher's settings. */
    fun setPageLook(uid: String, look: app.gridlauncher.data.PageLook?) = viewModelScope.launch {
        store.updatePages { PageOps.setLook(it, uid, look) }
    }
    fun setThemedIcons(value: Boolean) = viewModelScope.launch { store.setThemedIcons(value) }
    fun setFavoritesEnabled(value: Boolean) = viewModelScope.launch { store.setFavoritesEnabled(value) }
    fun setShowWallpaper(value: Boolean) = viewModelScope.launch { store.setShowWallpaper(value) }
    fun setSnapToGrid(value: Boolean) = viewModelScope.launch { store.setSnapToGrid(value) }
    fun setLockLayout(value: Boolean) = viewModelScope.launch { store.setLockLayout(value) }
    fun setFontFamily(name: String) = viewModelScope.launch { store.setFontFamily(name) }
    fun setMonitorEnabled(value: Boolean) = viewModelScope.launch { store.setMonitorEnabled(value) }
    fun setNotificationDots(style: DotStyle) = viewModelScope.launch { store.setNotificationDots(style) }
    fun setNavStyle(style: app.gridlauncher.data.NavStyle) = viewModelScope.launch { store.setNavStyle(style) }
    fun setRecentsEnabled(value: Boolean) = viewModelScope.launch { store.setRecentsEnabled(value) }
    fun setIconPack(pkg: String) = viewModelScope.launch { store.setIconPack(pkg) }
    fun setMonitorAlerts(value: Boolean) = viewModelScope.launch { store.setMonitorAlerts(value) }

    // ---- Folders -----------------------------------------------------------------------------

    /** Starts a folder holding [app], in its place on custom screen [pageUid]. */
    fun newFolder(pageUid: String, app: AppEntry) = viewModelScope.launch {
        store.updatePages { PageOps.createFolder(it, pageUid, newUid(), "Folder", listOf(app.key)) }
        toast("Folder made. Long-press other apps to add them.")
    }

    fun addToFolder(pageUid: String, folderId: String, app: AppEntry) = viewModelScope.launch {
        store.updatePages { PageOps.addToFolder(it, pageUid, folderId, app.key) }
    }

    fun removeFromFolder(pageUid: String, folderId: String, app: AppEntry) = viewModelScope.launch {
        store.updatePages { PageOps.removeFromFolder(it, pageUid, folderId, app.key) }
    }

    fun renameFolder(pageUid: String, folderId: String, name: String) = viewModelScope.launch {
        store.updatePages { PageOps.renameFolder(it, pageUid, folderId, name) }
    }
    fun setAccentColor(argb: Int) = viewModelScope.launch { store.setAccentColor(argb) }
    fun setWidgetCorner(dp: Float) = viewModelScope.launch { store.setWidgetCorner(dp) }
    fun setWallpaperDim(value: Float) = viewModelScope.launch { store.setWallpaperDim(value) }
    fun setWallpaperBlur(value: Boolean) = viewModelScope.launch { store.setWallpaperBlur(value) }
    fun setTutorialDone(value: Boolean) = viewModelScope.launch { store.setTutorialDone(value) }

    fun toast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
    }
}
