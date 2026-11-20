package app.gridlauncher.data

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ConcurrentHashMap

/**
 * Icon packs in the common launcher format: an app that lists its icons in appfilter.xml,
 * matching each app's component to a drawable in the pack.
 */
// Icon packs are other apps whose icons can only be found by name, so resource lookups by name
// (getIdentifier) are the one way to read them.
@SuppressLint("DiscouragedApi")
object IconPacks {
    private val PACK_ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme")
    private val maps = ConcurrentHashMap<String, Map<String, String>>()

    class Pack(val packageName: String, val label: String)

    fun installed(context: Context): List<Pack> {
        val pm = context.packageManager
        return PACK_ACTIONS
            .flatMap { action -> runCatching { pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA) }.getOrDefault(emptyList()) }
            .map { it.activityInfo.packageName }
            .distinct()
            .map { pkg -> Pack(pkg, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)) }
            .sortedBy { it.label.lowercase() }
    }

    /** Component (flattened, full class name) to drawable name, read once per pack. */
    private fun mapOf(context: Context, pkg: String): Map<String, String> {
        maps[pkg]?.let { return it }
        val result = HashMap<String, String>()
        val ok = runCatching {
            val res = context.packageManager.getResourcesForApplication(pkg)
            val xmlId = res.getIdentifier("appfilter", "xml", pkg)
            val parser: XmlPullParser = if (xmlId != 0) {
                res.getXml(xmlId)
            } else {
                Xml.newPullParser().apply { setInput(res.assets.open("appfilter.xml"), null) }
            }
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    val inner = component?.substringAfter("ComponentInfo{", "")?.substringBefore("}")
                    val name = inner?.let(ComponentName::unflattenFromString)?.flattenToString()
                    if (name != null && !drawable.isNullOrBlank()) result.putIfAbsent(name, drawable)
                }
                event = parser.next()
            }
        }.isSuccess
        // A pack that failed to read (not installed yet, or mid-update) is tried again next time.
        if (ok) maps[pkg] = result
        return result
    }

    /** The pack's icon for [component], or null if the pack doesn't draw that app. */
    fun icon(context: Context, pkg: String, component: ComponentName, densityDpi: Int): Drawable? {
        val name = mapOf(context, pkg)[component.flattenToString()] ?: return null
        return runCatching {
            val res = context.packageManager.getResourcesForApplication(pkg)
            val id = res.getIdentifier(name, "drawable", pkg)
            if (id == 0) null else res.getDrawableForDensity(id, densityDpi, null)
        }.getOrNull()
    }

    fun forget(pkg: String) {
        maps.remove(pkg)
    }
}
