package app.gridlauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gridlauncher.data.AppEntry
import app.gridlauncher.data.LauncherSettings
import app.gridlauncher.data.PageApps
import app.gridlauncher.domain.AppOrdering
import app.gridlauncher.domain.SortMode
import app.gridlauncher.ui.LauncherViewModel
import app.gridlauncher.ui.components.AppTile
import app.gridlauncher.ui.components.ScreenHeader
import app.gridlauncher.ui.components.rememberAppIcon
import app.gridlauncher.ui.theme.Palette

/**
 * Chooses the custom set of apps for one home screen. Apps can be ticked one by one, or copied
 * from all apps, favorites or another screen's set.
 */
@Composable
fun AppPickerScreen(vm: LauncherViewModel, settings: LauncherSettings) {
    val targetUid by vm.targetPage.collectAsStateWithLifecycle()
    val all by vm.allApps.collectAsStateWithLifecycle()
    val visible by vm.gridApps.collectAsStateWithLifecycle()
    val version by vm.contentVersion.collectAsStateWithLifecycle()
    val pageIndex = settings.pages.indexOfFirst { it.uid == targetUid }
    val page = settings.pages.getOrNull(pageIndex)
    if (page == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Choose apps", onBack = vm::back)
            Text("This screen no longer exists.", color = Palette.Muted, modifier = Modifier.padding(20.dp))
        }
        return
    }
    val sorted = remember(all) {
        AppOrdering.sort(all, AppEntry::key, AppEntry::label, SortMode.AZ, emptyMap(), emptyList())
    }
    val installed = remember(all) { all.map { it.key }.toSet() }
    // Apps inside this screen's folders are already on it; they're shown ticked but fixed.
    val inFolders = remember(page.folders) { page.folders.flatMap { f -> f.apps.map { it to f.name } }.toMap() }
    var selected by rememberSaveable(page.uid) {
        mutableStateOf(if (page.appsMode == PageApps.CUSTOM) page.apps.filter { it in installed } else emptyList())
    }

    // Sources to copy from: all apps, favorites, and every other screen with its own set.
    val sources = buildList {
        add("All apps" to visible.map { it.key })
        if (settings.favorites.isNotEmpty()) add("Favorites" to settings.favorites)
        settings.pages.forEachIndexed { i, other ->
            if (other.uid != page.uid && other.appsMode == PageApps.CUSTOM && other.apps.isNotEmpty()) {
                add("Screen ${i + 1}" to other.apps)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Apps on Screen ${pageIndex + 1}", onBack = vm::back)
        Text(
            "Tick the apps for this screen, or copy a set to start from.",
            fontSize = 13.sp,
            color = Palette.Muted,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(sources, key = { it.first }) { (label, keys) ->
                Chip("Copy $label") { selected = (selected + keys.filter { it in installed && it !in inFolders }).distinct() }
            }
            item(key = "none") { Chip("Select none") { selected = emptyList() } }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(sorted, key = { it.key }) { app ->
                val folderName = inFolders[app.key]
                val checked = app.key in selected || folderName != null
                val icon = rememberAppIcon(vm, app, settings.themedIcons, 40.dp, version)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (checked) Palette.Surface2 else Palette.Surface)
                        .toggleable(value = checked, enabled = folderName == null, role = Role.Checkbox) { on ->
                            selected = if (on) selected + app.key else selected - app.key
                        }
                        .heightIn(min = 60.dp)
                        .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppTile(icon, 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(app.label, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (folderName != null) Text("In the $folderName folder", fontSize = 12.sp, color = Palette.Muted)
                    }
                    Checkbox(
                        checked = checked,
                        onCheckedChange = null,
                        enabled = folderName == null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = Palette.Accent,
                            checkmarkColor = Palette.Ground,
                            uncheckedColor = Palette.Subtle,
                        ),
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .background(Palette.Ground)
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(Palette.Accent)
                    .clickable(role = Role.Button) {
                        vm.setPageApps(page.uid, selected)
                        vm.back()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (selected.size == 1) "Save 1 app" else "Save ${selected.size} apps",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.Ground,
                )
            }
        }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .clip(CircleShape)
            .border(1.dp, Palette.Line, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, color = Palette.FgSoft)
    }
}
