package dev.reelscounter.app.stats

import dev.reelscounter.app.data.ReelEventStore.Row
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class HistoryAndFormatTest {
  private val zone = ZoneId.of("America/New_York")

  private fun local(y: Int, m: Int, d: Int, h: Int): Long =
    LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

  @Test
  fun groupsNewestFirstRowsByLocalDayWithTotals() {
    val today = LocalDate.of(2026, 6, 15)
    val items = History.groupByDay(
      listOf(
        Row(3, "instagram", local(2026, 6, 15, 12), 4000),
        Row(2, "instagram", local(2026, 6, 15, 11), null),
        Row(1, "instagram", local(2026, 6, 14, 12), 6000),
        Row(0, "instagram", local(2026, 6, 10, 12), 1000),
      ),
      today,
      zone,
    )
    assertEquals(listOf("h", "v", "v", "h", "v", "h", "v"), items.map { if (it is HistoryItem.Header) "h" else "v" })
    val headers = items.filterIsInstance<HistoryItem.Header>()
    assertEquals(HistoryItem.Header("h-2026-06-15", "Today", 2, 4000), headers[0])
    assertEquals(HistoryItem.Header("h-2026-06-14", "Yesterday", 1, 6000), headers[1])
    assertEquals(1, headers[2].count)
    assertTrue(headers[2].title.contains("10"))
  }

  @Test
  fun emptyHistory() {
    assertEquals(emptyList<HistoryItem>(), History.groupByDay(emptyList(), LocalDate.of(2026, 6, 15), zone))
  }

  @Test
  fun csvUsesLocalTimeWithOffset() {
    val jan = ZonedDateTime.of(2026, 1, 15, 17, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
    val jul = ZonedDateTime.of(2026, 7, 15, 17, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
    val csv = Format.csv(listOf(Row(7, "instagram", jan, 1234), Row(8, "youtube_shorts", jul, null)), zone)
    assertEquals(
      listOf(
        "id,app,viewed_at_local,viewed_at_ms,duration_ms",
        "7,instagram,2026-01-15T12:00:00.000-05:00,$jan,1234",
        "8,youtube_shorts,2026-07-15T13:00:00.000-04:00,$jul,",
        "",
      ),
      csv.split("\n"),
    )
  }

  @Test
  fun durationAndHourFormatting() {
    assertEquals("0s", Format.duration(0))
    assertEquals("45s", Format.duration(45_000))
    assertEquals("18m", Format.duration(18 * 60_000L + 5_000))
    assertEquals("1h 5m", Format.duration(65 * 60_000L))
    assertEquals("12 AM", Format.hour(0))
    assertEquals("9 PM", Format.hour(21))
    assertEquals("—", Format.hour(null))
    assertEquals("—", Format.seconds(null))
  }

  @Test
  fun changeFormatting() {
    assertEquals("new" to Format.Tone.UP, Format.change(null))
    assertEquals("0%" to Format.Tone.FLAT, Format.change(0.2))
    assertEquals("+50%" to Format.Tone.UP, Format.change(50.0))
    assertEquals("-25%" to Format.Tone.DOWN, Format.change(-25.0))
  }
}
