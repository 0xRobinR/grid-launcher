package app.gridlauncher.data

/** Pure edits to the list of home screens. Every function returns a new list. */
object PageOps {
    const val MAX_PAGES = 8

    fun appWidgetIds(pages: List<LauncherPage>): Set<Int> =
        pages.flatMap { it.widgets }.filter { it.kind == WidgetKind.APP_WIDGET }.map { it.appWidgetId }.toSet()

    fun findWidget(pages: List<LauncherPage>, uid: String): PlacedWidget? =
        pages.firstNotNullOfOrNull { page -> page.widgets.firstOrNull { it.uid == uid } }

    fun updateWidget(pages: List<LauncherPage>, uid: String, change: (PlacedWidget) -> PlacedWidget) =
        pages.map { page ->
            if (page.widgets.none { it.uid == uid }) page
            else page.copy(widgets = page.widgets.map { if (it.uid == uid) change(it) else it })
        }

    fun removeWidget(pages: List<LauncherPage>, uid: String) =
        pages.map { page -> page.copy(widgets = page.widgets.filterNot { it.uid == uid }) }

    fun bringToFront(pages: List<LauncherPage>, uid: String) = pages.map { page ->
        val target = page.widgets.firstOrNull { it.uid == uid } ?: return@map page
        page.copy(widgets = page.widgets.filterNot { it.uid == uid } + target)
    }

    /** Adds [widget] to [pageUid], or to the first screen if that screen no longer exists. */
    fun addWidget(pages: List<LauncherPage>, pageUid: String?, widget: PlacedWidget): List<LauncherPage> {
        val target = pages.firstOrNull { it.uid == pageUid }?.uid ?: pages.firstOrNull()?.uid ?: return pages
        return pages.map { if (it.uid == target) it.copy(widgets = it.widgets + widget) else it }
    }

    /** Inserts [page] right after [afterUid], or at the end. Ignored once there are [MAX_PAGES]. */
    fun addPage(pages: List<LauncherPage>, afterUid: String?, page: LauncherPage): List<LauncherPage> {
        if (pages.size >= MAX_PAGES) return pages
        val index = pages.indexOfFirst { it.uid == afterUid }
        val result = pages.toMutableList()
        if (index < 0) result.add(page) else result.add(index + 1, page)
        return result
    }

    /** Removes a screen. The last remaining screen can't be removed. */
    fun removePage(pages: List<LauncherPage>, uid: String): List<LauncherPage> =
        if (pages.size <= 1) pages else pages.filterNot { it.uid == uid }

    fun movePage(pages: List<LauncherPage>, uid: String, delta: Int): List<LauncherPage> {
        val from = pages.indexOfFirst { it.uid == uid }
        val to = from + delta
        if (from < 0 || to !in pages.indices) return pages
        val result = pages.toMutableList()
        result.add(to, result.removeAt(from))
        return result
    }

    fun setApps(pages: List<LauncherPage>, uid: String, mode: PageApps, apps: List<String>? = null) =
        pages.map { if (it.uid == uid) it.copy(appsMode = mode, apps = apps?.distinct() ?: it.apps) else it }

    /**
     * Merges an edited list back into [original]: keys the edit couldn't see (apps not listed right
     * now, such as a locked Private Space) keep their place instead of being dropped.
     */
    fun mergeKeepingUnlisted(original: List<String>, edited: List<String>, listed: Set<String>): List<String> {
        val result = edited.distinct().toMutableList()
        original.forEachIndexed { index, key ->
            if (key !in listed && key !in result) result.add(index.coerceAtMost(result.size), key)
        }
        return result
    }

    /**
     * Moves several widgets at once, for a drop that also pushed others down. A screen without a
     * layout width yet takes [layoutWidth].
     */
    fun updateFrames(pages: List<LauncherPage>, frames: Map<String, app.gridlauncher.domain.WidgetBounds>, layoutWidth: Float = 0f) =
        pages.map { page ->
            if (page.widgets.none { it.uid in frames }) page
            else page.copy(
                widgets = page.widgets.map { w -> frames[w.uid]?.let { w.copy(bounds = it) } ?: w },
                layoutWidth = if (page.layoutWidth > 0f) page.layoutWidth else layoutWidth,
            )
        }

    // ---- Widget areas between apps ----

    fun addBoard(pages: List<LauncherPage>, pageUid: String, board: Board) =
        pages.map { if (it.uid == pageUid && it.boards.none { b -> b.uid == board.uid }) it.copy(boards = it.boards + board) else it }

    /** Moves widget area [boardUid] down (+1) or up (-1) by a row of apps. */
    fun moveBoard(pages: List<LauncherPage>, pageUid: String, boardUid: String, delta: Int) = pages.map { page ->
        if (page.uid != pageUid) page
        else page.copy(boards = page.boards.map { if (it.uid == boardUid) it.copy(afterRow = (it.afterRow + delta).coerceAtLeast(0)) else it })
    }

    /** Removes widget area [boardUid] and every widget in it. */
    fun removeBoard(pages: List<LauncherPage>, pageUid: String, boardUid: String) = pages.map { page ->
        if (page.uid != pageUid) page
        else page.copy(boards = page.boards.filterNot { it.uid == boardUid }, widgets = page.widgets.filterNot { it.board == boardUid })
    }

    /** Puts a removed widget area back with its widgets, for Undo. */
    fun reinsertBoard(pages: List<LauncherPage>, pageUid: String, board: Board, widgets: List<PlacedWidget>) = pages.map { page ->
        if (page.uid != pageUid || page.boards.any { it.uid == board.uid }) page
        else page.copy(boards = page.boards + board, widgets = page.widgets + widgets.filter { w -> page.widgets.none { it.uid == w.uid } })
    }

    fun setLook(pages: List<LauncherPage>, uid: String, look: PageLook?) =
        pages.map { if (it.uid == uid) it.copy(look = look) else it }

    fun setRule(pages: List<LauncherPage>, uid: String, rule: ScreenRule) =
        pages.map { if (it.uid == uid) it.copy(rule = rule) else it }

    /**
     * Copies a screen, right after the original, with fresh ids. Other apps' widgets can't be
     * copied (Android binds each to one place), so the copy keeps only the launcher's own widgets.
     */
    fun duplicate(pages: List<LauncherPage>, uid: String, newUid: String, widgetUid: () -> String): List<LauncherPage> {
        val source = pages.firstOrNull { it.uid == uid } ?: return pages
        val copy = source.copy(
            uid = newUid,
            name = if (source.name.isBlank()) "" else "${source.name} copy".take(24),
            widgets = source.widgets.filter { it.kind != WidgetKind.APP_WIDGET }
                .map { it.copy(uid = widgetUid(), timerEndsAt = 0L, timerLengthMs = 0L) },
            rule = ScreenRule(),
        )
        return addPage(pages, uid, copy)
    }

    /** Where every widget on a screen sits, and in what order, to put back with Undo. */
    data class LayoutStep(val pageUid: String, val order: List<String>, val bounds: Map<String, app.gridlauncher.domain.WidgetBounds>, val layoutWidth: Float)

    fun layoutOf(page: LauncherPage) =
        LayoutStep(page.uid, page.widgets.map { it.uid }, page.widgets.associate { it.uid to it.bounds }, page.layoutWidth)

    /**
     * Puts a screen's widgets back where [step] had them. Widgets added since stay, on top; widgets
     * removed since stay removed; text, timers and everything else are left as they are now.
     */
    fun restoreLayout(pages: List<LauncherPage>, step: LayoutStep) = pages.map { page ->
        if (page.uid != step.pageUid) return@map page
        val rank = step.order.withIndex().associate { (i, uid) -> uid to i }
        page.copy(
            widgets = page.widgets
                .map { w -> step.bounds[w.uid]?.let { w.copy(bounds = it) } ?: w }
                .sortedBy { rank[it.uid] ?: Int.MAX_VALUE },
            layoutWidth = step.layoutWidth,
        )
    }

    /**
     * The screens on home right now: those without a rule, and those whose rule matches. If every
     * screen is hidden, the first one shows, so home is never empty.
     */
    fun visible(pages: List<LauncherPage>, minuteOfDay: Int, wifi: String?): List<LauncherPage> =
        pages.filter { !it.rule.active || it.rule.matches(minuteOfDay, wifi) }.ifEmpty { pages.take(1) }

    /** The screen to show on return to home: the first whose rule matches, or null if none does. */
    fun ruleMatch(pages: List<LauncherPage>, minuteOfDay: Int, wifi: String?): LauncherPage? =
        pages.firstOrNull { it.rule.matches(minuteOfDay, wifi) }

    /**
     * Makes a folder from [appKeys] in screen [pageUid]: it takes the place of the first of them in
     * the set, and the others leave the top level.
     */
    fun createFolder(pages: List<LauncherPage>, pageUid: String, folderId: String, name: String, appKeys: List<String>) = pages.map { page ->
        if (page.uid != pageUid || appKeys.isEmpty()) return@map page
        val folder = AppFolder(folderId, name.ifBlank { "Folder" }, appKeys.distinct())
        val index = page.apps.indexOfFirst { it in appKeys }.let { if (it < 0) page.apps.size else it }
        val rest = page.apps.filterNot { it in appKeys }.toMutableList()
        rest.add(index.coerceAtMost(rest.size), folder.marker)
        page.copy(appsMode = PageApps.CUSTOM, apps = rest, folders = page.folders + folder)
    }

    fun addToFolder(pages: List<LauncherPage>, pageUid: String, folderId: String, appKey: String) = pages.map { page ->
        if (page.uid != pageUid) return@map page
        page.copy(
            apps = page.apps.filterNot { it == appKey },
            folders = page.folders.map { f -> if (f.id == folderId && appKey !in f.apps) f.copy(apps = f.apps + appKey) else f },
        )
    }

    /** Takes an app out of a folder, back into the set just after it. An emptied folder goes away. */
    fun removeFromFolder(pages: List<LauncherPage>, pageUid: String, folderId: String, appKey: String) = pages.map { page ->
        if (page.uid != pageUid) return@map page
        val folder = page.folders.firstOrNull { it.id == folderId } ?: return@map page
        val left = folder.apps.filterNot { it == appKey }
        val apps = page.apps.toMutableList()
        val at = apps.indexOf(folder.marker)
        if (appKey !in apps) apps.add(if (at < 0) apps.size else at + 1, appKey)
        if (left.isEmpty()) {
            apps.remove(folder.marker)
            page.copy(apps = apps, folders = page.folders.filterNot { it.id == folderId })
        } else {
            page.copy(apps = apps, folders = page.folders.map { if (it.id == folderId) it.copy(apps = left) else it })
        }
    }

    fun renameFolder(pages: List<LauncherPage>, pageUid: String, folderId: String, name: String) = pages.map { page ->
        if (page.uid != pageUid) page
        else page.copy(folders = page.folders.map { if (it.id == folderId) it.copy(name = name.trim().take(24).ifBlank { "Folder" }) else it })
    }

    fun setName(pages: List<LauncherPage>, uid: String, name: String) =
        pages.map { if (it.uid == uid) it.copy(name = name.trim().take(24)) else it }

    fun setIcon(pages: List<LauncherPage>, uid: String, icon: String) =
        pages.map { if (it.uid == uid) it.copy(icon = icon) else it }

    fun pageOf(pages: List<LauncherPage>, widgetUid: String): LauncherPage? =
        pages.firstOrNull { page -> page.widgets.any { it.uid == widgetUid } }

    /** Puts a removed widget back on its screen at its old place in the stacking order. */
    fun reinsertWidget(pages: List<LauncherPage>, pageUid: String, index: Int, widget: PlacedWidget) = pages.map { page ->
        if (page.uid != pageUid || page.widgets.any { it.uid == widget.uid }) page
        else page.copy(widgets = page.widgets.toMutableList().apply { add(index.coerceIn(0, size), widget) })
    }

    /** Puts a deleted screen back at its old position. */
    fun reinsertPage(pages: List<LauncherPage>, index: Int, page: LauncherPage): List<LauncherPage> =
        if (pages.any { it.uid == page.uid }) pages else pages.toMutableList().apply { add(index.coerceIn(0, size), page) }

    /** Empties a screen's set, folders included, leaving a custom set with nothing chosen. */
    fun clearApps(pages: List<LauncherPage>, uid: String) =
        pages.map { if (it.uid == uid) it.copy(appsMode = PageApps.CUSTOM, apps = emptyList(), folders = emptyList()) else it }

    fun addApp(pages: List<LauncherPage>, uid: String, appKey: String) = pages.map { page ->
        if (page.uid != uid || appKey in page.apps) page
        else page.copy(appsMode = PageApps.CUSTOM, apps = page.apps + appKey)
    }

    fun removeApp(pages: List<LauncherPage>, uid: String, appKey: String) = pages.map { page ->
        if (page.uid != uid) page else page.copy(apps = page.apps.filterNot { it == appKey })
    }
}
