package app.gridlauncher.monitor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.gridlauncher.MainActivity
import app.gridlauncher.R
import app.gridlauncher.launcherApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Every 15 minutes: logs the battery level for the 24-hour chart and, if monitor alerts are on,
 * warns when the phone runs hot or storage is nearly full. Each alert repeats at most every 6 hours.
 */
class MonitorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (battery != null) {
            val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (level >= 0) {
                BatteryHistory.record(
                    context,
                    BatterySample(
                        System.currentTimeMillis(),
                        level * 100 / scale,
                        status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
                    ),
                )
            }
        }
        val alertsOn = runCatching { context.launcherApp.settings.settings.first().monitorAlerts }.getOrDefault(false)
        if (alertsOn) checkAlerts(context, battery)
        return Result.success()
    }

    private fun checkAlerts(context: Context, battery: Intent?) {
        val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.div(10f)
        val thermal = runCatching { context.getSystemService(PowerManager::class.java).currentThermalStatus }.getOrDefault(0)
        if (thermal >= PowerManager.THERMAL_STATUS_SEVERE || (temp != null && temp >= HOT_C)) {
            alert(
                context, KEY_HOT, ID_HOT,
                "Your phone is running hot",
                buildString {
                    append(if (temp != null) "Battery at ${temp.toInt()}°C. " else "")
                    append("Let it cool down: close heavy apps and take it out of the sun.")
                },
            )
        }
        val stat = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        if (stat != null && stat.totalBytes > 0 && stat.availableBytes.toDouble() / stat.totalBytes < LOW_STORAGE) {
            alert(
                context, KEY_STORAGE, ID_STORAGE,
                "Storage is almost full",
                "Only ${stat.availableBytes / (1024 * 1024 * 1024)} GB left. Free some space to keep the phone running smoothly.",
            )
        }
    }

    private fun alert(context: Context, key: String, id: Int, title: String, text: String) {
        val prefs = context.getSharedPreferences("monitor_alerts", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(key, 0L) < REPEAT_MS) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Phone monitor alerts", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(id, notification) }
        prefs.edit { putLong(key, now) }
    }

    companion object {
        private const val NAME = "phone-monitor"
        private const val CHANNEL = "monitor_alerts"
        private const val KEY_HOT = "hot"
        private const val KEY_STORAGE = "storage"
        private const val ID_HOT = 0x4D01
        private const val ID_STORAGE = 0x4D02
        private const val HOT_C = 45f
        private const val LOW_STORAGE = 0.10
        private const val REPEAT_MS = 6 * 60 * 60 * 1000L

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MonitorWorker>(15, TimeUnit.MINUTES).build(),
            )
        }
    }
}
