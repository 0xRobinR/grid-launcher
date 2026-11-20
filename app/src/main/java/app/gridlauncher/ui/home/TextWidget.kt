package app.gridlauncher.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.gridlauncher.data.TextAlignment
import app.gridlauncher.data.TextSpec
import app.gridlauncher.data.TextTone
import app.gridlauncher.ui.components.SegmentedRow
import app.gridlauncher.ui.theme.FontCatalog
import app.gridlauncher.ui.theme.Palette

fun TextTone.color(): Color = when (this) {
    TextTone.PRIMARY -> Palette.Fg
    TextTone.MUTED -> Palette.Muted
    TextTone.ACCENT -> Palette.Accent
}

private fun TextAlignment.textAlign(): TextAlign = when (this) {
    TextAlignment.START -> TextAlign.Start
    TextAlignment.CENTER -> TextAlign.Center
    TextAlignment.END -> TextAlign.End
}

private fun TextAlignment.boxAlign(): Alignment = when (this) {
    TextAlignment.START -> Alignment.CenterStart
    TextAlignment.CENTER -> Alignment.Center
    TextAlignment.END -> Alignment.CenterEnd
}

/** The text label widget: the text grows or shrinks to fill its frame. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TextLabelWidget(
    spec: TextSpec,
    interactive: Boolean,
    /** The height in dp the text really takes inside its frame, after it has sized itself. */
    onTextHeight: (Float) -> Unit = {},
    onLongPress: () -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val appFamily = LocalTextStyle.current.fontFamily ?: FontFamily.Default
    val family = remember(spec.font, appFamily) { if (spec.font.isBlank()) appFamily else FontCatalog.family(spec.font) }
    Box(
        Modifier
            .fillMaxSize()
            .combinedClickable(
                enabled = interactive,
                interactionSource = null,
                indication = null,
                onLongClick = onLongPress,
                onClick = {},
            )
            .semantics { contentDescription = spec.text },
        contentAlignment = spec.align.boxAlign(),
    ) {
        BasicText(
            text = spec.text.ifBlank { " " },
            style = TextStyle(
                color = spec.tone.color(),
                fontFamily = family,
                fontWeight = FontWeight(spec.weight.coerceIn(100, 900)),
                textAlign = spec.align.textAlign(),
                lineHeight = 1.1.em,
                letterSpacing = (-0.01).em,
            ),
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 320.sp, stepSize = 1.sp),
            // One line per line the user typed: a word too wide for the frame shrinks the text
            // rather than being broken across lines.
            softWrap = false,
            maxLines = spec.text.ifBlank { " " }.lines().size,
            onTextLayout = { layout -> onTextHeight(with(density) { layout.size.height.toDp().value }) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Edits a text label: words, font, weight, alignment and color, with a live preview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorSheet(initial: TextSpec, onDismiss: () -> Unit, onSave: (TextSpec) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.text) }
    var font by rememberSaveable { mutableStateOf(initial.font) }
    var weight by rememberSaveable { mutableIntStateOf(initial.weight) }
    var align by rememberSaveable { mutableStateOf(initial.align) }
    var tone by rememberSaveable { mutableStateOf(initial.tone) }
    var customFont by rememberSaveable {
        mutableStateOf(if (initial.font.isNotBlank() && initial.font !in FontCatalog.families) initial.font else "")
    }
    val spec = TextSpec(text, font, weight, align, tone)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = Palette.Surface,
        contentColor = Palette.Fg,
        scrimColor = Color.Black.copy(alpha = 0.55f),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Text label", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.Muted) }
                TextButton(onClick = { onSave(spec.copy(text = text.trim().ifEmpty { "Label" })) }) {
                    Text("Save", color = Palette.Accent, fontWeight = FontWeight.SemiBold)
                }
            }
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Palette.Ground)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                TextLabelWidget(spec, interactive = false, onLongPress = {})
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(120) },
                label = { Text("Text") },
                maxLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors(),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            )

            Text("Font", fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.selectableGroup(),
            ) {
                item(key = "app") { FontChip("App font", FontFamily.Default, font.isBlank()) { font = "" } }
                items(FontCatalog.families, key = { it }) { name ->
                    val family = remember(name) { FontCatalog.family(name) }
                    FontChip(name, family, font == name) { font = name }
                }
            }
            Row(
                Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val apply = { if (customFont.isNotBlank()) font = customFont.trim() }
                OutlinedTextField(
                    value = customFont,
                    onValueChange = { customFont = it },
                    singleLine = true,
                    label = { Text("Any Google Fonts family") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { apply() }),
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors(),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = apply, enabled = customFont.isNotBlank()) {
                    Text("Use", color = if (customFont.isNotBlank()) Palette.Accent else Palette.Subtle)
                }
            }

            SegmentedRow(
                title = "Weight",
                hint = null,
                options = listOf(300 to "Light", 400 to "Regular", 500 to "Medium", 700 to "Bold"),
                selected = weight,
                onSelect = { weight = it },
            )
            SegmentedRow(
                title = "Alignment",
                hint = null,
                options = listOf(TextAlignment.START to "Left", TextAlignment.CENTER to "Center", TextAlignment.END to "Right"),
                selected = align,
                onSelect = { align = it },
            )
            Text("Color", fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp))
            Row(
                Modifier.padding(horizontal = 16.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                listOf(TextTone.PRIMARY to "White", TextTone.MUTED to "Grey", TextTone.ACCENT to "Blue").forEach { (value, label) ->
                    val selected = value == tone
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(if (selected) 3.dp else 1.dp, if (selected) Palette.Accent else Palette.Line, CircleShape)
                            .selectable(selected = selected, role = Role.RadioButton) { tone = value }
                            .semantics { contentDescription = label }
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(value.color()),
                    )
                }
            }
        }
    }
}

@Composable
private fun FontChip(label: String, family: FontFamily, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(if (selected) Palette.Accent else Palette.Ground)
            .border(1.dp, if (selected) Palette.Accent else Palette.Line, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 15.sp, fontFamily = family, color = if (selected) Palette.Ground else Palette.Fg, maxLines = 1)
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Accent,
    unfocusedBorderColor = Palette.Line,
    focusedLabelColor = Palette.Accent,
    unfocusedLabelColor = Palette.Muted,
    cursorColor = Palette.Accent,
)
