package dev.reelscounter.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.reelscounter.app.stats.HourRow

/**
 * The app's only database. The accessibility service writes here and the UI
 * reads here; both run in the same process and share this one helper, so
 * SQLite's own locking is enough (WAL lets readers run during writes).
 *
 * Only a timestamp, the app key and a duration are stored per reel.
 */
class ReelEventStore private constructor(context: Context) :
  SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

  init {
    setWriteAheadLoggingEnabled(true)
  }

  data class Row(val id: Long, val app: String, val viewedAt: Long, val durationMs: Long?)

  data class Totals(val count: Int, val durationMs: Long)

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE reel_views (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        app TEXT NOT NULL,
        viewed_at INTEGER NOT NULL,
        duration_ms INTEGER,
        created_at INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
      )
      """.trimIndent(),
    )
    db.execSQL("CREATE INDEX idx_reel_views_viewed_at ON reel_views(viewed_at)")
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // v1 is the only schema so far. Future migrations go here, one step per version.
  }

  // region Writes (service + debug tools)

  fun startView(app: String, viewedAt: Long, durationMs: Long? = null): Long {
    val values = ContentValues().apply {
      put("app", app)
      put("viewed_at", viewedAt)
      if (durationMs != null) put("duration_ms", durationMs) else putNull("duration_ms")
    }
    return writableDatabase.insertOrThrow("reel_views", null, values)
  }

  /** Bulk insert in one transaction (debug seeding). */
  fun insertMany(rows: List<Triple<String, Long, Long?>>) {
    val db = writableDatabase
    db.beginTransaction()
    try {
      for ((app, viewedAt, durationMs) in rows) startView(app, viewedAt, durationMs)
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  fun addDuration(id: Long, durationMs: Long) {
    if (durationMs <= 0) return
    writableDatabase.execSQL(
      "UPDATE reel_views SET duration_ms = COALESCE(duration_ms, 0) + ? WHERE id = ?",
      arrayOf<Any>(durationMs, id),
    )
  }

  fun clearAll() {
    writableDatabase.delete("reel_views", null, null)
  }

  // endregion

  // region Reads

  fun totalsSince(sinceMs: Long): Totals {
    readableDatabase.rawQuery(
      "SELECT COUNT(*), COALESCE(SUM(duration_ms), 0) FROM reel_views WHERE viewed_at >= ?",
      arrayOf(sinceMs.toString()),
    ).use { c ->
      return if (c.moveToFirst()) Totals(c.getInt(0), c.getLong(1)) else Totals(0, 0)
    }
  }

  /**
   * Counts grouped by local calendar day and hour. SQLite's 'localtime'
   * modifier applies the device time zone (including DST) per row, so all
   * aggregation above this is plain calendar math on local dates.
   */
  fun hourRows(fromMs: Long, toMs: Long): List<HourRow> {
    val out = ArrayList<HourRow>()
    readableDatabase.rawQuery(
      """
      SELECT
        date(viewed_at / 1000, 'unixepoch', 'localtime') AS day,
        CAST(strftime('%H', viewed_at / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour,
        COUNT(*),
        COALESCE(SUM(duration_ms), 0),
        COUNT(duration_ms)
      FROM reel_views
      WHERE viewed_at >= ? AND viewed_at < ?
      GROUP BY day, hour
      ORDER BY day, hour
      """.trimIndent(),
      arrayOf(fromMs.toString(), toMs.toString()),
    ).use { c ->
      while (c.moveToNext()) {
        out += HourRow(
          day = c.getString(0),
          hour = c.getInt(1),
          count = c.getInt(2),
          durationMs = c.getLong(3),
          timedCount = c.getInt(4),
        )
      }
    }
    return out
  }

  /** Newest first, keyset-paginated: pass the last row of the previous page as [after]. */
  fun page(limit: Int, after: Row? = null): List<Row> {
    val (where, args) = if (after != null) {
      "WHERE viewed_at < ? OR (viewed_at = ? AND id < ?)" to
        arrayOf(after.viewedAt.toString(), after.viewedAt.toString(), after.id.toString(), limit.toString())
    } else {
      "" to arrayOf(limit.toString())
    }
    return queryRows(
      "SELECT id, app, viewed_at, duration_ms FROM reel_views $where ORDER BY viewed_at DESC, id DESC LIMIT ?",
      args,
    )
  }

  fun allAscending(): List<Row> =
    queryRows("SELECT id, app, viewed_at, duration_ms FROM reel_views ORDER BY viewed_at ASC, id ASC", emptyArray())

  private fun queryRows(sql: String, args: Array<String>): List<Row> {
    val rows = ArrayList<Row>()
    readableDatabase.rawQuery(sql, args).use { c ->
      while (c.moveToNext()) {
        rows += Row(
          id = c.getLong(0),
          app = c.getString(1),
          viewedAt = c.getLong(2),
          durationMs = if (c.isNull(3)) null else c.getLong(3),
        )
      }
    }
    return rows
  }

  // endregion

  companion object {
    private const val DB_NAME = "reels.db"
    private const val DB_VERSION = 1

    @Volatile
    private var instance: ReelEventStore? = null

    fun get(context: Context): ReelEventStore =
      instance ?: synchronized(this) {
        instance ?: ReelEventStore(context).also { instance = it }
      }
  }
}
