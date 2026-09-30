package dev.reelscounter.app.badge

import java.time.Instant
import java.time.ZoneId

/**
 * In-memory cache of today's totals for the badge, reset at local midnight.
 * Seeded from the native store, then updated incrementally per view so the
 * badge never has to query the database on the hot path.
 */
class DailyCounter(private val zone: () -> ZoneId = { ZoneId.systemDefault() }) {
  private var dayKey = Long.MIN_VALUE
  private var count = 0
  private var durationMs = 0L

  fun startOfDayMs(nowMs: Long): Long {
    val z = zone()
    return Instant.ofEpochMilli(nowMs).atZone(z).toLocalDate().atStartOfDay(z).toInstant().toEpochMilli()
  }

  fun msUntilNextMidnight(nowMs: Long): Long {
    val z = zone()
    val next = Instant.ofEpochMilli(nowMs).atZone(z).toLocalDate().plusDays(1).atStartOfDay(z)
    return (next.toInstant().toEpochMilli() - nowMs).coerceAtLeast(1L)
  }

  fun load(count: Int, durationMs: Long, nowMs: Long) {
    dayKey = dayOf(nowMs)
    this.count = count
    this.durationMs = durationMs
  }

  fun increment(nowMs: Long): Int {
    rollIfNeeded(nowMs)
    count++
    return count
  }

  /** Only counts duration for views that started today. */
  fun addDuration(ms: Long, viewStartedAtMs: Long, nowMs: Long) {
    rollIfNeeded(nowMs)
    if (ms > 0 && dayOf(viewStartedAtMs) == dayKey) durationMs += ms
  }

  fun count(nowMs: Long): Int {
    rollIfNeeded(nowMs)
    return count
  }

  fun durationMs(nowMs: Long): Long {
    rollIfNeeded(nowMs)
    return durationMs
  }

  /**
   * Today's watch time including the reel playing right now, for the live
   * badge clock. Mirrors [addDuration]: an open segment that started before
   * today's midnight is not counted, so the clock never jumps back when the
   * view finally closes.
   */
  fun liveDurationMs(nowMs: Long, openSinceMs: Long?, openElapsedMs: Long): Long {
    val closed = durationMs(nowMs)
    if (openSinceMs == null || openElapsedMs <= 0) return closed
    return if (dayOf(openSinceMs) == dayKey) closed + openElapsedMs else closed
  }

  /** True when [nowMs] is on a different local day than the cached totals. */
  fun isStale(nowMs: Long) = dayOf(nowMs) != dayKey

  private fun rollIfNeeded(nowMs: Long) {
    val today = dayOf(nowMs)
    if (today != dayKey) {
      dayKey = today
      count = 0
      durationMs = 0
    }
  }

  private fun dayOf(ms: Long): Long = Instant.ofEpochMilli(ms).atZone(zone()).toLocalDate().toEpochDay()
}
