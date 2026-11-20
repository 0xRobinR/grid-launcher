package app.gridlauncher.monitor

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import java.io.File
import java.util.Calendar
import kotlin.math.abs

data class BatteryInfo(
    val percent: Int,
    val charging: Boolean,
    val status: String,
    val source: String,
    val temperatureC: Float?,
    val voltageV: Float?,
    /** Positive while charging, negative while discharging; null if the phone doesn't report it. */
    val currentMa: Int?,
    val chargeMah: Int?,
    val health: String,
    val technology: String?,
    val cycleCount: Int?,
    val timeToFullMs: Long?,
)

data class NetworkInfo(
    val type: String,
    val online: Boolean,
    val metered: Boolean,
    val downBytesPerSec: Long,
    val upBytesPerSec: Long,
    val signalDbm: Int?,
    val signalLevel: Int?,
    val linkDownKbps: Int?,
    val linkUpKbps: Int?,
    val wifiLinkMbps: Int?,
    val wifiBand: String?,
    val carrier: String?,
    val receivedSinceBoot: Long?,
    val sentSinceBoot: Long?,
)

data class MemoryInfo(val totalBytes: Long, val availableBytes: Long, val low: Boolean)

data class StorageInfo(val totalBytes: Long, val freeBytes: Long)

data class CoreInfo(val currentMhz: Int?, val maxMhz: Int?)

data class CpuInfo(val cores: List<CoreInfo>, val soc: String?, val abi: String)

data class ThermalInfo(val status: String, val level: Int, val headroom: Float?)

data class DeviceInfo(
    val name: String,
    val android: String,
    val sdk: Int,
    val securityPatch: String,
    val uptimeMs: Long,
    val sensors: Int,
    val brightness: Int?,
)

data class AppUsage(val label: String, val foregroundMs: Long)

data class UsageInfo(val screenTimeMs: Long, val unlocks: Int, val topApps: List<AppUsage>)

data class AppData(val label: String, val bytes: Long)

data class DataUsage(val wifiBytes: Long, val mobileBytes: Long, val topApps: List<AppData>)

data class Snapshot(
    val battery: BatteryInfo?,
    val network: NetworkInfo,
    val memory: MemoryInfo,
    val storage: StorageInfo,
    val cpu: CpuInfo,
    val thermal: ThermalInfo,
    val device: DeviceInfo,
)

/**
 * Reads live phone statistics from public Android APIs. Nothing here needs a runtime permission,
 * except screen time, which needs the user to grant usage access in system settings.
 */
class SystemMonitor(context: Context) {
    private companion object {
        const val LOOKBACK_MS = 6 * 60 * 60 * 1000L
    }

    private val app = context.applicationContext
    private val batteryManager = app.getSystemService(BatteryManager::class.java)
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val activityManager = app.getSystemService(ActivityManager::class.java)
    private val powerManager = app.getSystemService(PowerManager::class.java)
    private val telephony = app.getSystemService(TelephonyManager::class.java)
    private val sensorCount by lazy {
        runCatching { app.getSystemService(SensorManager::class.java).getSensorList(Sensor.TYPE_ALL).size }.getOrDefault(0)
    }

    private var lastRx = -1L
    private var lastTx = -1L
    private var lastTrafficAt = 0L
    private var lastHeadroomAt = 0L
    private var lastHeadroom: Float? = null

    /** Call when sampling restarts, so the first speed isn't averaged over the time away. */
    fun resetTraffic() {
        lastRx = -1L
        lastTx = -1L
    }

    fun snapshot(): Snapshot = Snapshot(
        battery = readBattery(),
        network = readNetwork(),
        memory = readMemory(),
        storage = readStorage(),
        cpu = readCpu(),
        thermal = readThermal(),
        device = readDevice(),
    )

    // ---- Battery ----------------------------------------------------------------------------

    private fun readBattery(): BatteryInfo? {
        val intent = runCatching { app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
            ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val percent = if (level >= 0) level * 100 / scale else batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val statusCode = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val charging = statusCode == BatteryManager.BATTERY_STATUS_CHARGING || statusCode == BatteryManager.BATTERY_STATUS_FULL
        val status = when (statusCode) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }
        val source = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "Dock"
            else -> "Battery"
        }
        val health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            else -> "Unknown"
        }
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val cycles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1).takeIf { it >= 0 }
        } else {
            null
        }
        val timeToFull = if (charging) runCatching { batteryManager.computeChargeTimeRemaining() }.getOrDefault(-1L) else -1L
        return BatteryInfo(
            percent = percent.coerceIn(0, 100),
            charging = charging,
            status = status,
            source = source,
            temperatureC = if (temp != Int.MIN_VALUE) temp / 10f else null,
            voltageV = if (voltage > 0) voltage / 1000f else null,
            currentMa = microOrMilli(batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)),
            chargeMah = microOrMilli(batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER))?.let(::abs),
            health = health,
            technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() },
            cycleCount = cycles,
            timeToFullMs = timeToFull.takeIf { it > 0 },
        )
    }

    /** Most phones report micro-units, some report milli-units; values over 10,000 are micro. */
    private fun microOrMilli(raw: Int): Int? = when {
        raw == Int.MIN_VALUE || raw == 0 -> null
        abs(raw) > 10_000 -> raw / 1000
        else -> raw
    }

    // ---- Network ----------------------------------------------------------------------------

    private fun readNetwork(): NetworkInfo {
        val network = connectivity.activeNetwork
        val caps = network?.let { runCatching { connectivity.getNetworkCapabilities(it) }.getOrNull() }
        val type = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            else -> "Other"
        }
        val wifi = caps?.transportInfo as? WifiInfo
        val signal = caps?.signalStrength?.takeIf { it != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED }
        val cellLevel = if (type == "Mobile") runCatching { telephony?.signalStrength?.level }.getOrNull() else null

        val now = SystemClock.elapsedRealtime()
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        var down = 0L
        var up = 0L
        if (rx >= 0 && tx >= 0 && lastRx >= 0 && now > lastTrafficAt) {
            val seconds = (now - lastTrafficAt) / 1000.0
            down = ((rx - lastRx).coerceAtLeast(0) / seconds).toLong()
            up = ((tx - lastTx).coerceAtLeast(0) / seconds).toLong()
        }
        lastRx = rx
        lastTx = tx
        lastTrafficAt = now

        return NetworkInfo(
            type = type,
            online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            metered = caps != null && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            downBytesPerSec = down,
            upBytesPerSec = up,
            signalDbm = signal,
            signalLevel = cellLevel,
            linkDownKbps = caps?.linkDownstreamBandwidthKbps?.takeIf { it > 0 },
            linkUpKbps = caps?.linkUpstreamBandwidthKbps?.takeIf { it > 0 },
            wifiLinkMbps = wifi?.linkSpeed?.takeIf { it > 0 },
            wifiBand = wifi?.frequency?.takeIf { it > 0 }?.let {
                when {
                    it >= 5925 -> "6 GHz"
                    it >= 4900 -> "5 GHz"
                    else -> "2.4 GHz"
                }
            },
            carrier = runCatching { telephony?.networkOperatorName }.getOrNull()?.takeIf { it.isNotBlank() },
            receivedSinceBoot = rx.takeIf { it >= 0 },
            sentSinceBoot = tx.takeIf { it >= 0 },
        )
    }

    // ---- Memory, storage, CPU, thermal, device ----------------------------------------------

    private fun readMemory(): MemoryInfo {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return MemoryInfo(info.totalMem, info.availMem, info.lowMemory)
    }

    private fun readStorage(): StorageInfo {
        val stat = StatFs(Environment.getDataDirectory().path)
        return StorageInfo(stat.totalBytes, stat.availableBytes)
    }

    private fun readCpu(): CpuInfo {
        val count = Runtime.getRuntime().availableProcessors()
        val cores = (0 until count).map { i ->
            CoreInfo(
                currentMhz = readKhz("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")?.div(1000),
                maxMhz = readKhz("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")?.div(1000),
            )
        }
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
                .filter { it.isNotBlank() && it != Build.UNKNOWN }
                .joinToString(" ")
                .ifBlank { null }
        } else {
            null
        }
        return CpuInfo(cores, soc, Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
    }

    /** Some phones don't let apps read CPU clock speeds; those cores read as null. */
    private fun readKhz(path: String): Int? = runCatching { File(path).readText().trim().toInt() }.getOrNull()

    private fun readThermal(): ThermalInfo {
        val level = runCatching { powerManager.currentThermalStatus }.getOrDefault(PowerManager.THERMAL_STATUS_NONE)
        val status = when (level) {
            PowerManager.THERMAL_STATUS_NONE -> "Normal"
            PowerManager.THERMAL_STATUS_LIGHT -> "Light"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
            else -> "Unknown"
        }
        // The platform allows one headroom reading per second at most; refresh every few seconds.
        val now = SystemClock.elapsedRealtime()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && now - lastHeadroomAt >= 5_000) {
            lastHeadroomAt = now
            lastHeadroom = runCatching { powerManager.getThermalHeadroom(10) }.getOrNull()?.takeIf { !it.isNaN() }
        }
        return ThermalInfo(status, level, lastHeadroom)
    }

    private fun readDevice(): DeviceInfo {
        val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val name = if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) Build.MODEL else "$maker ${Build.MODEL}"
        return DeviceInfo(
            name = name,
            android = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            uptimeMs = SystemClock.elapsedRealtime(),
            sensors = sensorCount,
            brightness = runCatching { Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrNull(),
        )
    }

    // ---- Screen time ------------------------------------------------------------------------

    fun hasUsageAccess(): Boolean {
        val appOps = app.getSystemService(AppOpsManager::class.java)
        // Deprecated in API 36 but still the supported check on every version this app runs on.
        @Suppress("DEPRECATION")
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            app.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /** Data each app has used since midnight, on Wi-Fi and mobile; null without usage access. */
    @Suppress("DEPRECATION") // NetworkStatsManager still takes the old network type constants.
    fun readDataToday(): DataUsage? {
        if (!hasUsageAccess()) return null
        val nsm = app.getSystemService(NetworkStatsManager::class.java) ?: return null
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = System.currentTimeMillis()
        val perUid = HashMap<Int, Long>()
        var wifi = 0L
        var mobile = 0L
        for (type in listOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            val stats = runCatching { nsm.querySummary(type, null, start, end) }.getOrNull() ?: continue
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val bytes = bucket.rxBytes + bucket.txBytes
                perUid.merge(bucket.uid, bytes, Long::plus)
                if (type == ConnectivityManager.TYPE_WIFI) wifi += bytes else mobile += bytes
            }
            stats.close()
        }
        val pm = app.packageManager
        val top = perUid.entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(5).map { (uid, bytes) ->
            val label = when (uid) {
                NetworkStats.Bucket.UID_REMOVED -> "Removed apps"
                NetworkStats.Bucket.UID_TETHERING -> "Hotspot and tethering"
                Process.SYSTEM_UID -> "Android system"
                else -> pm.getPackagesForUid(uid)?.firstOrNull()?.let { pkg ->
                    runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                } ?: "App $uid"
            }
            AppData(label, bytes)
        }
        return DataUsage(wifi, mobile, top)
    }

    /** Screen time, unlocks and top apps since midnight; null without usage access. */
    fun readUsageToday(): UsageInfo? {
        if (!hasUsageAccess()) return null
        val usm = app.getSystemService(UsageStatsManager::class.java) ?: return null
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = System.currentTimeMillis()
        // Start a few hours early so a session that began before midnight is counted from midnight.
        val events = runCatching { usm.queryEvents(start - LOOKBACK_MS, end) }.getOrNull() ?: return null
        val event = UsageEvents.Event()
        val totals = HashMap<String, Long>()
        var unlocks = 0
        // One timeline: whichever app came to the front last is the one being used. This counts
        // split screen and picture-in-picture time once, not once per visible activity.
        var currentPkg: String? = null
        var currentSince = 0L
        fun close(at: Long) {
            val pkg = currentPkg ?: return
            val from = maxOf(currentSince, start)
            if (at > from) totals.merge(pkg, at - from, Long::plus)
            currentPkg = null
        }
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val t = event.timeStamp
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val pkg = event.packageName ?: continue
                    if (pkg != currentPkg) {
                        close(t)
                        currentPkg = pkg
                        currentSince = t
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    if (event.packageName == currentPkg) close(t)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN,
                UsageEvents.Event.DEVICE_SHUTDOWN -> close(t)
                UsageEvents.Event.KEYGUARD_HIDDEN -> if (t >= start) unlocks++
            }
        }
        // This runs while the launcher is in front, so any app still "open" is a stale record.
        if (currentPkg == app.packageName) close(end)
        totals.remove(app.packageName) // Time on the home screen isn't app time.
        val pm = app.packageManager
        val top = totals.entries.sortedByDescending { it.value }.take(5).map { (pkg, ms) ->
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            AppUsage(label, ms)
        }
        return UsageInfo(totals.values.sum(), unlocks, top)
    }
}
