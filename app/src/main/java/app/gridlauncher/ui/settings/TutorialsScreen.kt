package app.gridlauncher.ui.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gridlauncher.ui.LauncherViewModel
import app.gridlauncher.ui.Screen
import app.gridlauncher.ui.components.ScreenHeader
import app.gridlauncher.ui.theme.Palette

/** One lesson: a looping drawing, a title and a sentence or two. */
private class Lesson(val art: Art, val title: String, val body: String)

private enum class Art { QUICK_ACTIONS, APP_MENU, EDIT_MODE, MOVE_RESIZE, MAKE_ROOM, UNDO, SCREENS, ADD_SCREEN, AUTO_SCREEN, TEXT_LABEL, FOLDERS, GESTURES, BOARD, SHARE, SCAN, LIVE }

private val LESSONS = listOf(
    Lesson(Art.QUICK_ACTIONS, "Quick actions", "The buttons under an app open its shortcuts straight away: Selfie under Camera, Compose under Mail. Choose none, one or two in Settings."),
    Lesson(Art.APP_MENU, "Long-press an app", "Hold an app for all its shortcuts, pins, favorites, folders, app info, hide and uninstall."),
    Lesson(Art.EDIT_MODE, "Edit a screen", "Long-press empty space or a widget. The screen opens in edit mode, with Add widget, Screen, Wallpaper and Settings at the bottom."),
    Lesson(Art.MOVE_RESIZE, "Move and resize widgets", "In edit mode, drag a widget to move it, and pull a corner to resize it. Every widget resizes both ways. It snaps to edges, the center and other widgets."),
    Lesson(Art.MAKE_ROOM, "Hold to make room", "Drop a widget on another and it overlaps. Hold it there for 2 seconds instead, and the one underneath glides down to make room."),
    Lesson(Art.UNDO, "Undo a change", "Moved something by mistake? Tap the Undo arrow next to Done. Each tap steps back one move, resize or align."),
    Lesson(Art.SCREENS, "Many screens", "Swipe left and right between screens, or tap the bar at the bottom. Each screen has its own widgets and its own apps."),
    Lesson(Art.ADD_SCREEN, "Add a screen", "Swipe past the last screen and tap the plus. The new screen's options open right away: give it a name, an icon and its apps."),
    Lesson(Art.AUTO_SCREEN, "Screens that show themselves", "In a screen's options, set hours or a Wi-Fi network. It's on home only then, and comes up by itself when you return home."),
    Lesson(Art.BOARD, "Apps and photos on the board", "Mix apps in with your widgets: long-press an app and tap Place on the board, or pick App in Add widget. Add a Photo, then change its picture or shape from its toolbar. The grid of apps stays below. For widgets between apps, open a screen's options and tap Add a widget area."),
    Lesson(Art.TEXT_LABEL, "Text labels", "Add a text label from Add widget. Its text grows to fill the frame; pick any Google Font, weight, alignment and color."),
    Lesson(Art.FOLDERS, "Folders", "On a screen with its own apps, long-press an app and tap New folder. Add more apps to it from their own menus."),
    Lesson(Art.SHARE, "Share by scanning", "Settings › Share your launcher, or Share this screen in a screen's options, shows one code to scan. A big launcher becomes a live code that plays its frames on a loop. Save image or Share image sends it as one picture."),
    Lesson(Art.SCAN, "Import a shared code", "Settings › Import a shared code › Scan a code, and hold the camera on the other phone. A live code is read in one go: the ring fills in a second or two. Got a picture instead? Use From a screenshot."),
    Lesson(Art.LIVE, "Live codes, one scan", "1. On the sharing phone, a big launcher shows one live code: it plays its frames on a loop. 2. On the other phone, tap Scan a code (allow the camera once). 3. Hold it on the live code for a second or two while the ring fills. 4. Choose to add its screens or switch to it."),
    Lesson(Art.GESTURES, "Gestures", "Swipe down for notifications, swipe up past the apps to lock, double-tap empty space to lock. Choose each in Settings."),
)

/** Every tutorial on one page, each with a small looping drawing of the gesture. */
@Composable
fun TutorialsScreen(vm: LauncherViewModel) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // One clock drives every drawing, so they loop together without a timer each.
    val loop = rememberInfiniteTransition(label = "tutorials")
    val t by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart), label = "t")
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Tutorials", onBack = vm::back)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = navBottom + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "tour") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Palette.Accent.copy(alpha = 0.12f))
                        .border(1.dp, Palette.Accent.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                        .clickable(role = Role.Button) {
                            vm.setTutorialDone(false)
                            vm.open(Screen.HOME)
                        }
                        .padding(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Show the tour again", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Accent)
                        Text("The four-step introduction, on your home screen", fontSize = 12.sp, color = Palette.Muted)
                    }
                }
            }
            items(LESSONS, key = { it.title }) { lesson -> LessonCard(lesson) { t } }
        }
    }
}

@Composable
private fun LessonCard(lesson: Lesson, time: () -> Float) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.Line, RoundedCornerShape(22.dp)),
    ) {
        // Read the clock while drawing only, so the list doesn't recompose on every frame.
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Palette.Ground)
                .clearAndSetSemantics {},
        ) { drawLesson(lesson.art, time()) }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(lesson.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(lesson.body, fontSize = 13.sp, lineHeight = 19.sp, color = Palette.FgSoft)
        }
    }
}

/** Shapes inside the drawings: a step lighter than the phone, so they read clearly. */
private val El = Color(0xFF2C3138)
private val ElLight = Color(0xFF353B43)

/** A small styled code: round dots, three rounded eyes and the mark in the middle, inside a white tile. */
private fun DrawScope.miniCode(p: Phone, x: Float, y: Float, w: Float, salt: Int, alpha: Float = 1f) {
    if (alpha <= 0f) return
    val size = p.w * w
    val left = p.x(x)
    val top = p.y(y)
    drawRoundRect(Color.White.copy(alpha = alpha), Offset(left, top), Size(size, size), CornerRadius(6.dp.toPx()))
    val n = 15
    val cell = size * 0.84f / n
    val o = size * 0.08f
    val ink = Color(0xFF2B2C7A).copy(alpha = alpha)
    fun eye(ex: Int, ey: Int) = (ex < 4 && ey < 4) || (ex > n - 5 && ey < 4) || (ex < 4 && ey > n - 5)
    for (j in 0 until n) for (i in 0 until n) {
        if (eye(i, j) || (i in 6..8 && j in 6..8)) continue
        if (((i * 7 + j * 13 + salt * 5 + i * j) % 3) == 0) {
            drawCircle(ink, radius = cell * 0.4f, center = Offset(left + o + (i + 0.5f) * cell, top + o + (j + 0.5f) * cell))
        }
    }
    for ((ex, ey) in listOf(0 to 0, n - 4 to 0, 0 to n - 4)) {
        val ox = left + o + ex * cell
        val oy = top + o + ey * cell
        drawRoundRect(ink, Offset(ox + cell * 0.25f, oy + cell * 0.25f), Size(cell * 3.5f, cell * 3.5f), CornerRadius(cell * 1.2f), style = Stroke(cell * 0.6f))
        drawRoundRect(ink, Offset(ox + cell * 1.2f, oy + cell * 1.2f), Size(cell * 1.6f, cell * 1.6f), CornerRadius(cell * 0.6f))
    }
    for (i in 0..1) for (j in 0..1) {
        drawRoundRect(ink, Offset(left + o + (6.3f + i * 1.3f) * cell, top + o + (6.3f + j * 1.3f) * cell), Size(cell * 1.1f, cell * 1.1f), CornerRadius(cell * 0.35f))
    }
}

/** A progress ring: a faint track and an accent arc for [fraction] of it. */
private fun DrawScope.progressRing(center: Offset, radius: Float, fraction: Float, accent: Color) {
    val stroke = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
    val topLeft = Offset(center.x - radius, center.y - radius)
    drawCircle(Color.White.copy(alpha = 0.15f), radius, center, style = Stroke(3.dp.toPx()))
    drawArc(accent, -90f, 360f * fraction.coerceIn(0f, 1f), false, topLeft, Size(radius * 2, radius * 2), style = stroke)
}

/**
 * Two phones side by side: the left plays a live code, the right holds its camera on it while the
 * ring fills, then shows a tick.
 */
private fun DrawScope.liveCode(t: Float) {
    val accent = Palette.Accent
    val h = size.height - 20.dp.toPx()
    val w = h * 0.56f
    val gap = 18.dp.toPx()
    val left = Phone((size.width - 2 * w - gap) / 2f, 10.dp.toPx(), w, h)
    val right = Phone(left.left + w + gap, 10.dp.toPx(), w, h)
    for (ph in listOf(left, right)) {
        drawRoundRect(Palette.Surface, Offset(ph.left, ph.top), Size(w, h), CornerRadius(14.dp.toPx()))
        drawRoundRect(Palette.LineStrong, Offset(ph.left, ph.top), Size(w, h), CornerRadius(14.dp.toPx()), style = Stroke(1.5.dp.toPx()))
    }
    // Left: the live code, a new frame several times a second.
    val frame = ((t * 9f).toInt() % 4)
    miniCode(left, 0.12f, 0.24f, 0.76f, salt = frame + 7)
    for (i in 0..3) drawCircle(if (i == frame) accent else Palette.LineStrong, radius = 2.dp.toPx(), center = Offset(left.x(0.35f + i * 0.1f), left.y(0.72f)))
    // Right: the camera on it; the ring fills over most of the loop, then a tick.
    drawRoundRect(Color(0xFF0B0C0E), Offset(right.x(0.06f), right.y(0.05f)), Size(w * 0.88f, h * 0.9f), CornerRadius(10.dp.toPx()))
    miniCode(right, 0.2f, 0.22f, 0.6f, salt = frame + 7, alpha = 0.85f)
    val l = right.x(0.16f); val r = right.x(0.84f); val top = right.y(0.19f); val bottom = top + w * 0.68f
    val arm = w * 0.1f
    val stroke = 2.dp.toPx()
    for ((cx, cy, dx, dy) in listOf(listOf(l, top, 1f, 1f), listOf(r, top, -1f, 1f), listOf(l, bottom, 1f, -1f), listOf(r, bottom, -1f, -1f))) {
        drawLine(accent, Offset(cx, cy), Offset(cx + dx * arm, cy), stroke, StrokeCap.Round)
        drawLine(accent, Offset(cx, cy), Offset(cx, cy + dy * arm), stroke, StrokeCap.Round)
    }
    val fill = seg(t, 0.1f, 0.75f)
    val c = Offset(right.x(0.5f), right.y(0.8f))
    progressRing(c, 9.dp.toPx(), fill, accent)
    if (t > 0.78f) {
        val tick = seg(t, 0.78f, 0.9f)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(c.x - 4.dp.toPx(), c.y)
            lineTo(c.x - 1.dp.toPx(), c.y + 3.dp.toPx())
            lineTo(c.x + 4.5.dp.toPx(), c.y - 3.5.dp.toPx())
        }
        drawPath(path, accent.copy(alpha = tick), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

// ---- Drawings ------------------------------------------------------------------------------

/** Progress of [t] through [a]..[b], eased in and out, clamped to 0..1. */
private fun seg(t: Float, a: Float, b: Float): Float {
    val x = ((t - a) / (b - a)).coerceIn(0f, 1f)
    return x * x * (3 - 2 * x)
}

private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

/** A phone outline in the middle of the canvas; drawing inside it uses 0..1 fractions of it. */
private class Phone(val left: Float, val top: Float, val w: Float, val h: Float) {
    fun x(f: Float) = left + w * f
    fun y(f: Float) = top + h * f
}

private fun DrawScope.phone(): Phone {
    val h = size.height - 20.dp.toPx()
    val w = h * 0.56f
    val p = Phone((size.width - w) / 2f, 10.dp.toPx(), w, h)
    drawRoundRect(Palette.Surface, Offset(p.left, p.top), Size(w, h), CornerRadius(14.dp.toPx()))
    drawRoundRect(Palette.LineStrong, Offset(p.left, p.top), Size(w, h), CornerRadius(14.dp.toPx()), style = Stroke(1.5.dp.toPx()))
    return p
}

private fun DrawScope.box(p: Phone, x: Float, y: Float, w: Float, h: Float, color: Color, r: Float = 4f, stroke: Float = 0f, dashed: Boolean = false) {
    val style = if (stroke > 0f) {
        Stroke(stroke.dp.toPx(), pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 5f)) else null)
    } else {
        androidx.compose.ui.graphics.drawscope.Fill
    }
    drawRoundRect(color, Offset(p.x(x), p.y(y)), Size(p.w * w, p.h * h), CornerRadius(r.dp.toPx()), style = style)
}

/** A fingertip. [press] 0..1 grows a ring around it, for a tap or a hold. */
private fun DrawScope.finger(x: Float, y: Float, press: Float = 0f, alpha: Float = 1f) {
    if (alpha <= 0f) return
    if (press > 0f) drawCircle(Palette.Accent.copy(alpha = 0.5f * (1 - press) * alpha), radius = (10 + 14 * press).dp.toPx(), center = Offset(x, y))
    drawCircle(Color.White.copy(alpha = 0.85f * alpha), radius = 8.dp.toPx(), center = Offset(x, y))
    drawCircle(Palette.Accent.copy(alpha = alpha), radius = 8.dp.toPx(), center = Offset(x, y), style = Stroke(2.dp.toPx()))
}

/** A row of three app icons, each with a label line under it. */
private fun DrawScope.appRow(p: Phone, y: Float, dim: Float = 1f) {
    for (i in 0..2) {
        val cx = 0.2f + i * 0.3f
        box(p, cx - 0.09f, y, 0.18f, 0.1f, El.copy(alpha = dim), r = 5f)
        box(p, cx - 0.07f, y + 0.12f, 0.14f, 0.018f, Palette.LineStrong.copy(alpha = dim), r = 2f)
    }
}

private fun DrawScope.drawLesson(art: Art, t: Float) {
    if (art == Art.LIVE) return liveCode(t)
    val p = phone()
    val accent = Palette.Accent
    when (art) {
        Art.QUICK_ACTIONS -> {
            appRow(p, 0.12f)
            for (i in 0..2) {
                val on = i == 1 && t in 0.35f..0.6f
                box(p, 0.08f + i * 0.3f, 0.3f, 0.24f, 0.06f, if (on) accent.copy(alpha = 0.35f) else Palette.Line, r = 8f, stroke = if (on) 0f else 1f)
            }
            appRow(p, 0.48f, 0.6f)
            val press = seg(t, 0.3f, 0.6f)
            finger(p.x(0.5f), p.y(0.33f), press, alpha = 1f - seg(t, 0.75f, 0.9f))
        }
        Art.APP_MENU -> {
            appRow(p, 0.12f)
            appRow(p, 0.4f, 0.5f)
            val hold = seg(t, 0.1f, 0.45f)
            val open = seg(t, 0.45f, 0.6f)
            finger(p.x(0.2f), p.y(0.17f), hold, alpha = 1f - open)
            if (open > 0f) {
                box(p, 0.12f, 0.3f, 0.7f * open, 0.46f * open, El, r = 8f)
                for (i in 0..4) if (open > 0.9f) box(p, 0.18f, 0.35f + i * 0.08f, 0.45f - (i % 2) * 0.12f, 0.025f, Palette.LineStrong, r = 2f)
            }
        }
        Art.EDIT_MODE -> {
            val on = seg(t, 0.45f, 0.6f)
            box(p, 0.08f, 0.1f, 0.84f, 0.4f, Palette.LineStrong.copy(alpha = on), r = 10f, stroke = 1.2f, dashed = true)
            box(p, 0.14f, 0.16f, 0.46f, 0.12f, El, r = 6f)
            box(p, 0.14f, 0.32f, 0.3f, 0.1f, El, r = 6f)
            appRow(p, 0.58f, 1f - 0.6f * on)
            box(p, 0.08f, 0.86f, 0.84f, 0.09f * on, El, r = 8f)
            finger(p.x(0.72f), p.y(0.38f), seg(t, 0.1f, 0.45f), alpha = 1f - on)
        }
        Art.MOVE_RESIZE -> {
            val move = seg(t, 0.08f, 0.4f)
            val grow = seg(t, 0.5f, 0.82f)
            val x = lerp(0.1f, 0.3f, move)
            val w = lerp(0.4f, 0.6f, grow)
            val h = lerp(0.18f, 0.28f, grow)
            box(p, 0.06f, 0.08f, 0.88f, 0.6f, Palette.LineStrong, r = 10f, stroke = 1f, dashed = true)
            box(p, x, 0.2f, w, h, El, r = 8f)
            box(p, x, 0.2f, w, h, accent, r = 8f, stroke = 1.5f)
            drawCircle(accent, radius = 4.dp.toPx(), center = Offset(p.x(x + w), p.y(0.2f + h)))
            if (t < 0.45f) finger(p.x(x + w / 2), p.y(0.29f), 0.2f, alpha = 1f - seg(t, 0.4f, 0.45f))
            else finger(p.x(x + w), p.y(0.2f + h), 0.2f, alpha = seg(t, 0.45f, 0.5f) * (1f - seg(t, 0.88f, 0.95f)))
            appRow(p, 0.74f, 0.4f)
        }
        Art.MAKE_ROOM -> {
            val drag = seg(t, 0.05f, 0.3f)
            val hold = seg(t, 0.32f, 0.6f)
            val push = seg(t, 0.6f, 0.75f)
            box(p, 0.06f, 0.08f, 0.88f, 0.7f, Palette.LineStrong, r = 10f, stroke = 1f, dashed = true)
            box(p, 0.12f, lerp(0.42f, 0.6f, push), 0.76f, 0.13f, El, r = 8f)
            val y = lerp(0.14f, 0.42f, drag)
            box(p, 0.12f, y, 0.76f, 0.14f, ElLight, r = 8f)
            box(p, 0.12f, y, 0.76f, 0.14f, accent, r = 8f, stroke = 1.5f)
            if (hold > 0f && push < 1f) {
                drawArc(accent, -90f, 360f * hold, false, Offset(p.x(0.5f) - 13.dp.toPx(), p.y(y + 0.07f) - 13.dp.toPx()), Size(26.dp.toPx(), 26.dp.toPx()), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
            finger(p.x(0.5f), p.y(y + 0.07f), 0f, alpha = 1f - seg(t, 0.85f, 0.95f))
        }
        Art.UNDO -> {
            val away = seg(t, 0.1f, 0.35f)
            val back = seg(t, 0.6f, 0.8f)
            val x = lerp(0.12f, lerp(0.42f, 0.12f, back), away)
            box(p, 0.06f, 0.16f, 0.88f, 0.55f, Palette.LineStrong, r = 10f, stroke = 1f, dashed = true)
            box(p, x, 0.3f, 0.46f, 0.18f, El, r = 8f)
            // The Undo button, top right, pressed in the middle of the loop.
            val tap = seg(t, 0.45f, 0.6f)
            drawCircle(if (tap in 0.01f..0.99f) accent.copy(alpha = 0.35f) else El, radius = 9.dp.toPx(), center = Offset(p.x(0.62f), p.y(0.08f)))
            drawArc(Palette.Fg, 200f, 230f, false, Offset(p.x(0.62f) - 4.dp.toPx(), p.y(0.08f) - 4.dp.toPx()), Size(8.dp.toPx(), 8.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            box(p, 0.7f, 0.05f, 0.22f, 0.06f, accent, r = 8f)
            finger(p.x(0.62f), p.y(0.08f), tap, alpha = seg(t, 0.38f, 0.45f) * (1f - seg(t, 0.62f, 0.7f)))
        }
        Art.SCREENS -> {
            val slide = seg(t, 0.15f, 0.45f) - seg(t, 0.65f, 0.95f)
            for (i in 0..1) {
                val dx = (i - slide) * 1f
                if (dx in -1f..1f) {
                    box(p, 0.1f + dx, 0.1f, 0.8f, 0.2f, if (i == 0) El else accent.copy(alpha = 0.3f), r = 8f)
                    for (r in 0..1) for (c in 0..2) box(p, 0.14f + c * 0.26f + dx, 0.38f + r * 0.18f, 0.18f, 0.1f, El, r = 5f)
                }
            }
            for (i in 0..1) drawCircle(if ((slide > 0.5f) == (i == 1)) accent else Palette.LineStrong, radius = 3.dp.toPx(), center = Offset(p.x(0.44f + i * 0.12f), p.y(0.9f)))
            finger(p.x(lerp(0.75f, 0.25f, seg(t, 0.15f, 0.45f))), p.y(0.5f), 0f, alpha = if (t < 0.5f) 1f - seg(t, 0.42f, 0.5f) else 0f)
        }
        Art.ADD_SCREEN -> {
            val sheet = seg(t, 0.5f, 0.68f)
            box(p, 0.25f, 0.22f, 0.5f, 0.28f, accent, r = 12f, stroke = 1.5f, dashed = true)
            drawLine(accent, Offset(p.x(0.5f), p.y(0.29f)), Offset(p.x(0.5f), p.y(0.43f)), 2.dp.toPx(), StrokeCap.Round)
            drawLine(accent, Offset(p.x(0.42f), p.y(0.36f)), Offset(p.x(0.58f), p.y(0.36f)), 2.dp.toPx(), StrokeCap.Round)
            finger(p.x(0.5f), p.y(0.36f), seg(t, 0.2f, 0.45f), alpha = 1f - sheet)
            if (sheet > 0f) {
                val top = lerp(1f, 0.5f, sheet)
                box(p, 0f, top, 1f, 1f - top, El, r = 12f)
                box(p, 0.1f, top + 0.08f, 0.5f, 0.04f, Palette.Fg.copy(alpha = 0.7f), r = 2f)
                for (i in 0..3) box(p, 0.1f + i * 0.21f, top + 0.18f, 0.16f, 0.09f, if (i == 1) accent else Palette.Line, r = 5f)
            }
        }
        Art.AUTO_SCREEN -> {
            val shown = seg(t, 0.35f, 0.55f) - seg(t, 0.8f, 0.95f)
            val c = Offset(p.x(0.5f), p.y(0.2f))
            drawCircle(Palette.LineStrong, radius = 12.dp.toPx(), center = c, style = Stroke(1.5.dp.toPx()))
            val ang = Math.toRadians((t * 360f - 90f).toDouble())
            drawLine(accent, c, Offset(c.x + 8.dp.toPx() * kotlin.math.cos(ang).toFloat(), c.y + 8.dp.toPx() * kotlin.math.sin(ang).toFloat()), 2.dp.toPx(), StrokeCap.Round)
            box(p, 0.1f, 0.38f, 0.8f, 0.5f, Palette.LineStrong, r = 8f, stroke = 1f, dashed = true)
            box(p, 0.1f, 0.38f, 0.8f, 0.5f, accent.copy(alpha = 0.3f * shown), r = 8f)
            box(p, 0.18f, 0.46f, 0.5f * shown, 0.06f, Palette.Fg.copy(alpha = shown), r = 3f)
        }
        Art.TEXT_LABEL -> {
            val grow = seg(t, 0.2f, 0.6f) - seg(t, 0.75f, 0.95f)
            val w = lerp(0.4f, 0.8f, grow)
            box(p, 0.1f, 0.2f, w, 0.16f, accent, r = 6f, stroke = 1.5f)
            // The "text": a bar that fills its frame's width, as the label's font does.
            box(p, 0.13f, 0.24f, w - 0.06f, 0.08f, Palette.Fg.copy(alpha = 0.85f), r = 3f)
            drawCircle(accent, radius = 4.dp.toPx(), center = Offset(p.x(0.1f + w), p.y(0.36f)))
            finger(p.x(0.1f + w), p.y(0.36f), 0.2f, alpha = 1f - seg(t, 0.62f, 0.7f) + seg(t, 0.92f, 1f))
            appRow(p, 0.5f, 0.5f)
        }
        Art.FOLDERS -> {
            val menu = seg(t, 0.25f, 0.4f) - seg(t, 0.55f, 0.6f)
            val folder = seg(t, 0.6f, 0.75f)
            for (i in 0..2) {
                val cx = 0.2f + i * 0.3f
                if (i == 0 && folder > 0f) {
                    box(p, cx - 0.09f, 0.12f, 0.18f, 0.1f, El, r = 5f)
                    for (k in 0..3) box(p, cx - 0.07f + (k % 2) * 0.075f, 0.135f + (k / 2) * 0.04f, 0.06f, 0.032f, accent.copy(alpha = folder), r = 2f)
                } else {
                    box(p, cx - 0.09f, 0.12f, 0.18f, 0.1f, El, r = 5f)
                }
            }
            finger(p.x(0.2f), p.y(0.17f), seg(t, 0.05f, 0.25f), alpha = 1f - seg(t, 0.25f, 0.3f))
            if (menu > 0f) {
                box(p, 0.12f, 0.27f, 0.6f, 0.3f * menu, El, r = 8f)
                if (menu > 0.9f) box(p, 0.16f, 0.4f, 0.42f, 0.05f, accent.copy(alpha = 0.6f), r = 3f)
            }
            appRow(p, 0.64f, 0.5f)
        }
        Art.BOARD -> {
            // A widget, an app flying up from the grid into the board, and a round photo.
            box(p, 0.06f, 0.06f, 0.88f, 0.52f, Palette.LineStrong, r = 10f, stroke = 1f, dashed = true)
            box(p, 0.12f, 0.1f, 0.5f, 0.14f, El, r = 6f)
            val photo = seg(t, 0.55f, 0.75f)
            drawCircle(accent.copy(alpha = 0.25f + 0.35f * photo), radius = p.w * 0.13f, center = Offset(p.x(0.76f), p.y(0.18f)))
            val fly = seg(t, 0.1f, 0.45f)
            val ax = lerp(0.2f, 0.32f, fly)
            val ay = lerp(0.68f, 0.34f, fly)
            box(p, ax - 0.09f, ay, 0.18f, 0.1f, accent.copy(alpha = 0.7f), r = 5f)
            box(p, ax - 0.07f, ay + 0.12f, 0.14f, 0.018f, Palette.LineStrong, r = 2f)
            finger(p.x(ax), p.y(ay + 0.05f), 0.2f, alpha = 1f - seg(t, 0.45f, 0.55f))
            appRow(p, 0.68f, 0.5f)
        }
        Art.SHARE -> {
            // The share sheet: one live code playing its frames in place, then a tap on Share image.
            val frame = ((t * 9f).toInt() % 3)
            box(p, 0.06f, 0.06f, 0.88f, 0.9f, El, r = 10f)
            miniCode(p, 0.18f, 0.14f, 0.64f, salt = frame)
            for (i in 0..2) {
                drawCircle(if (i == frame) accent else Palette.LineStrong, radius = 2.5.dp.toPx(), center = Offset(p.x(0.4f + i * 0.08f), p.y(0.63f)))
            }
            box(p, 0.12f, 0.7f, 0.36f, 0.07f, ElLight, r = 8f)
            val tap = seg(t, 0.82f, 0.95f)
            box(p, 0.52f, 0.7f, 0.36f, 0.07f, if (tap in 0.01f..0.99f) accent.copy(alpha = 0.5f) else ElLight, r = 8f)
            finger(p.x(0.7f), p.y(0.735f), tap, alpha = seg(t, 0.72f, 0.8f) * (1f - seg(t, 0.95f, 1f)))
        }
        Art.SCAN -> {
            // The camera view: the live code in the frame, its frames changing, a line sweeping it.
            val part = (t * 3f).toInt().coerceAtMost(2)
            val inPart = (t * 3f) % 1f
            val enter = seg(inPart, 0f, 0.3f)
            box(p, 0.06f, 0.06f, 0.88f, 0.9f, Color(0xFF0B0C0E), r = 10f)
            miniCode(p, 0.2f + (1f - enter) * 0.5f, 0.18f, 0.6f, salt = part + 3, alpha = enter)
            // The frame's four corners.
            val l = p.x(0.16f); val r = p.x(0.84f); val top = p.y(0.14f); val bottom = p.y(0.14f) + p.w * 0.68f
            val arm = p.w * 0.1f
            val stroke = 2.dp.toPx()
            for ((cx, cy, dx, dy) in listOf(listOf(l, top, 1f, 1f), listOf(r, top, -1f, 1f), listOf(l, bottom, 1f, -1f), listOf(r, bottom, -1f, -1f))) {
                drawLine(accent, Offset(cx, cy), Offset(cx + dx * arm, cy), stroke, StrokeCap.Round)
                drawLine(accent, Offset(cx, cy), Offset(cx, cy + dy * arm), stroke, StrokeCap.Round)
            }
            if (enter >= 1f) {
                val sweep = seg(inPart, 0.35f, 0.85f)
                val y = lerp(top, bottom, sweep)
                drawLine(accent.copy(alpha = 0.8f), Offset(l + arm * 0.4f, y), Offset(r - arm * 0.4f, y), 1.5.dp.toPx())
            }
            // The ring fills as the live code's frames are read.
            progressRing(Offset(p.x(0.5f), p.y(0.84f)), 9.dp.toPx(), (part + inPart) / 3f, accent)
        }
        // Drawn as two phones, before this single phone (see liveCode).
        Art.LIVE -> Unit
        Art.GESTURES -> {
            val down = seg(t, 0.05f, 0.35f)
            val shade = seg(t, 0.25f, 0.4f) - seg(t, 0.5f, 0.6f)
            box(p, 0f, 0f, 1f, 0.45f * shade, El, r = 12f)
            for (i in 0..2) if (shade > 0.8f) box(p, 0.1f, 0.06f + i * 0.11f, 0.8f, 0.08f, Palette.Line, r = 5f)
            if (t < 0.4f) finger(p.x(0.5f), p.y(lerp(0.1f, 0.4f, down)), 0f, alpha = 1f - seg(t, 0.35f, 0.4f))
            // Then a double-tap on empty space.
            val tap1 = seg(t, 0.65f, 0.75f)
            val tap2 = seg(t, 0.78f, 0.88f)
            if (t >= 0.62f) finger(p.x(0.5f), p.y(0.7f), if (t < 0.77f) tap1 else tap2, alpha = 1f - seg(t, 0.92f, 0.98f))
            appRow(p, 0.48f, 0.6f)
        }
    }
}
