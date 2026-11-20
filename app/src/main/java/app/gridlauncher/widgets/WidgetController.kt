package app.gridlauncher.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.view.ViewGroup
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import kotlin.math.max
import kotlin.math.roundToInt

/** All widgets one app offers, for the "Add a widget" sheet. */
class WidgetGroup(
    val title: String,
    val packageName: String,
    val user: UserHandle,
    val providers: List<WidgetOption>,
)

class WidgetOption(
    val info: AppWidgetProviderInfo,
    val label: String,
    val widthDp: Float,
    val heightDp: Float,
)

/** Requests that need the activity: binding permission and widget configuration screens. */
interface WidgetRequests {
    fun requestAdd(info: AppWidgetProviderInfo)
}

/**
 * Owns the [LauncherWidgetHostView]s of one activity. Views are created once per widget id and
 * reused, so scrolling the home screen does not rebuild them.
 */
class WidgetController(
    private val activity: Activity,
    private val host: LauncherWidgetHost,
    private val requests: WidgetRequests,
) {
    private val manager = AppWidgetManager.getInstance(activity)
    private val views = HashMap<Int, LauncherWidgetHostView>()
    private val density = activity.resources.displayMetrics.density

    fun providerInfo(appWidgetId: Int): AppWidgetProviderInfo? =
        runCatching { manager.getAppWidgetInfo(appWidgetId) }.getOrNull()

    fun hostView(appWidgetId: Int): LauncherWidgetHostView? {
        views[appWidgetId]?.let { return it }
        val info = providerInfo(appWidgetId) ?: return null
        val view = runCatching { host.createView(activity, appWidgetId, info) as LauncherWidgetHostView }.getOrNull()
            ?: return null
        views[appWidgetId] = view
        return view
    }

    fun requestAdd(info: AppWidgetProviderInfo) = requests.requestAdd(info)

    /** The installed widget provider with this flattened component, or null if its app isn't installed. */
    fun findProvider(flat: String): AppWidgetProviderInfo? {
        val component = android.content.ComponentName.unflattenFromString(flat) ?: return null
        return runCatching { manager.getInstalledProvidersForProfile(Process.myUserHandle()) }.getOrNull()
            ?.firstOrNull { it.provider == component }
    }

    fun delete(appWidgetId: Int) {
        views.remove(appWidgetId)?.let { (it.parent as? ViewGroup)?.removeView(it) }
        runCatching { host.deleteAppWidgetId(appWidgetId) }
    }

    /** Frees widget ids the system still holds for us but that no longer appear on the home screen. */
    fun deleteOrphans(keep: Set<Int>) {
        val held = runCatching { host.appWidgetIds }.getOrNull() ?: return
        held.filter { it !in keep }.forEach { delete(it) }
    }

    fun defaultSizeDp(info: AppWidgetProviderInfo): Pair<Float, Float> {
        val w = max(info.minWidth / density, 110f)
        val h = max(info.minHeight / density, 56f)
        return w to h
    }

    fun minSizeDp(info: AppWidgetProviderInfo): Pair<Float, Float> {
        val w = (if (info.minResizeWidth > 0) info.minResizeWidth else info.minWidth) / density
        val h = (if (info.minResizeHeight > 0) info.minResizeHeight else info.minHeight) / density
        return w to h
    }

    suspend fun loadGroups(context: Context): List<WidgetGroup> = withContext(Dispatchers.IO) {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val pm = context.packageManager
        val me = Process.myUserHandle()
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        val profiles = runCatching { launcherApps.profiles }.getOrNull().orEmpty().ifEmpty { listOf(me) }
        profiles.flatMap { user ->
            val providers = runCatching { manager.getInstalledProvidersForProfile(user) }.getOrNull().orEmpty()
                .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
            providers.groupBy { it.provider.packageName }.map { (pkg, infos) ->
                val appInfo = runCatching { launcherApps.getApplicationInfo(pkg, 0, user) }.getOrNull()
                val appLabel = appInfo?.loadLabel(pm)?.toString() ?: pkg
                val title = if (user == me) appLabel else pm.getUserBadgedLabel(appLabel, user).toString()
                val options = infos.map { info ->
                    val (w, h) = defaultSizeDp(info)
                    WidgetOption(info, info.loadLabel(pm) ?: appLabel, w, h)
                }.sortedWith { a, b -> collator.compare(a.label, b.label) }
                WidgetGroup(title, pkg, user, options)
            }
        }.sortedWith { a, b -> collator.compare(a.title, b.title) }
    }

    suspend fun loadPreview(context: Context, info: AppWidgetProviderInfo, maxPx: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val dpi = context.resources.displayMetrics.densityDpi
            val drawable = runCatching { info.loadPreviewImage(context, dpi) }.getOrNull()
                ?: runCatching { info.loadIcon(context, dpi) }.getOrNull()
                ?: return@withContext null
            render(drawable, maxPx)
        }

    private fun render(drawable: Drawable, maxPx: Int): ImageBitmap {
        val iw = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxPx
        val ih = drawable.intrinsicHeight.takeIf { it > 0 } ?: maxPx
        val scale = minOf(1f, maxPx.toFloat() / max(iw, ih))
        val w = max(1, (iw * scale).roundToInt())
        val h = max(1, (ih * scale).roundToInt())
        val bitmap = createBitmap(w, h)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(Canvas(bitmap))
        return bitmap.asImageBitmap()
    }
}
