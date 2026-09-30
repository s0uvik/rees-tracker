import { ackEvents, drainPendingEvents } from 'reels-tracker';

import { getDb } from './client';
import { upsertNativeEvents } from './queries';

const BATCH = 500;
/** Safety valve so a misbehaving buffer can never spin forever. */
const MAX_BATCHES = 100;

let inFlight: Promise<number> | null = null;

/**
 * Copies unsynced rows from the native buffer into the JS database, then acks
 * them. Upsert-by-native-id makes a crash between upsert and ack harmless: the
 * rows are simply upserted again next time. Concurrent callers share one run.
 */
export function syncFromNative(): Promise<number> {
  if (!inFlight) {
    inFlight = runSync().finally(() => {
      inFlight = null;
    });
  }
  return inFlight;
}

async function runSync(): Promise<number> {
  const db = await getDb();
  let total = 0;
  for (let i = 0; i < MAX_BATCHES; i++) {
    const pending = await drainPendingEvents(BATCH);
    if (pending.length === 0) break;
    await upsertNativeEvents(db, pending);
    await ackEvents(pending.map((e) => ({ id: e.nativeId, version: e.version })));
    total += pending.length;
    if (pending.length < BATCH) break;
  }
  return total;
}
