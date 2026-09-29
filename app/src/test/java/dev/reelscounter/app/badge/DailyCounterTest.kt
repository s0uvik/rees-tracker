package dev.reelscounter.app.badge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DailyCounterTest {
  private val zone = ZoneId.of("America/New_York")
  private val counter = DailyCounter { zone }

  private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0, s: Int = 0): Long =
    LocalDateTime.of(y, m, d, h, min, s).atZone(zone).toInstant().toEpochMilli()

  @Test
  fun incrementsWithinADay() {
    counter.load(5, 60_000, at(2026, 6, 1, 9))
    assertEquals(6, counter.increment(at(2026, 6, 1, 10)))
    assertEquals(7, counter.increment(at(2026, 6, 1, 23, 59, 59)))
    assertEquals(7, counter.count(at(2026, 6, 1, 23, 59, 59)))
  }

  @Test
  fun resetsAtLocalMidnight() {
    counter.load(42, 600_000, at(2026, 6, 1, 20))
    assertFalse(counter.isStale(at(2026, 6, 1, 23, 59, 59)))
    assertTrue(counter.isStale(at(2026, 6, 2, 0, 0, 0)))
    assertEquals(0, counter.count(at(2026, 6, 2, 0, 0, 1)))
    assertEquals(0L, counter.durationMs(at(2026, 6, 2, 0, 0, 1)))
    assertEquals(1, counter.increment(at(2026, 6, 2, 0, 1)))
  }

  @Test
  fun midnightIsLocalNotUtc() {
    // 23:30 New York = 03:30 UTC next day; still the same local day.
    counter.load(3, 0, at(2026, 1, 10, 12))
    assertEquals(4, counter.increment(at(2026, 1, 10, 23, 30)))
  }

  @Test
  fun durationOnlyCountsViewsStartedToday() {
    val now = at(2026, 6, 2, 0, 5)
    counter.load(0, 0, now)
    counter.addDuration(300_000, viewStartedAtMs = at(2026, 6, 1, 23, 58), nowMs = now)
    assertEquals(0L, counter.durationMs(now))
    counter.addDuration(20_000, viewStartedAtMs = at(2026, 6, 2, 0, 1), nowMs = now)
    assertEquals(20_000L, counter.durationMs(now))
  }

  @Test
  fun startOfDayAndNextMidnight() {
    val now = at(2026, 6, 1, 18, 30)
    assertEquals(at(2026, 6, 1, 0), counter.startOfDayMs(now))
    assertEquals(5 * 3_600_000L + 30 * 60_000L, counter.msUntilNextMidnight(now))
  }

  @Test
  fun nextMidnightAcrossSpringForwardIsA23HourDay() {
    // 2026-03-08 has no 02:00 in New York.
    val start = at(2026, 3, 8, 0)
    assertEquals(23 * 3_600_000L, counter.msUntilNextMidnight(start))
  }

  @Test
  fun nextMidnightAcrossFallBackIsA25HourDay() {
    val start = at(2026, 11, 1, 0)
    assertEquals(25 * 3_600_000L, counter.msUntilNextMidnight(start))
  }
}
