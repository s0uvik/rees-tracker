package dev.reelscounter.app.stats

import dev.reelscounter.app.data.ReelEventStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/** Display helpers shared by screens. Pure, so they're JVM-testable. */
object Format {
  fun duration(ms: Long): String {
    val totalSeconds = (ms / 1000.0).roundToInt().coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
      h > 0 -> "${h}h ${m}m"
      m > 0 -> "${m}m"
      else -> "${s}s"
    }
  }

  fun seconds(ms: Double?): String {
    if (ms == null) return "—"
    return if (ms < 10_000) "%.1fs".format(ms / 1000) else "${(ms / 1000).roundToInt()}s"
  }

  /** 0–23 → "9 PM". */
  fun hour(hour: Int?): String {
    if (hour == null) return "—"
    val suffix = if (hour < 12) "AM" else "PM"
    val h = if (hour % 12 == 0) 12 else hour % 12
    return "$h $suffix"
  }

  enum class Tone { UP, DOWN, FLAT }

  fun change(pct: Double?): Pair<String, Tone> {
    if (pct == null) return "new" to Tone.UP
    if (abs(pct) < 0.5) return "0%" to Tone.FLAT
    val r = pct.roundToInt()
    return (if (r > 0) "+$r%" else "$r%") to (if (r > 0) Tone.UP else Tone.DOWN)
  }

  private val CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx")

  /** CSV of all views; values are numbers or known app keys, so no quoting is needed. */
  fun csv(rows: List<ReelEventStore.Row>, zone: ZoneId): String = buildString {
    append("id,app,viewed_at_local,viewed_at_ms,duration_ms\n")
    for (r in rows) {
      append(r.id).append(',').append(r.app).append(',')
        .append(CSV_TIME.format(Instant.ofEpochMilli(r.viewedAt).atZone(zone))).append(',')
        .append(r.viewedAt).append(',').append(r.durationMs ?: "").append('\n')
    }
  }
}
