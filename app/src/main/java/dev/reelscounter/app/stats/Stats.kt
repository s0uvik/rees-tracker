package dev.reelscounter.app.stats

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Pure aggregation over the SQL day × hour rollup. Everything works on local
 * calendar dates (java.time), so DST days and month lengths are handled by the
 * calendar rather than by millisecond arithmetic. No Android imports: JVM-testable.
 */

enum class Period { DAILY, WEEKLY, MONTHLY }

/** One row of the day × hour rollup, in local time. [day] is yyyy-MM-dd. */
data class HourRow(
  val day: String,
  val hour: Int,
  val count: Int,
  val durationMs: Long,
  /** Rows in this group that have a duration (a view still open has none yet). */
  val timedCount: Int,
)

data class Bucket(
  /** yyyy-MM-dd (day, or the Monday of an ISO week) or yyyy-MM (month). */
  val key: String,
  val label: String,
  val start: LocalDate,
  val count: Int,
  val durationMs: Long,
)

data class PeriodSummary(
  val totalReels: Int,
  val totalWatchMs: Long,
  /** null when no view in the period has a duration yet. */
  val avgMsPerReel: Double?,
  /** Local hour 0–23 with the most views; null when empty. */
  val peakHour: Int?,
)

data class PeriodStats(
  val period: Period,
  val buckets: List<Bucket>,
  /** Today / this ISO week / this month. */
  val current: PeriodSummary,
  /** Yesterday / last week / last month. */
  val previous: PeriodSummary,
  /** % change of reel count vs previous; null when previous is 0 and current is not. */
  val changePct: Double?,
)

data class HourBucket(val hour: Int, val count: Int, val durationMs: Long)

object Stats {
  const val DAILY_BUCKETS = 30
  const val WEEKLY_BUCKETS = 12
  const val MONTHLY_BUCKETS = 12

  private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

  fun dayKey(d: LocalDate): String = d.format(DAY_FORMAT)
  fun monthKey(d: LocalDate): String = YearMonth.from(d).toString()
  fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
  fun weekKey(d: LocalDate): String = dayKey(weekStart(d))

  private fun keyFor(period: Period, d: LocalDate): String = when (period) {
    Period.DAILY -> dayKey(d)
    Period.WEEKLY -> weekKey(d)
    Period.MONTHLY -> monthKey(d)
  }

  /** Earliest instant any stats view needs (first day of the monthly chart). */
  fun rangeStartMs(today: LocalDate, zone: ZoneId): Long =
    today.withDayOfMonth(1).minusMonths((MONTHLY_BUCKETS - 1).toLong())
      .atStartOfDay(zone).toInstant().toEpochMilli()

  /** Exclusive end: start of tomorrow. */
  fun rangeEndMs(today: LocalDate, zone: ZoneId): Long =
    today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

  /** Mirror of the SQL 'localtime' rollup for raw events (tests, and anywhere a DB is not at hand). */
  fun rollup(events: List<Pair<Long, Long?>>, zone: ZoneId): List<HourRow> {
    data class Acc(var count: Int = 0, var durationMs: Long = 0, var timed: Int = 0)
    val map = sortedMapOf<Pair<String, Int>, Acc>(compareBy({ it.first }, { it.second }))
    for ((viewedAt, durationMs) in events) {
      val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(viewedAt), zone)
      val acc = map.getOrPut(dayKey(t.toLocalDate()) to t.hour) { Acc() }
      acc.count++
      if (durationMs != null) {
        acc.durationMs += durationMs
        acc.timed++
      }
    }
    return map.map { (k, a) -> HourRow(k.first, k.second, a.count, a.durationMs, a.timed) }
  }

  fun summarize(rows: List<HourRow>): PeriodSummary {
    var total = 0
    var watch = 0L
    var timed = 0
    val perHour = IntArray(24)
    for (r in rows) {
      total += r.count
      watch += r.durationMs
      timed += r.timedCount
      if (r.hour in 0..23) perHour[r.hour] += r.count
    }
    var peak: Int? = null
    var peakCount = 0
    for (h in 0 until 24) {
      if (perHour[h] > peakCount) {
        peakCount = perHour[h]
        peak = h
      }
    }
    return PeriodSummary(
      totalReels = total,
      totalWatchMs = watch,
      avgMsPerReel = if (timed > 0) watch.toDouble() / timed else null,
      peakHour = peak,
    )
  }

  fun percentChange(current: Int, previous: Int): Double? {
    if (previous == 0) return if (current == 0) 0.0 else null
    return (current - previous) * 100.0 / previous
  }

  fun daily(rows: List<HourRow>, today: LocalDate, days: Int = DAILY_BUCKETS): PeriodStats {
    val starts = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }
    return build(Period.DAILY, rows, starts, { it.dayOfMonth.toString() }, today, today.minusDays(1))
  }

  fun weekly(rows: List<HourRow>, today: LocalDate, weeks: Int = WEEKLY_BUCKETS): PeriodStats {
    val thisWeek = weekStart(today)
    val starts = (weeks - 1 downTo 0).map { thisWeek.minusWeeks(it.toLong()) }
    return build(
      Period.WEEKLY, rows, starts, { "W${it.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}" },
      thisWeek, thisWeek.minusWeeks(1),
    )
  }

  fun monthly(rows: List<HourRow>, today: LocalDate, months: Int = MONTHLY_BUCKETS): PeriodStats {
    val thisMonth = today.withDayOfMonth(1)
    val starts = (months - 1 downTo 0).map { thisMonth.minusMonths(it.toLong()) }
    return build(
      Period.MONTHLY, rows, starts, { it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) },
      thisMonth, thisMonth.minusMonths(1),
    )
  }

  fun forPeriod(period: Period, rows: List<HourRow>, today: LocalDate): PeriodStats = when (period) {
    Period.DAILY -> daily(rows, today)
    Period.WEEKLY -> weekly(rows, today)
    Period.MONTHLY -> monthly(rows, today)
  }

  /** 24 zero-filled hourly buckets for one local day. */
  fun hourly(rows: List<HourRow>, day: LocalDate): List<HourBucket> {
    val key = dayKey(day)
    val counts = IntArray(24)
    val durations = LongArray(24)
    for (r in rows) {
      if (r.day != key || r.hour !in 0..23) continue
      counts[r.hour] += r.count
      durations[r.hour] += r.durationMs
    }
    return (0 until 24).map { HourBucket(it, counts[it], durations[it]) }
  }

  private fun build(
    period: Period,
    rows: List<HourRow>,
    starts: List<LocalDate>,
    label: (LocalDate) -> String,
    currentStart: LocalDate,
    previousStart: LocalDate,
  ): PeriodStats {
    // Parse each row's day once, then group by bucket key.
    val byKey = rows.groupBy { keyFor(period, LocalDate.parse(it.day)) }
    val buckets = starts.map { start ->
      val key = keyFor(period, start)
      val inBucket = byKey[key].orEmpty()
      Bucket(key, label(start), start, inBucket.sumOf { it.count }, inBucket.sumOf { it.durationMs })
    }
    val current = summarize(byKey[keyFor(period, currentStart)].orEmpty())
    val previous = summarize(byKey[keyFor(period, previousStart)].orEmpty())
    return PeriodStats(period, buckets, current, previous, percentChange(current.totalReels, previous.totalReels))
  }
}
