package app.gridlauncher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Icons a user can give a screen in the navbar. Line icons on a 24-unit grid, built on demand. */
object IconLibrary {
    class Entry(val key: String, val label: String, val path: String)

    val entries = listOf(
        Entry("home", "Home", "M4 11l8-7 8 7v9H4z M10 20v-6h4v6"),
        Entry("work", "Work", "M4 8h16v11H4z M9 8V5h6v3 M4 13h16"),
        Entry("star", "Star", "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z"),
        Entry("heart", "Heart", "M12 20s-8-5-8-11a4.5 4.5 0 0 1 8-2.5A4.5 4.5 0 0 1 20 9c0 6-8 11-8 11z"),
        Entry("focus", "Focus", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M12 12h.01"),
        Entry("music", "Music", "M9 18V5l11-2v13 M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0z M20 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z"),
        Entry("game", "Games", "M6 8h12a4 4 0 0 1 4 4v1a4 4 0 0 1-7.2 2.4L14 14h-4l-.8 1.4A4 4 0 0 1 2 13v-1a4 4 0 0 1 4-4z M7 11v3 M5.5 12.5h3 M16 12h.01 M18 13.5h.01"),
        Entry("book", "Reading", "M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2z M4 21V5 M8 7h7"),
        Entry("code", "Code", "M8 7l-5 5 5 5 M16 7l5 5-5 5 M14 4l-4 16"),
        Entry("chat", "Social", "M4 5h16v11H8l-4 4z M8 10h8 M8 13h5"),
        Entry("camera", "Photo", "M4 8h3l2-3h6l2 3h3v11H4z M12 17a4 4 0 1 0 0-8 4 4 0 0 0 0 8z"),
        Entry("film", "Watch", "M4 4h16v16H4z M8 4v16 M16 4v16 M4 9h4 M4 15h4 M16 9h4 M16 15h4"),
        Entry("mail", "Mail", "M3 6h18v12H3z M3 7l9 6 9-6"),
        Entry("calendar", "Plan", "M4 6h16v14H4z M4 10h16 M8 3v4 M16 3v4"),
        Entry("clock", "Time", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M12 7v5l3 2"),
        Entry("coffee", "Morning", "M4 9h12v5a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5z M16 10h2a2 2 0 0 1 0 4h-2 M8 3v3 M12 3v3"),
        Entry("sun", "Day", "M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M12 2v2 M12 20v2 M4.9 4.9l1.4 1.4 M17.7 17.7l1.4 1.4 M2 12h2 M20 12h2 M4.9 19.1l1.4-1.4 M17.7 6.3l1.4-1.4"),
        Entry("moon", "Night", "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"),
        Entry("bolt", "Energy", "M13 3L5 13h6l-1 8 8-10h-6z"),
        Entry("leaf", "Calm", "M5 19c0-9 6-14 15-14 0 9-5 15-14 15 M5 19l8-8"),
        Entry("plane", "Travel", "M10 14l-6 2 1-3 5-2V5a2 2 0 0 1 4 0v6l5 2 1 3-6-2v4l2 2h-6l2-2z"),
        Entry("car", "Drive", "M5 16h14v-4l-2-5H7l-2 5z M5 12h14 M7 19v-3 M17 19v-3 M8 14h.01 M16 14h.01"),
        Entry("cart", "Shop", "M3 4h3l2 11h11l2-8H7 M10 19a1 1 0 1 0 0 .01 M18 19a1 1 0 1 0 0 .01"),
        Entry("wallet", "Money", "M3 7h16a2 2 0 0 1 2 2v9H3z M3 7l13-3v3 M16 13h2"),
        Entry("pulse", "Health", "M3 12h4l2-5 4 10 2-5h6"),
        Entry("compass", "Explore", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M15.5 8.5l-2 5-5 2 2-5z"),
        Entry("globe", "World", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M3 12h18 M12 3c2.5 2.5 3.5 5.5 3.5 9s-1 6.5-3.5 9c-2.5-2.5-3.5-5.5-3.5-9s1-6.5 3.5-9z"),
        Entry("phone", "Calls", "M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z"),
        Entry("palette", "Create", "M12 3a9 9 0 1 0 0 18c1.5 0 2-1 2-2s-1-1.5-1-2.5 1-1.5 2-1.5h2a4 4 0 0 0 4-4c0-4.5-4-8-9-8z M7.5 11h.01 M10 7h.01 M14.5 7h.01"),
        Entry("school", "Study", "M2 9l10-5 10 5-10 5z M6 11v5c3 2 9 2 12 0v-5 M22 9v5"),
        Entry("tool", "Tools", "M14 6a4 4 0 0 0 5 5l-9 9a2 2 0 0 1-3-3l9-9a4 4 0 0 1-2-2z"),
        Entry("chart", "Stats", "M4 20V10 M10 20V4 M16 20v-7 M22 20H2"),
        Entry("cloud", "Cloud", "M7 18h10a4 4 0 0 0 0-8 5 5 0 0 0-9.6 1.5A3.3 3.3 0 0 0 7 18z"),
        Entry("flame", "Hot", "M12 21c-4 0-6-3-6-6 0-4 4-6 4-11 3 2 7 6 7 11 0 3-2 6-5 6z M12 21c-1.5 0-2.5-1-2.5-2.5 0-2 2.5-3 2.5-5 1.5 1 2.5 3 2.5 5 0 1.5-1 2.5-2.5 2.5z"),
        Entry("bell", "Alerts", "M6 16V11a6 6 0 0 1 12 0v5l2 2H4z M10 20a2 2 0 0 0 4 0"),
        Entry("user", "Me", "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M4 21a8 8 0 0 1 16 0"),
        Entry("users", "Family", "M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z M2 20a7 7 0 0 1 14 0 M16 4.5a3.5 3.5 0 0 1 0 6.5 M18 13.5a6 6 0 0 1 4 6.5"),
        Entry("grid", "Apps", "M4 4h6v6H4z M14 4h6v6h-6z M4 14h6v6H4z M14 14h6v6h-6z"),
        Entry("layers", "Stack", "M12 3l9 5-9 5-9-5z M3 13l9 5 9-5"),
        Entry("flag", "Goals", "M5 21V4 M5 4h11l-2 4 2 4H5"),
    )

    private val byKey = entries.associateBy { it.key }
    private val cache = HashMap<String, ImageVector>()

    /** The default icon for a screen with none chosen: home first, then a grid. */
    fun defaultKey(index: Int) = if (index == 0) "home" else "grid"

    fun keyFor(chosen: String, index: Int) = chosen.takeIf { it in byKey } ?: defaultKey(index)

    fun vector(key: String): ImageVector = cache.getOrPut(key) {
        val entry = byKey[key] ?: byKey.getValue("grid")
        ImageVector.Builder(entry.key, 24.dp, 24.dp, 24f, 24f).apply {
            entry.path.split(" M").forEachIndexed { i, part ->
                addPath(
                    pathData = addPathNodes(if (i == 0) part else "M$part"),
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
    }
}
