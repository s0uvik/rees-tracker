import { openDatabaseAsync, type SQLiteDatabase } from 'expo-sqlite';

import { migrateDbIfNeeded } from './migrations';

export const DATABASE_NAME = 'reels.db';

let dbPromise: Promise<SQLiteDatabase> | null = null;

/**
 * Single shared connection, opened and migrated once. Used by stores and
 * helpers that run outside React components (sync, CSV export).
 */
export function getDb(): Promise<SQLiteDatabase> {
  if (!dbPromise) {
    dbPromise = (async () => {
      const db = await openDatabaseAsync(DATABASE_NAME);
      await migrateDbIfNeeded(db);
      return db;
    })().catch((error: unknown) => {
      dbPromise = null;
      throw error;
    });
  }
  return dbPromise;
}
