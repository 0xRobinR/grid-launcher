package app.gridlauncher.monitor

import android.content.Context
import java.io.File

/** One battery reading: when, the charge level, and whether it was charging. */
data class BatterySample(val time: Long, val percent: Int, val charging: Boolean)

/**
 * A small on-phone log of battery readings for the monitor's 24-hour chart. Readings come from
 * a background check every 15 minutes, plus the monitor itself while it's open. Two days are kept.
 */
object BatteryHistory {
    private const val FILE = "battery_history.csv"
    private const val KEEP_MS = 48 * 60 * 60 * 1000L
    private const val MIN_GAP_MS = 5 * 60 * 1000L
    private val lock = Any()

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    fun record(context: Context, sample: BatterySample) {
        synchronized(lock) {
            val samples = readAll(context)
            val last = samples.lastOrNull()
            // Skip near-duplicates: same level and state within a few minutes.
            if (last != null && sample.time - last.time < MIN_GAP_MS && last.percent == sample.percent && last.charging == sample.charging) return
            val kept = (samples + sample).filter { it.time >= sample.time - KEEP_MS }
            runCatching {
                file(context).writeText(kept.joinToString("\n") { "${it.time},${it.percent},${if (it.charging) 1 else 0}" })
            }
        }
    }

    fun read(context: Context, sinceMs: Long): List<BatterySample> = synchronized(lock) {
        readAll(context).filter { it.time >= sinceMs }
    }

    private fun readAll(context: Context): List<BatterySample> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            f.readLines().mapNotNull { line ->
                val parts = line.split(',')
                if (parts.size != 3) return@mapNotNull null
                BatterySample(parts[0].toLongOrNull() ?: return@mapNotNull null, parts[1].toIntOrNull() ?: return@mapNotNull null, parts[2] == "1")
            }.sortedBy { it.time }
        }.getOrDefault(emptyList())
    }
}
