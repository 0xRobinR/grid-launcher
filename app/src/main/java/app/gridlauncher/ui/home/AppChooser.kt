package app.gridlauncher.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import app.gridlauncher.ui.LauncherViewModel
import app.gridlauncher.ui.components.AppTile
import app.gridlauncher.ui.components.rememberAppIcon
import app.gridlauncher.ui.theme.Palette

/** Picks one app to pin on the board, from every app that isn't hidden, A to Z. */
@Composable
fun AppChooserDialog(vm: LauncherViewModel, themed: Boolean, onDismiss: () -> Unit, onPick: (AppEntry) -> Unit) {
    val apps by vm.allApps.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val version by vm.contentVersion.collectAsStateWithLifecycle()
    val hidden = settings?.hiddenApps.orEmpty()
    val choices = apps.filter { it.key !in hidden }.sortedBy { it.label.lowercase() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface3,
        titleContentColor = Palette.Fg,
        title = { Text("Pin an app to the board", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(choices, key = { it.key }) { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button) { onPick(app) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AppTile(rememberAppIcon(vm, app, themed, 36.dp, version), 36.dp)
                        Text(app.label, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.FgSoft) } },
    )
}
