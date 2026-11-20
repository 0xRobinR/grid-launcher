package app.gridlauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AppOrderingTest {
    private data class App(val key: String, val label: String)

    private val apps = listOf(App("m", "Maps"), App("c", "camera"), App("b", "Browser"), App("a", "Ärzte"))

    private fun sort(mode: SortMode, counts: Map<String, Int> = emptyMap(), custom: List<String> = emptyList()) =
        AppOrdering.sort(apps, App::key, App::label, mode, counts, custom).map { it.key }

    @Test
    fun alphabeticalIgnoresCaseAndAccents() {
        assertEquals(listOf("a", "b", "c", "m"), sort(SortMode.AZ))
    }

    @Test
    fun mostUsedFallsBackToAlphabetical() {
        assertEquals(listOf("m", "c", "a", "b"), sort(SortMode.MOST_USED, mapOf("m" to 5, "c" to 2)))
    }

    @Test
    fun customOrderPutsUnplacedAppsLast() {
        assertEquals(listOf("m", "b", "a", "c"), sort(SortMode.CUSTOM, custom = listOf("m", "b", "gone")))
    }

    @Test
    fun moveShiftsItems() {
        assertEquals(listOf(2, 3, 1, 4), AppOrdering.move(listOf(1, 2, 3, 4), 0, 2))
        assertEquals(listOf(1, 2), AppOrdering.move(listOf(1, 2), 0, 5))
    }

    @Test
    fun parseUnknownSortModeIsAlphabetical() {
        assertEquals(SortMode.AZ, SortMode.parse("nope"))
        assertEquals(SortMode.CUSTOM, SortMode.parse("CUSTOM"))
    }
}
