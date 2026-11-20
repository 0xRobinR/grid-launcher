package app.gridlauncher.ui.monitor

import android.content.Intent
import android.provider.Settings
import android.view.View
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gridlauncher.monitor.BatteryHistory
import app.gridlauncher.monitor.BatterySample
import app.gridlauncher.monitor.DataUsage
import app.gridlauncher.monitor.Snapshot
import app.gridlauncher.monitor.SystemMonitor
import app.gridlauncher.monitor.UsageInfo
import app.gridlauncher.ui.LauncherViewModel
import app.gridlauncher.ui.components.startSafely
import app.gridlauncher.ui.theme.FontCatalog
import app.gridlauncher.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private const val HISTORY = 60
private const val DAY_MS = 24 * 60 * 60 * 1000L
private val Good = Color(0xFF8FD6A8)
private val Warn = Color(0xFFF2C46D)

/**
 * The phone monitor: a live, read-only dashboard of the phone. It samples once a second, and only
 * while it is the screen in view.
 */
@Composable
fun MonitorScreen(vm: LauncherViewModel, active: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val monitor = remember { SystemMonitor(context) }
    val mono = remember { FontCatalog.family("JetBrains Mono") }
    val allApps by vm.allApps.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var snapshot by remember { mutableStateOf<Snapshot?>(null) }
    var down by remember { mutableStateOf(List(HISTORY) { 0f }) }
    var up by remember { mutableStateOf(List(HISTORY) { 0f }) }
    var current by remember { mutableStateOf(List(HISTORY) { 0f }) }
    var usage by remember { mutableStateOf<UsageInfo?>(null) }
    var usageAllowed by remember { mutableStateOf(true) }
    var data by remember { mutableStateOf<DataUsage?>(null) }
    var history by remember { mutableStateOf<List<BatterySample>>(emptyList()) }
    var lastLogged by remember { mutableStateOf<BatterySample?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Sample only while this screen is in view and the launcher is in front; coming back to it
    // (for example after granting usage access) starts fresh readings straight away.
    LaunchedEffect(active, lifecycle) {
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            monitor.resetTraffic()
            launch {
                while (isActive) {
                    val s = withContext(Dispatchers.IO) { monitor.snapshot() }
                    snapshot = s
                    down = (down + s.network.downBytesPerSec.toFloat()).takeLast(HISTORY)
                    up = (up + s.network.upBytesPerSec.toFloat()).takeLast(HISTORY)
                    current = (current + (s.battery?.currentMa?.toFloat() ?: 0f)).takeLast(HISTORY)
                    s.battery?.let { b ->
                        val now = System.currentTimeMillis()
                        val last = lastLogged
                        if (last == null || last.percent != b.percent || last.charging != b.charging || now - last.time > 5 * 60_000L) {
                            val sample = BatterySample(now, b.percent, b.charging)
                            lastLogged = sample
                            withContext(Dispatchers.IO) { BatteryHistory.record(context, sample) }
                        }
                    }
                    delay(1_000)
                }
            }
            launch {
                while (isActive) {
                    usageAllowed = withContext(Dispatchers.IO) { monitor.hasUsageAccess() }
                    usage = if (usageAllowed) withContext(Dispatchers.IO) { monitor.readUsageToday() } else null
                    data = if (usageAllowed) withContext(Dispatchers.IO) { monitor.readDataToday() } else null
                    history = withContext(Dispatchers.IO) {
                        BatteryHistory.read(context, System.currentTimeMillis() - DAY_MS)
                    }
                    delay(30_000)
                }
            }
        }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val s = snapshot

    LazyColumn(
        modifier = Modifier.fillMaxSize().techGrid(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = statusTop + 20.dp, bottom = navBottom + 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { Header(s, mono) }
        if (s == null) return@LazyColumn
        item(key = "battery") { BatteryPanel(s, current, mono) }
        item(key = "battery-day") { BatteryDayPanel(history, mono) }
        item(key = "network") { NetworkPanel(s, down, up, mono) }
        item(key = "usage") {
            UsagePanel(usage, usageAllowed, mono) {
                val pkg = context.packageName
                if (!context.startSafely(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, "package:$pkg".toUri()))) {
                    context.startSafely(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
        }
        if (usageAllowed) item(key = "data") { DataPanel(data, mono) }
        item(key = "memory") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MeterPanel(
                    title = "Memory",
                    used = s.memory.totalBytes - s.memory.availableBytes,
                    total = s.memory.totalBytes,
                    note = if (s.memory.low) "Low memory" else "${bytes(s.memory.availableBytes)} free",
                    warn = s.memory.low,
                    mono = mono,
                    modifier = Modifier.weight(1f),
                )
                MeterPanel(
                    title = "Storage",
                    used = s.storage.totalBytes - s.storage.freeBytes,
                    total = s.storage.totalBytes,
                    note = "${bytes(s.storage.freeBytes)} free",
                    warn = s.storage.freeBytes < s.storage.totalBytes / 10,
                    mono = mono,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item(key = "cpu") { CpuPanel(s, mono) }
        item(key = "system") {
            SystemPanel(
                s = s,
                display = displayText(view),
                apps = allApps.size,
                launches = settings?.launchCounts?.values?.sum() ?: 0,
                mono = mono,
            )
        }
    }
}

// ---- Panels ---------------------------------------------------------------------------------

@Composable
private fun Header(s: Snapshot?, mono: FontFamily) {
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "live-dot",
    )
    Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).alpha(pulse).clip(CircleShape).background(Good))
            Text("LIVE", fontFamily = mono, fontSize = 11.sp, letterSpacing = 0.2.em, color = Good)
            Text("·  SYSTEM MONITOR", fontFamily = mono, fontSize = 11.sp, letterSpacing = 0.2.em, color = Palette.Muted)
        }
        Text(
            s?.device?.name ?: "Reading sensors…",
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.02).em,
            modifier = Modifier.semantics { heading() },
        )
        if (s != null) {
            Text(
                "Android ${s.device.android} · API ${s.device.sdk} · up ${duration(s.device.uptimeMs)}",
                fontFamily = mono,
                fontSize = 12.sp,
                color = Palette.Muted,
            )
        }
    }
}

@Composable
private fun BatteryPanel(s: Snapshot, history: List<Float>, mono: FontFamily) {
    val b = s.battery
    Panel("Battery", trailing = b?.let { if (it.charging) "${it.source} · charging" else it.status }, mono = mono) {
        if (b == null) {
            Text("Battery details aren't available on this phone.", fontSize = 13.sp, color = Palette.Muted)
            return@Panel
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Ring(
                fraction = b.percent / 100f,
                color = when {
                    b.charging -> Good
                    b.percent <= 15 -> Palette.Danger
                    else -> Palette.Accent
                },
                modifier = Modifier.size(112.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${b.percent}", fontFamily = mono, fontSize = 34.sp, fontWeight = FontWeight.Medium)
                    Text("PERCENT", fontFamily = mono, fontSize = 9.sp, letterSpacing = 0.15.em, color = Palette.Muted)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Stat("Temp", b.temperatureC?.let { String.format(Locale.US, "%.1f °C", it) }, mono, warn = (b.temperatureC ?: 0f) >= 42f)
                Stat("Voltage", b.voltageV?.let { String.format(Locale.US, "%.2f V", it) }, mono)
                Stat("Current", b.currentMa?.let { "${if (it > 0) "+" else ""}$it mA" }, mono)
                Stat("Charge", b.chargeMah?.let { "$it mAh" }, mono)
                Stat("Health", b.health, mono, warn = b.health != "Good" && b.health != "Unknown")
                b.cycleCount?.let { Stat("Cycles", "$it", mono) }
                b.timeToFullMs?.let { Stat("Full in", duration(it), mono) }
            }
        }
        if (history.any { it != 0f }) {
            ChartLabel("Current draw · last 60 s", mono)
            Sparkline(listOf(history.map { abs0(it) } to Palette.Accent), Modifier.fillMaxWidth().height(56.dp))
        }
    }
}

@Composable
private fun NetworkPanel(s: Snapshot, down: List<Float>, up: List<Float>, mono: FontFamily) {
    val n = s.network
    Panel("Network", trailing = if (n.type == "Offline") "Offline" else if (n.online) "${n.type} · online" else "${n.type} · no internet", mono = mono) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SpeedReadout("↓  DOWN", n.downBytesPerSec, Palette.Accent, mono, Modifier.weight(1f))
            SpeedReadout("↑  UP", n.upBytesPerSec, Palette.FgSoft, mono, Modifier.weight(1f))
        }
        ChartLabel("Throughput · last 60 s", mono)
        Sparkline(listOf(down to Palette.Accent, up to Palette.FgSoft), Modifier.fillMaxWidth().height(72.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (n.type == "Wi-Fi") {
                Stat("Signal", n.signalDbm?.let { "$it dBm · ${wifiQuality(it)}" }, mono)
                Stat("Link", listOfNotNull(n.wifiLinkMbps?.let { "$it Mbps" }, n.wifiBand).joinToString(" · ").ifBlank { null }, mono)
            } else if (n.type == "Mobile") {
                Stat("Carrier", n.carrier, mono)
                Stat("Signal", n.signalLevel?.let { "${"▮".repeat(it)}${"▯".repeat(4 - it.coerceIn(0, 4))}  $it/4" } ?: n.signalDbm?.let { "$it dBm" }, mono)
            }
            Stat("Estimate", n.linkDownKbps?.let { "↓ ${kbps(it)}  ↑ ${kbps(n.linkUpKbps ?: 0)}" }, mono)
            Stat("Metered", if (n.type == "Offline") null else if (n.metered) "Yes" else "No", mono)
            Stat("Since boot", if (n.receivedSinceBoot != null) "↓ ${bytes(n.receivedSinceBoot)}  ↑ ${bytes(n.sentSinceBoot ?: 0)}" else null, mono)
        }
    }
}

@Composable
private fun UsagePanel(usage: UsageInfo?, allowed: Boolean, mono: FontFamily, onGrant: () -> Unit) {
    Panel("Screen time · today", trailing = usage?.let { "${it.unlocks} unlocks" }, mono = mono) {
        if (!allowed) {
            Text(
                "To show screen time, unlocks and your top apps, allow usage access for Grid Launcher. The data stays on your phone.",
                fontSize = 13.sp,
                color = Palette.FgSoft,
            )
            Box(
                Modifier
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(Palette.Accent)
                    .clickable(role = Role.Button, onClick = onGrant)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Allow usage access", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ground)
            }
            return@Panel
        }
        if (usage == null) {
            Text("Reading usage…", fontFamily = mono, fontSize = 13.sp, color = Palette.Muted)
            return@Panel
        }
        Text(duration(usage.screenTimeMs), fontFamily = mono, fontSize = 32.sp, fontWeight = FontWeight.Medium)
        val maxMs = usage.topApps.maxOfOrNull { it.foregroundMs }?.coerceAtLeast(1) ?: 1
        usage.topApps.forEach { app ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(app.label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(duration(app.foregroundMs), fontFamily = mono, fontSize = 12.sp, color = Palette.Muted)
                }
                Bar(app.foregroundMs.toFloat() / maxMs, Palette.Accent)
            }
        }
        if (usage.topApps.isEmpty()) Text("No app time yet today.", fontSize = 13.sp, color = Palette.Muted)
    }
}

@Composable
private fun MeterPanel(
    title: String,
    used: Long,
    total: Long,
    note: String,
    warn: Boolean,
    mono: FontFamily,
    modifier: Modifier = Modifier,
) {
    val fraction = if (total > 0) used.toFloat() / total else 0f
    Panel(title, trailing = null, mono = mono, modifier = modifier) {
        Text("${(fraction * 100).roundToInt()}%", fontFamily = mono, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        Bar(fraction, if (warn) Warn else Palette.Accent)
        Text("${bytes(used)} / ${bytes(total)}", fontFamily = mono, fontSize = 11.sp, color = Palette.Muted)
        Text(note, fontSize = 12.sp, color = if (warn) Warn else Palette.FgSoft)
    }
}

@Composable
private fun CpuPanel(s: Snapshot, mono: FontFamily) {
    val cpu = s.cpu
    Panel("Processor", trailing = "${cpu.cores.size} cores", mono = mono) {
        Stat("Chip", cpu.soc, mono)
        Stat("ABI", cpu.abi, mono)
        Stat("Thermal", s.thermal.status + (s.thermal.headroom?.let { String.format(Locale.US, " · %.0f%% of limit", it * 100) } ?: ""), mono, warn = s.thermal.level >= 2)
        if (cpu.cores.any { it.currentMhz != null }) {
            ChartLabel("Core clocks", mono)
            cpu.cores.forEachIndexed { i, core ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CPU$i", fontFamily = mono, fontSize = 11.sp, color = Palette.Muted, modifier = Modifier.width(36.dp))
                    Box(Modifier.weight(1f)) {
                        Bar(if (core.currentMhz != null && core.maxMhz != null && core.maxMhz > 0) core.currentMhz.toFloat() / core.maxMhz else 0f, Palette.Accent)
                    }
                    Text(core.currentMhz?.let { "$it MHz" } ?: "—", fontFamily = mono, fontSize = 11.sp, color = Palette.FgSoft, modifier = Modifier.width(72.dp))
                }
            }
        } else {
            Text("This phone doesn't let apps read core clock speeds.", fontSize = 12.sp, color = Palette.Muted)
        }
    }
}

@Composable
private fun SystemPanel(s: Snapshot, display: String?, apps: Int, launches: Int, mono: FontFamily) {
    Panel("System", trailing = null, mono = mono) {
        Stat("Security patch", s.device.securityPatch, mono)
        Stat("Display", display, mono)
        Stat("Brightness", s.device.brightness?.let { "${(it * 100 / 255).coerceIn(0, 100)}%" }, mono)
        Stat("Sensors", "${s.device.sensors}", mono)
        Stat("Apps", "$apps installed", mono)
        Stat("Launches", "$launches from this launcher", mono)
    }
}

@Composable
private fun BatteryDayPanel(samples: List<BatterySample>, mono: FontFamily) {
    Panel("Battery · 24 h", trailing = samples.lastOrNull()?.let { "${it.percent}% now" }, mono = mono) {
        if (samples.size < 2) {
            Text(
                "The chart fills in over the day: a reading is saved every 15 minutes.",
                fontSize = 12.sp,
                color = Palette.Muted,
            )
            return@Panel
        }
        val now = System.currentTimeMillis()
        Canvas(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Ground.copy(alpha = 0.6f))) {
            val grid = Palette.Line.copy(alpha = 0.6f)
            listOf(0.25f, 0.5f, 0.75f).forEach { f -> drawLine(grid, Offset(0f, size.height * f), Offset(size.width, size.height * f), 1f) }
            fun x(t: Long) = ((t - (now - DAY_MS)).toFloat() / DAY_MS).coerceIn(0f, 1f) * size.width
            fun y(p: Int) = size.height - p / 100f * (size.height - 8.dp.toPx()) - 4.dp.toPx()
            // Each stretch is drawn in green while charging and in the accent color otherwise.
            samples.zipWithNext().forEach { (a, b) ->
                drawLine(
                    color = if (a.charging) Color(0xFF8FD6A8) else Palette.Accent,
                    start = Offset(x(a.time), y(a.percent)),
                    end = Offset(x(b.time), y(b.percent)),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            val last = samples.last()
            drawCircle(if (last.charging) Color(0xFF8FD6A8) else Palette.Accent, 3.5.dp.toPx(), Offset(x(last.time), y(last.percent)))
        }
        Row {
            Text("24 h ago", fontFamily = mono, fontSize = 10.sp, color = Palette.Subtle, modifier = Modifier.weight(1f))
            Text("Charging", fontFamily = mono, fontSize = 10.sp, color = Color(0xFF8FD6A8))
            Text("  ·  Now", fontFamily = mono, fontSize = 10.sp, color = Palette.Subtle)
        }
        val low = samples.minOf { it.percent }
        val high = samples.maxOf { it.percent }
        Stat("Range today", "$low% – $high%", mono)
    }
}

@Composable
private fun DataPanel(data: DataUsage?, mono: FontFamily) {
    Panel("Data · today", trailing = data?.let { "${bytes(it.wifiBytes + it.mobileBytes)} total" }, mono = mono) {
        if (data == null) {
            Text("Reading data use…", fontFamily = mono, fontSize = 13.sp, color = Palette.Muted)
            return@Panel
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("WI-FI", fontFamily = mono, fontSize = 10.sp, letterSpacing = 0.12.em, color = Palette.Accent)
                Text(bytes(data.wifiBytes), fontFamily = mono, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            }
            Column(Modifier.weight(1f)) {
                Text("MOBILE", fontFamily = mono, fontSize = 10.sp, letterSpacing = 0.12.em, color = Palette.FgSoft)
                Text(bytes(data.mobileBytes), fontFamily = mono, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            }
        }
        val max = data.topApps.maxOfOrNull { it.bytes }?.coerceAtLeast(1) ?: 1
        data.topApps.forEach { app ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(app.label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(bytes(app.bytes), fontFamily = mono, fontSize = 12.sp, color = Palette.Muted)
                }
                Bar(app.bytes.toFloat() / max, Palette.Accent)
            }
        }
        if (data.topApps.isEmpty()) Text("No data used yet today.", fontSize = 13.sp, color = Palette.Muted)
    }
}

// ---- Building blocks ------------------------------------------------------------------------

@Composable
private fun Panel(
    title: String,
    trailing: String?,
    mono: FontFamily,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Surface.copy(alpha = 0.92f))
            .border(1.dp, Palette.Line, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "[ ${title.uppercase()} ]",
                fontFamily = mono,
                fontSize = 11.sp,
                letterSpacing = 0.12.em,
                color = Palette.Accent,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (trailing != null) Text(trailing, fontFamily = mono, fontSize = 11.sp, color = Palette.Muted, maxLines = 1)
        }
        content()
    }
}

@Composable
private fun Stat(label: String, value: String?, mono: FontFamily, warn: Boolean = false) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.weight(1f))
        Text(value, fontFamily = mono, fontSize = 13.sp, color = if (warn) Warn else Palette.Fg, maxLines = 1)
    }
}

@Composable
private fun ChartLabel(text: String, mono: FontFamily) {
    Text(text.uppercase(), fontFamily = mono, fontSize = 10.sp, letterSpacing = 0.1.em, color = Palette.Subtle)
}

@Composable
private fun RowScope.SpeedReadout(label: String, bytesPerSec: Long, color: Color, mono: FontFamily, modifier: Modifier) {
    val (value, unit) = speedParts(bytesPerSec)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontFamily = mono, fontSize = 10.sp, letterSpacing = 0.12.em, color = color)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, fontFamily = mono, fontSize = 26.sp, fontWeight = FontWeight.Medium)
            Text(unit, fontFamily = mono, fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Palette.Line)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(color))
    }
}

@Composable
private fun Ring(fraction: Float, color: Color, modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.semantics { contentDescription = "${(fraction * 100).roundToInt()} percent" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            // A 270° gauge open at the bottom, with tick marks every 10%.
            drawArc(Palette.Line, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Line charts that share one scale, drawn over a faint grid. */
@Composable
private fun Sparkline(series: List<Pair<List<Float>, Color>>, modifier: Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(10.dp)).background(Palette.Ground.copy(alpha = 0.6f))) {
        val grid = Palette.Line.copy(alpha = 0.6f)
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        val peak = max(series.maxOf { (values, _) -> values.maxOrNull() ?: 0f }, 1f)
        series.forEach { (values, color) ->
            if (values.size < 2) return@forEach
            val step = size.width / (values.size - 1)
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = i * step
                val y = size.height - (v / peak) * (size.height - 6.dp.toPx()) - 3.dp.toPx()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val fill = Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent)))
            drawPath(path, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val last = values.last()
            drawCircle(color, 3.dp.toPx(), Offset(size.width, size.height - (last / peak) * (size.height - 6.dp.toPx()) - 3.dp.toPx()))
        }
    }
}

/** A faint engineering grid behind the whole monitor. */
private fun Modifier.techGrid(): Modifier = drawBehind {
    val step = 24.dp.toPx()
    val color = Palette.Line.copy(alpha = 0.35f)
    var x = 0f
    while (x < size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

// ---- Formatting -----------------------------------------------------------------------------

private fun abs0(v: Float) = if (v < 0) -v else v

private fun displayText(view: View): String? {
    val display = view.display ?: return null
    val mode = display.mode
    return String.format(Locale.US, "%d × %d · %.0f Hz", mode.physicalWidth, mode.physicalHeight, display.refreshRate)
}

internal fun bytes(value: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var v = value.toDouble().coerceAtLeast(0.0)
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return if (i == 0) "${v.toLong()} B" else String.format(Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, units[i])
}

internal fun speedParts(bytesPerSec: Long): Pair<String, String> {
    val b = bytesPerSec.coerceAtLeast(0).toDouble()
    return when {
        b >= 1024 * 1024 -> String.format(Locale.US, "%.1f", b / (1024 * 1024)) to "MB/s"
        b >= 1024 -> String.format(Locale.US, "%.0f", b / 1024) to "KB/s"
        else -> String.format(Locale.US, "%.0f", b) to "B/s"
    }
}

private fun kbps(value: Int): String =
    if (value >= 1000) String.format(Locale.US, "%.0f Mbps", value / 1000.0) else "$value kbps"

private fun wifiQuality(dbm: Int): String = when {
    dbm >= -55 -> "Excellent"
    dbm >= -67 -> "Good"
    dbm >= -75 -> "Fair"
    else -> "Weak"
}

internal fun duration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes / 60) % 24
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
