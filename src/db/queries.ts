import type { SQLiteDatabase } from 'expo-sqlite';

import type { HourRow } from '@/features/stats/types';

export type ReelViewRow = {
  id: number;
  app: string;
  viewedAt: number;
  durationMs: number | null;
};

export type UpsertEvent = {
  nativeId: number;
  app: string;
  viewedAt: number;
  durationMs: number | null;
};

/** Idempotent: a native row seen again (e.g. after its duration was filled in) updates in place. */
export async function upsertNativeEvents(db: SQLiteDatabase, events: UpsertEvent[]): Promise<void> {
  if (events.length === 0) return;
  await db.withExclusiveTransactionAsync(async (txn) => {
    const stmt = await txn.prepareAsync(
      `INSERT INTO reel_views (app, viewed_at, duration_ms, native_id)
       VALUES ($app, $viewedAt, $durationMs, $nativeId)
       ON CONFLICT(native_id) DO UPDATE SET
         app = excluded.app,
         viewed_at = excluded.viewed_at,
         duration_ms = excluded.duration_ms`,
    );
    try {
      for (const e of events) {
        await stmt.executeAsync({
          $app: e.app,
          $viewedAt: e.viewedAt,
          $durationMs: e.durationMs,
          $nativeId: e.nativeId,
        });
      }
    } finally {
      await stmt.finalizeAsync();
    }
  });
}

/**
 * Counts grouped by local calendar day and hour. SQLite's 'localtime'
 * modifier applies the device time zone (including DST) per row, so every
 * aggregate above this is plain calendar math on local dates.
 */
export async function getHourRows(
  db: SQLiteDatabase,
  fromMs: number,
  toMs: number,
): Promise<HourRow[]> {
  return db.getAllAsync<HourRow>(
    `SELECT
       date(viewed_at / 1000, 'unixepoch', 'localtime') AS day,
       CAST(strftime('%H', viewed_at / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour,
       COUNT(*) AS count,
       COALESCE(SUM(duration_ms), 0) AS durationMs,
       COUNT(duration_ms) AS timedCount
     FROM reel_views
     WHERE viewed_at >= ? AND viewed_at < ?
     GROUP BY day, hour
     ORDER BY day, hour`,
    fromMs,
    toMs,
  );
}

/** Keyset pagination, newest first. Pass the last row's viewedAt/id as the cursor. */
export async function getViewsPage(
  db: SQLiteDatabase,
  limit: number,
  cursor?: { viewedAt: number; id: number },
): Promise<ReelViewRow[]> {
  if (cursor) {
    return db.getAllAsync<ReelViewRow>(
      `SELECT id, app, viewed_at AS viewedAt, duration_ms AS durationMs
       FROM reel_views
       WHERE viewed_at < ? OR (viewed_at = ? AND id < ?)
       ORDER BY viewed_at DESC, id DESC
       LIMIT ?`,
      cursor.viewedAt,
      cursor.viewedAt,
      cursor.id,
      limit,
    );
  }
  return db.getAllAsync<ReelViewRow>(
    `SELECT id, app, viewed_at AS viewedAt, duration_ms AS durationMs
     FROM reel_views
     ORDER BY viewed_at DESC, id DESC
     LIMIT ?`,
    limit,
  );
}

export async function getAllViewsAscending(db: SQLiteDatabase): Promise<ReelViewRow[]> {
  return db.getAllAsync<ReelViewRow>(
    `SELECT id, app, viewed_at AS viewedAt, duration_ms AS durationMs
     FROM reel_views
     ORDER BY viewed_at ASC, id ASC`,
  );
}

export async function countViews(db: SQLiteDatabase): Promise<number> {
  const row = await db.getFirstAsync<{ n: number }>('SELECT COUNT(*) AS n FROM reel_views');
  return row?.n ?? 0;
}

export async function deleteAllViews(db: SQLiteDatabase): Promise<void> {
  await db.runAsync('DELETE FROM reel_views');
}

// Settings (JS-only preferences; badge + limit settings live natively so the service can read them)

export async function getSetting(db: SQLiteDatabase, key: string): Promise<string | null> {
  const row = await db.getFirstAsync<{ value: string }>(
    'SELECT value FROM settings WHERE key = ?',
    key,
  );
  return row?.value ?? null;
}

export async function setSetting(db: SQLiteDatabase, key: string, value: string): Promise<void> {
  await db.runAsync(
    'INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value',
    key,
    value,
  );
}

export async function deleteSetting(db: SQLiteDatabase, key: string): Promise<void> {
  await db.runAsync('DELETE FROM settings WHERE key = ?', key);
}
