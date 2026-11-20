package app.gridlauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickActionsTest {
    private val available = listOf("selfie", "video", "portrait", "scan")

    @Test
    fun defaultsToTheAppsTopShortcuts() {
        assertEquals(listOf("selfie", "video"), QuickActions.resolve(available, null, 2))
        assertEquals(listOf("selfie"), QuickActions.resolve(available, null, 1))
        assertEquals(emptyList<String>(), QuickActions.resolve(available, null, 0))
    }

    @Test
    fun explicitPinsWinAndSkipMissingShortcuts() {
        assertEquals(listOf("scan"), QuickActions.resolve(available, listOf("gone", "scan"), 2))
        assertEquals(emptyList<String>(), QuickActions.resolve(available, emptyList(), 2))
    }

    @Test
    fun pinningPastTheLimitDropsTheOldest() {
        assertEquals(listOf("video", "scan"), QuickActions.togglePin(available, null, 2, "scan"))
    }

    @Test
    fun unpinningKeepsAnExplicitList() {
        assertEquals(listOf("video"), QuickActions.togglePin(available, null, 2, "selfie"))
        assertEquals(emptyList<String>(), QuickActions.togglePin(available, listOf("video"), 2, "video"))
    }

    @Test
    fun unknownIdsAreIgnored() {
        assertEquals(listOf("selfie", "video"), QuickActions.togglePin(available, null, 2, "missing"))
    }
}
