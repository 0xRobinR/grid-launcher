package app.gridlauncher.ui.home

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.gridlauncher.data.ScreenRule
import app.gridlauncher.data.currentWifiName
import app.gridlauncher.ui.components.SwitchRow
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.gridlauncher.ui.theme.IconLibrary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gridlauncher.data.LauncherPage
import app.gridlauncher.data.PageApps
import app.gridlauncher.ui.components.NavRow
import app.gridlauncher.ui.components.RowDivider
import app.gridlauncher.ui.components.SegmentedRow
import app.gridlauncher.ui.theme.LauncherIcons
import app.gridlauncher.ui.theme.Palette

/** Edit-mode options for the screen in view: its apps, its position, adding and deleting screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageSheet(
    page: LauncherPage,
    index: Int,
    count: Int,
    canAdd: Boolean,
    onDismiss: () -> Unit,
    onMode: (PageApps) -> Unit,
    onName: (String) -> Unit,
    onIcon: (String) -> Unit,
    onChooseApps: () -> Unit,
    onMove: (Int) -> Unit,
    onRule: (ScreenRule) -> Unit,
    /** The launcher's grid look, which a screen without its own follows. */
    launcherLook: app.gridlauncher.data.PageLook,
    onLook: (app.gridlauncher.data.PageLook?) -> Unit,
    /** Adds a widget area between rows of this screen's apps. */
    onAddBoard: () -> Unit,
    /** Shares this screen as a code. */
    onShare: () -> Unit,
    /** Saves this screen, under a name, in Saved setups. */
    onSave: (String) -> Unit,
    onDuplicate: () -> Unit,
    onAdd: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = Palette.Surface,
        contentColor = Palette.Fg,
        scrimColor = Color.Black.copy(alpha = 0.55f),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Screen ${index + 1} of $count", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Each screen has its own widgets and its own apps.", fontSize = 13.sp, color = Palette.Muted)
            }
            ScreenLook(page, index, onName, onIcon)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Ground)) {
                SegmentedRow(
                    title = "Apps on this screen",
                    hint = when (page.appsMode) {
                        PageApps.ALL -> "Every app that isn't hidden"
                        PageApps.CUSTOM -> if (page.apps.isEmpty()) "No apps chosen yet: widgets only" else "Only the apps you choose (${page.apps.size})"
                        PageApps.NONE -> "No app grid: a widgets-only screen"
                    },
                    options = listOf(PageApps.ALL to "All apps", PageApps.CUSTOM to "Custom", PageApps.NONE to "None"),
                    selected = page.appsMode,
                    onSelect = onMode,
                )
                if (page.appsMode == PageApps.CUSTOM) {
                    RowDivider()
                    NavRow("Choose apps", value = "${page.apps.size}", hint = "Pick apps or copy another screen's set", onClick = onChooseApps)
                }
            }
            if (page.appsMode != PageApps.NONE) GridLook(page.look, launcherLook, onLook)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Ground).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Widget areas", fontSize = 15.sp)
                Text(
                    if (page.boards.isEmpty()) "Add widget areas between rows of apps: widgets, apps, more widgets, more apps. Move or remove each one in edit mode."
                    else "${page.boards.size} between your apps, besides the one at the top. Move or remove each one in edit mode.",
                    fontSize = 12.sp,
                    color = Palette.Muted,
                )
                PillButton("Add a widget area", LauncherIcons.AddWidget, onAddBoard)
            }
            AutoShow(page.rule, onRule)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (index > 0) PillButton("Move left", LauncherIcons.Back) { onMove(-1) }
                if (index < count - 1) PillButton("Move right", LauncherIcons.Chevron) { onMove(1) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canAdd) PillButton("Add a screen", LauncherIcons.Screens, onAdd)
                if (canAdd) PillButton("Duplicate", LauncherIcons.Front, onDuplicate)
            }
            var saving by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Save this screen", LauncherIcons.Check) { saving = true }
                PillButton("Share this screen", LauncherIcons.Shortcut, onShare)
            }
            if (saving) {
                app.gridlauncher.ui.settings.NameDialog(
                    title = "Save this screen",
                    label = "Name",
                    initial = page.name.ifBlank { "Screen ${index + 1}" },
                    confirm = "Save",
                    onDismiss = { saving = false },
                ) { name ->
                    saving = false
                    onSave(name)
                }
            }
            if (count > 1) PillButton("Delete this screen", LauncherIcons.Trash, onDelete)
        }
    }
}

/** The screen's name and navbar icon, picked from the icon library. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScreenLook(page: LauncherPage, index: Int, onName: (String) -> Unit, onIcon: (String) -> Unit) {
    val nameState = rememberSaveable(page.uid) { mutableStateOf(page.name) }
    var name by nameState
    val icon = IconLibrary.keyFor(page.icon, index)
    val focus = LocalFocusManager.current
    val latestOnName by rememberUpdatedState(onName)
    // Save a typed name when the sheet closes, too. This saves the name only, never the icon, and
    // holds on to this screen's own text even if the sheet switches to another screen.
    DisposableEffect(page.uid) {
        val state = nameState
        val original = page.name
        onDispose { if (state.value.trim() != original) latestOnName(state.value) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(24) },
            singleLine = true,
            label = { Text("Name in the navbar") },
            placeholder = { Text("Screen ${index + 1}") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onName(name)
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
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Icon", fontSize = 15.sp)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.selectableGroup(),
        ) {
            IconLibrary.entries.forEach { entry ->
                val selected = entry.key == icon
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) Palette.Accent else Palette.Ground)
                        .selectable(selected = selected, role = Role.RadioButton) { onIcon(entry.key) }
                        .semantics { contentDescription = entry.label },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        IconLibrary.vector(entry.key),
                        contentDescription = null,
                        tint = if (selected) Palette.Ground else Palette.FgSoft,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/**
 * This screen's own app grid: quick actions, notification dots, labels, themed icons and grid
 * size. Off, the screen follows the launcher's settings; turning it on starts from them.
 */
@Composable
private fun GridLook(
    look: app.gridlauncher.data.PageLook?,
    launcher: app.gridlauncher.data.PageLook,
    onLook: (app.gridlauncher.data.PageLook?) -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Ground)) {
        SwitchRow(
            "Own grid settings",
            if (look == null) "Follows the launcher's settings (grid ${launcher.gridSize.title})" else "Only this screen; other screens keep theirs",
            look != null,
        ) { on -> onLook(if (on) launcher else null) }
        if (look != null) {
            RowDivider()
            SegmentedRow(
                title = "Grid size",
                hint = "${look.gridSize.columns} columns · ${look.gridSize.iconDp} dp icons",
                options = app.gridlauncher.data.GridSize.entries.map { it to it.title },
                selected = look.gridSize,
                onSelect = { onLook(look.copy(gridSize = it)) },
            )
            RowDivider()
            SegmentedRow(
                title = "Quick actions per app",
                hint = null,
                options = listOf(0 to "None", 1 to "1", 2 to "2"),
                selected = look.quickActions,
                onSelect = { onLook(look.copy(quickActions = it)) },
            )
            RowDivider()
            SegmentedRow(
                title = "Notification dots",
                hint = null,
                options = listOf(app.gridlauncher.data.DotStyle.OFF to "Off", app.gridlauncher.data.DotStyle.DOT to "Dot", app.gridlauncher.data.DotStyle.COUNT to "Count"),
                selected = look.dots,
                onSelect = { onLook(look.copy(dots = it)) },
            )
            RowDivider()
            SwitchRow("App labels", "Name under every icon", look.labels) { onLook(look.copy(labels = it)) }
            RowDivider()
            SwitchRow("Themed icons", "Monochrome icons for apps that support them", look.themedIcons) { onLook(look.copy(themedIcons = it)) }
        }
    }
}

/** "Show automatically": this screen comes up by itself at set hours or on a Wi-Fi network. */
@Composable
private fun AutoShow(rule: ScreenRule, onRule: (ScreenRule) -> Unit) {
    val context = LocalContext.current
    val is24 = DateFormat.is24HourFormat(context)
    fun fmt(minute: Int): String {
        val h = minute / 60
        val m = minute % 60
        return if (is24) String.format(Locale.US, "%02d:%02d", h, m)
        else String.format(Locale.US, "%d:%02d %s", if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")
    }
    fun pick(minute: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, minute / 60, minute % 60, is24).show()
    }
    fun useCurrentNetwork() {
        val name = currentWifiName(context)
        if (name == null) {
            Toast.makeText(context, "Connect to the Wi-Fi network first, with location turned on", Toast.LENGTH_LONG).show()
        } else {
            onRule(rule.copy(wifiEnabled = true, wifiName = name))
        }
    }
    // Android 12+ only shows the location prompt when precise and approximate are asked together.
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        when {
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true -> useCurrentNetwork()
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ->
                Toast.makeText(context, "Reading the Wi-Fi name needs precise location. Approximate isn't enough.", Toast.LENGTH_LONG).show()
            else -> Toast.makeText(context, "Android shares the Wi-Fi name only with location access", Toast.LENGTH_LONG).show()
        }
    }
    fun chooseNetwork() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            useCurrentNetwork()
        } else {
            askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Ground)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Show automatically", fontSize = 15.sp)
            Text(
                "Only on home while this matches, and it comes up by itself when you come back to home. Outside it the screen is hidden, but still shows while you edit screens.",
                fontSize = 12.sp,
                color = Palette.Muted,
            )
        }
        SwitchRow(
            "During these hours",
            "${fmt(rule.fromMinute)} – ${fmt(rule.toMinute)}",
            rule.timeEnabled,
        ) { onRule(rule.copy(timeEnabled = it)) }
        if (rule.timeEnabled) {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("From ${fmt(rule.fromMinute)}", IconLibrary.vector("clock")) { pick(rule.fromMinute) { onRule(rule.copy(fromMinute = it)) } }
                PillButton("To ${fmt(rule.toMinute)}", IconLibrary.vector("clock")) { pick(rule.toMinute) { onRule(rule.copy(toMinute = it)) } }
            }
        }
        RowDivider()
        SwitchRow(
            "On a Wi-Fi network",
            if (rule.wifiName.isBlank()) "Uses the network you're connected to now" else rule.wifiName,
            rule.wifiEnabled,
        ) { on -> if (on && rule.wifiName.isBlank()) chooseNetwork() else onRule(rule.copy(wifiEnabled = on)) }
        if (rule.wifiEnabled) {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                PillButton("Use current network", IconLibrary.vector("globe")) { chooseNetwork() }
            }
        }
    }
}
