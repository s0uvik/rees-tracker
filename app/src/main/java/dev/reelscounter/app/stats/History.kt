package dev.reelscounter.app.stats

import dev.reelscounter.app.data.ReelEventStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One entry of the flattened history list: a day header or a single view. */
sealed interface HistoryItem {
  val key: String

  data class Header(override val key: String, val title: String, val count: Int, val durationMs: Long) : HistoryItem

  data class View(override val key: String, val row: ReelEventStore.Row) : HistoryItem
}

object History {
  private val TITLE = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())

  fun dayTitle(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> day.format(TITLE)
  }

  /** Flattens newest-first rows into header + view items, with per-day totals over the given rows. */
  fun groupByDay(rows: List<ReelEventStore.Row>, today: LocalDate, zone: ZoneId): List<HistoryItem> {
    val items = ArrayList<HistoryItem>(rows.size + 8)
    var headerIndex = -1
    var currentDay: LocalDate? = null
    var count = 0
    var duration = 0L

    fun closeHeader() {
      if (headerIndex >= 0) {
        val h = items[headerIndex] as HistoryItem.Header
        items[headerIndex] = h.copy(count = count, durationMs = duration)
      }
    }

    for (row in rows) {
      val day = Instant.ofEpochMilli(row.viewedAt).atZone(zone).toLocalDate()
      if (day != currentDay) {
        closeHeader()
        currentDay = day
        count = 0
        duration = 0
        headerIndex = items.size
        items += HistoryItem.Header("h-$day", dayTitle(day, today), 0, 0)
      }
      count++
      duration += row.durationMs ?: 0
      items += HistoryItem.View("v-${row.id}", row)
    }
    closeHeader()
    return items
  }
}
