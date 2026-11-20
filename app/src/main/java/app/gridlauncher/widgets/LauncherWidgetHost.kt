package app.gridlauncher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.hypot

class LauncherWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?,
    ): AppWidgetHostView = LauncherWidgetHostView(context)
}

/**
 * Hosts one app widget. A press held without moving opens edit mode; everything else
 * (taps, scrolling lists inside the widget) reaches the widget untouched.
 */
class LauncherWidgetHostView(context: Context) : AppWidgetHostView(context) {

    var onLongPress: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private var downX = 0f
    private var downY = 0f
    private var longPressFired = false
    private var cancelSent = false
    private var appliedWidthDp = -1f
    private var appliedHeightDp = -1f

    private val longPressRunnable = Runnable {
        if (isAttachedToWindow && onLongPress != null) {
            longPressFired = true
            cancelSent = false
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLongPress?.invoke()
        }
    }

    /**
     * Watches every event of the gesture here rather than in onInterceptTouchEvent: when no child
     * claims the down event (a blank part of the widget), ViewGroup stops calling the intercept
     * hook, and the long-press timer would never be cancelled by the finger lifting.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressFired = false
                downX = ev.x
                downY = ev.y
                removeCallbacks(longPressRunnable)
                postDelayed(longPressRunnable, longPressTimeout)
            }
            MotionEvent.ACTION_MOVE ->
                if (hypot(ev.x - downX, ev.y - downY) > touchSlop) removeCallbacks(longPressRunnable)
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                removeCallbacks(longPressRunnable)
        }
        if (longPressFired) {
            // The long press opened edit mode: cancel the widget's own gesture once, then swallow the rest.
            if (!cancelSent) {
                cancelSent = true
                val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
            }
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                longPressFired = false
                cancelSent = false
            }
            return true
        }
        val handled = super.dispatchTouchEvent(ev)
        // Keep receiving the gesture even when no child wants it, so the timer is always cancelled.
        return handled || ev.actionMasked == MotionEvent.ACTION_DOWN
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // A child (for example a list inside the widget) took the gesture: it is not a long press.
        if (disallowIntercept) removeCallbacks(longPressRunnable)
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        removeCallbacks(longPressRunnable)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        longPressFired = false
        cancelSent = false
        super.onDetachedFromWindow()
    }

    /** Tells the widget the size it is shown at so it can pick the right layout. */
    fun applySize(widthDp: Float, heightDp: Float) {
        if (widthDp == appliedWidthDp && heightDp == appliedHeightDp) return
        appliedWidthDp = widthDp
        appliedHeightDp = heightDp
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
        } else {
            val w = widthDp.toInt()
            val h = heightDp.toInt()
            @Suppress("DEPRECATION")
            updateAppWidgetSize(null, w, h, w, h)
        }
    }
}
