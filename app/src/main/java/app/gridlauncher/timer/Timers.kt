package app.gridlauncher.timer

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import app.gridlauncher.MainActivity
import app.gridlauncher.R
import app.gridlauncher.data.PageOps
import app.gridlauncher.data.WidgetKind
import app.gridlauncher.launcherApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick timers run in the launcher itself, so the widget can stop them. An exact alarm fires at
 * the end; meanwhile a quiet notification counts down with a Cancel button, and at zero a loud
 * one rings with the alarm sound until dismissed.
 */
object Timers {
    private const val CHANNEL_RUNNING = "timers_running"
    private const val CHANNEL_DONE = "timers_done"
    const val ACTION_FIRE = "app.gridlauncher.timer.FIRE"
    const val ACTION_CANCEL = "app.gridlauncher.timer.CANCEL"
    const val ACTION_DISMISS = "app.gridlauncher.timer.DISMISS"
    const val EXTRA_UID = "uid"
    const val EXTRA_LENGTH = "length"

    private fun requestCode(uid: String) = uid.hashCode()
    private fun runningId(uid: String) = uid.hashCode()
    private fun doneId(uid: String) = uid.hashCode() xor 0x5A5A5A5A

    private fun intent(context: Context, action: String, uid: String, lengthMs: Long = 0L) =
        Intent(context, TimerReceiver::class.java).setAction(action).putExtra(EXTRA_UID, uid).putExtra(EXTRA_LENGTH, lengthMs)

    private fun broadcast(context: Context, action: String, uid: String, lengthMs: Long = 0L): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(uid) + action.hashCode(),
            intent(context, action, uid, lengthMs),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openLauncher(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Starts (or restarts) the timer for widget [uid], ending at [endsAt] wall-clock ms. */
    fun schedule(context: Context, uid: String, endsAt: Long, lengthMs: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val fire = broadcast(context, ACTION_FIRE, uid, lengthMs)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, fire)
        } else {
            // Without exact alarms the timer may ring a little late, but it still rings.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, fire)
        }
        showRunning(context, uid, endsAt, lengthMs)
    }

    /** Stops the timer for widget [uid] and removes both of its notifications. */
    fun cancel(context: Context, uid: String) {
        context.getSystemService(AlarmManager::class.java).cancel(broadcast(context, ACTION_FIRE, uid))
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(runningId(uid))
        nm.cancel(doneId(uid))
    }

    private fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_RUNNING) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_RUNNING, "Running timers", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "A countdown while a Quick timer runs"
                    setShowBadge(false)
                },
            )
        }
        if (nm.getNotificationChannel(CHANNEL_DONE) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, "Finished timers", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Rings when a Quick timer reaches zero"
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                    )
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
                },
            )
        }
    }

    private fun showRunning(context: Context, uid: String, endsAt: Long, lengthMs: Long) {
        if (!canNotify(context)) return
        channels(context)
        val minutes = (lengthMs / 60_000L).coerceAtLeast(1)
        val notification = Notification.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$minutes min timer")
            .setContentText("Running")
            .setWhen(endsAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Notification.CATEGORY_STOPWATCH else Notification.CATEGORY_PROGRESS)
            .setContentIntent(openLauncher(context))
            .addAction(Notification.Action.Builder(null, "Cancel", broadcast(context, ACTION_CANCEL, uid)).build())
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(runningId(uid), notification) }
    }

    internal fun showDone(context: Context, uid: String, lengthMs: Long) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(runningId(uid))
        if (!canNotify(context)) return
        channels(context)
        val minutes = (lengthMs / 60_000L).coerceAtLeast(1)
        val dismiss = broadcast(context, ACTION_DISMISS, uid)
        val notification = Notification.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Time's up")
            .setContentText("Your $minutes min timer has finished")
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openLauncher(context))
            .setDeleteIntent(dismiss)
            .addAction(Notification.Action.Builder(null, "Dismiss", dismiss).build())
            .build()
        // Keep ringing until the user dismisses it, like a clock app's timer.
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        runCatching { nm.notify(doneId(uid), notification) }
    }

    internal fun clearDone(context: Context, uid: String) {
        context.getSystemService(NotificationManager::class.java).cancel(doneId(uid))
    }
}

/** Fires, cancels and dismisses Quick timers, from the alarm and from notification buttons. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uid = intent.getStringExtra(Timers.EXTRA_UID) ?: return
        when (intent.action) {
            Timers.ACTION_FIRE -> Timers.showDone(context, uid, intent.getLongExtra(Timers.EXTRA_LENGTH, 0L))
            Timers.ACTION_DISMISS -> Timers.clearDone(context, uid)
            Timers.ACTION_CANCEL -> {
                Timers.cancel(context, uid)
                // Clear the widget's countdown too.
                val pending = goAsync()
                scope.launch {
                    try {
                        context.launcherApp.settings.updatePages { pages ->
                            PageOps.updateWidget(pages, uid) { it.copy(timerEndsAt = 0L, timerLengthMs = 0L) }
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/** Alarms don't survive a restart: put back any Quick timer that hasn't finished yet. */
class TimerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                context.launcherApp.settings.settings.first().pages
                    .flatMap { it.widgets }
                    .filter { it.kind == WidgetKind.TIMER && it.timerEndsAt > now }
                    .forEach { Timers.schedule(context, it.uid, it.timerEndsAt, it.timerLengthMs) }
            } finally {
                pending.finish()
            }
        }
    }
}
