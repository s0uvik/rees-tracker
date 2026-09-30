import { create } from 'zustand';

import { getDb } from '@/db/client';
import { getHourRows } from '@/db/queries';
import { syncFromNative } from '@/db/sync';
import { statsRangeEnd, statsRangeStart } from '@/features/stats/aggregate';
import type { HourRow } from '@/features/stats/types';

type StatsState = {
  /** Day × hour rollup covering every stats view (see statsRangeStart). */
  rows: HourRow[];
  /** When `rows` were computed (store creation time before the first load); the "now" all derived stats use. */
  loadedAt: number;
  refreshing: boolean;
  error: string | null;
  /** Sync the native buffer, then re-query. Concurrent calls coalesce into one follow-up run. */
  refresh: () => Promise<void>;
};

let running: Promise<void> | null = null;
let rerunRequested = false;

export const useStatsStore = create<StatsState>((set) => {
  async function load(): Promise<void> {
    set({ refreshing: true });
    try {
      await syncFromNative();
      const db = await getDb();
      const now = new Date();
      const rows = await getHourRows(
        db,
        statsRangeStart(now).getTime(),
        statsRangeEnd(now).getTime(),
      );
      set({ rows, loadedAt: now.getTime(), error: null });
    } catch (e) {
      set({ error: e instanceof Error ? e.message : String(e) });
    } finally {
      set({ refreshing: false });
    }
  }

  return {
    rows: [],
    loadedAt: Date.now(),
    refreshing: false,
    error: null,
    refresh: () => {
      if (running) {
        rerunRequested = true;
        return running;
      }
      running = (async () => {
        do {
          rerunRequested = false;
          await load();
        } while (rerunRequested);
      })().finally(() => {
        running = null;
      });
      return running;
    },
  };
});
