package app.gridlauncher.gestures

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import app.gridlauncher.data.GestureAction

/**
 * Lets home-screen gestures open notifications or quick settings and lock the screen. Android
 * offers these to third-party apps only through an accessibility service the user turns on. The
 * service reads nothing on screen; it only performs those three system actions.
 */
class GestureService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: GestureService? = null

        val isEnabled: Boolean get() = instance != null

        /** Performs [action]; returns false if it needs the service and the service is off. */
        fun perform(action: GestureAction): Boolean {
            val service = instance ?: return action == GestureAction.NONE
            val global = when (action) {
                GestureAction.NONE -> return true
                GestureAction.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
                GestureAction.QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
                GestureAction.LOCK -> GLOBAL_ACTION_LOCK_SCREEN
            }
            return service.performGlobalAction(global)
        }

        fun openSettings(context: Context) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
