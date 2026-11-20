package app.gridlauncher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Stroke icons from the design, drawn on a 24-unit grid. Tint them with `Icon(tint = …)`. */
object LauncherIcons {
    private fun stroke(name: String, vararg paths: String, width: Float = 1.8f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Back = stroke("back", "M15 5l-7 7 7 7", width = 2f)
    val Chevron = stroke("chevron", "M9 5l7 7-7 7", width = 2f)
    val Close = stroke("close", "M6 6l12 12 M18 6L6 18", width = 2f)
    val Check = stroke("check", "M5 12.5l4.5 4.5L19 7", width = 2f)
    val Trash = stroke("trash", "M4 7h16 M9 7V4h6v3 M6 7l1 13h10l1-13")
    val Front = stroke("front", "M4 4h7v7H4z M13 13h7v7h-7z")
    val AddWidget = stroke("add_widget", "M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M16.5 13v7 M13 16.5h7")
    val Wallpaper = stroke("wallpaper", "M4 5h16v14H4z M4 16l5-5 4 4 3-3 4 4")
    val Sliders = stroke("sliders", "M4 6h10 M18 6h2 M4 12h4 M12 12h8 M4 18h12 M20 18h0 M16 4v4 M10 10v4 M18 16v4")
    val Info = stroke("info", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M12 11v5 M12 8h.01")
    val Hide = stroke(
        "hide",
        "M3 3l18 18 M10.6 6.2A9.8 9.8 0 0 1 12 6c5 0 9 6 9 6a17 17 0 0 1-2.6 3.2 M6.6 6.6C4.3 8.2 3 12 3 12s4 6 9 6a9 9 0 0 0 4.4-1.2",
    )
    val Star = stroke("star", "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z")
    val Move = stroke("move", "M12 3v18 M3 12h18 M9 6l3-3 3 3 M9 18l3 3 3-3 M6 9l-3 3 3 3 M18 9l3 3-3 3")
    val Pin = stroke("pin", "M9 4h6l-1 6 3 3H7l3-3z M12 13v7")
    val Drag = stroke("drag", "M9 6h.01 M15 6h.01 M9 12h.01 M15 12h.01 M9 18h.01 M15 18h.01", width = 2.6f)
    val Screens = stroke("screens", "M7 4h10v16H7z M3 7v10 M21 7v10")
    val Pulse = stroke("pulse", "M3 12h4l2-5 4 10 2-5h6")
    val Plus = stroke("plus", "M12 5v14 M5 12h14", width = 2f)
    val Shape = stroke("shape", "M4 4h7v7H4z M17.5 4a3.5 3.5 0 1 1 0 7 3.5 3.5 0 0 1 0-7z M4 18c2-3 4 3 6 0s4 3 6 0 4 3 4 0")
    val Undo = stroke("undo", "M9 5L4 10l5 5 M4 10h10a5 5 0 0 1 0 10h-3", width = 2f)
    val AlignLeft = stroke("align_left", "M4 4v16 M8 7h10v4H8z M8 13h6v4H8z")
    val AlignCenter = stroke("align_center", "M12 4v3 M12 11v2 M12 17v3 M7 7h10v4H7z M9 13h6v4H9z")
    val AlignRight = stroke("align_right", "M20 4v16 M6 7h10v4H6z M10 13h6v4h-6z")
    val Edit = stroke("edit", "M4 20h4L19 9l-4-4L4 16z M13.5 6.5l4 4")
    val Shortcut = stroke("shortcut", "M7 17L17 7 M9 7h8v8")
}
