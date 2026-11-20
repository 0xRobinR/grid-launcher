package app.gridlauncher.data

import app.gridlauncher.domain.SortMode
import app.gridlauncher.domain.WidgetBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    @Test
    fun aFullLayoutRoundTrips() {
        val s = LauncherSettings(
            quickActionCount = 1,
            sortMode = SortMode.CUSTOM,
            favoritesEnabled = true,
            fontFamily = "Inter",
            hiddenApps = setOf("a", "b"),
            favorites = listOf("c"),
            customOrder = listOf("b", "a"),
            launchCounts = mapOf("a" to 4),
            pinnedActions = mapOf("a" to listOf("x")),
            pages = listOf(
                LauncherPage(
                    "p1",
                    widgets = listOf(PlacedWidget("w", WidgetKind.NOTE, -1, WidgetBounds(0f, 0f, 300f, 100f), TextSpec(text = "hi"))),
                    name = "Work",
                    icon = "work",
                    rule = ScreenRule(timeEnabled = true, fromMinute = 540, toMinute = 1020, wifiEnabled = true, wifiName = "Office"),
                ),
            ),
            accentColor = 0xFF8FD6A8.toInt(),
            widgetCorner = 12f,
            swipeUp = GestureAction.LOCK,
            tutorialDone = true,
        )
        assertEquals(s, Backup.decode(Backup.encode(s)))
    }

    @Test
    fun otherFilesAreRejected() {
        assertNull(Backup.decode("not json"))
        assertNull(Backup.decode("""{"format":"something-else","pages":[]}"""))
        assertNull(Backup.decode("""{"format":"grid-launcher-backup","pages":[]}"""))
    }

    @Test
    fun rulesMatchTimeWindowsAndWifi() {
        val day = ScreenRule(timeEnabled = true, fromMinute = 9 * 60, toMinute = 17 * 60)
        assertTrue(day.matches(9 * 60, null))
        assertFalse(day.matches(17 * 60, null))
        val night = ScreenRule(timeEnabled = true, fromMinute = 22 * 60, toMinute = 6 * 60)
        assertTrue(night.matches(23 * 60, null))
        assertTrue(night.matches(5 * 60, null))
        assertFalse(night.matches(12 * 60, null))
        val home = ScreenRule(wifiEnabled = true, wifiName = "Home")
        assertTrue(home.matches(0, "Home"))
        assertFalse(home.matches(0, null))
        assertFalse(ScreenRule().matches(600, "Home"))
        val both = ScreenRule(timeEnabled = true, fromMinute = 9 * 60, toMinute = 17 * 60, wifiEnabled = true, wifiName = "Office")
        assertFalse(both.matches(10 * 60, "Home"))
        assertTrue(both.matches(10 * 60, "Office"))
    }

    @Test
    fun duplicatingAScreenSkipsOtherAppsWidgets() {
        val pages = listOf(
            LauncherPage(
                "p1",
                widgets = listOf(
                    PlacedWidget("c", WidgetKind.CLOCK, -1, WidgetBounds(0f, 0f, 100f, 50f)),
                    PlacedWidget("a", WidgetKind.APP_WIDGET, 7, WidgetBounds(0f, 60f, 100f, 50f)),
                ),
                name = "Work",
            ),
            LauncherPage("p2"),
        )
        var n = 0
        val result = PageOps.duplicate(pages, "p1", "copy") { "w${n++}" }
        assertEquals(listOf("p1", "copy", "p2"), result.map { it.uid })
        assertEquals(listOf(WidgetKind.CLOCK), result[1].widgets.map { it.kind })
        assertEquals("Work copy", result[1].name)
        assertEquals(setOf(7), PageOps.appWidgetIds(result))
    }
}
