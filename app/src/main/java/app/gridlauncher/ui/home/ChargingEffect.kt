package app.gridlauncher.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.Surface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.content.ContextCompat
import app.gridlauncher.data.ChargingStyle
import app.gridlauncher.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** The screen edge nearest the charging port, as the phone is held now. */
enum class PortEdge { BOTTOM, RIGHT, TOP, LEFT }

/** Whether the phone is charging, and how full it is (0..1). */
data class ChargeState(val charging: Boolean, val level: Float)

/** Follows the battery: charging or not, and its level. */
@Composable
fun rememberChargeState(): ChargeState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(readCharge(context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                state = readCharge(intent)
            }
        }
        // The battery broadcast is sticky: registering also hands back the latest state.
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            ?.let { state = readCharge(it) }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return state
}

private fun readCharge(intent: Intent?): ChargeState {
    if (intent == null) return ChargeState(false, 0f)
    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    return ChargeState(plugged, if (level < 0) 0f else level.toFloat() / scale)
}

/**
 * The edge the charging port is on. Phones have it at the bottom when held upright; turned to
 * landscape the screen rotates, so the port ends up on the right (rotation 90) or left (270).
 */
@Composable
fun rememberPortEdge(): PortEdge {
    val view = LocalView.current
    val context = LocalContext.current
    fun edgeNow(): PortEdge = when (view.display?.rotation ?: Surface.ROTATION_0) {
        Surface.ROTATION_90 -> PortEdge.RIGHT
        Surface.ROTATION_180 -> PortEdge.TOP
        Surface.ROTATION_270 -> PortEdge.LEFT
        else -> PortEdge.BOTTOM
    }
    var edge by remember { mutableStateOf(edgeNow()) }
    // Follow the display itself: turning straight from one landscape side to the other is a
    // rotation without a configuration change, so a configuration alone would miss it.
    DisposableEffect(view) {
        val displays = context.getSystemService(android.hardware.display.DisplayManager::class.java)
        val listener = object : android.hardware.display.DisplayManager.DisplayListener {
            override fun onDisplayChanged(displayId: Int) {
                edge = edgeNow()
            }
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
        }
        displays?.registerDisplayListener(listener, android.os.Handler(android.os.Looper.getMainLooper()))
        edge = edgeNow()
        onDispose { displays?.unregisterDisplayListener(listener) }
    }
    return edge
}

/** The drawing itself; also used for the previews in Settings. */
@Composable
fun ChargingCanvas(style: ChargingStyle, edge: PortEdge, level: Float, modifier: Modifier = Modifier) {
    val loop = rememberInfiniteTransition(label = "charging")
    // Seconds, looping every minute; effects use whole cycles of it so the loop is seamless.
    val time by loop.animateFloat(0f, 60f, infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart), label = "time")
    val accent = Palette.Accent
    Canvas(modifier.clearAndSetSemantics {}) {
        val frame = EdgeFrame(edge, size.width, size.height)
        when (style) {
            ChargingStyle.OFF -> Unit
            ChargingStyle.GLOW -> glow(frame, time, accent)
            ChargingStyle.FIRE -> fire(frame, time)
            ChargingStyle.WAVE -> wave(frame, time, level, accent)
            ChargingStyle.RINGS -> rings(frame, time, accent)
            ChargingStyle.BUBBLES -> bubbles(frame, time, accent)
            ChargingStyle.ELECTRIC -> electric(frame, time, accent)
            ChargingStyle.AURORA -> aurora(frame, time, accent)
            ChargingStyle.NEON -> neon(frame, time, accent)
            ChargingStyle.ORB -> orb(frame, time, accent)
            ChargingStyle.FOUNTAIN -> fountain(frame, time, accent)
            ChargingStyle.HEARTBEAT -> heartbeat(frame, time, accent)
            ChargingStyle.RAINBOW -> rainbow(frame, time)
        }
    }
}

/**
 * Edge coordinates: [e] runs along the port's edge (0..[length]), [h] is the distance in from it.
 * Every effect draws in these, so one drawing works on any edge.
 */
internal class EdgeFrame(val edge: PortEdge, private val w: Float, private val h: Float) {
    val length = if (edge == PortEdge.BOTTOM || edge == PortEdge.TOP) w else h

    fun pt(e: Float, d: Float): Offset = when (edge) {
        PortEdge.BOTTOM -> Offset(e, h - d)
        PortEdge.TOP -> Offset(w - e, d)
        PortEdge.RIGHT -> Offset(w - d, e)
        PortEdge.LEFT -> Offset(d, h - e)
    }
}

/** A repeatable pseudo-random number in 0..1 for [i]. */
internal fun rand(i: Int, salt: Int = 0): Float {
    val x = sin((i * 12.9898 + salt * 78.233) * 43758.5453)
    return (x - kotlin.math.floor(x)).toFloat()
}

internal fun DrawScope.glow(f: EdgeFrame, t: Float, accent: Color) {
    val depth = px(90)
    val pulse = 0.55f + 0.45f * ((sin(t * 2 * PI / 2.5) + 1) / 2).toFloat()
    // Brightest over the port, fading along the edge.
    val steps = 48
    for (i in 0 until steps) {
        val e0 = f.length * i / steps
        val e1 = f.length * (i + 1) / steps
        val mid = (e0 + e1) / 2
        val near = 1f - (abs(mid - f.length / 2) / (f.length / 2)).coerceIn(0f, 1f)
        val strength = (0.25f + 0.75f * near * near) * pulse
        val path = Path().apply {
            f.pt(e0, 0f).let { moveTo(it.x, it.y) }
            f.pt(e1, 0f).let { lineTo(it.x, it.y) }
            f.pt(e1, depth * (0.4f + 0.6f * near)).let { lineTo(it.x, it.y) }
            f.pt(e0, depth * (0.4f + 0.6f * near)).let { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path, Brush.linearGradient(listOf(accent.copy(alpha = 0.55f * strength), Color.Transparent), f.pt(mid, 0f), f.pt(mid, depth * (0.4f + 0.6f * near))))
    }
}

private fun DrawScope.fire(f: EdgeFrame, t: Float) {
    val center = f.length / 2
    val span = minOf(f.length * 0.55f, px(260))
    val tongues = 18
    val base = px(46)
    // Flames: tongues that sway and flicker, tallest over the port.
    for (i in 0 until tongues) {
        val u = i / (tongues - 1f)
        val e = center - span / 2 + span * u
        val near = 1f - abs(u - 0.5f) * 2f
        val flicker = 0.7f + 0.3f * sin(t * (7 + rand(i) * 5) + i).toFloat()
        val height = base * (0.35f + 0.9f * near) * flicker
        val sway = px(6) * sin(t * 3 + i * 0.7f).toFloat()
        val half = span / tongues * 0.9f
        val path = Path().apply {
            f.pt(e - half, 0f).let { moveTo(it.x, it.y) }
            val c1 = f.pt(e - half * 0.6f + sway * 0.3f, height * 0.55f)
            val tip = f.pt(e + sway, height)
            quadraticTo(c1.x, c1.y, tip.x, tip.y)
            val c2 = f.pt(e + half * 0.6f + sway * 0.3f, height * 0.55f)
            val end = f.pt(e + half, 0f)
            quadraticTo(c2.x, c2.y, end.x, end.y)
            close()
        }
        drawPath(
            path,
            Brush.linearGradient(
                listOf(Color(0xFFFFF3B0), Color(0xFFFFB347), Color(0xFFFF5A2A).copy(alpha = 0.8f), Color.Transparent),
                f.pt(e, 0f),
                f.pt(e, height),
            ),
        )
    }
    // A warm bed of light under the flames.
    drawLine(
        Brush.linearGradient(listOf(Color.Transparent, Color(0xFFFF8A3D), Color.Transparent), f.pt(center - span / 2, 0f), f.pt(center + span / 2, 0f)),
        f.pt(center - span / 2, px(1)),
        f.pt(center + span / 2, px(1)),
        strokeWidth = px(4),
    )
    // Sparks: rise from the fire, drift, and fade out.
    val sparks = 26
    for (i in 0 until sparks) {
        val life = 1.4f + rand(i, 1) * 1.2f
        val phase = ((t + rand(i, 2) * life) % life) / life
        val e = center + (rand(i, 3) - 0.5f) * span * 0.8f + px(18) * sin(t * 2 + i).toFloat() * phase
        val d = base * 0.4f + phase * (px(110) + rand(i, 4) * px(60))
        val alpha = (1f - phase) * (0.6f + 0.4f * sin(t * 20 + i).toFloat().coerceAtLeast(0f))
        drawCircle(Color(0xFFFFD27A).copy(alpha = alpha.coerceIn(0f, 1f)), radius = px((1.2f + rand(i, 5) * 1.6f)), center = f.pt(e, d))
    }
}

private fun DrawScope.wave(f: EdgeFrame, t: Float, level: Float, accent: Color) {
    // The water stands as high as the battery is full.
    val height = px(18) + px(54) * level.coerceIn(0f, 1f)
    for (layer in 0..1) {
        val speed = if (layer == 0) 1f else -0.7f
        val amp = px((if (layer == 0) 6 else 4))
        val path = Path()
        val steps = 64
        for (s in 0..steps) {
            val e = f.length * s / steps
            val d = height - layer * px(6) + amp * sin(e / f.length * 4 * PI + t * 2 * PI * speed / 3).toFloat()
            val p = f.pt(e, d)
            if (s == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        f.pt(f.length, 0f).let { path.lineTo(it.x, it.y) }
        f.pt(0f, 0f).let { path.lineTo(it.x, it.y) }
        path.close()
        drawPath(path, Brush.linearGradient(listOf(accent.copy(alpha = if (layer == 0) 0.45f else 0.3f), accent.copy(alpha = 0.05f)), f.pt(0f, 0f), f.pt(0f, height)))
    }
}

private fun DrawScope.rings(f: EdgeFrame, t: Float, accent: Color) {
    val origin = f.pt(f.length / 2, 0f)
    val max = px(150)
    for (i in 0 until 4) {
        val phase = ((t / 2.4f) + i / 4f) % 1f
        val r = px(12) + phase * max
        drawCircle(accent.copy(alpha = (1f - phase) * 0.6f), radius = r, center = origin, style = Stroke(width = px(2) * (1.2f - phase)))
    }
    drawCircle(accent.copy(alpha = 0.8f), radius = px(5), center = origin)
}

private fun DrawScope.bubbles(f: EdgeFrame, t: Float, accent: Color) {
    val center = f.length / 2
    for (i in 0 until 22) {
        val life = 2.2f + rand(i, 7) * 1.6f
        val phase = ((t + rand(i, 8) * life) % life) / life
        val e = center + (rand(i, 9) - 0.5f) * px(120) + px(14) * sin(t * 1.5f + i).toFloat()
        val d = phase * (px(140) + rand(i, 10) * px(60))
        val r = px((2.5f + rand(i, 11) * 5f)) * (0.6f + phase * 0.6f)
        val alpha = (1f - phase) * 0.75f
        drawCircle(accent.copy(alpha = alpha * 0.25f), radius = r, center = f.pt(e, d))
        drawCircle(accent.copy(alpha = alpha), radius = r, center = f.pt(e, d), style = Stroke(px(1.2)))
    }
}

private fun DrawScope.electric(f: EdgeFrame, t: Float, accent: Color) {
    // A jagged arc along the edge that re-forms several times a second, brightest over the port.
    val frameId = (t * 14).toInt()
    val segments = 40
    val path = Path()
    for (s in 0..segments) {
        val e = f.length * s / segments
        val near = 1f - (abs(e - f.length / 2) / (f.length / 2)).coerceIn(0f, 1f)
        val d = px(6) + (rand(s, frameId) * px(22)) * (0.3f + near)
        val p = f.pt(e, d)
        if (s == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    drawPath(path, accent.copy(alpha = 0.25f), style = Stroke(width = px(8), cap = StrokeCap.Round))
    drawPath(path, Color.White.copy(alpha = 0.85f), style = Stroke(width = px(1.6), cap = StrokeCap.Round))
    // A glow at the port.
    val origin = f.pt(f.length / 2, 0f)
    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent), origin, px(70)), radius = px(70), center = origin)
}

/** Curtains of light that drift along the edge, in the accent and two cooler tones. */
private fun DrawScope.aurora(f: EdgeFrame, t: Float, accent: Color) {
    val colors = listOf(accent, Color(0xFF5EE6C2), Color(0xFFB08CFF))
    colors.forEachIndexed { layer, color ->
        val height = px(70 + layer * 22)
        val speed = 0.15f + layer * 0.05f
        val path = Path()
        val steps = 60
        for (s in 0..steps) {
            val e = f.length * s / steps
            val x = e / f.length
            val d = height * (0.55f + 0.45f * sin(x * (3 + layer) * PI + t * 2 * PI * speed + layer).toFloat()) *
                (0.6f + 0.4f * sin(x * 7 * PI - t * 2 * PI * speed * (2.0 / 3.0)).toFloat().let { (it + 1) / 2 })
            val p = f.pt(e, d)
            if (s == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        f.pt(f.length, 0f).let { path.lineTo(it.x, it.y) }
        f.pt(0f, 0f).let { path.lineTo(it.x, it.y) }
        path.close()
        drawPath(path, Brush.linearGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f)), f.pt(0f, 0f), f.pt(0f, height)))
    }
}

/** Pulses of light racing out from the port along the edge, both ways, leaving trails. */
private fun DrawScope.neon(f: EdgeFrame, t: Float, accent: Color) {
    val center = f.length / 2
    val y = px(3)
    drawLine(accent.copy(alpha = 0.18f), f.pt(0f, y), f.pt(f.length, y), strokeWidth = px(2))
    for (i in 0 until 3) {
        val phase = ((t / 1.5f) + i / 3f) % 1f
        val reach = phase * (f.length / 2)
        val trail = px(90)
        for (dir in listOf(-1f, 1f)) {
            val head = center + dir * reach
            val tail = center + dir * (reach - trail).coerceAtLeast(0f)
            val alpha = 1f - phase * 0.7f
            drawLine(
                Brush.linearGradient(listOf(accent.copy(alpha = 0f), accent.copy(alpha = alpha)), f.pt(tail, y), f.pt(head, y)),
                f.pt(tail, y), f.pt(head, y), strokeWidth = px(3), cap = StrokeCap.Round,
            )
            drawCircle(Color.White.copy(alpha = alpha), radius = px(2.5), center = f.pt(head, y))
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = alpha * 0.5f), Color.Transparent), f.pt(head, y), px(16)), radius = px(16), center = f.pt(head, y))
        }
    }
}

/** A breathing orb of light half-risen over the port, with sparks circling it. */
private fun DrawScope.orb(f: EdgeFrame, t: Float, accent: Color) {
    val origin = f.pt(f.length / 2, 0f)
    val breath = 0.85f + 0.15f * sin(t * 2 * PI / 3).toFloat()
    val r = px(46) * breath
    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.15f), Color.Transparent), origin, r * 2.2f), radius = r * 2.2f, center = origin)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), accent.copy(alpha = 0.7f), accent.copy(alpha = 0f)), origin, r), radius = r, center = origin)
    for (i in 0 until 8) {
        val angle = (t * (0.6f + rand(i) * 0.6f) + i / 8f) * 2 * PI
        // Only the half that's on screen: angles above the edge.
        val a = (angle % (2 * PI)).let { if (it > PI) 2 * PI - it else it }
        val radius = r * (1.3f + rand(i, 3) * 0.6f)
        val e = f.length / 2 + radius * kotlin.math.cos(a).toFloat()
        val d = radius * sin(a).toFloat()
        drawCircle(Color.White.copy(alpha = 0.8f), radius = px(1.6), center = f.pt(e, d))
    }
}

/** Sparks thrown up from the port that arc and fall back, like a fountain. */
private fun DrawScope.fountain(f: EdgeFrame, t: Float, accent: Color) {
    val center = f.length / 2
    for (i in 0 until 40) {
        val life = 1.6f + rand(i, 1) * 0.8f
        val p = ((t + rand(i, 2) * life) % life) / life
        val spread = (rand(i, 3) - 0.5f) * px(150)
        val lift = px(130) + rand(i, 4) * px(70)
        val e = center + spread * p
        // Up, then down again: a parabola over the spark's life.
        val d = lift * 4 * p * (1 - p)
        val alpha = (1f - p * 0.8f)
        val color = if (i % 3 == 0) Color.White else accent
        drawCircle(color.copy(alpha = alpha), radius = px(1.4 + rand(i, 5) * 1.6), center = f.pt(e, d))
    }
    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.5f), Color.Transparent), f.pt(center, 0f), px(40)), radius = px(40), center = f.pt(center, 0f))
}

/** A heartbeat trace running along the edge, its beats tallest over the port. */
private fun DrawScope.heartbeat(f: EdgeFrame, t: Float, accent: Color) {
    val period = px(160)
    val shift = (t * px(120)) % period
    val path = Path()
    val steps = (f.length / px(2)).toInt().coerceAtLeast(2)
    for (s in 0..steps) {
        val e = f.length * s / steps
        val x = ((e + shift) % period) / period
        val near = 1f - (abs(e - f.length / 2) / (f.length / 2)).coerceIn(0f, 1f)
        val amp = px(10) + px(34) * near
        // A flat line with a sharp beat: small rise, tall spike, dip, return.
        val beat = when {
            x < 0.40f -> 0f
            x < 0.45f -> (x - 0.40f) / 0.05f * 0.15f
            x < 0.50f -> 0.15f - (x - 0.45f) / 0.05f * 0.45f
            x < 0.55f -> -0.3f + (x - 0.50f) / 0.05f * 1.3f
            x < 0.60f -> 1f - (x - 0.55f) / 0.05f * 1.4f
            x < 0.65f -> -0.4f + (x - 0.60f) / 0.05f * 0.4f
            else -> 0f
        }
        val p = f.pt(e, px(16) + beat * amp)
        if (s == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    drawPath(path, accent.copy(alpha = 0.25f), style = Stroke(width = px(6), cap = StrokeCap.Round))
    drawPath(path, accent, style = Stroke(width = px(1.8), cap = StrokeCap.Round))
}

/** A glow whose colors flow along the edge through the whole rainbow. */
private fun DrawScope.rainbow(f: EdgeFrame, t: Float) {
    val depth = px(80)
    val steps = 48
    for (i in 0 until steps) {
        val e0 = f.length * i / steps
        val e1 = f.length * (i + 1) / steps
        val mid = (e0 + e1) / 2
        val hue = ((mid / f.length) * 360f + t * 36f) % 360f
        val color = Color.hsv(hue, 0.65f, 1f)
        val near = 1f - (abs(mid - f.length / 2) / (f.length / 2)).coerceIn(0f, 1f)
        val reach = depth * (0.45f + 0.55f * near)
        val path = Path().apply {
            f.pt(e0, 0f).let { moveTo(it.x, it.y) }
            f.pt(e1, 0f).let { lineTo(it.x, it.y) }
            f.pt(e1, reach).let { lineTo(it.x, it.y) }
            f.pt(e0, reach).let { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path, Brush.linearGradient(listOf(color.copy(alpha = 0.5f), Color.Transparent), f.pt(mid, 0f), f.pt(mid, reach)))
    }
}

/** [v] dp in pixels. */
internal fun DrawScope.px(v: Number): Float = v.toFloat() * density
