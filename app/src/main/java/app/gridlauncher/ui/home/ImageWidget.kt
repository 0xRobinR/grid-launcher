package app.gridlauncher.ui.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gridlauncher.data.ImageShape
import app.gridlauncher.data.ImageStore
import app.gridlauncher.ui.theme.LauncherIcons
import app.gridlauncher.ui.theme.LocalWidgetCorner
import app.gridlauncher.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A photo the user picked, filling its frame, cut to [shape]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageWidget(path: String, shape: ImageShape, interactive: Boolean, onLongPress: () -> Unit) {
    val context = LocalContext.current
    // Decoded off the main thread; a new path (a changed photo) loads afresh.
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = if (path.isEmpty()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(ImageStore.file(context, path).path)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    val outline = rememberShapeFor(shape)
    Box(
        Modifier
            .fillMaxSize()
            .clip(outline)
            .background(Palette.Surface)
            .combinedClickable(enabled = interactive, interactionSource = null, indication = null, onLongClick = onLongPress, onClick = {})
            .semantics { contentDescription = "Photo" },
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            // No photo yet, or its file is gone (say, after restoring a backup on a new phone).
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(12.dp)) {
                Icon(LauncherIcons.Wallpaper, contentDescription = null, tint = Palette.Muted, modifier = Modifier.size(24.dp))
                Text(
                    if (path.isEmpty()) "Choose a photo" else "Photo missing. Edit the screen and tap Change photo.",
                    fontSize = 11.sp,
                    color = Palette.Muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The Compose shape for an outline; [corner] is the launcher's widget corner radius. */
fun ImageShape.toShape(corner: Dp): Shape = when (this) {
    ImageShape.ROUNDED -> RoundedCornerShape(corner)
    ImageShape.OVAL -> PathShape { size -> Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)) } }
    // Fully round across the top, gently rounded at the bottom: like a window arch.
    ImageShape.ARCH -> PathShape { size ->
        val r = minOf(size.width / 2f, size.height)
        val b = minOf(size.width, size.height) * 0.08f
        Path().apply {
            moveTo(0f, r)
            arcTo(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, r * 2f), 180f, 180f, false)
            lineTo(size.width, size.height - b)
            quadraticTo(size.width, size.height, size.width - b, size.height)
            lineTo(b, size.height)
            quadraticTo(0f, size.height, 0f, size.height - b)
            close()
        }
    }
    ImageShape.SCALLOP -> PathShape { size -> wavy(size, waves = 12, depth = 0.045f) }
    ImageShape.CLOVER -> PathShape { size -> wavy(size, waves = 4, depth = 0.11f) }
}

/** An ellipse whose edge rises and falls [waves] times around, by [depth] of its radius. */
private fun wavy(size: Size, waves: Int, depth: Float): Path {
    val cx = size.width / 2f
    val cy = size.height / 2f
    // Scaled so the bumps' peaks touch the frame's edges.
    val rx = cx / (1f + depth)
    val ry = cy / (1f + depth)
    val steps = 180
    return Path().apply {
        for (i in 0..steps) {
            val a = 2.0 * PI * i / steps
            // A squarer base for few waves, so a clover fills its frame like the original's corners.
            val k = 1f + depth * cos(waves * a).toFloat()
            val x = cx + rx * k * cos(a).toFloat()
            val y = cy + ry * k * sin(a).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

private class PathShape(private val build: (Size) -> Path) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) = Outline.Generic(build(size))
}

@Composable
internal fun rememberShapeFor(shape: ImageShape): Shape {
    val corner = LocalWidgetCorner.current
    return remember(shape, corner) { shape.toShape(corner) }
}
