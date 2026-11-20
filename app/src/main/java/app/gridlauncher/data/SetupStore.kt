package app.gridlauncher.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** A setup saved on this phone: the whole launcher, or one screen, under a name. */
data class SavedSetup(
    val id: String,
    val name: String,
    val kind: Kind,
    val savedAt: Long,
    /** How many screens it holds. */
    val screens: Int,
) {
    enum class Kind { LAUNCHER, SCREEN }
}

/**
 * Saved setups, one small JSON file each in the app's own files. Unlike share codes they're
 * complete: a launcher setup keeps hidden and locked apps, usage, photos and other apps'
 * widgets, as it never leaves the phone.
 */
object SetupStore {
    private const val TAG = "SetupStore"
    private const val DIR = "setups"

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    suspend fun list(context: Context): List<SavedSetup> = withContext(Dispatchers.IO) {
        dir(context).listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { f ->
            runCatching {
                val o = JSONObject(f.readText())
                SavedSetup(
                    id = f.nameWithoutExtension,
                    name = o.optString("name", "Setup"),
                    kind = SavedSetup.Kind.entries.firstOrNull { it.name == o.optString("kind") } ?: SavedSetup.Kind.LAUNCHER,
                    savedAt = o.optLong("savedAt", f.lastModified()),
                    screens = o.optInt("screens", 1),
                )
            }.onFailure { Log.w(TAG, "Skipping unreadable setup ${f.name}", it) }.getOrNull()
        }.sortedByDescending { it.savedAt }
    }

    suspend fun saveLauncher(context: Context, id: String, name: String, s: LauncherSettings) =
        write(context, id, name, SavedSetup.Kind.LAUNCHER, s.pages.size, Backup.encode(s))

    suspend fun saveScreen(context: Context, id: String, name: String, page: LauncherPage) =
        write(context, id, name, SavedSetup.Kind.SCREEN, 1, Codec.encodePages(listOf(page)))

    private suspend fun write(context: Context, id: String, name: String, kind: SavedSetup.Kind, screens: Int, data: String) =
        withContext(Dispatchers.IO) {
            val o = JSONObject()
                .put("name", name)
                .put("kind", kind.name)
                .put("savedAt", System.currentTimeMillis())
                .put("screens", screens)
                .put("data", data)
            // Written aside, then moved into place, so a crash can't leave half a file.
            val tmp = File(dir(context), "$id.tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(File(dir(context), "$id.json"))
        }

    /** A saved launcher's settings, or null. */
    suspend fun readLauncher(context: Context, id: String): LauncherSettings? = withContext(Dispatchers.IO) {
        data(context, id)?.let(Backup::decode)
    }

    /** A saved screen, or null. */
    suspend fun readScreen(context: Context, id: String): LauncherPage? = withContext(Dispatchers.IO) {
        data(context, id)?.let { Codec.decodePages(it).firstOrNull() }
    }

    private fun data(context: Context, id: String): String? =
        runCatching { JSONObject(File(dir(context), "$id.json").readText()).optString("data").takeIf { it.isNotEmpty() } }.getOrNull()

    suspend fun rename(context: Context, id: String, name: String) = withContext(Dispatchers.IO) {
        val f = File(dir(context), "$id.json")
        runCatching {
            val o = JSONObject(f.readText()).put("name", name)
            val tmp = File(dir(context), "$id.tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(f)
        }
    }

    suspend fun delete(context: Context, id: String) = withContext(Dispatchers.IO) { File(dir(context), "$id.json").delete() }

    /** Photo files that saved setups still show, so cleaning up never deletes them. */
    suspend fun photosInUse(context: Context): Set<String> =
        widgetsInSetups(context).mapNotNull { it.image.takeIf(String::isNotEmpty) }.toSet()

    /** Other apps' widget ids that saved setups still hold, so they stay set up for when one is applied. */
    suspend fun widgetIdsInUse(context: Context): Set<Int> =
        widgetsInSetups(context).filter { it.kind == WidgetKind.APP_WIDGET }.map { it.appWidgetId }.toSet()

    private suspend fun widgetsInSetups(context: Context): List<PlacedWidget> = withContext(Dispatchers.IO) {
        dir(context).listFiles { f -> f.name.endsWith(".json") }.orEmpty().flatMap { f ->
            runCatching {
                val o = JSONObject(f.readText())
                val data = o.optString("data")
                val pages = if (o.optString("kind") == SavedSetup.Kind.SCREEN.name) Codec.decodePages(data) else Backup.decode(data)?.pages.orEmpty()
                pages.flatMap { it.widgets }
            }.getOrDefault(emptyList())
        }
    }
}
