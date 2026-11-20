package app.gridlauncher.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import app.gridlauncher.data.NavStyle
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gridlauncher.ui.theme.LauncherIcons
import app.gridlauncher.ui.theme.Palette

class NavItem(val icon: ImageVector, val label: String)

/**
 * The floating bar that switches screens, in the chosen [style]. [progress] is the pager position
 * (page plus swipe fraction), read only while drawing so sliding indicators follow the finger.
 * [compact] draws a smaller copy for the style preview in Settings.
 */
@Composable
fun ScreenNavBar(
    items: List<NavItem>,
    current: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: NavStyle = NavStyle.PILL,
    progress: () -> Float = { current.toFloat() },
    compact: Boolean = false,
) {
    val shell = modifier
        .widthIn(max = 380.dp)
        .clip(CircleShape)
        .background(Palette.Surface3.copy(alpha = 0.94f))
        .border(1.dp, Palette.Line, CircleShape)
    when (style) {
        NavStyle.PILL, NavStyle.ICONS, NavStyle.LABELS -> TabBar(items, current, onSelect, modifier = shell, style = style, compact = compact)
        NavStyle.DOTS -> DotBar(items, current, onSelect, modifier = shell, compact = compact)
        NavStyle.SNAKE -> SnakeBar(items, onSelect, modifier = shell, progress = progress, compact = compact)
        NavStyle.LINE -> LineBar(items, onSelect, modifier = shell, progress = progress, compact = compact)
        NavStyle.OFF -> Unit
    }
}

/** Pill, icons-only and label tabs. */
@Composable
private fun TabBar(items: List<NavItem>, current: Int, onSelect: (Int) -> Unit, modifier: Modifier, style: NavStyle, compact: Boolean) {
    val tab = if (compact) 30.dp else 44.dp
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(if (compact) 3.dp else 5.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val active = index == current
            val showIcon = style != NavStyle.LABELS
            val showLabel = style == NavStyle.LABELS || (style == NavStyle.PILL && active)
            val fg = if (active) Palette.Ground else Palette.Muted
            Row(
                Modifier
                    .height(tab)
                    .then(if (style == NavStyle.ICONS) Modifier.widthIn(min = tab) else Modifier)
                    .clip(CircleShape)
                    .background(if (active) Palette.Accent else Palette.Surface3.copy(alpha = 0f))
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) })
                    .semantics { contentDescription = item.label }
                    .animateContentSize()
                    .padding(
                        horizontal = when {
                            style == NavStyle.ICONS -> 0.dp
                            compact -> 9.dp
                            showLabel -> 14.dp
                            else -> 11.dp
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                if (showIcon) {
                    Icon(item.icon, contentDescription = null, tint = fg, modifier = Modifier.size(if (compact) 15.dp else 20.dp))
                }
                if (showLabel) {
                    Text(
                        item.label,
                        fontSize = if (compact) 10.sp else 13.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (active) Palette.Ground else Palette.Fg,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Small dots; the one in view stretches into a wide accent capsule. */
@Composable
private fun DotBar(items: List<NavItem>, current: Int, onSelect: (Int) -> Unit, modifier: Modifier, compact: Boolean) {
    val dot = if (compact) 6.dp else 8.dp
    Row(
        modifier.padding(horizontal = if (compact) 6.dp else 10.dp).selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val active = index == current
            val width by animateDpAsState(if (active) dot * 3 else dot, label = "dot")
            Box(
                Modifier
                    .size(width = width + (if (compact) 8.dp else 16.dp), height = if (compact) 24.dp else 40.dp)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) })
                    .semantics { contentDescription = item.label },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = width, height = dot).clip(CircleShape).background(if (active) Palette.Accent else Palette.Subtle))
            }
        }
    }
}

/**
 * Dots with an accent body that stretches from one dot to the next as you swipe: the front races
 * ahead in the first half of the swipe, the back catches up in the second.
 */
@Composable
private fun SnakeBar(items: List<NavItem>, onSelect: (Int) -> Unit, modifier: Modifier, progress: () -> Float, compact: Boolean) {
    val dot = if (compact) 7.dp else 9.dp
    val slot = if (compact) 18.dp else 30.dp
    Row(
        modifier
            .padding(horizontal = if (compact) 6.dp else 10.dp)
            .drawBehind {
                val slotPx = slot.toPx()
                val d = dot.toPx()
                val left = 0f // drawn inside the padding
                val p = progress()
                val i = p.toInt().coerceAtMost(items.size - 1)
                val f = p - i
                val head = (f * 2f).coerceAtMost(1f)
                val tail = ((f - 0.5f) * 2f).coerceAtLeast(0f)
                val start = left + (i + tail) * slotPx + (slotPx - d) / 2f
                val end = left + (i + head) * slotPx + (slotPx + d) / 2f
                drawRoundRect(
                    color = Palette.Accent,
                    topLeft = Offset(start, (size.height - d) / 2f),
                    size = Size(end - start, d),
                    cornerRadius = CornerRadius(d / 2f),
                )
            }
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            Box(
                Modifier
                    .size(width = slot, height = if (compact) 24.dp else 40.dp)
                    .selectable(selected = index == progress().roundToInt(), role = Role.Tab, onClick = { onSelect(index) })
                    .semantics { contentDescription = item.label },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(dot).border(1.5.dp, Palette.Subtle, CircleShape))
            }
        }
    }
}

/** A row of thin segments with an accent marker that slides along under your swipe. */
@Composable
private fun LineBar(items: List<NavItem>, onSelect: (Int) -> Unit, modifier: Modifier, progress: () -> Float, compact: Boolean) {
    val seg = if (compact) 20.dp else 30.dp
    val gap = if (compact) 4.dp else 6.dp
    val thick = if (compact) 3.dp else 4.dp
    Row(
        modifier
            .padding(horizontal = if (compact) 9.dp else 14.dp)
            .drawBehind {
                val left = 0f // drawn inside the padding
                val step = (seg + gap).toPx()
                val t = thick.toPx()
                drawRoundRect(
                    color = Palette.Accent,
                    topLeft = Offset(left + progress() * step, (size.height - t) / 2f),
                    size = Size(seg.toPx(), t),
                    cornerRadius = CornerRadius(t / 2f),
                )
            }
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            Box(
                Modifier
                    .size(width = seg, height = if (compact) 24.dp else 40.dp)
                    .selectable(selected = index == progress().roundToInt(), role = Role.Tab, onClick = { onSelect(index) })
                    .semantics { contentDescription = item.label },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = seg, height = thick).clip(CircleShape).background(Palette.Line))
            }
        }
    }
}

/** The page past the last screen: one big button that adds a new screen. */
@Composable
fun AddScreenPage(count: Int, max: Int, onAdd: () -> Unit) {
    // One screen per tap: ignore further taps until the new screen exists.
    var adding by remember(count) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(
                Modifier
                    .size(132.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .drawBehind {
                        drawRoundRect(
                            color = Palette.Accent,
                            cornerRadius = CornerRadius(40.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                        )
                    }
                    .clickable(role = Role.Button, onClickLabel = "Add a screen") {
                        if (!adding) {
                            adding = true
                            onAdd()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(LauncherIcons.Plus, contentDescription = "Add a screen", tint = Palette.Accent, modifier = Modifier.size(44.dp))
            }
            Text("Add a screen", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "$count of $max screens. A new screen starts empty: add widgets, a label and its own apps.",
                fontSize = 13.sp,
                color = Palette.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp),
            )
        }
    }
}
