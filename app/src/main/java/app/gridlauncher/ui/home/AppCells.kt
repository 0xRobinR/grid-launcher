package app.gridlauncher.ui.home

import android.content.pm.ShortcutInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.gridlauncher.data.AppEntry
import app.gridlauncher.data.AppFolder
import app.gridlauncher.data.DotStyle
import app.gridlauncher.notifications.NotificationCounts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.widthIn
import app.gridlauncher.data.LauncherSettings
import app.gridlauncher.domain.QuickActions
import app.gridlauncher.ui.LauncherViewModel
import app.gridlauncher.ui.components.AppTile
import app.gridlauncher.ui.components.LaunchSource
import app.gridlauncher.ui.components.ShortcutGlyph
import app.gridlauncher.ui.components.launchArgs
import app.gridlauncher.ui.components.rememberAppIcon
import app.gridlauncher.ui.components.rememberShortcuts
import app.gridlauncher.ui.components.trackLaunchSource
import app.gridlauncher.ui.theme.LauncherIcons
import app.gridlauncher.ui.theme.Palette

/** Everything a cell needs from the screen, bundled to keep call sites short. */
class CellContext(
    val vm: LauncherViewModel,
    val settings: LauncherSettings,
    val version: Int,
    val permission: Boolean,
    val editing: Boolean,
    /** Set when the cell sits on a screen with its own app set: its menu edits that set. */
    val customPageUid: String? = null,
    /** Screens with their own app sets, as (uid, "Screen N"), for "Add to Screen N". */
    val customPages: List<Pair<String, String>> = emptyList(),
    /** Notification counts by [NotificationCounts.key]. */
    val dots: Map<String, Int> = emptyMap(),
    /** Folders on this cell's custom screen, for "Add to folder". */
    val folders: List<AppFolder> = emptyList(),
    /** Set for a cell inside a folder: its menu offers "Remove from folder". */
    val folderId: String? = null,
    /** The screen this cell is on, for "Place on the board". */
    val screenUid: String? = null,
    /** Set for an app pinned on the board: the widget holding it, for "Remove from the board". */
    val boardUid: String? = null,
    /** The widget area just above this cell's part of the grid: where "Place on the board" puts it. */
    val sectionBoard: String = "",
) {
    fun inSection(board: String) = CellContext(vm, settings, version, permission, editing, customPageUid, customPages, dots, folders, folderId, screenUid, boardUid, board)

    fun forAllApps() = CellContext(vm, settings, version, permission, editing, null, customPages, dots, screenUid = screenUid)

    fun inFolder(id: String) = CellContext(vm, settings, version, permission, editing, customPageUid, customPages, dots, folders, id, screenUid)

    fun onBoard(widgetUid: String) = CellContext(vm, settings, version, permission, editing, null, customPages, dots, emptyList(), null, screenUid, widgetUid)

    fun dotCount(app: AppEntry): Int = dots[NotificationCounts.key(app.packageName, app.user)] ?: 0
}

/** An app icon with its notification dot (or count) and, for locked apps, a lock badge. */
@Composable
fun BadgedTile(app: AppEntry, ctx: CellContext, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val icon = rememberAppIcon(ctx.vm, app, ctx.settings.themedIcons, size, ctx.version)
    val count = ctx.dotCount(app)
    Box(modifier) {
        AppTile(icon, size)
        if (count > 0 && ctx.settings.notificationDots != DotStyle.OFF) {
            val showCount = ctx.settings.notificationDots == DotStyle.COUNT
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 0.dp, end = 0.dp)
                    .then(if (showCount) Modifier.height(20.dp).widthIn(min = 20.dp) else Modifier.size(12.dp))
                    .clip(CircleShape)
                    .background(Palette.Accent)
                    .border(2.dp, Palette.Ground, CircleShape)
                    .semantics { contentDescription = "$count notifications" },
                contentAlignment = Alignment.Center,
            ) {
                if (showCount) {
                    Text(if (count > 99) "99+" else "$count", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ground, modifier = Modifier.padding(horizontal = 5.dp))
                }
            }
        }
        if (app.key in ctx.settings.lockedApps) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Palette.Surface3)
                    .border(1.dp, Palette.Ground, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(LockIcon, contentDescription = "Locked", tint = Palette.Fg, modifier = Modifier.size(11.dp))
            }
        }
    }
}

private val LockIcon by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("lock", 24.dp, 24.dp, 24f, 24f).apply {
        listOf("M6 11h12v9H6z", "M8.5 11V8a3.5 3.5 0 0 1 7 0v3").forEach { d ->
            addPath(
                pathData = androidx.compose.ui.graphics.vector.addPathNodes(d),
                stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
            )
        }
    }.build()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppGridCell(
    app: AppEntry,
    ctx: CellContext,
    menuOpen: Boolean,
    onOpenMenu: () -> Unit,
    onCloseMenu: () -> Unit,
) {
    val vm = ctx.vm
    val settings = ctx.settings
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    val source = remember { LaunchSource() }
    val shortcuts = rememberShortcuts(vm, app, settings.quickActionCount > 0 || menuOpen, ctx.permission, ctx.version)
    val available = remember(shortcuts) { shortcuts.map { it.id } }
    val chipIds = QuickActions.resolve(available, settings.pinnedActions[app.key], settings.quickActionCount)
    val chips = chipIds.mapNotNull { id -> shortcuts.firstOrNull { it.id == id } }

    Box {
        Column(
            Modifier.fillMaxWidth().alpha(if (ctx.editing) 0.32f else 1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(
                        enabled = !ctx.editing,
                        role = Role.Button,
                        onClickLabel = "Open",
                        onLongClickLabel = "Show options",
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenMenu()
                        },
                        onClick = {
                            val args = launchArgs(view, source)
                            vm.launch(app, args.sourceBounds, args.options)
                        },
                    )
                    .then(if (settings.showLabels) Modifier else Modifier.semantics { contentDescription = app.label })
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BadgedTile(app, ctx, settings.gridSize.iconDp.dp, Modifier.trackLaunchSource(source))
                if (settings.showLabels) {
                    Text(
                        app.label,
                        fontSize = settings.gridSize.labelSp.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    )
                }
            }
            if (chips.isNotEmpty()) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    chips.forEach { shortcut ->
                        QuickActionChip(vm, shortcut, enabled = !ctx.editing) { args ->
                            vm.launchShortcut(app, shortcut, args.sourceBounds, args.options)
                        }
                    }
                }
            }
        }
        AppMenu(
            expanded = menuOpen,
            onDismiss = onCloseMenu,
            app = app,
            ctx = ctx,
            shortcuts = shortcuts,
            pinnedIds = chipIds,
        )
    }
}

@Composable
private fun QuickActionChip(
    vm: LauncherViewModel,
    shortcut: ShortcutInfo,
    enabled: Boolean,
    onClick: (app.gridlauncher.ui.components.LaunchArgs) -> Unit,
) {
    val view = LocalView.current
    val source = remember { LaunchSource() }
    val shape = CircleShape
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(shape)
            .border(1.dp, Palette.Line, shape)
            .trackLaunchSource(source)
            .clickable(enabled = enabled, role = Role.Button) { onClick(launchArgs(view, source)) }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        ShortcutGlyph(vm, shortcut, 14.dp)
        Text(
            shortcut.shortLabel?.toString().orEmpty(),
            fontSize = 12.sp,
            color = Palette.FgSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
fun FavoritesRow(
    favorites: List<AppEntry>,
    ctx: CellContext,
    openMenuKey: String?,
    onOpenMenu: (String) -> Unit,
    onCloseMenu: () -> Unit,
    title: String = "Favorites",
    keyPrefix: String = "fav",
    emptyHint: String = "Long-press any app and choose Add to favorites.",
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title.uppercase(),
            fontSize = 12.sp,
            letterSpacing = 0.08.em,
            color = Palette.Muted,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (favorites.isEmpty()) {
            Text(
                emptyHint,
                fontSize = 13.sp,
                color = Palette.Subtle,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                items(favorites, key = { it.key }) { app ->
                    FavoriteCell(
                        app = app,
                        ctx = ctx,
                        menuOpen = openMenuKey == "$keyPrefix:${app.key}",
                        onOpenMenu = { onOpenMenu("$keyPrefix:${app.key}") },
                        onCloseMenu = onCloseMenu,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteCell(
    app: AppEntry,
    ctx: CellContext,
    menuOpen: Boolean,
    onOpenMenu: () -> Unit,
    onCloseMenu: () -> Unit,
) {
    val vm = ctx.vm
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    val source = remember { LaunchSource() }
    val shortcuts = rememberShortcuts(vm, app, menuOpen, ctx.permission, ctx.version)
    val available = remember(shortcuts) { shortcuts.map { it.id } }
    val pinned = QuickActions.resolve(available, ctx.settings.pinnedActions[app.key], ctx.settings.quickActionCount)
    Box {
        Column(
            Modifier
                .width(76.dp)
                .alpha(if (ctx.editing) 0.32f else 1f)
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    enabled = !ctx.editing,
                    role = Role.Button,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenMenu()
                    },
                    onClick = {
                        val args = launchArgs(view, source)
                        vm.launch(app, args.sourceBounds, args.options)
                    },
                )
                .then(if (ctx.settings.showLabels) Modifier else Modifier.semantics { contentDescription = app.label })
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BadgedTile(app, ctx, 52.dp, Modifier.trackLaunchSource(source))
            if (ctx.settings.showLabels) {
                Text(
                    app.label,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                )
            }
        }
        AppMenu(menuOpen, onCloseMenu, app, ctx, shortcuts, pinned)
    }
}

@Composable
private fun AppMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    app: AppEntry,
    ctx: CellContext,
    shortcuts: List<ShortcutInfo>,
    pinnedIds: List<String>,
) {
    val vm = ctx.vm
    val settings = ctx.settings
    val available = remember(shortcuts) { shortcuts.map { it.id } }
    val isFavorite = app.key in settings.favorites
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Palette.Surface3,
        border = BorderStroke(1.dp, Palette.Line),
        modifier = Modifier.widthIn(min = 260.dp),
    ) {
        Text(
            app.label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Palette.Fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (shortcuts.isNotEmpty()) {
            MenuCaption("Shortcuts")
            shortcuts.forEach { shortcut ->
                val pinned = shortcut.id in pinnedIds
                DropdownMenuItem(
                    text = {
                        Text(
                            shortcut.shortLabel?.toString().orEmpty(),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = { ShortcutGlyph(vm, shortcut, 18.dp) },
                    trailingIcon = if (settings.quickActionCount > 0) {
                        {
                            IconButton(onClick = { vm.togglePin(app, available, shortcut.id) }) {
                                Icon(
                                    LauncherIcons.Pin,
                                    contentDescription = if (pinned) "Remove from grid" else "Show on grid",
                                    tint = if (pinned) Palette.Accent else Palette.Subtle,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    colors = itemColors(),
                    onClick = {
                        onDismiss()
                        vm.launchShortcut(app, shortcut, null, null)
                    },
                )
            }
            if (settings.quickActionCount > 0) {
                Text(
                    "Pinned shortcuts show under the app. Up to ${settings.quickActionCount}.",
                    fontSize = 12.sp,
                    color = Palette.Muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            HorizontalDivider(color = Palette.Line, modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp))
        } else if (!ctx.permission) {
            Text(
                "Set Grid Launcher as your home app to see this app's shortcuts.",
                fontSize = 12.sp,
                color = Palette.Muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            HorizontalDivider(color = Palette.Line, modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp))
        }
        if (settings.favoritesEnabled) {
            MenuAction(
                if (isFavorite) "Remove from favorites" else "Add to favorites",
                LauncherIcons.Star,
            ) {
                onDismiss()
                vm.setFavorite(app, !isFavorite)
            }
        }
        ctx.customPages
            .filter { (uid, _) -> uid != ctx.customPageUid }
            .filter { (uid, _) ->
                val page = settings.pages.firstOrNull { it.uid == uid }
                page != null && app.key !in page.apps && page.folders.none { app.key in it.apps }
            }
            .forEach { (uid, label) ->
                MenuAction("Add to $label", LauncherIcons.AddWidget) {
                    onDismiss()
                    vm.addAppToPage(uid, app)
                    vm.toast("Added ${app.label} to $label")
                }
            }
        val boardUid = ctx.boardUid
        val screenUid = ctx.screenUid
        if (boardUid != null) {
            MenuAction("Remove from the board", LauncherIcons.Close) {
                onDismiss()
                vm.removeWidget(boardUid)
            }
        } else if (screenUid != null) {
            MenuAction("Place on the board", LauncherIcons.AddWidget) {
                onDismiss()
                vm.placeAppOnBoard(screenUid, app, ctx.sectionBoard)
            }
        }
        val folderId = ctx.folderId
        val pageUid = ctx.customPageUid
        if (boardUid != null) {
            // An app on the board isn't in the grid: no folders, no "remove from this screen".
        } else if (pageUid != null && folderId != null) {
            MenuAction("Remove from folder", LauncherIcons.Close) {
                onDismiss()
                vm.removeFromFolder(pageUid, folderId, app)
            }
        } else if (pageUid != null) {
            ctx.folders.forEach { folder ->
                MenuAction("Add to ${folder.name}", FolderIcon) {
                    onDismiss()
                    vm.addToFolder(pageUid, folder.id, app)
                }
            }
            MenuAction("New folder", FolderIcon) {
                onDismiss()
                vm.newFolder(pageUid, app)
            }
        }
        val locked = app.key in settings.lockedApps
        MenuAction(if (locked) "Unlock app" else "Lock in Grid Launcher", LockIcon) {
            onDismiss()
            vm.setLocked(app, !locked)
        }
        MenuAction("Reorder apps", LauncherIcons.Move) {
            onDismiss()
            vm.openArrange(fromSettings = false, pageUid = ctx.customPageUid)
        }
        MenuAction("App info", LauncherIcons.Info) {
            onDismiss()
            vm.openAppInfo(app, null)
        }
        val customPage = ctx.customPageUid
        if (boardUid != null) {
            // Removing it from the board is above.
        } else if (customPage != null && ctx.folderId == null) {
            MenuAction("Remove from this screen", LauncherIcons.Close) {
                onDismiss()
                vm.removeAppFromPage(customPage, app)
            }
        } else if (customPage == null) {
            MenuAction("Hide from grid", LauncherIcons.Hide) {
                onDismiss()
                vm.setHidden(app, true)
                vm.toast("${app.label} is hidden. Unhide it in Settings › Hidden apps.")
            }
        }
        if (!app.isSystemApp) {
            MenuAction("Uninstall", LauncherIcons.Trash, color = Palette.Danger) {
                onDismiss()
                vm.uninstall(app)
            }
        }
    }
}

@Composable
private fun MenuCaption(text: String) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        letterSpacing = 0.08.em,
        color = Palette.Muted,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun MenuAction(label: String, icon: ImageVector, color: Color = Palette.Fg, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontSize = 14.sp, color = color) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp)) },
        colors = itemColors(),
        onClick = onClick,
    )
}

@Composable
private fun itemColors() = MenuDefaults.itemColors(
    textColor = Palette.Fg,
    leadingIconColor = Palette.Fg,
    trailingIconColor = Palette.Subtle,
)

val FolderIcon by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("folder", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = androidx.compose.ui.graphics.vector.addPathNodes("M3 6h6l2 2h10v11H3z"),
            stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
        )
    }.build()
}

/** A folder in the grid: a tile showing up to four of its apps, opening into a sheet. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderCell(folder: AppFolder, apps: List<AppEntry>, ctx: CellContext, onOpen: () -> Unit) {
    // The folder tile is as big as an app icon at this grid size; its four minis scale with it.
    val tile = ctx.settings.gridSize.iconDp
    val mini = (tile * 0.7f - 4f) / 2f
    val count = apps.sumOf { ctx.dotCount(it) }
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(if (ctx.editing) 0.32f else 1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !ctx.editing, role = Role.Button, onClickLabel = "Open folder", onClick = onOpen)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            Box(
                Modifier.size(tile.dp).clip(RoundedCornerShape((tile / 3).dp)).background(Palette.Surface2).padding((tile * 0.15f).dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    apps.take(4).chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { app ->
                                AppTile(rememberAppIcon(ctx.vm, app, ctx.settings.themedIcons, mini.dp, ctx.version), mini.dp)
                            }
                        }
                    }
                }
            }
            if (count > 0 && ctx.settings.notificationDots != DotStyle.OFF) {
                Box(Modifier.align(Alignment.TopEnd).size(12.dp).clip(CircleShape).background(Palette.Accent).border(2.dp, Palette.Ground, CircleShape))
            }
        }
        if (ctx.settings.showLabels) {
            Text(folder.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
