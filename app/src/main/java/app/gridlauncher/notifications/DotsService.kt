package app.gridlauncher.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How many notifications each app has showing, for the dots on app icons. */
object NotificationCounts {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    fun key(packageName: String, user: UserHandle) = "$packageName|${user.hashCode()}"

    internal fun publish(counts: Map<String, Int>) {
        _counts.value = counts
    }

    private val _arrivals = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    /** One event each time a new (not ongoing) notification arrives, for the edge flash. */
    val arrivals: kotlinx.coroutines.flow.SharedFlow<Unit> = _arrivals

    internal fun arrived() {
        _arrivals.tryEmit(Unit)
    }

    fun hasAccess(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    fun openSettings(context: Context) {
        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, DotsService::class.java).flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
    }
}

/**
 * Counts notifications per app. It only reads which app each notification belongs to; titles and
 * text are never looked at or stored.
 */
class DotsService : NotificationListenerService() {
    override fun onListenerConnected() = refresh()

    override fun onListenerDisconnected() = NotificationCounts.publish(emptyMap())

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refresh()
        // A fresh notification from another app, not an update to something ongoing like music.
        if (sbn != null && !sbn.isOngoing && sbn.packageName != packageName &&
            sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 &&
            sbn.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE == 0
        ) {
            NotificationCounts.arrived()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        NotificationCounts.publish(
            active
                .filter { !it.isOngoing && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
                .groupingBy { NotificationCounts.key(it.packageName, it.user) }
                .eachCount(),
        )
    }
}
