import { useEffect, useMemo } from 'react';
import { AppState } from 'react-native';
import { addListener } from 'reels-tracker';

import { useStatsStore } from '@/store/statsStore';

import {
  buildDailyStats,
  buildHourly,
  buildMonthlyStats,
  buildStats,
  buildWeeklyStats,
  dayKey,
} from './aggregate';
import type { HourBucket, Period, PeriodStats, PeriodSummary } from './types';

const EVENT_REFRESH_DELAY_MS = 400;

/**
 * Mount once near the root. Keeps the stats store fresh on:
 * launch, every onReelViewed / onDataChanged event (trailing debounce),
 * return to foreground, and local midnight (so "today" rolls over).
 */
export function useStatsAutoRefresh(): void {
  const refresh = useStatsStore((s) => s.refresh);

  useEffect(() => {
    let eventTimer: ReturnType<typeof setTimeout> | null = null;
    let midnightTimer: ReturnType<typeof setTimeout> | null = null;

    const scheduleEventRefresh = () => {
      if (eventTimer) clearTimeout(eventTimer);
      eventTimer = setTimeout(() => {
        eventTimer = null;
        void refresh();
      }, EVENT_REFRESH_DELAY_MS);
    };

    const scheduleMidnight = () => {
      const now = new Date();
      const next = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 0, 0, 1);
      midnightTimer = setTimeout(() => {
        void refresh();
        scheduleMidnight();
      }, next.getTime() - now.getTime());
    };

    void refresh();
    scheduleMidnight();

    const viewed = addListener('onReelViewed', scheduleEventRefresh);
    const changed = addListener('onDataChanged', scheduleEventRefresh);
    const appState = AppState.addEventListener('change', (state) => {
      if (state === 'active') void refresh();
    });

    return () => {
      if (eventTimer) clearTimeout(eventTimer);
      if (midnightTimer) clearTimeout(midnightTimer);
      viewed.remove();
      changed.remove();
      appState.remove();
    };
  }, [refresh]);
}

function useNow(): Date {
  const loadedAt = useStatsStore((s) => s.loadedAt);
  return useMemo(() => new Date(loadedAt), [loadedAt]);
}

export function useDailyStats(): PeriodStats {
  const rows = useStatsStore((s) => s.rows);
  const now = useNow();
  return useMemo(() => buildDailyStats(rows, now), [rows, now]);
}

export function useWeeklyStats(): PeriodStats {
  const rows = useStatsStore((s) => s.rows);
  const now = useNow();
  return useMemo(() => buildWeeklyStats(rows, now), [rows, now]);
}

export function useMonthlyStats(): PeriodStats {
  const rows = useStatsStore((s) => s.rows);
  const now = useNow();
  return useMemo(() => buildMonthlyStats(rows, now), [rows, now]);
}

export function usePeriodStats(period: Period): PeriodStats {
  const rows = useStatsStore((s) => s.rows);
  const now = useNow();
  return useMemo(() => buildStats(period, rows, now), [period, rows, now]);
}

export function useTodayHourly(): HourBucket[] {
  const rows = useStatsStore((s) => s.rows);
  const now = useNow();
  return useMemo(() => buildHourly(rows, now), [rows, now]);
}

export function useTodaySummary(): PeriodSummary & { day: string } {
  const daily = useDailyStats();
  const now = useNow();
  return { ...daily.current, day: dayKey(now) };
}

export function useStatsRefresh(): { refreshing: boolean; refresh: () => Promise<void> } {
  const refreshing = useStatsStore((s) => s.refreshing);
  const refresh = useStatsStore((s) => s.refresh);
  return { refreshing, refresh };
}
