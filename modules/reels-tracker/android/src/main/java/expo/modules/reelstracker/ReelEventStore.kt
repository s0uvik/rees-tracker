package expo.modules.reelstracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Native-side event buffer.
 *
 * Why a separate database instead of writing into the JS database directly:
 * expo-sqlite bundles its own copy of SQLite, while this class uses the
 * Android framework's copy. Two SQLite libraries opening the same file inside
 * one process can clobber each other's POSIX advisory locks (closing one
 * library's file descriptor releases the other's locks), a documented path to
 * database corruption. So the service writes here (WAL mode, a single
 * process-wide helper) and the JS side drains unsynced rows into its own
 * database on launch, on foreground, and on every new event.
 *
 * Synced rows are kept for a few days so the floating badge can compute
 * today's totals natively while the React Native app is closed.
 *
 * Sync protocol: every mutation bumps `version` and sets `synced = 0`. JS acks
 * with (id, version); an ack only applies if the row has not changed since it
 * was read, so a duration written mid-drain is never lost.
 */
class ReelEventStore private constructor(context: Context) :
  SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

  init {
    setWriteAheadLoggingEnabled(true)
  }

  data class Row(
    val id: Long,
    val app: String,
    val viewedAt: Long,
    val durationMs: Long?,
    val version: Int,
  )

  data class Totals(val count: Int, val durationMs: Long)

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE views (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        app TEXT NOT NULL,
        viewed_at INTEGER NOT NULL,
        duration_ms INTEGER,
        version INTEGER NOT NULL DEFAULT 1,
        synced INTEGER NOT NULL DEFAULT 0
      )
      """.trimIndent(),
    )
    db.execSQL("CREATE INDEX idx_views_viewed_at ON views(viewed_at)")
    db.execSQL("CREATE INDEX idx_views_synced ON views(synced)")
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // v1 is the only schema so far. Future migrations go here, one step per version.
  }

  fun startView(app: String, viewedAt: Long, durationMs: Long? = null): Long {
    val values = ContentValues().apply {
      put("app", app)
      put("viewed_at", viewedAt)
      if (durationMs != null) put("duration_ms", durationMs) else putNull("duration_ms")
    }
    return writableDatabase.insertOrThrow("views", null, values)
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
      "UPDATE views SET duration_ms = COALESCE(duration_ms, 0) + ?, version = version + 1, synced = 0 WHERE id = ?",
      arrayOf<Any>(durationMs, id),
    )
  }

  fun unsynced(limit: Int): List<Row> {
    val rows = ArrayList<Row>()
    readableDatabase.rawQuery(
      "SELECT id, app, viewed_at, duration_ms, version FROM views WHERE synced = 0 ORDER BY id LIMIT ?",
      arrayOf(limit.toString()),
    ).use { c ->
      while (c.moveToNext()) {
        rows += Row(
          id = c.getLong(0),
          app = c.getString(1),
          viewedAt = c.getLong(2),
          durationMs = if (c.isNull(3)) null else c.getLong(3),
          version = c.getInt(4),
        )
      }
    }
    return rows
  }

  /** Marks rows synced when their version still matches. Returns the number acknowledged. */
  fun ack(items: List<Pair<Long, Int>>): Int {
    if (items.isEmpty()) return 0
    val db = writableDatabase
    var acked = 0
    db.beginTransaction()
    try {
      db.compileStatement("UPDATE views SET synced = 1 WHERE id = ? AND version = ?").use { stmt ->
        for ((id, version) in items) {
          stmt.bindLong(1, id)
          stmt.bindLong(2, version.toLong())
          acked += stmt.executeUpdateDelete()
          stmt.clearBindings()
        }
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
    return acked
  }

  fun totalsSince(sinceMs: Long): Totals {
    readableDatabase.rawQuery(
      "SELECT COUNT(*), COALESCE(SUM(duration_ms), 0) FROM views WHERE viewed_at >= ?",
      arrayOf(sinceMs.toString()),
    ).use { c ->
      return if (c.moveToFirst()) Totals(c.getInt(0), c.getLong(1)) else Totals(0, 0)
    }
  }

  /** Drops synced rows older than [olderThanMs]. Unsynced rows are never pruned. */
  fun prune(olderThanMs: Long) {
    writableDatabase.delete("views", "synced = 1 AND viewed_at < ?", arrayOf(olderThanMs.toString()))
  }

  fun clearAll() {
    writableDatabase.delete("views", null, null)
  }

  companion object {
    private const val DB_NAME = "reels_native.db"
    private const val DB_VERSION = 1

    @Volatile
    private var instance: ReelEventStore? = null

    fun get(context: Context): ReelEventStore =
      instance ?: synchronized(this) {
        instance ?: ReelEventStore(context).also { instance = it }
      }
  }
}
