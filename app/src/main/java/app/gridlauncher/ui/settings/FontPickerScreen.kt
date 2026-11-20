package app.gridlauncher.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.googlefonts.isAvailableOnDevice
import app.gridlauncher.data.LauncherSettings
import app.gridlauncher.ui.LauncherViewModel
import app.gridlauncher.ui.components.ScreenHeader
import app.gridlauncher.ui.components.SectionLabel
import app.gridlauncher.ui.theme.FontCatalog
import app.gridlauncher.ui.theme.LauncherIcons
import app.gridlauncher.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FontPickerScreen(vm: LauncherViewModel, settings: LauncherSettings) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val providerAvailable by produceState(initialValue = true) {
        value = withContext(Dispatchers.IO) {
            runCatching { FontCatalog.provider.isAvailableOnDevice(context) }.getOrDefault(false)
        }
    }
    val current = settings.fontFamily
    val isCustom = current.isNotBlank() && FontCatalog.families.none { it.equals(current, ignoreCase = true) }
    var custom by rememberSaveable { mutableStateOf(if (isCustom) current else "") }
    val options = remember { listOf(FontCatalog.SYSTEM) + FontCatalog.families }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader("Font", onBack = vm::back)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = navBottom + 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!providerAvailable) {
                item(key = "unavailable") {
                    Text(
                        "Google Play services isn't available, so fonts can't be downloaded. The system font is used instead.",
                        fontSize = 13.sp,
                        color = Palette.Danger,
                    )
                }
            }
            item(key = "custom") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Any Google Fonts family")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val apply = {
                            val name = custom.trim()
                            if (name.isNotEmpty()) {
                                vm.setFontFamily(name)
                                focus.clearFocus()
                            }
                        }
                        OutlinedTextField(
                            value = custom,
                            onValueChange = { custom = it },
                            singleLine = true,
                            label = { Text("Family name, e.g. Space Mono") },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { apply() }),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Palette.Accent,
                                unfocusedBorderColor = Palette.Line,
                                focusedLabelColor = Palette.Accent,
                                unfocusedLabelColor = Palette.Muted,
                                cursorColor = Palette.Accent,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = apply, enabled = custom.isNotBlank()) {
                            Text("Apply", color = if (custom.isNotBlank()) Palette.Accent else Palette.Subtle)
                        }
                    }
                    Text(
                        "Type the name exactly as it appears on fonts.google.com. If it can't be found, the system font is used.",
                        fontSize = 12.sp,
                        color = Palette.Muted,
                    )
                }
            }
            item(key = "label") { SectionLabel("Popular") }
            item(key = "list") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Palette.Surface)
                        .selectableGroup(),
                ) {
                    options.forEach { name ->
                        FontOption(
                            name = name,
                            selected = name.equals(current, ignoreCase = true),
                            onSelect = { vm.setFontFamily(name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FontOption(name: String, selected: Boolean, onSelect: () -> Unit) {
    val family: FontFamily = remember(name) { FontCatalog.family(name) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(FontCatalog.displayName(name), fontSize = 17.sp, fontFamily = family, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "10:24 · Camera · Maps · Messages",
                fontSize = 13.sp,
                fontFamily = family,
                color = Palette.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) Palette.Accent else Palette.Surface),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(LauncherIcons.Check, contentDescription = null, tint = Palette.Ground, modifier = Modifier.size(16.dp))
        }
    }
}
