package app.gridlauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gridlauncher.data.AppGroup
import app.gridlauncher.data.AppGroups
import app.gridlauncher.data.LauncherSettings
import app.gridlauncher.data.PageOps
import app.gridlauncher.ui.LauncherViewModel
import app.gridlauncher.ui.components.AppTile
import app.gridlauncher.ui.components.ScreenHeader
import app.gridlauncher.ui.components.SectionLabel
import app.gridlauncher.ui.components.rememberAppIcon
import app.gridlauncher.ui.theme.IconLibrary
import app.gridlauncher.ui.theme.LauncherIcons
import app.gridlauncher.ui.theme.Palette

/**
 * Suggests screens made of apps that belong together, by type (games, finance, news...) or by
 * publisher (Google, Microsoft...). Each one ticked becomes a screen with just those apps, which
 * the user can then change like any other.
 */
@Composable
fun GroupScreensScreen(vm: LauncherViewModel, settings: LauncherSettings) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val apps by vm.allApps.collectAsStateWithLifecycle()
    val version by vm.contentVersion.collectAsStateWithLifecycle()
    val visible = remember(apps, settings.hiddenApps) { apps.filter { it.key !in settings.hiddenApps } }
    val categories by produceState<Map<String, Int>?>(null, visible) { value = vm.appCategories() }
    val types = remember(visible, categories) { categories?.let { c -> AppGroups.byType(visible, { c[it] }) }.orEmpty() }
    val publishers = remember(visible) { AppGroups.byPublisher(visible) }
    val picked = remember { mutableStateListOf<String>() }
    val room = (PageOps.MAX_PAGES - settings.pages.size).coerceAtLeast(0)
    val all = types + publishers

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Screens from app groups", onBack = vm::back)
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = navBottom + 110.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "intro") {
                    Text(
                        "Tick the groups you'd like as screens. Each becomes a screen with just those apps, named and with an icon; change it afterwards like any other screen. " +
                            if (room == 0) "You have ${PageOps.MAX_PAGES} screens already: delete one to make room." else "Room for $room more ${if (room == 1) "screen" else "screens"}.",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = Palette.Muted,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                item(key = "types") { SectionLabel("By type") }
                if (categories == null) {
                    item(key = "loading") { Text("Looking at your apps…", fontSize = 13.sp, color = Palette.Muted) }
                } else if (types.isEmpty()) {
                    item(key = "no-types") { Text("No types with two or more apps.", fontSize = 13.sp, color = Palette.Muted) }
                }
                items(types, key = { it.id }) { g -> GroupRow(vm, g, settings.themedIcons, version, g.id in picked, enabled = g.id in picked || picked.size < room) { on -> if (on) picked += g.id else picked -= g.id } }
                item(key = "publishers") { SectionLabel("By publisher", Modifier.padding(top = 12.dp)) }
                if (publishers.isEmpty()) {
                    item(key = "no-publishers") { Text("No publisher has three or more of your apps.", fontSize = 13.sp, color = Palette.Muted) }
                }
                items(publishers, key = { it.id }) { g -> GroupRow(vm, g, settings.themedIcons, version, g.id in picked, enabled = g.id in picked || picked.size < room) { on -> if (on) picked += g.id else picked -= g.id } }
            }
        }
        val count = picked.size
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth()
                .height(52.dp)
                .clip(CircleShape)
                .background(if (count > 0) Palette.Accent else Palette.Surface3)
                .clickable(enabled = count > 0, role = Role.Button) { vm.createGroupScreens(all.filter { it.id in picked }) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when (count) {
                    0 -> "Tick a group to make a screen"
                    1 -> "Make 1 screen"
                    else -> "Make $count screens"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (count > 0) Palette.Ground else Palette.Muted,
            )
        }
    }
}

@Composable
private fun GroupRow(vm: LauncherViewModel, group: AppGroup, themed: Boolean, version: Int, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Surface)
            .border(if (checked) 2.dp else 1.dp, if (checked) Palette.Accent else Palette.Line, shape)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Palette.Ground), contentAlignment = Alignment.Center) {
            Icon(IconLibrary.vector(group.icon), contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${group.title} · ${group.apps.size}", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (enabled) Palette.Fg else Palette.Subtle)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                group.apps.take(6).forEach { app -> AppTile(rememberAppIcon(vm, app, themed, 22.dp, version), 22.dp) }
                if (group.apps.size > 6) Text("+${group.apps.size - 6}", fontSize = 12.sp, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(if (checked) Palette.Accent else Palette.Ground).border(1.dp, if (checked) Palette.Accent else Palette.LineStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(LauncherIcons.Check, contentDescription = null, tint = Palette.Ground, modifier = Modifier.size(14.dp))
        }
    }
}
