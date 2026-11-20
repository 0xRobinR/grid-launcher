package app.gridlauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutTest {
    private val area = 380f

    @Test
    fun snapsToTheAreaCenterAndEdges() {
        val centered = WidgetLayout.align(WidgetBounds(115f, 37f, 140f, 80f), area, emptyList(), snapGrid = true)
        assertEquals(120f, centered.bounds.x)
        assertEquals(area / 2, centered.guideX)
        assertNull(centered.guideY)
        assertEquals(40f, centered.bounds.y) // No guide on Y: falls back to the grid.
        val right = WidgetLayout.align(WidgetBounds(236f, 0f, 140f, 80f), area, emptyList(), snapGrid = true)
        assertEquals(240f, right.bounds.x)
        assertEquals(area, right.guideX)
    }

    @Test
    fun snapsToOtherWidgets() {
        val other = WidgetBounds(20f, 200f, 100f, 60f)
        val aligned = WidgetLayout.align(WidgetBounds(23f, 196f, 50f, 40f), area, listOf(other), snapGrid = false)
        assertEquals(20f, aligned.bounds.x)
        assertEquals(200f, aligned.bounds.y)
        assertEquals(20f, aligned.guideX)
        assertEquals(200f, aligned.guideY)
    }

    @Test
    fun farFromGuidesUsesTheGrid() {
        val aligned = WidgetLayout.align(WidgetBounds(61f, 61f, 100f, 100f), area, emptyList(), snapGrid = true)
        assertEquals(WidgetBounds(64f, 64f, 100f, 100f), aligned.bounds)
        assertNull(aligned.guideX)
    }

    @Test
    fun overlappedWidgetsSlideDownAndCascade() {
        val moving = WidgetBounds(0f, 0f, 200f, 100f)
        val others = mapOf(
            "a" to WidgetBounds(0f, 80f, 200f, 60f),
            "b" to WidgetBounds(0f, 150f, 200f, 60f),
            "c" to WidgetBounds(250f, 0f, 100f, 60f),
        )
        val moved = WidgetLayout.pushDown(moving, others)
        assertEquals(setOf("a", "b"), moved.keys)
        assertTrue(moved.getValue("a").y >= moving.bottom + 6f)
        assertTrue(moved.getValue("b").y >= moved.getValue("a").bottom + 6f)
        val all = listOf(moving) + moved.values + others.getValue("c")
        for (i in all.indices) for (j in i + 1 until all.size) assertFalse(WidgetLayout.intersects(all[i], all[j]))
    }

    @Test
    fun alignButtonsPlaceAgainstTheEdges() {
        val b = WidgetBounds(50f, 10f, 100f, 40f)
        assertEquals(0f, WidgetLayout.alignTo(b, HorizontalAlign.LEFT, area).x)
        assertEquals(140f, WidgetLayout.alignTo(b, HorizontalAlign.CENTER, area).x)
        assertEquals(280f, WidgetLayout.alignTo(b, HorizontalAlign.RIGHT, area).x)
    }

    @Test
    fun overlapsTheMoveDoesntReachAreLeftAlone() {
        // A clock deliberately laid over a photo, far from the widget being moved.
        val others = mapOf(
            "photo" to WidgetBounds(0f, 400f, 300f, 200f),
            "clock" to WidgetBounds(20f, 420f, 200f, 80f),
        )
        assertEquals(emptyMap<String, WidgetBounds>(), WidgetLayout.pushDown(WidgetBounds(0f, 0f, 100f, 100f), others))
        // Dropping onto the photo pushes it, and the clock it then lands on moves with it.
        val moved = WidgetLayout.pushDown(WidgetBounds(0f, 380f, 300f, 60f), others)
        assertEquals(setOf("photo", "clock"), moved.keys)
        assertFalse(WidgetLayout.intersects(moved.getValue("photo"), moved.getValue("clock")))
    }
}
