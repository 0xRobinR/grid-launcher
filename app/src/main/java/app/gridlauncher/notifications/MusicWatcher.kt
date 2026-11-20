package app.gridlauncher.notifications

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Whether any app is playing music or other media right now. It reads only the play state of
 * media sessions, which Android shares with apps that have notification access (the same access
 * notification dots use); without that access it stays false. No sound is listened to.
 */
@Composable
fun rememberMediaPlaying(enabled: Boolean): Boolean {
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(context, enabled) {
        if (!enabled || !NotificationCounts.hasAccess(context)) {
            playing = false
            return@DisposableEffect onDispose { }
        }
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val listener = ComponentName(context, DotsService::class.java)
        val watched = mutableMapOf<MediaController, MediaController.Callback>()
        fun update() {
            playing = watched.keys.any { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        }
        fun watch(controllers: List<MediaController>?) {
            watched.forEach { (c, cb) -> c.unregisterCallback(cb) }
            watched.clear()
            controllers.orEmpty().forEach { c ->
                val cb = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) = update()
                    override fun onSessionDestroyed() = update()
                }
                c.registerCallback(cb)
                watched[c] = cb
            }
            update()
        }
        val sessions = MediaSessionManager.OnActiveSessionsChangedListener { watch(it) }
        runCatching {
            manager?.addOnActiveSessionsChangedListener(sessions, listener)
            watch(manager?.getActiveSessions(listener))
        }
        onDispose {
            runCatching { manager?.removeOnActiveSessionsChangedListener(sessions) }
            watched.forEach { (c, cb) -> c.unregisterCallback(cb) }
        }
    }
    return playing
}

/** True if this context may read media sessions (it has notification access). */
fun canWatchMedia(context: Context) = NotificationCounts.hasAccess(context)
