package app.gridlauncher.data

import app.gridlauncher.domain.WidgetBounds
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** JSON encoding for the structured values kept in DataStore preferences. */
object Codec {

    fun encodeList(values: List<String>): String {
        val array = JSONArray()
        values.forEach { array.put(it) }
        return array.toString()
    }

    fun decodeList(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optString(it, null) }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    fun encodeCounts(counts: Map<String, Int>): String {
        val obj = JSONObject()
        counts.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    fun decodeCounts(raw: String?): Map<String, Int> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            buildMap { obj.keys().forEach { k -> put(k, obj.optInt(k, 0)) } }
        } catch (_: JSONException) {
            emptyMap()
        }
    }

    fun encodePins(pins: Map<String, List<String>>): String {
        val obj = JSONObject()
        pins.forEach { (k, ids) ->
            val array = JSONArray()
            ids.forEach { array.put(it) }
            obj.put(k, array)
        }
        return obj.toString()
    }

    fun decodePins(raw: String?): Map<String, List<String>> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { k ->
                    val array = obj.optJSONArray(k) ?: return@forEach
                    put(k, (0 until array.length()).mapNotNull { array.optString(it, null) })
                }
            }
        } catch (_: JSONException) {
            emptyMap()
        }
    }

    fun encodeWidgets(widgets: List<PlacedWidget>): String {
        val array = JSONArray()
        widgets.forEach { w ->
            array.put(
                JSONObject()
                    .put("uid", w.uid)
                    .put("kind", w.kind.name)
                    .put("id", w.appWidgetId)
                    .put("x", w.bounds.x.toDouble())
                    .put("y", w.bounds.y.toDouble())
                    .put("w", w.bounds.width.toDouble())
                    .put("h", w.bounds.height.toDouble())
                    .also { o -> w.text?.let { o.put("text", encodeText(it)) } }
                    .also { o ->
                        if (w.appKey.isNotEmpty()) o.put("app", w.appKey)
                        if (w.image.isNotEmpty()) o.put("img", w.image)
                        if (w.kind == WidgetKind.IMAGE) o.put("shape", w.shape.name)
                        if (w.board.isNotEmpty()) o.put("board", w.board)
                        if (w.provider.isNotEmpty()) o.put("prov", w.provider)
                        if (w.label.isNotEmpty()) o.put("label", w.label)
                    }
                    .also { o ->
                        if (w.timerEndsAt > 0) {
                            o.put("tEnd", w.timerEndsAt)
                            o.put("tLen", w.timerLengthMs)
                        }
                    },
            )
        }
        return array.toString()
    }

    fun decodeWidgets(raw: String?): List<PlacedWidget> {
        if (raw.isNullOrEmpty()) return emptyList()
        return try {
            decodeWidgetArray(JSONArray(raw))
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private fun decodeWidgetArray(array: JSONArray): List<PlacedWidget> {
        return run {
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val uid = o.optString("uid", "")
                val kind = WidgetKind.entries.firstOrNull { it.name == o.optString("kind") }
                if (uid.isEmpty() || kind == null) return@mapNotNull null
                PlacedWidget(
                    uid = uid,
                    kind = kind,
                    appWidgetId = o.optInt("id", -1),
                    bounds = WidgetBounds(
                        x = o.optDouble("x", 0.0).toFloat(),
                        y = o.optDouble("y", 0.0).toFloat(),
                        width = o.optDouble("w", 160.0).toFloat(),
                        height = o.optDouble("h", 96.0).toFloat(),
                    ),
                    text = o.optJSONObject("text")?.let(::decodeText)
                        ?: when (kind) {
                            WidgetKind.TEXT -> TextSpec()
                            WidgetKind.NOTE -> TextSpec(text = "")
                            else -> null
                        },
                    timerEndsAt = o.optLong("tEnd", 0L),
                    timerLengthMs = o.optLong("tLen", 0L),
                    appKey = o.optString("app", ""),
                    image = o.optString("img", ""),
                    shape = ImageShape.entries.firstOrNull { it.name == o.optString("shape") } ?: ImageShape.ROUNDED,
                    board = o.optString("board", ""),
                    provider = o.optString("prov", ""),
                    label = o.optString("label", ""),
                )
            }
        }
    }

    private fun encodeText(t: TextSpec): JSONObject = JSONObject()
        .put("text", t.text)
        .put("font", t.font)
        .put("weight", t.weight)
        .put("align", t.align.name)
        .put("tone", t.tone.name)

    private fun decodeText(o: JSONObject) = TextSpec(
        text = o.optString("text", "Label"),
        font = o.optString("font", ""),
        weight = o.optInt("weight", 500),
        align = TextAlignment.entries.firstOrNull { it.name == o.optString("align") } ?: TextAlignment.START,
        tone = TextTone.entries.firstOrNull { it.name == o.optString("tone") } ?: TextTone.PRIMARY,
    )

    fun encodePages(pages: List<LauncherPage>): String {
        val array = JSONArray()
        pages.forEach { page ->
            array.put(
                JSONObject()
                    .put("uid", page.uid)
                    .put("mode", page.appsMode.name)
                    .put("lw", page.layoutWidth.toDouble())
                    .put("name", page.name)
                    .put("icon", page.icon)
                    .put(
                        "folders",
                        JSONArray().also { arr ->
                            page.folders.forEach { f ->
                                arr.put(JSONObject().put("id", f.id).put("name", f.name).put("apps", JSONArray().also { a -> f.apps.forEach { a.put(it) } }))
                            }
                        },
                    )
                    .put(
                        "rule",
                        JSONObject()
                            .put("time", page.rule.timeEnabled)
                            .put("from", page.rule.fromMinute)
                            .put("to", page.rule.toMinute)
                            .put("wifi", page.rule.wifiEnabled)
                            .put("ssid", page.rule.wifiName),
                    )
                    .also { obj ->
                        page.look?.let { l ->
                            obj.put(
                                "look",
                                JSONObject()
                                    .put("qa", l.quickActions)
                                    .put("dots", l.dots.name)
                                    .put("labels", l.labels)
                                    .put("themed", l.themedIcons)
                                    .put("grid", l.gridSize.name),
                            )
                        }
                    }
                    .put("boards", JSONArray().also { a -> page.boards.forEach { b -> a.put(JSONObject().put("uid", b.uid).put("row", b.afterRow)) } })
                    .put("apps", JSONArray().also { a -> page.apps.forEach { a.put(it) } })
                    .put("widgets", JSONArray(encodeWidgets(page.widgets))),
            )
        }
        return array.toString()
    }

    fun decodePages(raw: String?): List<LauncherPage> {
        if (raw.isNullOrEmpty()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val uid = o.optString("uid", "")
                if (uid.isEmpty()) return@mapNotNull null
                val apps = o.optJSONArray("apps")
                LauncherPage(
                    uid = uid,
                    widgets = o.optJSONArray("widgets")?.let(::decodeWidgetArray).orEmpty(),
                    appsMode = PageApps.entries.firstOrNull { it.name == o.optString("mode") } ?: PageApps.ALL,
                    apps = if (apps == null) emptyList() else (0 until apps.length()).mapNotNull { apps.optString(it, null) },
                    layoutWidth = o.optDouble("lw", 0.0).toFloat().coerceAtLeast(0f),
                    name = o.optString("name", ""),
                    boards = o.optJSONArray("boards")?.let { arr ->
                        (0 until arr.length()).mapNotNull { j ->
                            val b = arr.optJSONObject(j) ?: return@mapNotNull null
                            val id = b.optString("uid", "")
                            if (id.isEmpty()) null else Board(id, b.optInt("row", 2).coerceAtLeast(0))
                        }
                    }.orEmpty(),
                    look = o.optJSONObject("look")?.let { l ->
                        PageLook(
                            quickActions = l.optInt("qa", 2).coerceIn(0, 2),
                            dots = DotStyle.entries.firstOrNull { it.name == l.optString("dots") } ?: DotStyle.DOT,
                            labels = l.optBoolean("labels", true),
                            themedIcons = l.optBoolean("themed", true),
                            gridSize = GridSize.entries.firstOrNull { it.name == l.optString("grid") } ?: GridSize.M,
                        )
                    },
                    icon = o.optString("icon", ""),
                    rule = o.optJSONObject("rule")?.let { r ->
                        ScreenRule(
                            timeEnabled = r.optBoolean("time", false),
                            fromMinute = r.optInt("from", 9 * 60).coerceIn(0, 24 * 60 - 1),
                            toMinute = r.optInt("to", 17 * 60).coerceIn(0, 24 * 60 - 1),
                            wifiEnabled = r.optBoolean("wifi", false),
                            wifiName = r.optString("ssid", ""),
                        )
                    } ?: ScreenRule(),
                    folders = o.optJSONArray("folders")?.let { arr ->
                        (0 until arr.length()).mapNotNull { j ->
                            val f = arr.optJSONObject(j) ?: return@mapNotNull null
                            val id = f.optString("id", "")
                            if (id.isEmpty()) return@mapNotNull null
                            val apps = f.optJSONArray("apps")
                            AppFolder(id, f.optString("name", "Folder"), if (apps == null) emptyList() else (0 until apps.length()).mapNotNull { apps.optString(it, null) })
                        }
                    }.orEmpty(),
                )
            }.distinctBy { it.uid }
        } catch (_: JSONException) {
            emptyList()
        }
    }
}
