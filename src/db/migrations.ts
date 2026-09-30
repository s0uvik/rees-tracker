import type { SQLiteDatabase } from 'expo-sqlite';

/**
 * Ordered, append-only list of schema migrations. Index + 1 is the version
 * stored in `PRAGMA user_version` after the migration runs. Never edit a
 * shipped entry; add a new one.
 */
export const MIGRATIONS: readonly string[] = [
  // v1: initial schema. `native_id` links a row to the native buffer so drains are idempotent upserts.
  `
  CREATE TABLE IF NOT EXISTS reel_views (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    app TEXT NOT NULL,
    viewed_at INTEGER NOT NULL,
    duration_ms INTEGER,
    created_at INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
    native_id INTEGER UNIQUE
  );
  CREATE INDEX IF NOT EXISTS idx_reel_views_viewed_at ON reel_views(viewed_at);

  CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
  );
  `,
];

export const LATEST_VERSION = MIGRATIONS.length;

export async function migrateDbIfNeeded(db: SQLiteDatabase): Promise<void> {
  await db.execAsync('PRAGMA journal_mode = WAL;');
  const row = await db.getFirstAsync<{ user_version: number }>('PRAGMA user_version');
  let current = row?.user_version ?? 0;
  if (current >= LATEST_VERSION) return;

  while (current < LATEST_VERSION) {
    const sql = MIGRATIONS[current];
    const next = current + 1;
    if (sql === undefined) break;
    await db.withExclusiveTransactionAsync(async (txn) => {
      await txn.execAsync(sql);
      // PRAGMA does not accept bound parameters; `next` is a trusted integer.
      await txn.execAsync(`PRAGMA user_version = ${next}`);
    });
    current = next;
  }
}
