package app.gridlauncher.ui.settings

import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gridlauncher.data.SavedSetup
import app.gridlauncher.ui.LauncherViewModel
import app.gridlauncher.ui.components.ScreenHeader
import app.gridlauncher.ui.theme.IconLibrary
import app.gridlauncher.ui.theme.LauncherIcons
import app.gridlauncher.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * Setups saved on this phone: whole launchers and single screens, each under a name. Apply one
 * to switch to it (or add its screens), rename, share as a code, or delete.
 */
@Composable
fun SetupsScreen(vm: LauncherViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val setups by vm.setups.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshSetups() }
    var naming by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SavedSetup?>(null) }
    var applying by remember { mutableStateOf<SavedSetup?>(null) }
    var deleting by remember { mutableStateOf<SavedSetup?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Saved setups", onBack = vm::back)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = navBottom + 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "save") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(CircleShape)
                        .background(Palette.Accent)
                        .clickable(role = Role.Button) { naming = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(LauncherIcons.Plus, contentDescription = null, tint = Palette.Ground, modifier = Modifier.size(18.dp))
                        Text("Save this launcher", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ground)
                    }
                }
            }
            item(key = "intro") {
                Text(
                    "Keep as many setups as you like: every screen and setting, saved on this phone, including hidden apps, photos and other apps' widgets. To save one screen, open its options and tap Save this screen.",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Palette.Muted,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            if (setups.isEmpty()) {
                item(key = "empty") {
                    Text("Nothing saved yet.", fontSize = 14.sp, color = Palette.Subtle, modifier = Modifier.padding(vertical = 24.dp))
                }
            }
            items(setups, key = { it.id }) { setup ->
                SetupCard(
                    setup = setup,
                    onApply = { applying = setup },
                    onRename = { renaming = setup },
                    onShare = { scope.launch { vm.showShareCode(vm.setupShareCode(setup), setup.name, if (setup.kind == SavedSetup.Kind.LAUNCHER) "launcher" else "screen") } },
                    onDelete = { deleting = setup },
                )
            }
        }
    }

    if (naming) {
        NameDialog("Save this launcher", "Name", initial = "", confirm = "Save", onDismiss = { naming = false }) { name ->
            naming = false
            vm.saveLauncherSetup(name)
        }
    }
    renaming?.let { setup ->
        NameDialog("Rename", "Name", initial = setup.name, confirm = "Rename", onDismiss = { renaming = null }) { name ->
            renaming = null
            vm.renameSetup(setup.id, name)
        }
    }
    applying?.let { setup ->
        AlertDialog(
            onDismissRequest = { applying = null },
            containerColor = Palette.Surface3,
            titleContentColor = Palette.Fg,
            textContentColor = Palette.FgSoft,
            title = { Text(setup.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    if (setup.kind == SavedSetup.Kind.LAUNCHER) {
                        "Switch to this launcher? Your current screens and settings are replaced, so save them first if you'd like to come back. Or just add its ${setup.screens} screens to yours."
                    } else {
                        "Add this screen next to the one you're on?"
                    },
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                if (setup.kind == SavedSetup.Kind.LAUNCHER) {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { applying = null; vm.applySetup(setup, replace = true) }) { Text("Switch to it", color = Palette.Accent, fontWeight = FontWeight.SemiBold) }
                        TextButton(onClick = { applying = null; vm.applySetup(setup, replace = false) }) { Text("Only add its screens", color = Palette.Accent) }
                    }
                } else {
                    TextButton(onClick = { applying = null; vm.applySetup(setup, replace = false) }) { Text("Add screen", color = Palette.Accent, fontWeight = FontWeight.SemiBold) }
                }
            },
            dismissButton = { TextButton(onClick = { applying = null }) { Text("Cancel", color = Palette.FgSoft) } },
        )
    }
    deleting?.let { setup ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Palette.Surface3,
            titleContentColor = Palette.Fg,
            textContentColor = Palette.FgSoft,
            title = { Text("Delete \"${setup.name}\"?", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text("The saved copy is removed. Your current screens don't change.", fontSize = 14.sp) },
            confirmButton = { TextButton(onClick = { deleting = null; vm.deleteSetup(setup.id) }) { Text("Delete", color = Palette.Danger, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = Palette.FgSoft) } },
        )
    }
}

@Composable
private fun SetupCard(setup: SavedSetup, onApply: () -> Unit, onRename: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val launcher = setup.kind == SavedSetup.Kind.LAUNCHER
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Surface)
            .border(1.dp, Palette.Line, shape)
            .clickable(role = Role.Button, onClickLabel = "Apply", onClick = onApply)
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Palette.Ground), contentAlignment = Alignment.Center) {
                Icon(if (launcher) IconLibrary.vector("layers") else LauncherIcons.Screens, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(setup.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (launcher) "Launcher · ${setup.screens} ${if (setup.screens == 1) "screen" else "screens"}" else "Screen") + " · " +
                        DateUtils.getRelativeTimeSpanString(setup.savedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                    fontSize = 12.sp,
                    color = Palette.Muted,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
            CardAction("Apply", LauncherIcons.Check, onApply)
            CardAction("Rename", LauncherIcons.Edit, onRename)
            CardAction("Share as a code", LauncherIcons.Shortcut, onShare)
            CardAction("Delete", LauncherIcons.Trash, onDelete, danger = true)
        }
    }
}

@Composable
private fun CardAction(label: String, icon: ImageVector, onClick: () -> Unit, danger: Boolean = false) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) Palette.Danger else Palette.FgSoft, modifier = Modifier.size(18.dp))
    }
}

/** Asks for a name, with [initial] filled in. */
@Composable
fun NameDialog(title: String, label: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface3,
        titleContentColor = Palette.Fg,
        title = { Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text(label) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.Accent,
                    unfocusedBorderColor = Palette.LineStrong,
                    cursorColor = Palette.Accent,
                    focusedLabelColor = Palette.Accent,
                    focusedTextColor = Palette.Fg,
                    unfocusedTextColor = Palette.Fg,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text(confirm, color = Palette.Accent, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.FgSoft) } },
    )
}
