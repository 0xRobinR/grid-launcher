package app.gridlauncher.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow

/**
 * The live levels of the sound playing on the phone, for the music effect. Filled in by Android's
 * audio visualizer from the phone's audio output; read every frame by the drawing. Nothing is
 * recorded, stored or sent anywhere.
 */
class AudioLevels {
    /** Loudness per frequency band, low to high, 0..1. */
    val bands = FloatArray(BANDS)
    /** The sound wave itself, -1..1. */
    val wave = FloatArray(WAVE_POINTS)
    /** Overall loudness, 0..1. */
    @Volatile var energy = 0f
    /** False until the first data arrives, or if the visualizer couldn't start. */
    @Volatile var live = false

    companion object {
        const val BANDS = 32
        const val WAVE_POINTS = 96
    }
}

private const val TAG = "AudioLevels"

/** Whether the user has allowed the microphone permission, which Android requires for the visualizer. */
fun hasAudioPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/**
 * Runs the visualizer while [active], and returns the levels it fills in. With [active] false, or
 * without the permission, nothing runs and the levels stay silent.
 */
@Composable
fun rememberAudioLevels(context: Context, active: Boolean): AudioLevels {
    val levels = remember { AudioLevels() }
    DisposableEffect(active) {
        if (!active || !hasAudioPermission(context)) {
            levels.live = false
            return@DisposableEffect onDispose { }
        }
        val visualizer = runCatching {
            // Session 0 is the phone's mixed audio output: whatever app is playing.
            Visualizer(0).apply {
                enabled = false
                captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer?, data: ByteArray?, rate: Int) {
                            data ?: return
                            val step = data.size / AudioLevels.WAVE_POINTS.toFloat()
                            for (i in 0 until AudioLevels.WAVE_POINTS) {
                                val b = data[(i * step).toInt().coerceAtMost(data.size - 1)].toInt() and 0xFF
                                levels.wave[i] = (b - 128) / 128f
                            }
                        }

                        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {
                            fft ?: return
                            val n = fft.size / 2
                            var total = 0f
                            for (b in 0 until AudioLevels.BANDS) {
                                // Bands spaced like hearing: narrow at the bottom, wide at the top.
                                val from = (n.toDouble().pow(b / AudioLevels.BANDS.toDouble())).toInt().coerceIn(1, n - 1)
                                val to = (n.toDouble().pow((b + 1) / AudioLevels.BANDS.toDouble())).toInt().coerceIn(from + 1, n)
                                var sum = 0f
                                for (k in from until to) {
                                    val re = fft[2 * k].toFloat()
                                    val im = fft[2 * k + 1].toFloat()
                                    sum += hypot(re, im)
                                }
                                val avg = sum / (to - from)
                                val db = (20 * log10(1f + avg)).coerceIn(0f, 42f) / 42f
                                // Rise at once, fall gently, so bars don't flicker.
                                levels.bands[b] = maxOf(db, levels.bands[b] * 0.82f)
                                total += levels.bands[b]
                            }
                            levels.energy = total / AudioLevels.BANDS
                            levels.live = true
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    true,
                    true,
                )
                enabled = true
            }
        }.onFailure { Log.w(TAG, "The audio visualizer isn't available here", it) }.getOrNull()
        onDispose {
            levels.live = false
            runCatching {
                visualizer?.enabled = false
                visualizer?.release()
            }
        }
    }
    return levels
}
