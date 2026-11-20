package app.gridlauncher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.gridlauncher.data.LauncherSettings
import app.gridlauncher.data.MusicStyle
import app.gridlauncher.notifications.NotificationCounts
import app.gridlauncher.notifications.rememberMediaPlaying
import app.gridlauncher.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private val FullGreen = Color(0xFF7BE0A0)

/** Whether this charge's full-battery burst has played, so coming back to home doesn't replay it. */
private var celebratedThisCharge = false
private val LowRed = Color(0xFFFF5F57)
private val LowAmber = Color(0xFFF2C46D)

/**
 * Everything drawn along the edges of home: the charging effect (or the full-charge burst and
 * glow), the low-battery pulse, the music effect and the notification flash. All of it is part
 * of home only, so none of it ever shows over another app, and it takes no touches.
 */
@Composable
fun EdgeEffectsOverlay(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val charge = rememberChargeState()
    val edge = rememberPortEdge()
    val full = charge.charging && charge.level >= 0.995f
    // The burst is once per charge: unplugging (or dropping below full) makes it ready again.
    if (!full) celebratedThisCharge = false
    Box(modifier.clearAndSetSemantics {}) {
        when {
            full && settings.fullChargeBurst -> FullCharge(edge, Modifier.fillMaxSize())
            charge.charging -> if (settings.chargingStyle != app.gridlauncher.data.ChargingStyle.OFF) {
                ChargingCanvas(settings.chargingStyle, edge, charge.level, Modifier.fillMaxSize())
            }
            settings.lowBatteryPulse && charge.level <= 0.30f && charge.level > 0f ->
                LowBattery(edge, critical = charge.level <= 0.15f, Modifier.fillMaxSize())
        }
        // Music plays along the bottom of the screen as it's held, whatever the port does.
        if (settings.musicStyle != MusicStyle.OFF && rememberMediaPlaying(true)) {
            // With the user's permission, follow the actual sound; otherwise a steady beat.
            val context = androidx.compose.ui.platform.LocalContext.current
            val levels = app.gridlauncher.notifications.rememberAudioLevels(context, settings.musicLive)
            MusicCanvas(settings.musicStyle, Modifier.fillMaxSize(), levels)
        }
        if (settings.timeOfDaySky) TimeOfDaySky(Modifier.fillMaxSize())
        if (settings.timerEdge) {
            val timers = settings.pages.flatMap { p -> p.widgets.filter { it.kind == app.gridlauncher.data.WidgetKind.TIMER && it.timerEndsAt > 0L } }
            if (timers.isNotEmpty()) TimerEdge(timers.map { it.timerEndsAt to it.timerLengthMs }, Modifier.fillMaxSize())
        }
        if (settings.notifyFlash) NotificationFlash(Modifier.fillMaxSize())
    }
}

/** The shared clock for edge effects: seconds, looping each minute. */
@Composable
private fun edgeTime(): () -> Float {
    val loop = rememberInfiniteTransition(label = "edge")
    val time = loop.animateFloat(0f, 60f, infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart), label = "edge-time")
    return { time.value }
}

// ---- Low battery ----

@Composable
private fun LowBattery(edge: PortEdge, critical: Boolean, modifier: Modifier) {
    val time = edgeTime()
    Canvas(modifier) {
        val t = time()
        val f = EdgeFrame(edge, size.width, size.height)
        // Critical: a quicker, stronger red pulse. Low: a slow amber breath.
        val period = if (critical) 1.2f else 3f
        val pulse = ((sin(t * 2 * PI / period) + 1) / 2).toFloat()
        val strength = if (critical) 0.45f + 0.55f * pulse else 0.25f + 0.35f * pulse
        lowGlow(f, if (critical) LowRed else LowAmber, strength)
    }
}

private fun DrawScope.lowGlow(f: EdgeFrame, color: Color, strength: Float) {
    val depth = px(70)
    val steps = 40
    for (i in 0 until steps) {
        val e0 = f.length * i / steps
        val e1 = f.length * (i + 1) / steps
        val mid = (e0 + e1) / 2
        val near = 1f - (abs(mid - f.length / 2) / (f.length / 2)).coerceIn(0f, 1f)
        val reach = depth * (0.35f + 0.65f * near)
        val path = Path().apply {
            f.pt(e0, 0f).let { moveTo(it.x, it.y) }
            f.pt(e1, 0f).let { lineTo(it.x, it.y) }
            f.pt(e1, reach).let { lineTo(it.x, it.y) }
            f.pt(e0, reach).let { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path, Brush.linearGradient(listOf(color.copy(alpha = 0.6f * strength), Color.Transparent), f.pt(mid, 0f), f.pt(mid, reach)))
    }
}

// ---- Full charge ----

/** When charging reaches 100%: a burst of confetti from the port, then a calm green glow. */
@Composable
private fun FullCharge(edge: PortEdge, modifier: Modifier) {
    val burst = remember { Animatable(if (celebratedThisCharge) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!celebratedThisCharge) {
            celebratedThisCharge = true
            burst.animateTo(1f, tween(3200, easing = LinearEasing))
        }
    }
    val time = edgeTime()
    Canvas(modifier) {
        val f = EdgeFrame(edge, size.width, size.height)
        val t = time()
        val p = burst.value
        // The glow fades in as the burst settles, then breathes slowly.
        val settle = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)
        val breath = 0.7f + 0.3f * ((sin(t * 2 * PI / 4) + 1) / 2).toFloat()
        lowGlow(f, FullGreen, settle * breath * 0.8f)
        if (p < 1f) confetti(f, p)
    }
}

private val ConfettiColors = listOf(Color(0xFF7BE0A0), Color(0xFF9DB8FF), Color(0xFFF2C46D), Color(0xFFFF8FA3), Color.White)

private fun DrawScope.confetti(f: EdgeFrame, p: Float) {
    val origin = f.length / 2
    for (i in 0 until 70) {
        // Each piece flies out on its own arc, slows, and falls back with gravity.
        val angle = PI * (0.12 + 0.76 * rand(i, 21))
        val speed = px(420) * (0.55f + 0.45f * rand(i, 22))
        val tt = p * 1.6f
        val e = origin + (speed * kotlin.math.cos(angle) * tt).toFloat()
        val d = (speed * sin(angle) * tt).toFloat() - px(380) * tt * tt
        if (d < 0f) continue
        val alpha = (1f - p).coerceIn(0f, 1f)
        val color = ConfettiColors[i % ConfettiColors.size].copy(alpha = alpha)
        val w = px(3 + rand(i, 23) * 4)
        val spin = (sin(p * 20 + i).toFloat() + 1) / 2
        val c = f.pt(e, d)
        drawRect(color, topLeft = Offset(c.x - w / 2, c.y - w * spin / 2), size = Size(w, (w * spin).coerceAtLeast(px(1))))
    }
}

// ---- Music ----

/**
 * Music effects. They move to a steady beat rather than to the sound itself: reading the sound
 * would need the microphone permission, which a launcher shouldn't ask for.
 */
@Composable
fun MusicCanvas(style: MusicStyle, modifier: Modifier, levels: app.gridlauncher.notifications.AudioLevels? = null) {
    val time = edgeTime()
    val accent = Palette.Accent
    Canvas(modifier) {
        val t = time()
        val f = EdgeFrame(PortEdge.BOTTOM, size.width, size.height)
        val live = levels?.takeIf { it.live }
        when (style) {
            MusicStyle.OFF -> Unit
            MusicStyle.BARS -> if (live != null) liveBars(f, live, accent) else bars(f, t, accent)
            MusicStyle.PULSE -> if (live != null) livePulse(f, live, accent) else beatPulse(f, t, accent)
            MusicStyle.WAVE -> if (live != null) liveWave(f, live, accent) else waveform(f, t, accent)
        }
    }
}

// The same three looks, driven by the actual sound.

private fun DrawScope.liveBars(f: EdgeFrame, levels: app.gridlauncher.notifications.AudioLevels, accent: Color) {
    val count = app.gridlauncher.notifications.AudioLevels.BANDS
    val gap = px(3)
    val w = (f.length - gap * (count + 1)) / count
    for (i in 0 until count) {
        val h = px(4) + px(90) * levels.bands[i]
        val e0 = gap + i * (w + gap)
        val top = f.pt(e0, h)
        val bottom = f.pt(e0 + w, 0f)
        drawRoundRect(
            Brush.linearGradient(listOf(accent.copy(alpha = 0.8f), accent.copy(alpha = 0.15f)), top, f.pt(e0, 0f)),
            topLeft = Offset(minOf(top.x, bottom.x), minOf(top.y, bottom.y)),
            size = Size(abs(bottom.x - top.x), abs(bottom.y - top.y)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(px(2)),
        )
    }
}

private fun DrawScope.livePulse(f: EdgeFrame, levels: app.gridlauncher.notifications.AudioLevels, accent: Color) {
    val e = levels.energy.coerceIn(0f, 1f)
    lowGlow(f, accent, 0.2f + 0.8f * e)
    drawCircle(accent.copy(alpha = 0.25f + 0.5f * e), radius = px(20) + e * px(150), center = f.pt(f.length / 2, 0f), style = Stroke(px(2)))
}

private fun DrawScope.liveWave(f: EdgeFrame, levels: app.gridlauncher.notifications.AudioLevels, accent: Color) {
    val n = app.gridlauncher.notifications.AudioLevels.WAVE_POINTS
    val path = Path()
    for (i in 0 until n) {
        val x = i / (n - 1f)
        val env = sin(x * PI).toFloat()
        val p = f.pt(f.length * x, px(36) + px(32) * levels.wave[i] * env)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    drawPath(path, accent.copy(alpha = 0.3f), style = Stroke(px(6)))
    drawPath(path, accent, style = Stroke(px(1.8)))
}

/** A beat at 120 per minute: 1 on the beat, falling away until the next. */
private fun beat(t: Float): Float {
    val x = (t * 2f) % 1f
    return (1f - x).let { it * it }
}

private fun DrawScope.bars(f: EdgeFrame, t: Float, accent: Color) {
    val count = 32
    val gap = px(3)
    val w = (f.length - gap * (count + 1)) / count
    val b = beat(t)
    for (i in 0 until count) {
        // Each bar has its own rhythm, lifted together on the beat.
        val own = (sin(t * (5 + rand(i, 31) * 6) + i * 1.3f).toFloat() + 1) / 2
        val near = 1f - abs(i - count / 2f) / (count / 2f)
        val h = px(10) + px(70) * (0.25f + 0.45f * own + 0.3f * b) * (0.5f + 0.5f * near)
        val e0 = gap + i * (w + gap)
        val top = f.pt(e0, h)
        val bottom = f.pt(e0 + w, 0f)
        drawRoundRect(
            Brush.linearGradient(listOf(accent.copy(alpha = 0.7f), accent.copy(alpha = 0.15f)), top, f.pt(e0, 0f)),
            topLeft = Offset(minOf(top.x, bottom.x), minOf(top.y, bottom.y)),
            size = Size(abs(bottom.x - top.x), abs(bottom.y - top.y)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(px(2)),
        )
    }
}

private fun DrawScope.beatPulse(f: EdgeFrame, t: Float, accent: Color) {
    val b = beat(t)
    lowGlow(f, accent, 0.35f + 0.65f * b)
    // A ring thrown out on each beat.
    val ring = (t * 2f) % 1f
    drawCircle(accent.copy(alpha = (1f - ring) * 0.5f), radius = px(20) + ring * px(140), center = f.pt(f.length / 2, 0f), style = Stroke(px(2)))
}

private fun DrawScope.waveform(f: EdgeFrame, t: Float, accent: Color) {
    val b = beat(t)
    for (layer in 0..2) {
        val path = Path()
        val steps = 90
        for (s in 0..steps) {
            val x = s / steps.toFloat()
            val env = sin(x * PI).toFloat()
            val amp = px(14 + 10 * layer) * (0.5f + 0.5f * b) * env
            val y = px(34) + amp * sin(x * (9 + layer * 4) * PI + t * 2 * PI * (0.5f + layer * 0.25f)).toFloat()
            val p = f.pt(f.length * x, y)
            if (s == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, accent.copy(alpha = 0.75f - layer * 0.22f), style = Stroke(px(2f - layer * 0.4f)))
    }
}

// ---- Notification flash ----

/** A quick glow around every edge of home when a notification arrives. */
@Composable
private fun NotificationFlash(modifier: Modifier) {
    val flash = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        NotificationCounts.arrivals.collect {
            flash.snapTo(0f)
            flash.animateTo(1f, tween(1400))
        }
    }
    val accent = Palette.Accent
    Canvas(modifier) {
        val p = flash.value
        if (p >= 1f) return@Canvas
        // Up quickly, then fading out.
        val strength = if (p < 0.15f) p / 0.15f else 1f - (p - 0.15f) / 0.85f
        val depth = px(40)
        val c = accent.copy(alpha = 0.7f * strength)
        drawRect(Brush.verticalGradient(listOf(c, Color.Transparent), 0f, depth), size = Size(size.width, depth))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, c), size.height - depth, size.height), topLeft = Offset(0f, size.height - depth), size = Size(size.width, depth))
        drawRect(Brush.horizontalGradient(listOf(c, Color.Transparent), 0f, depth), size = Size(depth, size.height))
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, c), size.width - depth, size.width), topLeft = Offset(size.width - depth, 0f), size = Size(depth, size.height))
    }
}

// ---- Timer ----

private val TimerHot = Color(0xFFFF9C5A)

/**
 * Quick timers: a line along the bottom that drains as the soonest timer runs down, pulsing in
 * its last ten seconds; when one ends, the edge flashes until it's dismissed (for two minutes at most).
 */
@Composable
private fun TimerEdge(timers: List<Pair<Long, Long>>, modifier: Modifier) {
    val time = edgeTime()
    val accent = Palette.Accent
    Canvas(modifier) {
        time() // Redraw every frame, so the line moves smoothly.
        val now = System.currentTimeMillis()
        val f = EdgeFrame(PortEdge.BOTTOM, size.width, size.height)
        val finished = timers.filter { (end, _) -> end <= now && now - end < 120_000L }
        if (finished.isNotEmpty()) {
            val since = (now - finished.maxOf { it.first }) / 1000f
            val flash = ((sin(since * 2 * PI / 0.8) + 1) / 2).toFloat()
            lowGlow(f, TimerHot, 0.35f + 0.65f * flash)
            return@Canvas
        }
        val (end, length) = timers.filter { it.first > now }.minByOrNull { it.first } ?: return@Canvas
        if (length <= 0L) return@Canvas
        val left = ((end - now).toFloat() / length).coerceIn(0f, 1f)
        val remaining = (end - now) / 1000f
        // Last ten seconds: the line warms and pulses with each second.
        val urgent = remaining <= 10f
        val beat = if (urgent) (1f - (remaining % 1f)).let { it * it } else 0f
        val color = if (urgent) TimerHot else accent
        val y = px(3)
        val reach = f.length * left
        val start = (f.length - reach) / 2
        drawLine(color.copy(alpha = 0.18f), f.pt(0f, y), f.pt(f.length, y), strokeWidth = px(3))
        drawLine(color.copy(alpha = 0.85f), f.pt(start, y), f.pt(start + reach, y), strokeWidth = px(3 + 2 * beat), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.5f), Color.Transparent), f.pt(start, y), px(14)), radius = px(14), center = f.pt(start, y))
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.5f), Color.Transparent), f.pt(start + reach, y), px(14)), radius = px(14), center = f.pt(start + reach, y))
        if (urgent) lowGlow(f, TimerHot, 0.25f + 0.5f * beat)
    }
}

// ---- Time of day ----

/** Sky colours through the day, by minute after midnight, and how many stars show. */
private val SkyKeys = listOf(
    Triple(0, Color(0xFF2B2F6B), 1f),
    Triple(300, Color(0xFF2B2F6B), 1f),
    Triple(360, Color(0xFFFF8FA3), 0.2f),
    Triple(420, Color(0xFFFFC38A), 0f),
    Triple(540, Color(0xFF8EC9FF), 0f),
    Triple(1020, Color(0xFF8EC9FF), 0f),
    Triple(1110, Color(0xFFFF9A5A), 0f),
    Triple(1170, Color(0xFFC46BD6), 0.3f),
    Triple(1260, Color(0xFF2B2F6B), 1f),
    Triple(1440, Color(0xFF2B2F6B), 1f),
)

private fun skyAt(minute: Float): Pair<Color, Float> {
    val i = SkyKeys.indexOfLast { it.first <= minute }.coerceIn(0, SkyKeys.size - 2)
    val (m0, c0, s0) = SkyKeys[i]
    val (m1, c1, s1) = SkyKeys[i + 1]
    val f = ((minute - m0) / (m1 - m0).coerceAtLeast(1)).coerceIn(0f, 1f)
    return androidx.compose.ui.graphics.lerp(c0, c1, f) to (s0 + (s1 - s0) * f)
}

/** A soft sky along the top of home: dawn pink, daylight blue, sunset orange, night indigo with stars. */
@Composable
private fun TimeOfDaySky(modifier: Modifier) {
    val time = edgeTime()
    Canvas(modifier) {
        val t = time()
        val now = System.currentTimeMillis()
        val minute = (((now + java.util.TimeZone.getDefault().getOffset(now)) / 60_000L) % 1440L).toFloat()
        val (color, stars) = skyAt(minute)
        val depth = px(120)
        drawRect(Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), Color.Transparent), 0f, depth), size = Size(size.width, depth))
        if (stars > 0.01f) {
            for (i in 0 until 28) {
                val x = rand(i, 41) * size.width
                val y = rand(i, 42) * depth * 0.8f + px(6)
                val twinkle = (sin(t * (1.5f + rand(i, 43) * 2f) + i).toFloat() + 1) / 2
                drawCircle(Color.White.copy(alpha = stars * (0.25f + 0.6f * twinkle) * (1f - y / depth)), radius = px(0.8 + rand(i, 44) * 1.1), center = Offset(x, y))
            }
        }
    }
}
