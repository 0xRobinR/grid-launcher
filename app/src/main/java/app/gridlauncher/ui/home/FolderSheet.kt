package app.gridlauncher.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gridlauncher.data.AppEntry
import app.gridlauncher.data.AppFolder
import app.gridlauncher.ui.theme.Palette

/** An open folder: its name, editable, and its apps in the same grid cells as home. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FolderSheet(
    folder: AppFolder,
    apps: List<AppEntry>,
    ctx: CellContext,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(folder.id) { mutableStateOf(folder.name) }
    var openMenu by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    ModalBottomSheet(
        onDismissRequest = {
            if (name.trim() != folder.name) onRename(name)
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = Palette.Surface,
        contentColor = Palette.Fg,
        scrimColor = Color.Black.copy(alpha = 0.55f),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                singleLine = true,
                label = { Text("Folder name") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    onRename(name)
                    focus.clearFocus()
                }),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.Accent,
                    unfocusedBorderColor = Palette.Line,
                    focusedLabelColor = Palette.Accent,
                    unfocusedLabelColor = Palette.Muted,
                    cursorColor = Palette.Accent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )
            androidx.compose.foundation.layout.FlowRow(
                maxItemsInEachRow = 3,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                apps.forEach { app ->
                    Box(Modifier.weight(1f)) {
                        AppGridCell(
                            app = app,
                            ctx = ctx.inFolder(folder.id),
                            menuOpen = openMenu == app.key,
                            onOpenMenu = { openMenu = app.key },
                            onCloseMenu = { openMenu = null },
                        )
                    }
                }
                // Keep the last row's cells the same width as the others.
                repeat((3 - apps.size % 3) % 3) { Box(Modifier.weight(1f)) }
            }
            if (apps.isEmpty()) Text("This folder is empty.", fontSize = 13.sp, color = Palette.Muted, modifier = Modifier.width(240.dp))
        }
    }
}
