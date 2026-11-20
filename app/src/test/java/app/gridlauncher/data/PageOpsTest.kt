package app.gridlauncher.data

import app.gridlauncher.domain.WidgetBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageOpsTest {
    private val clock = PlacedWidget("w1", WidgetKind.CLOCK, -1, WidgetBounds(0f, 0f, 200f, 100f))
    private val app = PlacedWidget("w2", WidgetKind.APP_WIDGET, 7, WidgetBounds(0f, 120f, 200f, 100f))
    private val pages = listOf(
        LauncherPage("p1", listOf(clock, app)),
        LauncherPage("p2", appsMode = PageApps.CUSTOM, apps = listOf("a", "b")),
    )

    @Test
    fun widgetsAreFoundAndEditedOnAnyScreen() {
        assertEquals(setOf(7), PageOps.appWidgetIds(pages))
        val moved = PageOps.updateWidget(pages, "w2") { it.copy(bounds = it.bounds.moveBy(8f, 0f)) }
        assertEquals(8f, PageOps.findWidget(moved, "w2")!!.bounds.x)
        assertNull(PageOps.findWidget(PageOps.removeWidget(pages, "w1"), "w1"))
        assertEquals(listOf("w2", "w1"), PageOps.bringToFront(pages, "w1")[0].widgets.map { it.uid })
    }

    @Test
    fun newWidgetsGoToTheChosenScreenOrTheFirst() {
        val text = PlacedWidget("t", WidgetKind.TEXT, -1, WidgetBounds(0f, 0f, 100f, 40f), TextSpec())
        assertEquals(listOf("t"), PageOps.addWidget(pages, "p2", text)[1].widgets.map { it.uid })
        assertEquals(3, PageOps.addWidget(pages, "missing", text)[0].widgets.size)
    }

    @Test
    fun screensAreAddedMovedAndRemoved() {
        val added = PageOps.addPage(pages, "p1", LauncherPage("p3"))
        assertEquals(listOf("p1", "p3", "p2"), added.map { it.uid })
        assertEquals(listOf("p3", "p1", "p2"), PageOps.movePage(added, "p3", -1).map { it.uid })
        assertEquals(added, PageOps.movePage(added, "p1", -1))
        assertEquals(listOf("p1", "p2"), PageOps.removePage(added, "p3").map { it.uid })
        val single = listOf(LauncherPage("only"))
        assertEquals(single, PageOps.removePage(single, "only"))
        val full = (1..PageOps.MAX_PAGES).map { LauncherPage("p$it") }
        assertEquals(full, PageOps.addPage(full, null, LauncherPage("extra")))
    }

    @Test
    fun customAppSetsCanBeClearedFilledAndEdited() {
        val cleared = PageOps.setApps(pages, "p1", PageApps.CUSTOM, emptyList())
        assertEquals(PageApps.CUSTOM, cleared[0].appsMode)
        assertEquals(emptyList<String>(), cleared[0].apps)
        val withApp = PageOps.addApp(cleared, "p1", "x")
        assertEquals(listOf("x"), withApp[0].apps)
        assertEquals(listOf("x"), PageOps.addApp(withApp, "p1", "x")[0].apps)
        assertEquals(listOf("b"), PageOps.removeApp(pages, "p2", "a")[1].apps)
        assertEquals(listOf("a", "b"), PageOps.setApps(pages, "p2", PageApps.NONE)[1].apps)
    }

    @Test
    fun editsKeepAppsThatArentListedRightNow() {
        // "locked" is installed but hidden away (for example in a locked Private Space).
        val original = listOf("a", "locked", "b", "c")
        val listed = setOf("a", "b", "c", "d")
        assertEquals(listOf("c", "locked", "a", "d"), PageOps.mergeKeepingUnlisted(original, listOf("c", "a", "d"), listed))
        assertEquals(listOf("locked"), PageOps.mergeKeepingUnlisted(original, emptyList(), listed))
        assertEquals(listOf("b", "a"), PageOps.mergeKeepingUnlisted(listOf("a", "b"), listOf("b", "a"), setOf("a", "b")))
    }

    @Test
    fun foldersTakeAppsInAndGiveThemBack() {
        val start = listOf(LauncherPage("p", appsMode = PageApps.CUSTOM, apps = listOf("a", "b", "c", "d")))
        val made = PageOps.createFolder(start, "p", "f1", "Work", listOf("b", "d"))
        assertEquals(listOf("a", "folder:f1", "c"), made[0].apps)
        assertEquals(listOf("b", "d"), made[0].folders.single().apps)
        val added = PageOps.addToFolder(made, "p", "f1", "c")
        assertEquals(listOf("a", "folder:f1"), added[0].apps)
        assertEquals(listOf("b", "d", "c"), added[0].folders.single().apps)
        val out = PageOps.removeFromFolder(added, "p", "f1", "d")
        assertEquals(listOf("a", "folder:f1", "d"), out[0].apps)
        val emptied = PageOps.removeFromFolder(PageOps.removeFromFolder(out, "p", "f1", "b"), "p", "f1", "c")
        assertEquals(listOf("a", "c", "b", "d"), emptied[0].apps)
        assertEquals(emptyList<AppFolder>(), emptied[0].folders)
        assertEquals("Projects", PageOps.renameFolder(made, "p", "f1", " Projects ")[0].folders.single().name)
        assertEquals(made, Codec.decodePages(Codec.encodePages(made)))
    }

    @Test
    fun undoPutsBackOnlyWhatWasRemoved() {
        val widget = PlacedWidget("w9", WidgetKind.CLOCK, -1, WidgetBounds(0f, 0f, 100f, 50f))
        val removed = PageOps.removeWidget(pages, "w1")
        // Something else changes before Undo: it survives.
        val later = PageOps.addWidget(removed, "p2", widget)
        val undone = PageOps.reinsertWidget(later, "p1", 0, clock)
        assertEquals(listOf("w1", "w2"), undone[0].widgets.map { it.uid })
        assertEquals(listOf("w9"), undone[1].widgets.map { it.uid })
        assertEquals(undone, PageOps.reinsertWidget(undone, "p1", 0, clock))
        val withoutP1 = PageOps.removePage(pages, "p1")
        assertEquals(listOf("p1", "p2"), PageOps.reinsertPage(withoutP1, 0, pages[0]).map { it.uid })
        val cleared = PageOps.clearApps(PageOps.createFolder(pages, "p2", "f", "F", listOf("a")), "p2")
        assertEquals(emptyList<AppFolder>(), cleared[1].folders)
        assertEquals(emptyList<String>(), cleared[1].apps)
    }

    @Test
    fun aScreenTakesItsLayoutWidthOnFirstSave() {
        val saved = PageOps.updateFrames(pages, mapOf("w1" to WidgetBounds(8f, 0f, 200f, 100f)), layoutWidth = 380f)
        assertEquals(380f, saved[0].layoutWidth)
        val again = PageOps.updateFrames(saved, mapOf("w1" to WidgetBounds(16f, 0f, 200f, 100f)), layoutWidth = 700f)
        assertEquals(380f, again[0].layoutWidth)
        assertEquals(saved, Codec.decodePages(Codec.encodePages(saved)))
    }

    @Test
    fun screensWithARuleAreHiddenOutsideIt() {
        val work = pages[1].copy(rule = ScreenRule(timeEnabled = true, fromMinute = 9 * 60, toMinute = 17 * 60))
        val all = listOf(pages[0], work)
        assertEquals(listOf("p1", "p2"), PageOps.visible(all, 10 * 60, null).map { it.uid })
        assertEquals(listOf("p1"), PageOps.visible(all, 20 * 60, null).map { it.uid })
        val home = pages[1].copy(rule = ScreenRule(wifiEnabled = true, wifiName = "Home"))
        assertEquals(listOf("p2"), PageOps.visible(listOf(home, work), 8 * 60, "Home").map { it.uid })
        // Nothing matches: the first screen still shows.
        assertEquals(listOf("p2"), PageOps.visible(listOf(home), 8 * 60, null).map { it.uid })
    }

    @Test
    fun undoPutsWidgetsBackWithoutLosingOtherChanges() {
        val before = PageOps.layoutOf(pages[0])
        val moved = PageOps.bringToFront(PageOps.updateFrames(pages, mapOf("w1" to WidgetBounds(50f, 60f, 70f, 80f))), "w1")
        val extra = PlacedWidget("w9", WidgetKind.CLOCK, -1, WidgetBounds(0f, 0f, 100f, 50f))
        val later = PageOps.addWidget(moved, "p1", extra)
        val undone = PageOps.restoreLayout(later, before)
        assertEquals(pages[0].widgets.map { it.uid } + "w9", undone[0].widgets.map { it.uid })
        assertEquals(pages[0].widgets[0].bounds, undone[0].widgets[0].bounds)
        assertEquals(pages[1], undone[1])
    }

    @Test
    fun widgetAreasBetweenAppsKeepTheirWidgets() {
        val page = LauncherPage(uid = "p", widgets = listOf(
            PlacedWidget("top", WidgetKind.CLOCK, -1, WidgetBounds(0f, 0f, 100f, 50f)),
            PlacedWidget("mid", WidgetKind.NOTE, -1, WidgetBounds(0f, 0f, 100f, 50f), board = "b1"),
        ))
        val added = PageOps.addBoard(listOf(page), "p", Board("b1", 2))
        val moved = PageOps.moveBoard(PageOps.moveBoard(added, "p", "b1", 1), "p", "b1", -5)
        assertEquals(0, moved[0].boards.single().afterRow)
        val removed = PageOps.removeBoard(moved, "p", "b1")
        assertEquals(listOf("top"), removed[0].widgets.map { it.uid })
        assertEquals(emptyList<Board>(), removed[0].boards)
        val back = PageOps.reinsertBoard(removed, "p", Board("b1", 2), page.widgets.filter { it.board == "b1" })
        assertEquals(listOf("top", "mid"), back[0].widgets.map { it.uid })
        val decoded = Codec.decodePages(Codec.encodePages(back))[0]
        assertEquals(back[0].boards, decoded.boards)
        assertEquals(listOf("", "b1"), decoded.widgets.map { it.board })
    }
}
