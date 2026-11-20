package app.gridlauncher.data

import app.gridlauncher.domain.WidgetBounds
import org.junit.Assert.assertEquals
import org.junit.Test

class CodecTest {
    @Test
    fun listsRoundTrip() {
        val list = listOf("com.a/.Main#0", "com.b/.Main#10")
        assertEquals(list, Codec.decodeList(Codec.encodeList(list)))
        assertEquals(emptyList<String>(), Codec.decodeList("not json"))
        assertEquals(emptyList<String>(), Codec.decodeList(null))
    }

    @Test
    fun countsAndPinsRoundTrip() {
        val counts = mapOf("a" to 3, "b" to 1)
        assertEquals(counts, Codec.decodeCounts(Codec.encodeCounts(counts)))
        val pins = mapOf("a" to listOf("x", "y"), "b" to emptyList())
        assertEquals(pins, Codec.decodePins(Codec.encodePins(pins)))
    }

    @Test
    fun widgetsRoundTripAndSkipBrokenEntries() {
        val widgets = listOf(
            PlacedWidget("u1", WidgetKind.CLOCK, -1, WidgetBounds(0f, 0f, 264f, 112f)),
            PlacedWidget("u2", WidgetKind.APP_WIDGET, 42, WidgetBounds(8.5f, 120f, 180f, 96f)),
        )
        assertEquals(widgets, Codec.decodeWidgets(Codec.encodeWidgets(widgets)))
        val broken = """[{"uid":"","kind":"CLOCK"},{"uid":"x","kind":"NOPE"},{"uid":"ok","kind":"CLOCK","x":1,"y":2,"w":3,"h":4}]"""
        assertEquals(
            listOf(PlacedWidget("ok", WidgetKind.CLOCK, -1, WidgetBounds(1f, 2f, 3f, 4f))),
            Codec.decodeWidgets(broken),
        )
    }

    @Test
    fun pagesRoundTripWithTextWidgets() {
        val pages = listOf(
            LauncherPage(
                uid = "p1",
                widgets = listOf(
                    PlacedWidget("c", WidgetKind.CLOCK, -1, WidgetBounds(0f, 0f, 264f, 112f)),
                    PlacedWidget(
                        "t", WidgetKind.TEXT, -1, WidgetBounds(0f, 120f, 240f, 64f),
                        TextSpec("Work", "Space Grotesk", 700, TextAlignment.CENTER, TextTone.ACCENT),
                    ),
                ),
            ),
            LauncherPage("p2", appsMode = PageApps.CUSTOM, apps = listOf("a", "b")),
            LauncherPage("p3", appsMode = PageApps.NONE),
        )
        assertEquals(pages, Codec.decodePages(Codec.encodePages(pages)))
        assertEquals(emptyList<LauncherPage>(), Codec.decodePages("[{\"uid\":\"\"}]"))
        // A text widget saved without its text settings still gets defaults.
        val bare = Codec.decodeWidgets("""[{"uid":"t","kind":"TEXT"}]""")
        assertEquals(TextSpec(), bare.single().text)
    }

    @Test
    fun timerCountdownRoundTrips() {
        val timer = PlacedWidget("t", WidgetKind.TIMER, -1, WidgetBounds(0f, 0f, 380f, 88f), timerEndsAt = 1_800_000_123L, timerLengthMs = 300_000L)
        assertEquals(listOf(timer), Codec.decodeWidgets(Codec.encodeWidgets(listOf(timer))))
        val idle = timer.copy(timerEndsAt = 0L, timerLengthMs = 0L)
        assertEquals(listOf(idle), Codec.decodeWidgets(Codec.encodeWidgets(listOf(idle))))
    }

    @Test
    fun aScreenKeepsItsOwnGridLook() {
        val look = PageLook(quickActions = 0, dots = DotStyle.COUNT, labels = false, themedIcons = false, gridSize = GridSize.XL)
        val pages = listOf(LauncherPage(uid = "a", look = look), LauncherPage(uid = "b"))
        val back = Codec.decodePages(Codec.encodePages(pages))
        assertEquals(look, back[0].look)
        assertEquals(null, back[1].look)
        val s = LauncherSettings(gridSize = GridSize.S)
        assertEquals(GridSize.XL, s.forPage(back[0]).gridSize)
        assertEquals(0, s.forPage(back[0]).quickActionCount)
        assertEquals(GridSize.S, s.forPage(back[1]).gridSize)
    }

    @Test
    fun boardAppsAndPhotosRoundTrip() {
        val app = PlacedWidget("a", WidgetKind.APP, -1, WidgetBounds(0f, 0f, 120f, 140f), appKey = "com.example/.Main#0")
        val photo = PlacedWidget("p", WidgetKind.IMAGE, -1, WidgetBounds(10f, 20f, 200f, 200f), image = "images/p-1.jpg", shape = ImageShape.CLOVER)
        assertEquals(listOf(app, photo), Codec.decodeWidgets(Codec.encodeWidgets(listOf(app, photo))))
    }
}
