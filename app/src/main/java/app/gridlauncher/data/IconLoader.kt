package app.gridlauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.gridlauncher.ui.theme.Palette
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An app icon ready to draw. [monochrome] icons are the app's themed glyph and sit on a tile;
 * the others are the app's full, already shaped icon.
 */
class AppIcon(val bitmap: ImageBitmap, val monochrome: Boolean)

class IconLoader(context: Context) {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val packageManager = appContext.packageManager
    private val densityDpi = appContext.resources.displayMetrics.densityDpi

    private val icons = object : LruCache<String, AppIcon>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: AppIcon) = value.bitmap.width * value.bitmap.height * 4
    }
    private val shortcutIcons = object : LruCache<String, ImageBitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /** Package of the icon pack in use, or empty. Changing it drops the cached icons. */
    @Volatile
    var iconPack: String = ""
        set(value) {
            if (field != value) {
                field = value
                icons.evictAll()
            }
        }

    private fun iconKey(app: AppEntry, themed: Boolean, sizePx: Int) = "${app.key}|$themed|$sizePx|$iconPack"

    fun cached(app: AppEntry, themed: Boolean, sizePx: Int): AppIcon? = icons.get(iconKey(app, themed, sizePx))

    suspend fun load(app: AppEntry, themed: Boolean, sizePx: Int): AppIcon? = withContext(Dispatchers.IO) {
        val key = iconKey(app, themed, sizePx)
        icons.get(key)?.let { return@withContext it }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component)
        val info = runCatching { launcherApps.resolveActivity(intent, app.user) }.getOrNull()
        // An icon pack, when it draws this app, wins over the app's own icon.
        val packIcon = iconPack.takeIf { it.isNotEmpty() }?.let { IconPacks.icon(appContext, it, app.component, densityDpi) }
        if (packIcon != null) {
            val icon = AppIcon(render(if (app.isPrimaryUser) packIcon else packageManager.getUserBadgedIcon(packIcon, app.user), sizePx), monochrome = false)
            icons.put(key, icon)
            return@withContext icon
        }
        val drawable = info?.let { runCatching { it.getIcon(densityDpi) }.getOrNull() }
            ?: packageManager.defaultActivityIcon
        val mono = monochromeOf(drawable, themed)
        val icon = if (mono != null) {
            // Adaptive icon layers are drawn 25% larger than the icon on every side; match that.
            val glyph = render(mono, sizePx, overflow = 0.25f)
            AppIcon(if (app.isPrimaryUser) glyph else badge(glyph, app, sizePx), monochrome = true)
        } else {
            val full = if (app.isPrimaryUser) drawable else packageManager.getUserBadgedIcon(drawable, app.user)
            AppIcon(render(full, sizePx), monochrome = false)
        }
        icons.put(key, icon)
        icon
    }

    private fun monochromeOf(drawable: Drawable, themed: Boolean): Drawable? {
        if (!themed || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val adaptive = drawable as? AdaptiveIconDrawable ?: return null
        val mono = adaptive.monochrome ?: return null
        val copy = mono.constantState?.newDrawable(appContext.resources)?.mutate() ?: mono.mutate()
        copy.colorFilter = PorterDuffColorFilter(Palette.Fg.toArgb(), PorterDuff.Mode.SRC_IN)
        return copy
    }

    suspend fun loadShortcutIcon(shortcut: ShortcutInfo, sizePx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = "${shortcut.`package`}/${shortcut.id}#${shortcut.userHandle.hashCode()}|$sizePx|${shortcut.lastChangedTimestamp}"
        shortcutIcons.get(key)?.let { return@withContext it }
        val drawable = runCatching { launcherApps.getShortcutIconDrawable(shortcut, densityDpi) }.getOrNull()
            ?: return@withContext null
        val bitmap = render(drawable, sizePx)
        shortcutIcons.put(key, bitmap)
        bitmap
    }

    /** Drops cached icons for a package after it is updated or removed. */
    fun invalidate(packageName: String) {
        val prefix = "$packageName/"
        icons.snapshot().keys.filter { it.startsWith(prefix) }.forEach { icons.remove(it) }
        shortcutIcons.snapshot().keys.filter { it.startsWith(prefix) }.forEach { shortcutIcons.remove(it) }
    }

    private fun badge(glyph: ImageBitmap, app: AppEntry, sizePx: Int): ImageBitmap {
        val plain = glyph.asAndroidBitmap().toDrawable(appContext.resources)
        return render(packageManager.getUserBadgedIcon(plain, app.user), sizePx)
    }

    /** Draws [drawable] into a square; [overflow] extends it past each edge by that share of the size. */
    private fun render(drawable: Drawable, sizePx: Int, overflow: Float = 0f): ImageBitmap {
        val size = sizePx.coerceAtLeast(1)
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val old = drawable.copyBounds()
        val extra = (size * overflow).toInt()
        drawable.setBounds(-extra, -extra, size + extra, size + extra)
        drawable.draw(canvas)
        drawable.bounds = old
        return bitmap.asImageBitmap()
    }
}
