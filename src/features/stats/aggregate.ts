/**
 * Pure aggregation over the SQL day × hour rollup. Everything works on local
 * calendar dates via date-fns, so DST days (23 / 25 hours) and month lengths
 * are handled by the calendar, not by millisecond arithmetic.
 */
import {
  addDays,
  format,
  getISOWeek,
  parseISO,
  startOfDay,
  startOfISOWeek,
  startOfMonth,
  subDays,
  subMonths,
  subWeeks,
} from 'date-fns';

import type { Bucket, HourBucket, HourRow, Period, PeriodStats, PeriodSummary } from './types';

export const DAILY_BUCKETS = 30;
export const WEEKLY_BUCKETS = 12;
export const MONTHLY_BUCKETS = 12;

export const dayKey = (d: Date) => format(d, 'yyyy-MM-dd');
export const monthKey = (d: Date) => format(d, 'yyyy-MM');
/** ISO week (Monday start) identified by its Monday. */
export const weekKey = (d: Date) => dayKey(startOfISOWeek(d));

const keyFor: Record<Period, (d: Date) => string> = {
  daily: dayKey,
  weekly: weekKey,
  monthly: monthKey,
};

/** Earliest instant any stats view needs (monthly chart start). */
export function statsRangeStart(now: Date): Date {
  return startOfMonth(subMonths(now, MONTHLY_BUCKETS - 1));
}

/** Exclusive end: start of tomorrow. */
export function statsRangeEnd(now: Date): Date {
  return addDays(startOfDay(now), 1);
}

/**
 * Mirror of the SQL rollup for raw events, using the JS runtime's local time
 * zone. Used by tests and to fold in live events without a DB round trip.
 */
export function rollupEvents(
  events: readonly { viewedAt: number; durationMs?: number | null }[],
): HourRow[] {
  const map = new Map<string, HourRow>();
  for (const e of events) {
    const d = new Date(e.viewedAt);
    const day = dayKey(d);
    const hour = d.getHours();
    const k = `${day}|${hour}`;
    let row = map.get(k);
    if (!row) {
      row = { day, hour, count: 0, durationMs: 0, timedCount: 0 };
      map.set(k, row);
    }
    row.count += 1;
    if (e.durationMs != null) {
      row.durationMs += e.durationMs;
      row.timedCount += 1;
    }
  }
  return [...map.values()].sort((a, b) =>
    a.day === b.day ? a.hour - b.hour : a.day < b.day ? -1 : 1,
  );
}

export function summarize(rows: readonly HourRow[]): PeriodSummary {
  let totalReels = 0;
  let totalWatchMs = 0;
  let timed = 0;
  const perHour = new Array<number>(24).fill(0);
  for (const r of rows) {
    totalReels += r.count;
    totalWatchMs += r.durationMs;
    timed += r.timedCount;
    if (r.hour >= 0 && r.hour < 24) perHour[r.hour] = (perHour[r.hour] ?? 0) + r.count;
  }
  let peakHour: number | null = null;
  let peakCount = 0;
  perHour.forEach((c, h) => {
    if (c > peakCount) {
      peakCount = c;
      peakHour = h;
    }
  });
  return {
    totalReels,
    totalWatchMs,
    avgMsPerReel: timed > 0 ? totalWatchMs / timed : null,
    peakHour,
  };
}

export function percentChange(current: number, previous: number): number | null {
  if (previous === 0) return current === 0 ? 0 : null;
  return ((current - previous) / previous) * 100;
}

/** Rows whose local day maps to [key] under [period]. */
function rowsInBucket(rows: readonly HourRow[], period: Period, key: string): HourRow[] {
  const toKey = keyFor[period];
  return rows.filter((r) => toKey(parseISO(r.day)) === key);
}

function fillBuckets(
  rows: readonly HourRow[],
  period: Period,
  starts: Date[],
  label: (d: Date) => string,
): Bucket[] {
  const toKey = keyFor[period];
  const totals = new Map<string, { count: number; durationMs: number }>();
  for (const r of rows) {
    const k = toKey(parseISO(r.day));
    const t = totals.get(k) ?? { count: 0, durationMs: 0 };
    t.count += r.count;
    t.durationMs += r.durationMs;
    totals.set(k, t);
  }
  return starts.map((start) => {
    const key = toKey(start);
    const t = totals.get(key);
    return {
      key,
      label: label(start),
      startMs: start.getTime(),
      count: t?.count ?? 0,
      durationMs: t?.durationMs ?? 0,
    };
  });
}

function build(
  period: Period,
  rows: readonly HourRow[],
  starts: Date[],
  label: (d: Date) => string,
  currentStart: Date,
  previousStart: Date,
): PeriodStats {
  const current = summarize(rowsInBucket(rows, period, keyFor[period](currentStart)));
  const previous = summarize(rowsInBucket(rows, period, keyFor[period](previousStart)));
  return {
    period,
    buckets: fillBuckets(rows, period, starts, label),
    current,
    previous,
    changePct: percentChange(current.totalReels, previous.totalReels),
  };
}

export function buildDailyStats(
  rows: readonly HourRow[],
  now: Date,
  days = DAILY_BUCKETS,
): PeriodStats {
  const today = startOfDay(now);
  const starts = Array.from({ length: days }, (_, i) => subDays(today, days - 1 - i));
  return build('daily', rows, starts, (d) => format(d, 'd'), today, subDays(today, 1));
}

export function buildWeeklyStats(
  rows: readonly HourRow[],
  now: Date,
  weeks = WEEKLY_BUCKETS,
): PeriodStats {
  const thisWeek = startOfISOWeek(now);
  const starts = Array.from({ length: weeks }, (_, i) => subWeeks(thisWeek, weeks - 1 - i));
  return build('weekly', rows, starts, (d) => `W${getISOWeek(d)}`, thisWeek, subWeeks(thisWeek, 1));
}

export function buildMonthlyStats(
  rows: readonly HourRow[],
  now: Date,
  months = MONTHLY_BUCKETS,
): PeriodStats {
  const thisMonth = startOfMonth(now);
  const starts = Array.from({ length: months }, (_, i) => subMonths(thisMonth, months - 1 - i));
  return build(
    'monthly',
    rows,
    starts,
    (d) => format(d, 'MMM'),
    thisMonth,
    subMonths(thisMonth, 1),
  );
}

/** 24 hourly buckets for one local day, zero-filled. */
export function buildHourly(rows: readonly HourRow[], day: Date): HourBucket[] {
  const key = dayKey(day);
  const out: HourBucket[] = Array.from({ length: 24 }, (_, hour) => ({
    hour,
    count: 0,
    durationMs: 0,
  }));
  for (const r of rows) {
    if (r.day !== key) continue;
    const b = out[r.hour];
    if (!b) continue;
    b.count += r.count;
    b.durationMs += r.durationMs;
  }
  return out;
}

export function buildStats(period: Period, rows: readonly HourRow[], now: Date): PeriodStats {
  switch (period) {
    case 'daily':
      return buildDailyStats(rows, now);
    case 'weekly':
      return buildWeeklyStats(rows, now);
    case 'monthly':
      return buildMonthlyStats(rows, now);
  }
}
