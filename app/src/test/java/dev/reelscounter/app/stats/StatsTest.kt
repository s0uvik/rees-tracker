package dev.reelscounter.app.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Uses America/New_York explicitly, so DST and UTC-offset cases give the
 * same result on any machine.
 */
class StatsTest {
  private val zone = ZoneId.of("America/New_York")

  private fun local(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0, s: Int = 0): Long =
    LocalDateTime.of(y, m, d, h, min, s).atZone(zone).toInstant().toEpochMilli()

  private fun utc(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
    ZonedDateTime.of(y, m, d, h, min, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()

  private fun rows(vararg events: Pair<Long, Long?>) = Stats.rollup(events.toList(), zone)
  private fun ev(t: Long, d: Long? = 5_000) = t to d

  // region rollup (mirror of the SQL 'localtime' grouping)

  @Test
  fun rollupUsesLocalDayNotUtc() {
    // 03:30 UTC on Jan 1 is 22:30 on Dec 31 in New York.
    assertEquals(listOf(HourRow("2025-12-31", 22, 1, 1000, 1)), rows(ev(utc(2026, 1, 1, 3, 30), 1000)))
  }

  @Test
  fun rollupCountsUntimedViewsSeparately() {
    assertEquals(
      listOf(HourRow("2026-05-01", 9, 2, 5000, 1)),
      rows(ev(local(2026, 5, 1, 9)), ev(local(2026, 5, 1, 9, 30), null)),
    )
  }

  @Test
  fun fallBackMergesTheRepeatedHour() {
    // 05:30Z = 01:30 EDT, 06:30Z = 01:30 EST on 2026-11-01.
    assertEquals(
      listOf(HourRow("2026-11-01", 1, 2, 2000, 2)),
      rows(ev(utc(2026, 11, 1, 5, 30), 1000), ev(utc(2026, 11, 1, 6, 30), 1000)),
    )
  }

  // endregion

  // region daily

  @Test
  fun dailyHas30ZeroFilledBucketsEndingToday() {
    val today = LocalDate.of(2026, 6, 15)
    val stats = Stats.daily(rows(ev(local(2026, 6, 15, 9)), ev(local(2026, 6, 1, 9))), today)
    assertEquals(30, stats.buckets.size)
    assertEquals("2026-05-17", stats.buckets.first().key)
    assertEquals("2026-06-15", stats.buckets.last().key)
    assertEquals(listOf("2026-06-01", "2026-06-15"), stats.buckets.filter { it.count > 0 }.map { it.key })
  }

  @Test
  fun dailyComparesWithYesterday() {
    val stats = Stats.daily(
      rows(
        ev(local(2026, 6, 15, 8)), ev(local(2026, 6, 15, 9)), ev(local(2026, 6, 15, 10)),
        ev(local(2026, 6, 14, 23, 59, 59)), ev(local(2026, 6, 14, 1)),
      ),
      LocalDate.of(2026, 6, 15),
    )
    assertEquals(3, stats.current.totalReels)
    assertEquals(2, stats.previous.totalReels)
    assertEquals(50.0, stats.changePct!!, 0.001)
  }

  @Test
  fun dailyBucketsStayContiguousAcrossSpringForward() {
    val keys = Stats.daily(emptyList(), LocalDate.of(2026, 3, 10)).buckets.map { it.key }
    assertEquals(30, keys.toSet().size)
    assertEquals(listOf("2026-03-08", "2026-03-09", "2026-03-10"), keys.takeLast(3))
  }

  // endregion

  // region hourly

  @Test
  fun hourlyFills24Hours() {
    val hours = Stats.hourly(rows(ev(local(2026, 6, 15, 0, 5)), ev(local(2026, 6, 15, 23, 55))), LocalDate.of(2026, 6, 15))
    assertEquals(24, hours.size)
    assertEquals(1, hours[0].count)
    assertEquals(1, hours[23].count)
    assertEquals(2, hours.sumOf { it.count })
  }

  @Test
  fun hourlyHandlesTheMissingSpringForwardHour() {
    val hours = Stats.hourly(rows(ev(local(2026, 3, 8, 1, 30)), ev(local(2026, 3, 8, 3, 30))), LocalDate.of(2026, 3, 8))
    assertEquals(1, hours[1].count)
    assertEquals(0, hours[2].count)
    assertEquals(1, hours[3].count)
  }

  // endregion

  // region weekly (ISO, Monday start)

  @Test
  fun sundayBelongsToThePreviousMondayWeek() {
    val stats = Stats.weekly(
      rows(ev(local(2026, 3, 8, 23, 59)), ev(local(2026, 3, 9, 0, 0, 30)), ev(local(2026, 3, 11, 9))),
      LocalDate.of(2026, 3, 11),
    )
    assertEquals(12, stats.buckets.size)
    assertEquals("2026-03-09", stats.buckets[11].key)
    assertEquals("2026-03-02", stats.buckets[10].key)
    assertEquals(2, stats.current.totalReels)
    assertEquals(1, stats.previous.totalReels)
  }

  @Test
  fun sundayTodayIsTheEndOfItsWeek() {
    val stats = Stats.weekly(rows(ev(local(2026, 3, 9))), LocalDate.of(2026, 3, 15))
    assertEquals("2026-03-09", stats.buckets[11].key)
    assertEquals(1, stats.current.totalReels)
  }

  @Test
  fun isoWeeksSpanTheYearBoundary() {
    // Thursday 2026-01-01 is in ISO week 1 of 2026, which starts Monday 2025-12-29.
    val stats = Stats.weekly(rows(ev(local(2025, 12, 29, 10)), ev(local(2025, 12, 28, 10))), LocalDate.of(2026, 1, 1))
    assertEquals("2025-12-29", stats.buckets[11].key)
    assertEquals("W1", stats.buckets[11].label)
    assertEquals("W52", stats.buckets[10].label)
    assertEquals(1, stats.current.totalReels)
    assertEquals(1, stats.previous.totalReels)
  }

  // endregion

  // region monthly

  @Test
  fun monthsSplitAtLocalMidnightOnThe1st() {
    val stats = Stats.monthly(
      rows(ev(local(2026, 2, 28, 23, 59, 59)), ev(local(2026, 3, 1, 0, 0, 1)), ev(local(2026, 1, 31, 12))),
      LocalDate.of(2026, 3, 1),
    )
    assertEquals(12, stats.buckets.size)
    assertEquals("2025-04", stats.buckets.first().key)
    assertEquals("2026-03", stats.buckets.last().key)
    assertEquals(1, stats.buckets.first { it.key == "2026-02" }.count)
    assertEquals(1, stats.buckets.first { it.key == "2026-01" }.count)
    assertEquals(1, stats.current.totalReels)
    assertEquals(1, stats.previous.totalReels)
  }

  @Test
  fun utcNextMonthInstantStaysInLocalMonth() {
    // 2026-04-01 02:00Z is still March 31 in New York.
    val stats = Stats.monthly(rows(ev(utc(2026, 4, 1, 2), 1)), LocalDate.of(2026, 4, 2))
    assertEquals(1, stats.buckets.first { it.key == "2026-03" }.count)
    assertEquals(0, stats.current.totalReels)
  }

  @Test
  fun queryWindowCoversTheWholeMonthlyRange() {
    val today = LocalDate.of(2026, 3, 15)
    assertEquals(local(2025, 4, 1, 0), Stats.rangeStartMs(today, zone))
    assertEquals(local(2026, 3, 16, 0), Stats.rangeEndMs(today, zone))
  }

  // endregion

  // region summaries

  @Test
  fun summaryTotalsAverageAndPeakHour() {
    val s = Stats.summarize(
      rows(
        ev(local(2026, 6, 1, 21, 0), 10_000),
        ev(local(2026, 6, 1, 21, 10), 20_000),
        ev(local(2026, 6, 1, 21, 20), null),
        ev(local(2026, 6, 1, 8, 0), 30_000),
      ),
    )
    assertEquals(PeriodSummary(totalReels = 4, totalWatchMs = 60_000, avgMsPerReel = 20_000.0, peakHour = 21), s)
  }

  @Test
  fun emptySummary() {
    assertEquals(PeriodSummary(0, 0, null, null), Stats.summarize(emptyList()))
  }

  @Test
  fun percentChange() {
    assertEquals(0.0, Stats.percentChange(0, 0)!!, 0.0)
    assertNull(Stats.percentChange(5, 0))
    assertEquals(50.0, Stats.percentChange(15, 10)!!, 0.001)
    assertEquals(-50.0, Stats.percentChange(5, 10)!!, 0.001)
  }

  // endregion
}
