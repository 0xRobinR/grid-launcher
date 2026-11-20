package app.gridlauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetGeometryTest {
    private val area = 380f

    @Test
    fun snapRoundsToEightDp() {
        assertEquals(16f, WidgetGeometry.snap(13f, true))
        assertEquals(13f, WidgetGeometry.snap(13f, false))
    }

    @Test
    fun clampKeepsWidgetsInsideTheArea() {
        val b = WidgetGeometry.clamp(WidgetBounds(-20f, -5f, 500f, 10f), area)
        assertEquals(WidgetBounds(0f, 0f, area, WidgetGeometry.MIN_SIZE_DP), b)
        val right = WidgetGeometry.clamp(WidgetBounds(300f, 40f, 120f, 100f), area)
        assertEquals(260f, right.x)
    }

    @Test
    fun resizeFromTopLeftMovesTheOrigin() {
        val b = WidgetBounds(100f, 100f, 120f, 120f)
        val r = WidgetGeometry.resize(b, Corner.TOP_LEFT, -40f, -30f, 48f, 48f, area, horizontal = true, vertical = true)
        assertEquals(WidgetBounds(60f, 70f, 160f, 150f), r)
    }

    @Test
    fun resizeRespectsMinimumAndEdges() {
        val b = WidgetBounds(100f, 100f, 120f, 120f)
        val small = WidgetGeometry.resize(b, Corner.BOTTOM_RIGHT, -500f, -500f, 80f, 60f, area, horizontal = true, vertical = true)
        assertEquals(WidgetBounds(100f, 100f, 80f, 60f), small)
        val wide = WidgetGeometry.resize(b, Corner.BOTTOM_RIGHT, 900f, 0f, 48f, 48f, area, horizontal = true, vertical = true)
        assertEquals(area, wide.right)
    }

    @Test
    fun resizeIgnoresLockedAxes() {
        val b = WidgetBounds(0f, 0f, 120f, 120f)
        val r = WidgetGeometry.resize(b, Corner.BOTTOM_RIGHT, 40f, 40f, 48f, 48f, area, horizontal = true, vertical = false)
        assertEquals(WidgetBounds(0f, 0f, 160f, 120f), r)
    }

    @Test
    fun newWidgetsGoBelowExistingOnes() {
        val placed = WidgetGeometry.placeNew(listOf(WidgetBounds(0f, 0f, 200f, 100f)), 150f, 80f, area)
        assertTrue(placed.y >= 100f + WidgetGeometry.GAP_DP - WidgetGeometry.SNAP_DP / 2)
        assertEquals(0f, placed.x)
        assertEquals(0f, WidgetGeometry.placeNew(emptyList(), 150f, 80f, area).y)
    }

    @Test
    fun cellSpanMatchesLauncherFormula() {
        assertEquals(4, WidgetGeometry.cellSpan(250f))
        assertEquals(2, WidgetGeometry.cellSpan(110f))
        assertEquals(1, WidgetGeometry.cellSpan(10f))
    }
}
