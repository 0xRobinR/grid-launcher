package app.gridlauncher.ui.home

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.gridlauncher.domain.HorizontalAlign
import app.gridlauncher.ui.theme.LauncherIcons
import app.gridlauncher.ui.theme.LocalWidgetCorner
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.gridlauncher.data.PlacedWidget
import app.gridlauncher.data.TextSpec
import app.gridlauncher.data.WidgetKind
import app.gridlauncher.domain.Corner
import app.gridlauncher.domain.WidgetBounds
import app.gridlauncher.domain.WidgetGeometry
import app.gridlauncher.domain.WidgetLayout
import app.gridlauncher.ui.components.startSafely
import app.gridlauncher.ui.theme.Palette
import app.gridlauncher.widgets.WidgetController
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * The free-form area at the top of the home screen. Outside edit mode widgets are live; in edit
 * mode each widget gets a cover that moves it, and the selected one gets corner resize handles.
 */
@Composable
fun WidgetArea(
    widgets: List<PlacedWidget>,
    editing: Boolean,
    selectedUid: String?,
    snap: Boolean,
    locked: Boolean,
    controller: WidgetController,
    /** Draws an app pinned on the board: the same cell as in the grid, with its menu and quick actions. */
    appContent: @Composable (PlacedWidget) -> Unit = {},
    onEnterEdit: (String?) -> Unit,
    onSelect: (String?) -> Unit,
    /**
     * Saves a drop: the moved widget, any widgets it pushed down (in layout-width dp), which widget
     * to bring to front, and the layout width they're in.
     */
    onCommit: (frames: Map<String, WidgetBounds>, bringToFront: String?, layoutWidth: Float) -> Unit,
    /** The width the saved frames were laid out in; 0 means the current width. */
    layoutWidth: Float = 0f,
    onAreaWidth: (Float) -> Unit,
    actions: WidgetActions,
    /** Double-tap on empty space, outside edit mode. */
    onDoubleTap: () -> Unit = {},
    /** The least height the area takes, in dp: a widgets-only screen fills the screen with it. */
    minHeightDp: Float = 0f,
    /** The least height while editing, so there's room to drop widgets in. */
    minEditHeightDp: Float = 300f,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        val areaWidth = maxWidth.value
        LaunchedEffect(areaWidth) { onAreaWidth(areaWidth) }

        // The raw frame under the finger while a widget is moved or resized, and frames already
        // dropped that are waiting for the saved layout to catch up.
        val drafts = remember { mutableStateMapOf<String, Draft>() }
        val settled = remember { mutableStateMapOf<String, WidgetBounds>() }
        LaunchedEffect(editing) { if (!editing) drafts.clear() }
        // How tall each text label's text really is: its frame hugs it on drop, so the space
        // around a label in edit mode is the space you see on home.
        val textHeights = remember { HashMap<String, Float>() }
        val currentWidgets by rememberUpdatedState(widgets)
        // Free-form by default: a widget dropped on another just overlaps it. Holding it over the
        // same widgets for HOLD_TO_PUSH_MS arms "make room", and those widgets slide down.
        var pushArmed by remember { mutableStateOf(false) }
        // A widget just dropped on top of others stays drawn on top until the saved order says so.
        var pendingFront by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(widgets) { if (widgets.lastOrNull()?.uid == pendingFront) pendingFront = null }

        // Saved frames are in the screen's layout width; draw them scaled sideways to this width.
        val design = if (layoutWidth > 0f) layoutWidth else areaWidth
        val scale = areaWidth / design
        val toDisplay: (WidgetBounds) -> WidgetBounds = { b -> b.copy(x = b.x * scale, width = b.width * scale) }
        val toDesign: (WidgetBounds) -> WidgetBounds = { b -> b.copy(x = b.x / scale, width = b.width / scale) }
        val base = widgets.associate { w -> w.uid to (settled[w.uid] ?: WidgetGeometry.clamp(toDisplay(w.bounds), areaWidth)) }
        val active = drafts.entries.firstOrNull()?.let { it.key to it.value }
        // Widgets the dragged one already overlapped when it was picked up. Those are a layout the
        // user chose: only widgets it newly lands on count for "make room" and "bring to front".
        val startOverlaps = remember(active?.first) {
            active?.first?.let { uid -> overlapsOf(uid, base.getValue(uid), base) }.orEmpty()
        }
        val live = liveLayout(base, active, areaWidth, snap, push = pushArmed, keep = startOverlaps)
        val newOverlaps = live.overlaps - startOverlaps
        val frames = widgets.map { w -> w to (live.frames[w.uid] ?: base.getValue(w.uid)) }
        LaunchedEffect(active?.first, newOverlaps) {
            pushArmed = false
            if (active != null && newOverlaps.isNotEmpty()) {
                delay(HOLD_TO_PUSH_MS)
                pushArmed = true
            }
        }
        val currentStart by rememberUpdatedState(startOverlaps)
        // A light tick when the widget snaps to a guide, and a firmer one when room is made.
        val view = LocalView.current
        LaunchedEffect(live.guideX, live.guideY) {
            if (active != null && (live.guideX != null || live.guideY != null)) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
        LaunchedEffect(pushArmed) {
            if (pushArmed) {
                view.performHapticFeedback(
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                    else HapticFeedbackConstants.LONG_PRESS,
                )
            }
        }
        val currentBase by rememberUpdatedState(base)
        val currentSnap by rememberUpdatedState(snap)
        val currentPush by rememberUpdatedState(pushArmed)
        // Drop: save the moved widget and any it pushed down, in one write.
        val release: (String) -> Unit = { uid ->
            drafts.remove(uid)?.let { draft ->
                val laid = liveLayout(currentBase, uid to draft, areaWidth, currentSnap, push = currentPush, keep = currentStart)
                val result = hugText(laid, uid, currentWidgets, textHeights)
                // Only frames that really moved: an unchanged frame would never be confirmed by a
                // save and would pin the widget in place.
                val changed = result.frames.filter { (k, v) -> currentBase[k] != v }
                // Dropped onto a widget it wasn't already on, without making room: keep it on top.
                val front = uid.takeIf { !currentPush && (result.overlaps - currentStart).isNotEmpty() }
                settled.putAll(changed)
                pushArmed = false
                if (front != null) pendingFront = front
                if (changed.isNotEmpty() || front != null) onCommit(changed.mapValues { toDesign(it.value) }, front, design)
            }
        }
        val dragHint = when {
            active == null -> null
            newOverlaps.isEmpty() -> null
            pushArmed -> "Making room"
            else -> "Hold to make room"
        }
        val bottom = WidgetGeometry.contentBottom(frames.map { it.second })
        val height = when {
            editing -> max(max(bottom + 120f, minEditHeightDp), minHeightDp)
            widgets.isEmpty() -> max(8f, minHeightDp)
            else -> max(bottom, minHeightDp)
        }
        val currentFrames by rememberUpdatedState(frames.map { it.second })
        val currentEditing by rememberUpdatedState(editing)
        val currentDoubleTap by rememberUpdatedState(onDoubleTap)

        Box(
            // Widgets are placed by absolute offsets from the left, in every language direction.
            contentAlignment = AbsoluteAlignment.TopLeft,
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .then(if (editing) Modifier.editGrid() else Modifier)
                // Restarted on entering or leaving edit mode: double-tap only listens outside it, so
                // tap-to-deselect in edit mode isn't held back waiting for a second tap.
                .pointerInput(editing) {
                    detectTapGestures(
                        onTap = { if (currentEditing) onSelect(null) },
                        onDoubleTap = if (editing) null else { offset ->
                            val x = offset.x.toDp().value
                            val y = offset.y.toDp().value
                            if (currentFrames.none { it.contains(x, y) }) currentDoubleTap()
                        },
                        onLongPress = { offset ->
                            val x = offset.x.toDp().value
                            val y = offset.y.toDp().value
                            if (!currentEditing && currentFrames.none { it.contains(x, y) }) onEnterEdit(null)
                        },
                    )
                },
        ) {
            frames.forEach { (widget, frame) ->
                key(widget.uid) {
                    WidgetFrame(
                        widget = widget,
                        frame = frame,
                        editing = editing,
                        selected = widget.uid == selectedUid,
                        locked = locked,
                        areaWidth = areaWidth,
                        controller = controller,
                        appContent = appContent,
                        drafts = drafts,
                        settled = settled,
                        onSelect = onSelect,
                        onRelease = release,
                        onEnterEdit = onEnterEdit,
                        actions = actions,
                        dragHint = dragHint,
                        onTop = pendingFront == widget.uid,
                        savedFrame = toDisplay(widget.bounds),
                        onTextHeight = { textHeights[widget.uid] = it },
                    )
                }
            }
            if (live.guideX != null || live.guideY != null) Guides(live.guideX, live.guideY)
            // The selected widget's tools float right under it (or above it, near the bottom).
            val selected = widgets.firstOrNull { it.uid == selectedUid }
            if (editing && !locked && selected != null && drafts.isEmpty()) {
                val frame = live.frames[selected.uid] ?: base.getValue(selected.uid)
                WidgetToolbar(selected, frame, areaWidth, height, actions)
            }
        }
    }
}

private fun Modifier.editGrid(): Modifier = this
    .clip(RoundedCornerShape(24.dp))
    .background(Palette.Ground)
    .drawBehind {
        val step = 16.dp.toPx()
        val dot = 1.dp.toPx()
        val color = Palette.LineStrong.copy(alpha = 0.7f)
        var y = step / 2
        while (y < size.height) {
            var x = step / 2
            while (x < size.width) {
                drawCircle(color, radius = dot, center = Offset(x, y))
                x += step
            }
            y += step
        }
        drawRoundRect(
            color = Palette.LineStrong,
            cornerRadius = CornerRadius(24.dp.toPx()),
            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
        )
    }

@Composable
private fun WidgetFrame(
    widget: PlacedWidget,
    frame: WidgetBounds,
    editing: Boolean,
    selected: Boolean,
    locked: Boolean,
    areaWidth: Float,
    controller: WidgetController,
    appContent: @Composable (PlacedWidget) -> Unit,
    drafts: SnapshotStateMap<String, Draft>,
    settled: SnapshotStateMap<String, WidgetBounds>,
    onSelect: (String?) -> Unit,
    onRelease: (String) -> Unit,
    onEnterEdit: (String?) -> Unit,
    actions: WidgetActions,
    dragHint: String?,
    onTop: Boolean,
    /** The saved frame, scaled to this width, to tell when a settled drop has been saved. */
    savedFrame: WidgetBounds,
    onTextHeight: (Float) -> Unit,
) {
    val uid = widget.uid
    val info = remember(widget.appWidgetId) {
        if (widget.kind == WidgetKind.APP_WIDGET) controller.providerInfo(widget.appWidgetId) else null
    }
    // Every widget resizes both ways, even one whose app asks for a fixed size. On an axis the app
    // allows resizing, its own minimum holds; otherwise only the launcher's minimum does.
    val resizeH = true
    val resizeV = true
    val (minW, minH) = remember(info) {
        val min = WidgetGeometry.MIN_SIZE_DP
        if (info == null) {
            min to min
        } else {
            val (w, h) = controller.minSizeDp(info)
            val canH = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
            val canV = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
            (if (canH) w.coerceAtLeast(min) else min) to (if (canV) h.coerceAtLeast(min) else min)
        }
    }

    // Once the saved layout catches up with this exact frame, the settled frame is no longer needed.
    // Any later change to the saved frame (the save itself, Undo, align) ends the settled frame, so
    // it can never pin the widget in place.
    var firstBounds by remember { mutableStateOf(true) }
    LaunchedEffect(widget.bounds) {
        if (firstBounds) {
            firstBounds = false
            settled[uid]?.let { if (it.closeTo(savedFrame)) settled.remove(uid) }
        } else {
            settled.remove(uid)
        }
    }

    val latestFrame by rememberUpdatedState(frame)
    val latestArea by rememberUpdatedState(areaWidth)
    val latestReleaseState = rememberUpdatedState(onRelease)
    val latestRelease by latestReleaseState
    val latestReleaseOnDispose = latestReleaseState
    // This widget leaving the screen mid-gesture never leaves the gesture half done.
    DisposableEffect(uid) { onDispose { if (drafts.containsKey(uid)) latestReleaseOnDispose.value(uid) } }
    val commit = { latestRelease(uid) }
    val shape = RoundedCornerShape(LocalWidgetCorner.current)
    val active = drafts.containsKey(uid)
    // Widgets that make room, or slide back, glide there; the one under the finger follows it exactly.
    val position = remember { Animatable(Offset(frame.x, frame.y), Offset.VectorConverter) }
    LaunchedEffect(frame.x, frame.y, active, editing) {
        val target = Offset(frame.x, frame.y)
        if (active || !editing) position.snapTo(target) else position.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow))
    }

    Box(
        Modifier
            // Read in the layout phase, so a gliding widget doesn't recompose on every frame.
            .absoluteOffset { IntOffset(position.value.x.dp.roundToPx(), position.value.y.dp.roundToPx()) }
            .size(frame.width.dp, frame.height.dp)
            .zIndex(if (active || onTop) 1f else 0f),
    ) {
        val live = !editing
        val longPress = { onEnterEdit(uid) }
        when (widget.kind) {
            WidgetKind.CLOCK -> ClockWidget(heightDp = frame.height, interactive = live) { onEnterEdit(uid) }
            WidgetKind.TEXT -> TextLabelWidget(widget.text ?: TextSpec(), interactive = live, onTextHeight = onTextHeight) { onEnterEdit(uid) }
            WidgetKind.BATTERY -> BatteryWidget(live, longPress)
            WidgetKind.CONTROLS -> ControlsWidget(live, longPress)
            WidgetKind.AGENDA -> AgendaWidget(live, longPress)
            WidgetKind.TIMER -> TimerWidget(
                interactive = live,
                endsAt = widget.timerEndsAt,
                lengthMs = widget.timerLengthMs,
                onLongPress = longPress,
                onStart = { minutes -> actions.onTimer(uid, minutes) },
                onExtend = { actions.onTimerExtend(uid) },
                onDismiss = { actions.onTimer(uid, null) },
                onMessage = actions.onMessage,
            )
            WidgetKind.NOTE -> NoteWidget(widget.text?.text.orEmpty(), live, longPress) { actions.onEdit(uid) }
            WidgetKind.PROGRESS -> ProgressWidget(live, longPress)
            WidgetKind.APP_WIDGET -> AppWidget(widget.appWidgetId, frame, resizing = active, controller) { onEnterEdit(uid) }
            WidgetKind.APP -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { appContent(widget) }
            WidgetKind.IMAGE -> ImageWidget(widget.image, widget.shape, interactive = live, onLongPress = longPress)
            WidgetKind.WIDGET_SLOT -> WidgetSlot(widget, controller, interactive = live, onFill = { actions.onFillSlot(widget) }, onLongPress = longPress)
        }
        if (editing) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .border(if (selected) 2.dp else 1.dp, if (selected) Palette.Accent else Palette.LineStrong, shape)
                    .background(Palette.Ground.copy(alpha = if (selected) 0.12f else 0.28f))
                    .pointerInput(uid) { detectTapGestures(onTap = { onSelect(uid) }) }
                    .pointerInput(uid, locked) {
                        if (locked) return@pointerInput
                        detectDragGestures(
                            onDragStart = {
                                onSelect(uid)
                                // One widget at a time: a second finger on another widget is ignored.
                                if (drafts.isEmpty()) drafts[uid] = Draft(latestFrame, moving = true)
                            },
                            onDragEnd = { commit() },
                            onDragCancel = { commit() },
                            onDrag = { change, amount ->
                                change.consume()
                                val current = drafts[uid]
                                if (current != null && current.moving) {
                                    drafts[uid] = Draft(current.bounds.moveBy(amount.x.toDp().value, amount.y.toDp().value), moving = true)
                                }
                            },
                        )
                    },
            )
            if (selected && !locked && (resizeH || resizeV)) {
                // On a small widget four handles would cover it, leaving nothing to drag: keep one.
                // Decided when the resize starts and kept until it ends: a handle that vanished
                // under the finger would leave the resize unfinished.
                val small = minOf(frame.width, frame.height) < 112f
                var cornersSmall by remember { mutableStateOf(small) }
                if (!active) cornersSmall = small
                val corners = if (cornersSmall) listOf(Corner.BOTTOM_RIGHT) else Corner.entries
                corners.forEach { corner -> key(corner) {
                    ResizeHandle(
                        corner = corner,
                        modifier = Modifier.align(corner.alignment()),
                        onStart = { if (drafts.isEmpty()) drafts[uid] = Draft(latestFrame, moving = false) },
                        onDrag = { dx, dy ->
                            val current = drafts[uid]
                            if (current != null && !current.moving) {
                                drafts[uid] = Draft(WidgetGeometry.resize(current.bounds, corner, dx, dy, minW, minH, latestArea, resizeH, resizeV), moving = false)
                            }
                        },
                        onEnd = { commit() },
                    )
                } }
            }
            if (active) {
                Text(
                    dragHint ?: "${frame.width.toInt()} × ${frame.height.toInt()} dp",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Palette.Ground,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .clip(CircleShape)
                        .background(Palette.Accent)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
    }
}

private fun WidgetBounds.closeTo(o: WidgetBounds) =
    kotlin.math.abs(x - o.x) < 0.5f && kotlin.math.abs(y - o.y) < 0.5f &&
        kotlin.math.abs(width - o.width) < 0.5f && kotlin.math.abs(height - o.height) < 0.5f

/**
 * A text label's text sits centered in its frame, sized to the frame's width, so a tall frame
 * leaves empty bands above and below that look like extra space once edit frames are gone.
 * Shrinks a dropped label's frame to its text, keeping the text where it is.
 */
private fun hugText(laid: LiveLayout, uid: String, widgets: List<PlacedWidget>, heights: Map<String, Float>): LiveLayout {
    if (widgets.firstOrNull { it.uid == uid }?.kind != WidgetKind.TEXT) return laid
    val frame = laid.frames[uid] ?: return laid
    val text = heights[uid] ?: return laid
    val fit = (text + TEXT_HUG_PAD_DP).coerceAtLeast(WidgetGeometry.MIN_SIZE_DP)
    if (fit >= frame.height - 1f) return laid
    val hugged = frame.copy(y = (frame.y + (frame.height - fit) / 2f).coerceAtLeast(0f), height = fit)
    return LiveLayout(laid.frames + (uid to hugged), laid.guideX, laid.guideY, laid.overlaps)
}

/** Room left above and below a hugged label's text, in dp. */
private const val TEXT_HUG_PAD_DP = 4f

/** A widget under the finger: its raw frame, and whether it's being moved (true) or resized. */
data class Draft(val bounds: WidgetBounds, val moving: Boolean)

/** What a widget's floating toolbar can do. */
class WidgetActions(
    val onAlign: (String, HorizontalAlign) -> Unit,
    val onEdit: (String) -> Unit,
    val onFront: (String) -> Unit,
    val onRemove: (PlacedWidget) -> Unit,
    /** Starts (minutes) or stops (null) a Quick timer. */
    val onTimer: (String, Int?) -> Unit,
    val onTimerExtend: (String) -> Unit,
    val onMessage: (String) -> Unit,
    /** Opens the photo picker to change a photo widget's photo. */
    val onChangeImage: (String) -> Unit = {},
    /** Moves a photo widget on to its next outline. */
    val onShape: (String) -> Unit = {},
    /** Sets up a placeholder from a share code as the real widget. */
    val onFillSlot: (PlacedWidget) -> Unit = {},
)

private const val HOLD_TO_PUSH_MS = 2_000L

private class LiveLayout(
    val frames: Map<String, WidgetBounds>,
    val guideX: Float?,
    val guideY: Float?,
    /** Widgets the moving one sits on, by their saved places; empty when it's over open space. */
    val overlaps: Set<String> = emptySet(),
)

/**
 * Where every widget is drawn while one is under the finger: the moving one aligned to guides
 * (or the grid). Widgets it sits on stay put unless [push] is on, when they slide down.
 */
private fun liveLayout(
    base: Map<String, WidgetBounds>,
    active: Pair<String, Draft>?,
    areaWidth: Float,
    snap: Boolean,
    push: Boolean,
    /** Widgets the dragged one already overlapped when picked up: left in place, never pushed. */
    keep: Set<String> = emptySet(),
): LiveLayout {
    if (active == null || active.first !in base) return LiveLayout(base, null, null)
    val (uid, draft) = active
    val others = base - uid
    var guideX: Float? = null
    var guideY: Float? = null
    val placed = if (draft.moving) {
        val aligned = WidgetLayout.align(draft.bounds, areaWidth, others.values, snap)
        guideX = aligned.guideX
        guideY = aligned.guideY
        aligned.bounds
    } else {
        WidgetGeometry.settle(draft.bounds, snap, areaWidth)
    }
    val overlaps = others.filterValues { WidgetLayout.intersects(it, placed) }.keys
    val pushed = if (push) WidgetLayout.pushDown(placed, others - keep) else emptyMap()
    return LiveLayout(base + pushed + (uid to placed), guideX, guideY, overlaps)
}

private fun overlapsOf(uid: String, frame: WidgetBounds, base: Map<String, WidgetBounds>): Set<String> =
    base.filter { (k, v) -> k != uid && WidgetLayout.intersects(v, frame) }.keys

/** Align, edit, bring to front and remove, floating next to the selected widget. */
@Composable
private fun WidgetToolbar(widget: PlacedWidget, frame: WidgetBounds, areaWidth: Float, areaHeight: Float, actions: WidgetActions) {
    val editable = widget.kind == WidgetKind.TEXT || widget.kind == WidgetKind.NOTE
    val photo = widget.kind == WidgetKind.IMAGE
    val slot = widget.kind == WidgetKind.WIDGET_SLOT
    val count = 5 + (if (editable) 1 else 0) + (if (photo) 2 else 0) + (if (slot) 1 else 0)
    val width = count * 40f + (count - 1) * 4f + 12f
    val height = 52f
    val x = (frame.x + frame.width / 2f - width / 2f).coerceIn(0f, max(0f, areaWidth - width))
    val below = frame.bottom + 10f
    val y = if (below + height <= areaHeight) below else (frame.y - height - 10f).coerceAtLeast(0f)
    Row(
        Modifier
            .absoluteOffset(x.dp, y.dp)
            .zIndex(3f)
            .clip(CircleShape)
            .background(Palette.Surface3)
            .border(1.dp, Palette.LineStrong, CircleShape)
            // The whole bar takes touches, so a near miss between buttons can't reach what's under it.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolIcon("Align left", LauncherIcons.AlignLeft) { actions.onAlign(widget.uid, HorizontalAlign.LEFT) }
        ToolIcon("Align center", LauncherIcons.AlignCenter) { actions.onAlign(widget.uid, HorizontalAlign.CENTER) }
        ToolIcon("Align right", LauncherIcons.AlignRight) { actions.onAlign(widget.uid, HorizontalAlign.RIGHT) }
        if (editable) ToolIcon("Edit", LauncherIcons.Edit) { actions.onEdit(widget.uid) }
        if (slot) ToolIcon("Set up this widget", LauncherIcons.AddWidget) { actions.onFillSlot(widget) }
        if (photo) {
            ToolIcon("Change photo", LauncherIcons.Wallpaper) { actions.onChangeImage(widget.uid) }
            ToolIcon("Shape: ${widget.shape.title}", LauncherIcons.Shape) { actions.onShape(widget.uid) }
        }
        ToolIcon("Bring to front", LauncherIcons.Front) { actions.onFront(widget.uid) }
        ToolIcon("Remove", LauncherIcons.Trash, danger = true) { actions.onRemove(widget) }
    }
}

@Composable
private fun ToolIcon(label: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) Palette.Danger else Palette.Fg, modifier = Modifier.size(18.dp))
    }
}

/** Dashed accent lines showing what a moving widget lined up with. */
@Composable
private fun BoxScope.Guides(guideX: Float?, guideY: Float?) {
    Canvas(Modifier.matchParentSize().zIndex(2f)) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
        val stroke = 1.5.dp.toPx()
        guideX?.let { x ->
            val px = x.dp.toPx().coerceIn(stroke, size.width - stroke)
            drawLine(Palette.Accent, Offset(px, 0f), Offset(px, size.height), stroke, pathEffect = dash)
        }
        guideY?.let { y ->
            val py = y.dp.toPx().coerceIn(stroke, size.height - stroke)
            drawLine(Palette.Accent, Offset(0f, py), Offset(size.width, py), stroke, pathEffect = dash)
        }
    }
}

// Absolute alignment: frames use absolute coordinates, so handles must not mirror in RTL.
private fun Corner.alignment(): Alignment = when (this) {
    Corner.TOP_LEFT -> AbsoluteAlignment.TopLeft
    Corner.TOP_RIGHT -> AbsoluteAlignment.TopRight
    Corner.BOTTOM_LEFT -> AbsoluteAlignment.BottomLeft
    Corner.BOTTOM_RIGHT -> AbsoluteAlignment.BottomRight
}

@Composable
private fun ResizeHandle(
    corner: Corner,
    modifier: Modifier,
    onStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onEnd: () -> Unit,
) {
    val latestStart by rememberUpdatedState(onStart)
    val latestDrag by rememberUpdatedState(onDrag)
    val latestEnd by rememberUpdatedState(onEnd)
    // If the handle leaves the screen mid-drag, its gesture is dropped without an end: finish it.
    var dragging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { if (dragging) latestEnd() } }
    Box(
        modifier
            .size(40.dp)
            .pointerInput(corner) {
                detectDragGestures(
                    onDragStart = { dragging = true; latestStart() },
                    onDragEnd = { dragging = false; latestEnd() },
                    onDragCancel = { dragging = false; latestEnd() },
                    onDrag = { change, amount ->
                        change.consume()
                        latestDrag(amount.x.toDp().value, amount.y.toDp().value)
                    },
                )
            },
        contentAlignment = corner.alignment(),
    ) {
        Box(
            Modifier
                .padding(2.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(Palette.Ground)
                .border(2.dp, Palette.Accent, CircleShape),
        )
    }
}

@Composable
private fun AppWidget(
    appWidgetId: Int,
    frame: WidgetBounds,
    resizing: Boolean,
    controller: WidgetController,
    onLongPress: () -> Unit,
) {
    // Not remembered: the controller caches views it created, and a widget that was unavailable
    // (its app updating, say) is looked up again on the next recomposition.
    val hostView = controller.hostView(appWidgetId)
    if (hostView == null) {
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(LocalWidgetCorner.current)).background(Palette.Surface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Widget unavailable",
                fontSize = 13.sp,
                color = Palette.Muted,
                modifier = Modifier.padding(8.dp),
            )
        }
        return
    }
    val latestLongPress by rememberUpdatedState(onLongPress)
    key(appWidgetId) {
        AndroidView(
            factory = { context -> FrameLayout(context) },
            // Other apps' widgets get the same rounded corners as the launcher's own.
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(LocalWidgetCorner.current)),
            onRelease = { container -> container.removeAllViews() },
            update = { container ->
                if (hostView.parent !== container) {
                    (hostView.parent as? ViewGroup)?.removeView(hostView)
                    container.addView(
                        hostView,
                        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                    )
                }
                hostView.onLongPress = { latestLongPress() }
                // Each size update wakes the widget's app, so send it once the resize ends.
                if (!resizing) hostView.applySize(frame.width, frame.height)
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClockWidget(heightDp: Float, interactive: Boolean, onLongPress: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val t = System.currentTimeMillis()
            now = t
            delay(60_000L - t % 60_000L + 20L)
        }
    }
    LifecycleResumeEffect(Unit) {
        now = System.currentTimeMillis()
        onPauseOrDispose { }
    }
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val is24 = DateFormat.is24HourFormat(context)
    val timeText = remember(now, is24, locale) { formatTime(now, is24, locale) }
    val dateText = remember(now, locale) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale).format(Date(now))
    }
    val density = LocalDensity.current
    val dateDp = 15f
    val timeDp = (heightDp - dateDp * 1.6f - 8f).coerceIn(24f, 140f) * 0.92f
    val timeSize = with(density) { timeDp.dp.toSp() }
    val timeLine = with(density) { (timeDp * 1.05f).dp.toSp() }
    val dateSize = with(density) { dateDp.dp.toSp() }

    Column(
        Modifier
            .fillMaxSize()
            // Long-pressing blank space inside the clock edits it, like any other widget.
            .combinedClickable(
                enabled = interactive,
                interactionSource = null,
                indication = null,
                onLongClick = onLongPress,
                onClick = {},
            )
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(
            timeText,
            fontSize = timeSize,
            lineHeight = timeLine,
            fontWeight = FontWeight.Light,
            letterSpacing = (-0.04).em,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    enabled = interactive,
                    role = Role.Button,
                    onClickLabel = "Open alarms",
                    onLongClick = onLongPress,
                    onClick = { context.startSafely(Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
                ),
        )
        Text(
            dateText,
            fontSize = dateSize,
            color = Palette.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    enabled = interactive,
                    role = Role.Button,
                    onClickLabel = "Open calendar",
                    onLongClick = onLongPress,
                    onClick = {
                        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(now.toString()).build()
                        context.startSafely(Intent(Intent.ACTION_VIEW, uri))
                    },
                ),
        )
    }
}

private fun formatTime(millis: Long, is24: Boolean, locale: Locale): String {
    val skeleton = if (is24) "Hm" else "hm"
    // Drop the AM/PM marker: the large clock reads like the design, "10:24".
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton).replace("a", "").trim()
    return SimpleDateFormat(pattern, locale).format(Date(millis))
}
