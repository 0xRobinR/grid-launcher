package app.gridlauncher.data

import app.gridlauncher.domain.WidgetBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCodeTest {
    private val b = WidgetBounds(0f, 0f, 100f, 100f)
    private val page = LauncherPage(
        uid = "p1",
        name = "Work",
        appsMode = PageApps.CUSTOM,
        apps = listOf("com.a/.Main#0", "folder:f1", "com.gone/.Main#0"),
        folders = listOf(AppFolder("f1", "Docs", listOf("com.b/.Main#0"))),
        rule = ScreenRule(timeEnabled = true, wifiEnabled = true, wifiName = "HomeNet"),
        widgets = listOf(
            PlacedWidget("w1", WidgetKind.CLOCK, -1, b),
            PlacedWidget("w2", WidgetKind.APP_WIDGET, 42, b),
            PlacedWidget("w3", WidgetKind.IMAGE, -1, b, image = "images/x.jpg", shape = ImageShape.OVAL),
            PlacedWidget("w4", WidgetKind.APP, -1, b, appKey = "com.a/.Main#0"),
        ),
    )

    /** This "phone" has com.a and com.b, as user serial 10. */
    private val local: (String) -> String? = { ref -> if (ref.startsWith("com.a") || ref.startsWith("com.b")) "$ref#10" else null }

    @Test
    fun aScreenTravelsWithoutPrivateBits() {
        val code = ShareCode.screenCode(page)
        assertTrue(code.startsWith("GL1:"))
        // Survives line breaks added by a chat app.
        val read = ShareCode.read(code.chunked(40).joinToString("\n"), local) as ShareCode.Shared.Screens
        val p = read.pages.single()
        assertEquals("Work", p.name)
        assertEquals(listOf("com.a/.Main#10", "folder:f1"), p.apps)
        assertEquals(listOf("com.b/.Main#10"), p.folders.single().apps)
        assertEquals(listOf(WidgetKind.CLOCK, WidgetKind.IMAGE, WidgetKind.APP), p.widgets.map { it.kind })
        assertEquals("", p.widgets[1].image)
        assertEquals(ImageShape.OVAL, p.widgets[1].shape)
        assertEquals("com.a/.Main#10", p.widgets[2].appKey)
        assertEquals(false, p.rule.wifiEnabled)
        assertEquals("", p.rule.wifiName)
        assertEquals(true, p.rule.timeEnabled)
    }

    @Test
    fun aLauncherTravelsWithItsSettingsButNotItsHistory() {
        val s = LauncherSettings(
            pages = listOf(page),
            gridSize = GridSize.L,
            navStyle = NavStyle.SNAKE,
            hiddenApps = setOf("com.secret/.Main#0"),
            lockedApps = setOf("com.a/.Main#0"),
            launchCounts = mapOf("com.a/.Main#0" to 9),
            favorites = listOf("com.b/.Main#0", "com.gone/.Main#0"),
        )
        val read = ShareCode.read(ShareCode.launcherCode(s), local) as ShareCode.Shared.Launcher
        assertEquals(GridSize.L, read.settings.gridSize)
        assertEquals(NavStyle.SNAKE, read.settings.navStyle)
        assertEquals(emptySet<String>(), read.settings.hiddenApps)
        assertEquals(emptySet<String>(), read.settings.lockedApps)
        assertEquals(emptyMap<String, Int>(), read.settings.launchCounts)
        assertEquals(listOf("com.b/.Main#10"), read.settings.favorites)
        assertEquals(1, read.settings.pages.size)
    }

    @Test
    fun junkIsNotACode() {
        assertNull(ShareCode.read("hello", local))
        assertNull(ShareCode.read("GL1:!!!", local))
        assertNull(ShareCode.read("{\"format\":\"something-else\"}", local))
    }

    @Test
    fun publishersComeFromPackageOwners() {
        assertEquals("google", AppGroups.owner("com.google.android.gm"))
        assertEquals("mozilla", AppGroups.owner("org.mozilla.firefox"))
        assertNull(AppGroups.owner("com.android.settings"))
        assertNull(AppGroups.owner("single"))
    }

    @Test
    fun otherAppsWidgetsTravelAsPlaceholders() {
        val code = ShareCode.screenCode(page) { id -> if (id == 42) "com.spotify.music/.Widget" to "Spotify · Now playing" else null }
        val p = (ShareCode.read(code, local) as ShareCode.Shared.Screens).pages.single()
        val slot = p.widgets.single { it.kind == WidgetKind.WIDGET_SLOT }
        assertEquals("com.spotify.music/.Widget", slot.provider)
        assertEquals("Spotify · Now playing", slot.label)
        assertEquals(-1, slot.appWidgetId)
        assertEquals(b, slot.bounds)
        // A widget that can't be named (gone from the phone) is simply left out.
        assertEquals(3, ShareCode.read(ShareCode.screenCode(page), local).let { (it as ShareCode.Shared.Screens).pages.single().widgets.size })
    }
}
