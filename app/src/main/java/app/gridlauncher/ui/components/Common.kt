package app.gridlauncher.ui.components

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gridlauncher.data.AppEntry
import app.gridlauncher.data.AppIcon
import app.gridlauncher.ui.LauncherViewModel
import app.gridlauncher.ui.theme.Palette
import kotlin.math.roundToInt

/** Remembers where an element sits on screen so an app can open from it with a scale-up animation. */
class LaunchSource {
    var bounds: androidx.compose.ui.geometry.Rect? = null
}

fun Modifier.trackLaunchSource(source: LaunchSource): Modifier =
    onGloballyPositioned { source.bounds = it.boundsInWindow() }

class LaunchArgs(val sourceBounds: Rect?, val options: Bundle?)

fun launchArgs(view: View, source: LaunchSource): LaunchArgs {
    val b = source.bounds ?: return LaunchArgs(null, null)
    val inWindow = IntArray(2).also(view::getLocationInWindow)
    val onScreen = IntArray(2).also(view::getLocationOnScreen)
    val left = b.left.roundToInt() - inWindow[0]
    val top = b.top.roundToInt() - inWindow[1]
    val width = b.width.roundToInt().coerceAtLeast(1)
    val height = b.height.roundToInt().coerceAtLeast(1)
    val options = ActivityOptions.makeScaleUpAnimation(view, left, top, width, height).toBundle()
    val screenLeft = left + onScreen[0]
    val screenTop = top + onScreen[1]
    return LaunchArgs(Rect(screenLeft, screenTop, screenLeft + width, screenTop + height), options)
}

fun Context.startSafely(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}

@Composable
fun rememberAppIcon(vm: LauncherViewModel, app: AppEntry, themed: Boolean, size: Dp, version: Int): AppIcon? {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState(vm.icons.cached(app, themed, px), app.key, themed, px, version) {
        value = vm.icons.load(app, themed, px)
    }
    return icon
}

@Composable
fun rememberShortcuts(
    vm: LauncherViewModel,
    app: AppEntry,
    enabled: Boolean,
    permission: Boolean,
    version: Int,
): List<ShortcutInfo> {
    val shortcuts by produceState(vm.cachedShortcuts(app).orEmpty(), app.key, enabled, permission, version) {
        value = if (enabled && permission) vm.shortcuts(app) else emptyList()
    }
    return shortcuts
}

@Composable
fun rememberShortcutIcon(vm: LauncherViewModel, shortcut: ShortcutInfo, size: Dp): ImageBitmap? {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, shortcut.id, shortcut.`package`, shortcut.lastChangedTimestamp, px) {
        value = vm.icons.loadShortcutIcon(shortcut, px)
    }
    return icon
}

private val Grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** A shortcut's own icon, desaturated so it matches the monochrome grid. */
@Composable
fun ShortcutGlyph(vm: LauncherViewModel, shortcut: ShortcutInfo, size: Dp, modifier: Modifier = Modifier) {
    val icon = rememberShortcutIcon(vm, shortcut, size)
    if (icon != null) {
        Image(icon, contentDescription = null, colorFilter = Grayscale, modifier = modifier.size(size))
    } else {
        Box(modifier.size(size))
    }
}

/** An app icon: themed glyphs sit on a dark tile, full-colour icons draw as they are. */
@Composable
fun AppTile(icon: AppIcon?, size: Dp, modifier: Modifier = Modifier) {
    val corner = RoundedCornerShape(size * 0.33f)
    when {
        icon == null -> Box(modifier.size(size).clip(corner).background(Palette.Surface2))
        icon.monochrome -> Box(
            modifier.size(size).clip(corner).background(Palette.Surface2),
            contentAlignment = Alignment.Center,
        ) {
            Image(icon.bitmap, contentDescription = null, modifier = Modifier.size(size))
        }
        else -> Image(icon.bitmap, contentDescription = null, modifier = modifier.size(size))
    }
}
