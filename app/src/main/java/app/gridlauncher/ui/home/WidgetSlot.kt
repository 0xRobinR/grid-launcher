package app.gridlauncher.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gridlauncher.data.PlacedWidget
import app.gridlauncher.ui.theme.LauncherIcons
import app.gridlauncher.ui.theme.LocalWidgetCorner
import app.gridlauncher.ui.theme.Palette
import app.gridlauncher.widgets.WidgetController

/**
 * A place for another app's widget, from a share code. Tap it to add the real widget here, at
 * this size, through Android's usual prompt; if the app isn't installed, it says so.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WidgetSlot(widget: PlacedWidget, controller: WidgetController, interactive: Boolean, onFill: () -> Unit, onLongPress: () -> Unit) {
    val installed = remember(widget.provider) { controller.findProvider(widget.provider) != null }
    val corner = LocalWidgetCorner.current
    val name = widget.label.ifBlank { widget.provider.substringBefore('/') }
    val accent = Palette.Accent
    val line = Palette.LineStrong
    Box(
        Modifier
            .fillMaxSize()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(corner))
            .drawBehind {
                drawRoundRect(
                    color = if (installed) accent else line,
                    cornerRadius = CornerRadius(corner.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))),
                )
            }
            .combinedClickable(
                enabled = interactive,
                role = Role.Button,
                onClickLabel = if (installed) "Add the $name widget" else null,
                onLongClick = onLongPress,
                onClick = onFill,
            )
            .semantics { contentDescription = if (installed) "Tap to add the $name widget" else "$name isn't installed" }
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(LauncherIcons.AddWidget, contentDescription = null, tint = if (installed) accent else Palette.Muted, modifier = Modifier.size(20.dp))
            Text(name, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(
                if (installed) "Tap to add this widget" else "App isn't installed",
                fontSize = 11.sp,
                color = Palette.Muted,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}
