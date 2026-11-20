package app.gridlauncher.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Share codes: a launcher's settings and screens, or a single screen, as one line of text anyone
 * can paste into their own launcher. The JSON is compressed and written in URL-safe Base64 after
 * a "GL1:" tag; plain JSON is accepted on import too.
 *
 * A code carries no personal data: no hidden or locked apps, no usage counts or recent apps, no
 * photos, no Wi-Fi names, and no other apps' widgets (those only exist on the phone that made
 * them). Apps are named by component without the user profile, and matched to the importing
 * phone's apps when read.
 */
object ShareCode {
    private const val TAG = "GL1:"
    private const val SCREENS_FORMAT = "grid-launcher-screens"

    /** What a code holds once read. */
    sealed interface Shared {
        /** A whole launcher: settings and every screen. */
        data class Launcher(val settings: LauncherSettings) : Shared

        /** One or more screens, to add alongside the user's own. */
        data class Screens(val pages: List<LauncherPage>) : Shared
    }

    /**
     * Which widget another app's widget id shows, as (provider component, label), or null if it's
     * gone. In a code it becomes a placeholder the other phone sets up with a tap.
     */
    fun interface SlotLookup {
        fun slotFor(appWidgetId: Int): Pair<String, String>?
    }

    // Compact JSON (the backup file is indented for people to read; a code doesn't need that).
    fun launcherCode(s: LauncherSettings, slots: SlotLookup = SlotLookup { null }): String =
        pack(JSONObject(Backup.encode(portable(s, slots))).toString())

    fun screenCode(page: LauncherPage, slots: SlotLookup = SlotLookup { null }): String = pack(
        JSONObject()
            .put("format", SCREENS_FORMAT)
            .put("version", 1)
            .put("pages", JSONArray(Codec.encodePages(listOf(portable(page, slots)))))
            .toString(),
    )

    /**
     * Reads a code. [localKey] turns a shared app reference ("package/.Activity") into this
     * phone's app key, or null for an app that isn't installed. Returns null if it isn't a code.
     */
    fun read(text: String, localKey: (String) -> String?): Shared? {
        val json = unpack(text) ?: return null
        Backup.decode(json)?.let { return Shared.Launcher(localize(it, localKey)) }
        val o = try {
            JSONObject(json)
        } catch (_: JSONException) {
            return null
        }
        if (o.optString("format") != SCREENS_FORMAT) return null
        val pages = Codec.decodePages(o.optJSONArray("pages")?.toString())
        return if (pages.isEmpty()) null else Shared.Screens(pages.map { localize(it, localKey) })
    }

    // ---- Making it portable ----

    /** An app key without its user profile: "package/.Activity#0" becomes "package/.Activity". */
    fun portableKey(key: String): String = if (AppFolder.idOf(key) != null) key else key.substringBefore('#')

    private fun portable(s: LauncherSettings, slots: SlotLookup): LauncherSettings = s.copy(
        hiddenApps = emptySet(),
        // A permission-backed choice: each person turns it on (and grants it) themselves.
        musicLive = false,
        lockedApps = emptySet(),
        launchCounts = emptyMap(),
        recents = emptyList(),
        favorites = s.favorites.map(::portableKey),
        // The full app order only matters when sorting by it, and it's the biggest list there is.
        customOrder = if (s.sortMode == app.gridlauncher.domain.SortMode.CUSTOM) s.customOrder.map(::portableKey) else emptyList(),
        pinnedActions = s.pinnedActions.mapKeys { portableKey(it.key) },
        pages = s.pages.map { portable(it, slots) },
    )

    private fun portable(p: LauncherPage, slots: SlotLookup): LauncherPage = p.copy(
        apps = p.apps.map(::portableKey),
        folders = p.folders.map { f -> f.copy(apps = f.apps.map(::portableKey)) },
        // Other apps' widgets can't move between phones: each becomes a placeholder naming the
        // widget, set up again on the other phone. Photos stay on the phone they came from.
        widgets = p.widgets.mapNotNull { w ->
            if (w.kind == WidgetKind.APP_WIDGET) {
                slots.slotFor(w.appWidgetId)?.let { (provider, label) ->
                    w.copy(kind = WidgetKind.WIDGET_SLOT, appWidgetId = -1, provider = provider, label = label)
                }
            } else {
                w
            }
        }.map { w ->
            w.copy(appKey = if (w.appKey.isEmpty()) "" else portableKey(w.appKey), image = "", timerEndsAt = 0L, timerLengthMs = 0L)
        },
        // A Wi-Fi name says where someone lives or works: never shared.
        rule = p.rule.copy(wifiEnabled = false, wifiName = ""),
    )

    // ---- Reading it back ----

    private fun localize(s: LauncherSettings, localKey: (String) -> String?): LauncherSettings {
        val map = { k: String -> localKey(k) }
        return s.copy(
            favorites = s.favorites.mapNotNull(map),
            customOrder = s.customOrder.mapNotNull(map),
            pinnedActions = s.pinnedActions.mapNotNull { (k, v) -> map(k)?.let { it to v } }.toMap(),
            pages = s.pages.map { localize(it, localKey) },
        )
    }

    private fun localize(p: LauncherPage, localKey: (String) -> String?): LauncherPage = p.copy(
        // Folders keep their place; apps that aren't installed here are left out.
        apps = p.apps.mapNotNull { k -> if (AppFolder.idOf(k) != null) k else localKey(k) },
        folders = p.folders.map { f -> f.copy(apps = f.apps.mapNotNull(localKey)) },
        widgets = p.widgets.filter { w -> w.kind != WidgetKind.APP || localKey(w.appKey) != null }
            .map { w -> if (w.kind == WidgetKind.APP) w.copy(appKey = localKey(w.appKey) ?: w.appKey) else w },
    )

    // ---- The text form ----

    private fun pack(json: String): String {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(json.toByteArray())
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        return TAG + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    /**
     * The QR form of a code: the same compressed bytes in Base45, whose letters are exactly QR
     * codes' compact alphanumeric set, so a code holds about 1.6 times as much as Base64 text.
     */
    fun forQr(code: String): String {
        if (!code.startsWith(TAG)) return code
        val bytes = runCatching { Base64.getUrlDecoder().decode(code.removePrefix(TAG).filterNot { it.isWhitespace() }) }.getOrNull() ?: return code
        return QR_TAG + Base45.encode(bytes)
    }

    private const val QR_TAG = "GL2:"

    private fun unpack(text: String): String? {
        // Base45 uses the space character, so only line breaks and tabs are trimmed from a QR form.
        val raw = text.trim { it == '\n' || it == '\r' || it == '\t' }.trimStart()
        if (raw.startsWith(QR_TAG)) return Base45.decode(raw.removePrefix(QR_TAG))?.let(::inflate)
        val t = text.trim()
        if (t.startsWith("{")) return t
        // Tolerate a code that picked up spaces or line breaks on its way through a chat app.
        val body = t.filterNot { it.isWhitespace() }.substringAfter(TAG, missingDelimiterValue = "")
        if (body.isEmpty()) return null
        return runCatching { Base64.getUrlDecoder().decode(body) }.getOrNull()?.let(::inflate)
    }

    private fun inflate(bytes: ByteArray): String? = runCatching {
        val inflater = Inflater()
        inflater.setInput(bytes)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buf, 0, n)
            // A code can't sensibly be this big: stop rather than fill memory.
            if (out.size() > 4_000_000) return null
        }
        inflater.end()
        out.toString(Charsets.UTF_8.name())
    }.getOrNull()
}

/**
 * Base45 (RFC 9285): bytes as text in exactly the 45 characters QR codes store most compactly.
 * Two bytes become three characters.
 */
object Base45 {
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 3 / 2 + 2)
        var i = 0
        while (i + 1 < bytes.size) {
            val n = (bytes[i].toInt() and 0xFF) * 256 + (bytes[i + 1].toInt() and 0xFF)
            out.append(ALPHABET[n % 45]).append(ALPHABET[n / 45 % 45]).append(ALPHABET[n / 2025])
            i += 2
        }
        if (i < bytes.size) {
            val n = bytes[i].toInt() and 0xFF
            out.append(ALPHABET[n % 45]).append(ALPHABET[n / 45])
        }
        return out.toString()
    }

    /** The bytes, or null if [text] isn't valid Base45. */
    fun decode(text: String): ByteArray? {
        val values = text.map { ALPHABET.indexOf(it).takeIf { v -> v >= 0 } ?: return null }
        if (values.size % 3 == 1) return null
        val out = java.io.ByteArrayOutputStream(values.size * 2 / 3)
        var i = 0
        while (i + 2 < values.size) {
            val n = values[i] + values[i + 1] * 45 + values[i + 2] * 2025
            if (n > 0xFFFF) return null
            out.write(n shr 8)
            out.write(n and 0xFF)
            i += 3
        }
        if (i + 1 < values.size || (i < values.size && values.size - i == 2)) {
            val n = values[i] + values[i + 1] * 45
            if (n > 0xFF) return null
            out.write(n)
        }
        return out.toByteArray()
    }
}
