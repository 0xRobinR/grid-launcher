package app.gridlauncher.domain

import kotlin.math.max
import kotlin.math.roundToInt

/** A widget's frame inside the free-form widget area, in dp, measured from the top-left. */
data class WidgetBounds(val x: Float, val y: Float, val width: Float, val height: Float) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height

    fun contains(px: Float, py: Float): Boolean = px >= x && px <= right && py >= y && py <= bottom

    fun moveBy(dx: Float, dy: Float) = copy(x = x + dx, y = y + dy)
}

enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

object WidgetGeometry {
    const val SNAP_DP = 8f
    const val MIN_SIZE_DP = 48f
    const val GAP_DP = 12f

    fun snap(value: Float, enabled: Boolean): Float =
        if (enabled) (value / SNAP_DP).roundToInt() * SNAP_DP else value

    /** Keeps a frame inside the area: never wider than it, never off the left, right or top edge. */
    fun clamp(bounds: WidgetBounds, areaWidth: Float): WidgetBounds {
        val width = bounds.width.coerceIn(MIN_SIZE_DP.coerceAtMost(areaWidth), max(areaWidth, MIN_SIZE_DP))
        val height = bounds.height.coerceAtLeast(MIN_SIZE_DP)
        val x = bounds.x.coerceIn(0f, max(0f, areaWidth - width))
        val y = bounds.y.coerceAtLeast(0f)
        return WidgetBounds(x, y, width, height)
    }

    fun settle(bounds: WidgetBounds, snapEnabled: Boolean, areaWidth: Float): WidgetBounds {
        val snapped = WidgetBounds(
            x = snap(bounds.x, snapEnabled),
            y = snap(bounds.y, snapEnabled),
            width = snap(bounds.width, snapEnabled),
            height = snap(bounds.height, snapEnabled),
        )
        return clamp(snapped, areaWidth)
    }

    fun resize(
        bounds: WidgetBounds,
        corner: Corner,
        dx: Float,
        dy: Float,
        minWidth: Float,
        minHeight: Float,
        areaWidth: Float,
        horizontal: Boolean,
        vertical: Boolean,
    ): WidgetBounds {
        val minW = minWidth.coerceIn(MIN_SIZE_DP, max(MIN_SIZE_DP, areaWidth))
        val minH = minHeight.coerceAtLeast(MIN_SIZE_DP)
        var left = bounds.x
        var top = bounds.y
        var right = bounds.right
        var bottom = bounds.bottom
        if (horizontal) {
            when (corner) {
                Corner.TOP_LEFT, Corner.BOTTOM_LEFT -> left = (left + dx).coerceIn(0f, max(0f, right - minW))
                Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT -> right = (right + dx).coerceIn(left + minW, max(left + minW, areaWidth))
            }
        }
        if (vertical) {
            when (corner) {
                Corner.TOP_LEFT, Corner.TOP_RIGHT -> top = (top + dy).coerceIn(0f, max(0f, bottom - minH))
                Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT -> bottom = (bottom + dy).coerceAtLeast(top + minH)
            }
        }
        return WidgetBounds(left, top, right - left, bottom - top)
    }

    fun contentBottom(frames: Collection<WidgetBounds>): Float = frames.maxOfOrNull { it.bottom } ?: 0f

    /** Where a newly added widget goes: full left, just below everything already placed. */
    fun placeNew(existing: Collection<WidgetBounds>, width: Float, height: Float, areaWidth: Float): WidgetBounds {
        val y = if (existing.isEmpty()) 0f else snap(contentBottom(existing) + GAP_DP, true)
        return clamp(WidgetBounds(0f, y, width, height), areaWidth)
    }

    /** Legacy launcher cell formula: a widget n cells wide needs (70 * n - 30) dp. */
    fun cellSpan(sizeDp: Float): Int = max(1, ((sizeDp + 30f) / 70f).roundToInt())
}

/** Where an aligned frame snapped to: guide lines to draw, in dp, or null when not aligned. */
data class Aligned(val bounds: WidgetBounds, val guideX: Float?, val guideY: Float?)

enum class HorizontalAlign { LEFT, CENTER, RIGHT }

/** Alignment guides and making room: keeps widgets tidy while they're moved. */
object WidgetLayout {
    /** How close, in dp, a frame must come to a guide before it snaps to it. */
    const val ALIGN_THRESHOLD_DP = 10f

    fun intersects(a: WidgetBounds, b: WidgetBounds): Boolean =
        a.x < b.right && b.x < a.right && a.y < b.bottom && b.y < a.bottom

    /**
     * Snaps a moving frame to the area's left edge, center and right edge, and to the edges and
     * centers of [others]. An axis with no guide close enough falls back to the 8 dp grid.
     */
    fun align(raw: WidgetBounds, areaWidth: Float, others: Collection<WidgetBounds>, snapGrid: Boolean): Aligned {
        val w = raw.width
        val h = raw.height
        // (x the frame would take, x where the guide line is drawn)
        val xs = buildList {
            add(0f to 0f)
            add(areaWidth - w to areaWidth)
            add((areaWidth - w) / 2f to areaWidth / 2f)
            others.forEach { o ->
                add(o.x to o.x)
                add(o.right - w to o.right)
                add(o.x + o.width / 2f - w / 2f to o.x + o.width / 2f)
            }
        }
        val ys = buildList {
            others.forEach { o ->
                add(o.y to o.y)
                add(o.bottom - h to o.bottom)
                add(o.y + o.height / 2f - h / 2f to o.y + o.height / 2f)
            }
        }
        val bestX = xs.minByOrNull { kotlin.math.abs(it.first - raw.x) }
            ?.takeIf { kotlin.math.abs(it.first - raw.x) <= ALIGN_THRESHOLD_DP }
        val bestY = ys.minByOrNull { kotlin.math.abs(it.first - raw.y) }
            ?.takeIf { kotlin.math.abs(it.first - raw.y) <= ALIGN_THRESHOLD_DP }
        val x = bestX?.first ?: WidgetGeometry.snap(raw.x, snapGrid)
        val y = bestY?.first ?: WidgetGeometry.snap(raw.y, snapGrid)
        val bounds = WidgetGeometry.clamp(raw.copy(x = x, y = y), areaWidth)
        return Aligned(
            bounds = bounds,
            guideX = bestX?.second?.takeIf { bounds.x == x },
            guideY = bestY?.second?.takeIf { bounds.y == y },
        )
    }

    /** Places a frame against the area's left edge, center or right edge. */
    fun alignTo(bounds: WidgetBounds, mode: HorizontalAlign, areaWidth: Float): WidgetBounds {
        val x = when (mode) {
            HorizontalAlign.LEFT -> 0f
            HorizontalAlign.CENTER -> (areaWidth - bounds.width) / 2f
            HorizontalAlign.RIGHT -> areaWidth - bounds.width
        }
        return WidgetGeometry.clamp(bounds.copy(x = x), areaWidth)
    }

    /**
     * Makes room for [moving]: a widget it overlaps slides down just below it, and a widget that
     * slid down pushes whatever it then lands on, down the stack. Widgets the move never reaches
     * keep their places, even where they already overlapped on purpose. Returns only the widgets
     * that moved.
     */
    fun <K> pushDown(moving: WidgetBounds, others: Map<K, WidgetBounds>, gap: Float = WidgetGeometry.GAP_DP): Map<K, WidgetBounds> {
        val current = LinkedHashMap(others)
        val pushed = LinkedHashMap<K, WidgetBounds>()
        // Each step pushes the highest widget that overlaps the moving one or an already pushed
        // one. Widgets only ever move down, so this settles; the cap is a safety net.
        var guard = others.size * (others.size + 1) + 1
        while (guard-- > 0) {
            val blockers = listOf<Pair<K?, WidgetBounds>>(null to moving) + pushed.map { it.key to it.value }
            val hit = current.entries
                .filter { (key, frame) -> blockers.any { (bk, b) -> bk != key && intersects(b, frame) } }
                .minByOrNull { it.value.y }
                ?: break
            val below = blockers.filter { (bk, b) -> bk != hit.key && intersects(b, hit.value) }.maxOf { it.second.bottom }
            val moved = hit.value.copy(y = WidgetGeometry.snap(below + gap, true).coerceAtLeast(below + gap / 2))
            current[hit.key] = moved
            pushed[hit.key] = moved
        }
        return pushed
    }
}
