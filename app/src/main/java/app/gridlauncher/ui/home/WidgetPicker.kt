package app.gridlauncher.ui.home

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.gridlauncher.data.WidgetKind
import app.gridlauncher.domain.WidgetGeometry
import app.gridlauncher.ui.theme.LauncherIcons
import app.gridlauncher.ui.theme.Palette
import app.gridlauncher.widgets.WidgetController
import app.gridlauncher.widgets.WidgetGroup
import app.gridlauncher.widgets.WidgetOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerSheet(
    controller: WidgetController,
    onDismiss: () -> Unit,
    onPickBuiltIn: (WidgetKind) -> Unit,
    onPick: (AppWidgetProviderInfo) -> Unit,
) {
    val context = LocalContext.current
    val groups by produceState<List<WidgetGroup>?>(null) { value = controller.loadGroups(context) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = Palette.Surface,
        contentColor = Palette.Fg,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Palette.LineStrong),
            )
        },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Add a widget",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f).semantics { heading() },
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(LauncherIcons.Close, contentDescription = "Close", tint = Palette.Fg, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(
                        "Tap a widget to add it to the free-form area. You can move and resize it after.",
                        fontSize = 13.sp,
                        color = Palette.Muted,
                    )
                }
            }
            item(key = "builtin") {
                GroupSection("Grid Launcher", BUILT_INS.size) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(BUILT_INS, key = { it.kind.name }) { b ->
                            PickerCard(label = b.label, span = b.span, onClick = { onPickBuiltIn(b.kind) }) { b.preview() }
                        }
                    }
                }
            }
            val loaded = groups
            when {
                loaded == null -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Palette.Accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                }
                loaded.isEmpty() -> item(key = "empty") {
                    Text("No apps on this phone offer widgets.", fontSize = 13.sp, color = Palette.Muted)
                }
                else -> items(loaded, key = { "${it.packageName}#${it.user.hashCode()}" }) { group ->
                    GroupSection(group.title, group.providers.size) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(group.providers, key = { it.info.provider.flattenToShortString() }) { option ->
                                ProviderCard(controller, option) { onPick(option.info) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupSection(title: String, count: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.uppercase(),
                fontSize = 12.sp,
                letterSpacing = 0.08.em,
                color = Palette.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(if (count == 1) "1 widget" else "$count widgets", fontSize = 12.sp, color = Palette.Subtle)
        }
        content()
    }
}

@Composable
private fun ProviderCard(controller: WidgetController, option: WidgetOption, onClick: () -> Unit) {
    val context = LocalContext.current
    val maxPx = with(LocalDensity.current) { 220.dp.roundToPx() }
    val preview by produceState<ImageBitmap?>(null, option.info.provider) {
        value = controller.loadPreview(context, option.info, maxPx)
    }
    val span = "${WidgetGeometry.cellSpan(option.widthDp)}×${WidgetGeometry.cellSpan(option.heightDp)}"
    PickerCard(option.label, span, onClick) {
        val image = preview
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(88.dp))
        } else {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Line))
        }
    }
}

@Composable
private fun PickerCard(label: String, span: String, onClick: () -> Unit, preview: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .width(168.dp)
            .clip(shape)
            .border(1.dp, Palette.Line, shape)
            .background(Palette.Ground)
            .clickable(role = Role.Button, onClickLabel = "Add widget", onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.CenterStart) { preview() }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(span, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Palette.Muted)
        }
    }
}

private class BuiltIn(val kind: WidgetKind, val label: String, val span: String, val preview: @Composable () -> Unit)

@Composable
private fun PreviewLines(big: String, small: String, bigSize: Int = 28) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(big, fontSize = bigSize.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.03).em, maxLines = 1)
        Text(small, fontSize = 11.sp, color = Palette.Muted, maxLines = 1)
    }
}

@Composable
private fun PreviewBar(label: String, fraction: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 10.sp, color = Palette.Muted, modifier = Modifier.width(34.dp))
        Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Line)) {
            Box(Modifier.fillMaxWidth(fraction).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Accent))
        }
    }
}

private val BUILT_INS = listOf(
    BuiltIn(WidgetKind.CLOCK, "Clock & date", "4×2") { PreviewLines("10:24", "Thursday, 12 Nov", 30) },
    BuiltIn(WidgetKind.TEXT, "Text label", "Any") {
        Column {
            Text("Work", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em)
            Text("Name a screen, any font", fontSize = 11.sp, color = Palette.Muted, maxLines = 1)
        }
    },
    BuiltIn(WidgetKind.IMAGE, "Photo", "Any") { PreviewLines("Your photo", "Pick one; round, oval, arch, scallop or clover", 22) },
    BuiltIn(WidgetKind.APP, "App", "1×1") { PreviewLines("Any app", "Pin it among your widgets, quick actions too", 22) },
    BuiltIn(WidgetKind.AGENDA, "Up next", "5×2") { PreviewLines("Standup", "In 6 min · 10:30", 22) },
    BuiltIn(WidgetKind.CONTROLS, "Quick controls", "5×1") { PreviewLines("Torch · Wi-Fi", "Bluetooth and volume too", 20) },
    BuiltIn(WidgetKind.TIMER, "Quick timer", "5×1") { PreviewLines("1 · 5 · 10 · 25", "One-tap timers, in minutes", 20) },
    BuiltIn(WidgetKind.BATTERY, "Battery", "2×2") { PreviewLines("78%", "Charging · 31°C", 30) },
    BuiltIn(WidgetKind.NOTE, "Quick note", "5×2") { PreviewLines("Buy milk", "Tap to write, stays on the phone", 22) },
    BuiltIn(WidgetKind.PROGRESS, "Day progress", "5×2") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PreviewBar("Day", 0.43f)
            PreviewBar("Week", 0.52f)
            PreviewBar("Year", 0.87f)
        }
    },
)
