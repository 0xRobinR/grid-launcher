package app.gridlauncher.ui.home

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.gridlauncher.ui.components.startSafely
import app.gridlauncher.ui.theme.IconLibrary
import app.gridlauncher.ui.theme.LocalWidgetCorner
import app.gridlauncher.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt

private val Charge = Color(0xFF8FD6A8)

/** The shared card: a dark surface that opens edit mode on long-press. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SmartCard(
    interactive: Boolean,
    onLongPress: () -> Unit,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(LocalWidgetCorner.current))
            .background(Palette.Surface)
            .combinedClickable(
                enabled = interactive,
                onClick = { onClick?.invoke() },
                onLongClick = onLongPress,
            )
            .padding(14.dp),
    ) { content() }
}

@Composable
private fun CardLabel(text: String) {
    Text(text.uppercase(), fontSize = 10.sp, letterSpacing = 0.12.em, color = Palette.Muted, maxLines = 1)
}

// ---- Battery ----------------------------------------------------------------------------------

private class BatteryState(val percent: Int, val charging: Boolean, val temperatureC: Float?)

@Composable
fun BatteryWidget(interactive: Boolean, onLongPress: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<BatteryState?>(null) }
    // Listen only while the home screen is visible; the sticky broadcast catches up on return.
    LifecycleStartEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                state = BatteryState(
                    percent = if (level >= 0) level * 100 / scale else 0,
                    charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
                    temperatureC = if (temp != Int.MIN_VALUE) temp / 10f else null,
                )
            }
        }
        // The battery broadcast is sticky, so registering delivers the current state at once.
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onStopOrDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val b = state
    SmartCard(interactive, onLongPress, onClick = { context.startSafely(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val ring = minOf(maxHeight, 84.dp)
            val wide = maxWidth >= 150.dp
            val color = when {
                b == null -> Palette.Line
                b.charging -> Charge
                b.percent <= 15 -> Palette.Danger
                else -> Palette.Accent
            }
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(ring).semantics { contentDescription = "Battery ${b?.percent ?: 0} percent" }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 7.dp.toPx()
                        val arc = Size(size.width - stroke, size.height - stroke)
                        val tl = Offset(stroke / 2, stroke / 2)
                        drawArc(Palette.Line, 135f, 270f, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                        drawArc(color, 135f, 270f * ((b?.percent ?: 0) / 100f), false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    if (!wide) Text("${b?.percent ?: "–"}", fontSize = 20.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
                }
                if (wide) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        CardLabel("Battery")
                        Text("${b?.percent ?: "–"}%", fontSize = 30.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
                        Text(
                            when {
                                b == null -> "Reading…"
                                b.charging -> "Charging" + (b.temperatureC?.let { " · ${it.roundToInt()}°C" } ?: "")
                                else -> "On battery" + (b.temperatureC?.let { " · ${it.roundToInt()}°C" } ?: "")
                            },
                            fontSize = 12.sp,
                            color = if (b?.charging == true) Charge else Palette.Muted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// ---- Quick controls ---------------------------------------------------------------------------

@Composable
fun ControlsWidget(interactive: Boolean, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val camera = remember { context.getSystemService(CameraManager::class.java) }
    val torchId = remember {
        runCatching {
            camera?.cameraIdList?.firstOrNull { id ->
                val c = camera.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        }.getOrNull()
    }
    var torchOn by remember { mutableStateOf(false) }
    DisposableEffect(torchId) {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == torchId) torchOn = enabled
            }
        }
        if (torchId != null) runCatching { camera.registerTorchCallback(callback, Handler(Looper.getMainLooper())) }
        onDispose { runCatching { camera?.unregisterTorchCallback(callback) } }
    }
    SmartCard(interactive, onLongPress) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            if (torchId != null) {
                ControlButton("Flashlight", IconLibrary.vector("bolt"), active = torchOn, enabled = interactive) {
                    runCatching { camera.setTorchMode(torchId, !torchOn) }
                }
            }
            ControlButton("Internet", IconLibrary.vector("globe"), enabled = interactive) {
                context.startSafely(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            }
            ControlButton("Bluetooth", BluetoothIcon, enabled = interactive) {
                context.startSafely(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            ControlButton("Volume", VolumeIcon, enabled = interactive) {
                context.startSafely(Intent(Settings.Panel.ACTION_VOLUME))
            }
        }
    }
}

private val BluetoothIcon by lazy { lineIcon("bluetooth", "M7 7l10 10-5 4V3l5 4L7 17") }
private val VolumeIcon by lazy { lineIcon("volume", "M4 9h4l5-4v14l-5-4H4z M16 9a4 4 0 0 1 0 6 M19 6a8 8 0 0 1 0 12") }

private fun lineIcon(name: String, d: String): ImageVector =
    androidx.compose.ui.graphics.vector.ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        d.split(" M").forEachIndexed { i, part ->
            addPath(
                pathData = androidx.compose.ui.graphics.vector.addPathNodes(if (i == 0) part else "M$part"),
                stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
            )
        }
    }.build()

@Composable
private fun ControlButton(label: String, icon: ImageVector, active: Boolean = false, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (active) Palette.Accent else Palette.Surface2)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (active) Palette.Ground else Palette.Fg, modifier = Modifier.size(22.dp))
        }
        Text(label, fontSize = 10.sp, color = Palette.Muted, maxLines = 1)
    }
}

// ---- Up next (calendar) -----------------------------------------------------------------------

private class AgendaEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    /** Local calendar days (Julian day numbers) the event covers; what all-day events go by. */
    val startDay: Int,
    val endDay: Int,
)

/** Today's Julian day number, the same scale as CalendarContract's START_DAY and END_DAY. */
private fun todayJulian(): Int = java.time.LocalDate.now().getLong(java.time.temporal.JulianFields.JULIAN_DAY).toInt()

private fun readAgenda(context: Context, now: Long): List<AgendaEvent> {
    val endOfTomorrow = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, 2)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, now - DateUtils.DAY_IN_MILLIS)
        ContentUris.appendId(it, endOfTomorrow)
    }.build()
    val projection = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.DISPLAY_COLOR,
        CalendarContract.Instances.START_DAY,
        CalendarContract.Instances.END_DAY,
    )
    val today = todayJulian()
    val result = ArrayList<AgendaEvent>()
    runCatching {
        context.contentResolver.query(uri, projection, "${CalendarContract.Instances.VISIBLE} = 1", null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val begin = c.getLong(2)
                val end = c.getLong(3)
                val allDay = c.getInt(4) == 1
                val startDay = c.getInt(6)
                val endDay = c.getInt(7)
                // All-day events are stored at UTC midnight, so they're judged by local day number;
                // timed events by the clock. Either way: still to come, and no later than tomorrow.
                val keep = if (allDay) endDay >= today && startDay <= today + 1 else end > now && begin < endOfTomorrow
                if (!keep) continue
                result += AgendaEvent(
                    id = c.getLong(0),
                    title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(No title)",
                    begin = begin,
                    end = end,
                    allDay = allDay,
                    color = c.getInt(5),
                    startDay = startDay,
                    endDay = endDay,
                )
            }
        }
    }
    // Timed events first in time order; all-day events after them for the same day.
    return result.sortedWith(compareBy({ it.allDay }, { it.begin })).take(6)
}

@Composable
fun AgendaWidget(interactive: Boolean, onLongPress: () -> Unit) {
    val context = LocalContext.current
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    var allowed by remember { mutableStateOf(granted()) }
    var events by remember { mutableStateOf<List<AgendaEvent>?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // After "Don't allow" twice, Android stops showing the prompt; then the button opens settings.
    var blocked by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        val activity = context.findActivity()
        blocked = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CALENDAR)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(allowed, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                allowed = granted()
                now = System.currentTimeMillis()
                events = if (allowed) withContext(Dispatchers.IO) { readAgenda(context, now) } else null
                delay(60_000)
            }
        }
    }
    SmartCard(interactive, onLongPress, onClick = {
        context.startSafely(Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(now.toString()).build()))
    }) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardLabel("Up next")
            when {
                !allowed -> {
                    Text("See your next events here.", fontSize = 13.sp, color = Palette.FgSoft)
                    Box(
                        Modifier
                            .height(40.dp)
                            .clip(CircleShape)
                            .background(Palette.Accent)
                            .clickable(enabled = interactive, role = Role.Button) {
                                if (blocked) {
                                    context.startSafely(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                                    )
                                } else {
                                    ask.launch(Manifest.permission.READ_CALENDAR)
                                }
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (blocked) "Allow in settings" else "Allow calendar",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Palette.Ground,
                        )
                    }
                }
                events == null -> Text("Loading…", fontSize = 13.sp, color = Palette.Muted)
                events!!.isEmpty() -> Text("Nothing else today or tomorrow.", fontSize = 13.sp, color = Palette.Muted)
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val fit = (maxHeight.value / 40f).toInt().coerceAtLeast(1)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        events!!.take(fit).forEach { e -> AgendaRow(e, now, interactive) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaRow(e: AgendaEvent, now: Long, interactive: Boolean) {
    val context = LocalContext.current
    val is24 = DateFormat.is24HourFormat(context)
    val timeFormat = remember(is24) { java.text.SimpleDateFormat(if (is24) "HH:mm" else "h:mm", java.util.Locale.getDefault()) }
    val today = DateUtils.isToday(e.begin) || (e.begin < now && e.end > now)
    val whenText = when {
        e.allDay -> if (e.startDay <= todayJulian()) "Today · all day" else "Tomorrow · all day"
        e.begin <= now -> "Now · until ${timeFormat.format(Date(e.end))}"
        e.begin - now < DateUtils.HOUR_IN_MILLIS -> "In ${((e.begin - now) / 60_000).coerceAtLeast(1)} min · ${timeFormat.format(Date(e.begin))}"
        today -> "Today · ${timeFormat.format(Date(e.begin))}"
        else -> "Tomorrow · ${timeFormat.format(Date(e.begin))}"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = interactive, role = Role.Button) {
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id)
                context.startSafely(
                    Intent(Intent.ACTION_VIEW, uri)
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().clip(CircleShape).background(if (e.color != 0) Color(e.color) else Palette.Accent))
        Column(Modifier.weight(1f)) {
            Text(e.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(whenText, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Palette.Muted, maxLines = 1)
        }
    }
}

// ---- Quick timer ------------------------------------------------------------------------------

/**
 * One-tap timers, run by the launcher itself: tap a length to start, and the widget counts down
 * with +1 min and Stop. A notification counts down too, and rings at zero.
 */
@Composable
fun TimerWidget(
    interactive: Boolean,
    endsAt: Long,
    lengthMs: Long,
    onLongPress: () -> Unit,
    onStart: (Int) -> Unit,
    onExtend: () -> Unit,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Tick once a second while a countdown is showing and the home screen is visible.
    LaunchedEffect(endsAt, lifecycle) {
        if (endsAt <= 0L) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                if (now >= endsAt + DONE_SHOWN_MS) break
                delay(1_000L - now % 1_000L + 5L)
            }
        }
    }
    // Without notification permission the timer still runs, but can't ring: ask on first use.
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) onMessage("Timers will count down here, but can't ring without notifications")
    }
    fun start(minutes: Int?) {
        if (minutes == null) {
            // Any other length: the clock app's own timer screen.
            if (!context.startSafely(Intent(AlarmClock.ACTION_SET_TIMER))) onMessage("No clock app can set timers")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        now = System.currentTimeMillis()
        onStart(minutes)
    }
    val running = endsAt > 0L && now < endsAt
    val justDone = endsAt > 0L && now >= endsAt && now < endsAt + DONE_SHOWN_MS
    SmartCard(interactive, onLongPress) {
        if (running || justDone) {
            Countdown(
                remainingMs = (endsAt - now).coerceAtLeast(0L),
                lengthMs = lengthMs.coerceAtLeast(1L),
                endsAt = endsAt,
                done = justDone,
                interactive = interactive,
                onExtend = onExtend,
                onDismiss = onDismiss,
            )
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                CardLabel("Quick timer")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 5, 10, 25).forEach { m ->
                        TimerChip("${m}m", Modifier.weight(1f), interactive) { start(m) }
                    }
                    TimerChip("…", Modifier.weight(0.7f), interactive, label = "Other length, in the clock app") { start(null) }
                }
            }
        }
    }
}

/** How long "Time's up" stays on the widget after a timer ends. */
private const val DONE_SHOWN_MS = 60_000L

@Composable
private fun Countdown(
    remainingMs: Long,
    lengthMs: Long,
    endsAt: Long,
    done: Boolean,
    interactive: Boolean,
    onExtend: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val is24 = DateFormat.is24HourFormat(context)
    val endText = remember(endsAt, is24) {
        java.text.SimpleDateFormat(if (is24) "HH:mm" else "h:mm a", java.util.Locale.getDefault()).format(Date(endsAt))
    }
    val totalSeconds = (remainingMs + 999) / 1000
    val clock = if (totalSeconds >= 3600) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", totalSeconds / 3600, (totalSeconds / 60) % 60, totalSeconds % 60)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
    }
    val progress = 1f - (remainingMs.toFloat() / lengthMs).coerceIn(0f, 1f)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (done) "Time's up" else clock,
                    fontSize = 28.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = if (done) Charge else Palette.Fg,
                    maxLines = 1,
                    modifier = Modifier.semantics { contentDescription = if (done) "Timer finished" else "$clock left" },
                )
                Text(
                    if (done) "Ended at $endText" else "${lengthMs / 60_000} min · ends $endText",
                    fontSize = 11.sp,
                    color = Palette.Muted,
                    maxLines = 1,
                )
            }
            if (!done) RoundIcon("Add a minute", PlusOneIcon, interactive, onExtend)
            RoundIcon(if (done) "Dismiss" else "Stop timer", app.gridlauncher.ui.theme.LauncherIcons.Close, interactive, onDismiss)
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Palette.Line)) {
            Box(Modifier.fillMaxWidth(if (done) 1f else progress).height(4.dp).clip(CircleShape).background(if (done) Charge else Palette.Accent))
        }
    }
}

private val PlusOneIcon by lazy { lineIcon("plus_one", "M5 12h8 M9 8v8 M17 7v10 M15 9l2-2") }

@Composable
private fun RoundIcon(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 6.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(Palette.Surface2)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Palette.Fg, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TimerChip(text: String, modifier: Modifier, enabled: Boolean, label: String = "Start a $text timer", onClick: () -> Unit) {
    Box(
        modifier
            .height(40.dp)
            .clip(CircleShape)
            .border(1.dp, Palette.Line, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
    }
}

// ---- Quick note -------------------------------------------------------------------------------

@Composable
fun NoteWidget(text: String, interactive: Boolean, onLongPress: () -> Unit, onEdit: () -> Unit) {
    SmartCard(interactive, onLongPress, onClick = onEdit) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CardLabel("Note")
            if (text.isBlank()) {
                Text("Tap to write a note", fontSize = 15.sp, color = Palette.Subtle)
            } else {
                Text(text, fontSize = 15.sp, lineHeight = 21.sp, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorSheet(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    ModalBottomSheet(
        // Swiping the sheet away or pressing Back keeps the note; only Cancel throws edits away.
        onDismissRequest = { if (text != initial) onSave(text.trimEnd()) else onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = Palette.Surface,
        contentColor = Palette.Fg,
        scrimColor = Color.Black.copy(alpha = 0.55f),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Note", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.Muted) }
                TextButton(onClick = { onSave(text.trimEnd()) }) { Text("Save", color = Palette.Accent, fontWeight = FontWeight.SemiBold) }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(1000) },
                placeholder = { Text("Groceries, a door code, today's three things…") },
                minLines = 5,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.Accent,
                    unfocusedBorderColor = Palette.Line,
                    cursorColor = Palette.Accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Notes stay on this phone.", fontSize = 12.sp, color = Palette.Muted)
        }
    }
}

// ---- Day progress -----------------------------------------------------------------------------

/** How far through the current day, week, month and year [now] is, from 0 to 1. */
internal fun progressOf(now: Calendar): List<Pair<String, Float>> {
    fun span(start: Calendar, field: Int): Float {
        val end = (start.clone() as Calendar).apply { add(field, 1) }
        return ((now.timeInMillis - start.timeInMillis).toFloat() / (end.timeInMillis - start.timeInMillis)).coerceIn(0f, 1f)
    }
    fun startOfDay() = (now.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val week = startOfDay().apply {
        val back = (get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7
        add(Calendar.DAY_OF_YEAR, -back)
    }
    return listOf(
        "Day" to span(startOfDay(), Calendar.DAY_OF_YEAR),
        "Week" to span(week, Calendar.WEEK_OF_YEAR),
        "Month" to span(startOfDay().apply { set(Calendar.DAY_OF_MONTH, 1) }, Calendar.MONTH),
        "Year" to span(startOfDay().apply { set(Calendar.DAY_OF_YEAR, 1) }, Calendar.YEAR),
    )
}

@Composable
fun ProgressWidget(interactive: Boolean, onLongPress: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(60_000L - now % 60_000L + 20L)
            }
        }
    }
    val rows = remember(now) { progressOf(Calendar.getInstance().apply { timeInMillis = now }) }
    SmartCard(interactive, onLongPress) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val fit = ((maxHeight.value - 18f) / 24f).toInt().coerceIn(1, 4)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardLabel("Progress")
                rows.take(fit).forEach { (label, f) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(label, fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.width(44.dp))
                        Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(Palette.Line)) {
                            Box(Modifier.fillMaxWidth(f).height(6.dp).clip(CircleShape).background(Palette.Accent))
                        }
                        Text("${(f * 100).toInt()}%", fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(40.dp))
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
