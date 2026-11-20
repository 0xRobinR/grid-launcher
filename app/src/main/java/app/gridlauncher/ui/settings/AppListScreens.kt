package app.gridlauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gridlauncher.data.AppEntry
import app.gridlauncher.data.AppFolder
import app.gridlauncher.data.LauncherSettings
import app.gridlauncher.data.PageApps
import app.gridlauncher.domain.AppOrdering
import app.gridlauncher.domain.SortMode
import app.gridlauncher.ui.LauncherViewModel
import app.gridlauncher.ui.components.AppTile
import app.gridlauncher.ui.components.ScreenHeader
import app.gridlauncher.ui.components.rememberAppIcon
import app.gridlauncher.ui.theme.LauncherIcons
import app.gridlauncher.ui.theme.Palette

@Composable
fun HiddenAppsScreen(vm: LauncherViewModel, settings: LauncherSettings) {
    val all by vm.allApps.collectAsStateWithLifecycle()
    val version by vm.contentVersion.collectAsStateWithLifecycle()
    val hidden = remember(all, settings.hiddenApps) {
        AppOrdering.sort(all.filter { it.key in settings.hiddenApps }, AppEntry::key, AppEntry::label, SortMode.AZ, emptyMap(), emptyList())
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Hidden apps", onBack = vm::back)
        if (hidden.isEmpty()) {
            Text(
                "No hidden apps. To hide one, long-press it on the home screen and choose Hide from grid.",
                fontSize = 14.sp,
                color = Palette.Muted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = navBottom + 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(hidden, key = { it.key }) { app ->
                AppRow(vm, app, settings.themedIcons, version) {
                    Box(
                        Modifier
                            .heightIn(min = 44.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClickLabel = "Unhide ${app.label}") { vm.unhide(app.key) }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Unhide", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.Accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    vm: LauncherViewModel,
    app: AppEntry,
    themed: Boolean,
    version: Int,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val icon = rememberAppIcon(vm, app, themed, 40.dp, version)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Surface)
            .heightIn(min = 60.dp)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppTile(icon, 40.dp)
        Text(app.label, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Reorder the grid: long-press a row (or its handle) and drag. Saving switches sorting to Custom. */
@Composable
fun ArrangeAppsScreen(vm: LauncherViewModel, settings: LauncherSettings) {
    val targetPage by vm.targetPage.collectAsStateWithLifecycle()
    val gridApps by vm.gridApps.collectAsStateWithLifecycle()
    val allApps by vm.allApps.collectAsStateWithLifecycle()
    // A screen with its own apps arranges that list; otherwise the global grid order.
    val page = settings.pages.firstOrNull { it.uid == targetPage && it.appsMode == PageApps.CUSTOM }
    // Rows to arrange: apps, and on a custom screen its folders too, in their place in the set.
    val initial = remember(page, gridApps, allApps) {
        if (page == null) gridApps.map { ArrangeItem(it.key, it, null) } else {
            val byKey = allApps.associateBy(AppEntry::key)
            page.apps.mapNotNull { key ->
                val folderId = AppFolder.idOf(key)
                if (folderId == null) byKey[key]?.let { ArrangeItem(key, it, null) }
                else page.folders.firstOrNull { it.id == folderId }?.let { ArrangeItem(key, null, it) }
            }
        }
    }
    val version by vm.contentVersion.collectAsStateWithLifecycle()
    var order by remember { mutableStateOf(initial) }
    // New installs or uninstalls while this screen is open: keep the user's order, merge the change.
    LaunchedEffect(initial) {
        val keys = initial.map { it.key }.toSet()
        val kept = order.filter { it.key in keys }
        val keptKeys = kept.map { it.key }.toSet()
        order = kept + initial.filter { it.key !in keptKeys }
    }
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    fun swapIfNeeded() {
        val key = draggingKey ?: return
        val info = listState.layoutInfo.visibleItemsInfo
        val current = info.firstOrNull { it.key == key } ?: return
        val center = current.offset + dragOffset + current.size / 2f
        val target = info.firstOrNull { it.key != key && center >= it.offset && center <= it.offset + it.size } ?: return
        order = AppOrdering.move(order, current.index, target.index)
        dragOffset += (current.offset - target.offset).toFloat()
    }

    LaunchedEffect(draggingKey) {
        if (draggingKey == null) return@LaunchedEffect
        val edge = 96f
        while (true) {
            val layout = listState.layoutInfo
            val current = layout.visibleItemsInfo.firstOrNull { it.key == draggingKey }
            if (current != null) {
                val top = current.offset + dragOffset
                val bottom = top + current.size
                val step = when {
                    bottom > layout.viewportEndOffset - edge -> 18f
                    top < layout.viewportStartOffset + edge -> -18f
                    else -> 0f
                }
                if (step != 0f) {
                    val consumed = listState.scrollBy(step)
                    dragOffset += consumed
                    swapIfNeeded()
                }
            }
            withFrameNanos { }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Arrange apps", onBack = vm::back)
        Text(
            if (page != null) "Long-press an app and drag it to a new place on this screen."
            else "Long-press an app and drag it to a new place. The grid switches to your custom order.",
            fontSize = 13.sp,
            color = Palette.Muted,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        )
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = navBottom + 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(order, key = { it.key }) { item ->
                val dragging = draggingKey == item.key
                val rowModifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) dragOffset else 0f
                            scaleX = if (dragging) 1.02f else 1f
                            scaleY = if (dragging) 1.02f else 1f
                        }
                        .then(if (dragging) Modifier else Modifier.animateItem())
                        .pointerInput(item.key) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    draggingKey = item.key
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    swapIfNeeded()
                                },
                                onDragEnd = {
                                    draggingKey = null
                                    dragOffset = 0f
                                    vm.saveOrder(page?.uid, order.map { it.key })
                                },
                                onDragCancel = {
                                    draggingKey = null
                                    dragOffset = 0f
                                    vm.saveOrder(page?.uid, order.map { it.key })
                                },
                            )
                        }
                        .semantics { contentDescription = "${item.label}, long-press and drag to move" }
                val handle: @Composable () -> Unit = {
                    Icon(
                        LauncherIcons.Drag,
                        contentDescription = null,
                        tint = if (dragging) Palette.Accent else Palette.Subtle,
                        modifier = Modifier.padding(12.dp).size(20.dp),
                    )
                }
                if (item.app != null) {
                    AppRow(vm = vm, app = item.app, themed = settings.themedIcons, version = version, modifier = rowModifier, trailing = handle)
                } else {
                    Row(
                        rowModifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Palette.Surface)
                            .heightIn(min = 60.dp)
                            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Palette.Surface2), contentAlignment = Alignment.Center) {
                            Icon(app.gridlauncher.ui.home.FolderIcon, contentDescription = null, tint = Palette.Fg, modifier = Modifier.size(20.dp))
                        }
                        Text(
                            "${item.folder?.name} · ${item.folder?.apps?.size ?: 0} apps",
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        handle()
                    }
                }
            }
        }
    }
}

/** A row in Arrange apps: an app, or a folder in a screen's custom set. */
private class ArrangeItem(val key: String, val app: AppEntry?, val folder: AppFolder?) {
    val label: String get() = app?.label ?: folder?.name.orEmpty()
}
