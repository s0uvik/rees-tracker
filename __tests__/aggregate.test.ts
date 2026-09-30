/**
 * Runs with TZ=America/New_York (see jest.global-setup.js) so DST and
 * UTC-offset cases are deterministic. `local(...)` builds wall-clock times in
 * that zone.
 */
import {
  buildDailyStats,
  buildHourly,
  buildMonthlyStats,
  buildWeeklyStats,
  percentChange,
  rollupEvents,
  statsRangeEnd,
  statsRangeStart,
  summarize,
} from '@/features/stats/aggregate';

const local = (y: number, m: number, d: number, h = 12, min = 0, s = 0) =>
  new Date(y, m - 1, d, h, min, s);

const ev = (date: Date, durationMs: number | null = 5_000) => ({
  viewedAt: date.getTime(),
  durationMs,
});

describe('environment', () => {
  it('runs in a DST-observing zone', () => {
    // January is EST (UTC-5), July is EDT (UTC-4).
    expect(new Date(2026, 0, 15).getTimezoneOffset()).toBe(300);
    expect(new Date(2026, 6, 15).getTimezoneOffset()).toBe(240);
  });
});

describe('rollupEvents (mirror of the SQL localtime rollup)', () => {
  it('buckets by local day and hour, not UTC', () => {
    // 03:30 UTC on Jan 1 is 22:30 on Dec 31 in New York.
    const rows = rollupEvents([{ viewedAt: Date.UTC(2026, 0, 1, 3, 30), durationMs: 1000 }]);
    expect(rows).toEqual([
      { day: '2025-12-31', hour: 22, count: 1, durationMs: 1000, timedCount: 1 },
    ]);
  });

  it('counts untimed views but excludes them from timedCount', () => {
    const rows = rollupEvents([ev(local(2026, 5, 1, 9)), ev(local(2026, 5, 1, 9, 30), null)]);
    expect(rows).toEqual([
      { day: '2026-05-01', hour: 9, count: 2, durationMs: 5000, timedCount: 1 },
    ]);
  });
});

describe('daily stats', () => {
  it('returns 30 contiguous zero-filled buckets ending today', () => {
    const now = local(2026, 6, 15, 18);
    const rows = rollupEvents([ev(local(2026, 6, 15, 9)), ev(local(2026, 6, 1, 9))]);
    const stats = buildDailyStats(rows, now);

    expect(stats.buckets).toHaveLength(30);
    expect(stats.buckets[0]?.key).toBe('2026-05-17');
    expect(stats.buckets[29]?.key).toBe('2026-06-15');
    expect(stats.buckets.filter((b) => b.count > 0).map((b) => b.key)).toEqual([
      '2026-06-01',
      '2026-06-15',
    ]);
    expect(stats.buckets.every((b) => Number.isFinite(b.count))).toBe(true);
  });

  it('compares today with yesterday', () => {
    const now = local(2026, 6, 15, 18);
    const rows = rollupEvents([
      ev(local(2026, 6, 15, 8)),
      ev(local(2026, 6, 15, 9)),
      ev(local(2026, 6, 15, 10)),
      ev(local(2026, 6, 14, 23, 59, 59)),
      ev(local(2026, 6, 14, 1)),
    ]);
    const stats = buildDailyStats(rows, now);
    expect(stats.current.totalReels).toBe(3);
    expect(stats.previous.totalReels).toBe(2);
    expect(stats.changePct).toBeCloseTo(50);
  });

  it('keeps one bucket per calendar day across the spring-forward gap', () => {
    const now = local(2026, 3, 10, 12);
    const stats = buildDailyStats(rollupEvents([]), now);
    const keys = stats.buckets.map((b) => b.key);
    expect(new Set(keys).size).toBe(30);
    expect(keys).toContain('2026-03-08');
    expect(keys.slice(-3)).toEqual(['2026-03-08', '2026-03-09', '2026-03-10']);
  });
});

describe('hourly', () => {
  it('fills 24 hours for today', () => {
    const now = local(2026, 6, 15, 20);
    const rows = rollupEvents([ev(local(2026, 6, 15, 0, 5)), ev(local(2026, 6, 15, 23, 55))]);
    const hours = buildHourly(rows, now);
    expect(hours).toHaveLength(24);
    expect(hours[0]?.count).toBe(1);
    expect(hours[23]?.count).toBe(1);
    expect(hours.reduce((s, h) => s + h.count, 0)).toBe(2);
  });

  it('handles the 23-hour spring-forward day (02:00 does not exist)', () => {
    const day = local(2026, 3, 8);
    const rows = rollupEvents([ev(local(2026, 3, 8, 1, 30)), ev(local(2026, 3, 8, 3, 30))]);
    const hours = buildHourly(rows, day);
    expect(hours[1]?.count).toBe(1);
    expect(hours[2]?.count).toBe(0);
    expect(hours[3]?.count).toBe(1);
  });

  it('merges the repeated 01:00 hour on the fall-back day', () => {
    // 05:30Z = 01:30 EDT, 06:30Z = 01:30 EST on 2026-11-01.
    const rows = rollupEvents([
      { viewedAt: Date.UTC(2026, 10, 1, 5, 30), durationMs: 1000 },
      { viewedAt: Date.UTC(2026, 10, 1, 6, 30), durationMs: 1000 },
    ]);
    expect(rows).toEqual([
      { day: '2026-11-01', hour: 1, count: 2, durationMs: 2000, timedCount: 2 },
    ]);
  });
});

describe('weekly stats (ISO weeks, Monday start)', () => {
  it('puts Sunday in the week that started the previous Monday', () => {
    const now = local(2026, 3, 11); // Wednesday
    const rows = rollupEvents([
      ev(local(2026, 3, 8, 23, 59)), // Sunday → previous week
      ev(local(2026, 3, 9, 0, 0, 30)), // Monday → this week
      ev(local(2026, 3, 11, 9)), // Wednesday → this week
    ]);
    const stats = buildWeeklyStats(rows, now);
    expect(stats.buckets).toHaveLength(12);
    expect(stats.buckets[11]?.key).toBe('2026-03-09');
    expect(stats.buckets[10]?.key).toBe('2026-03-02');
    expect(stats.current.totalReels).toBe(2);
    expect(stats.previous.totalReels).toBe(1);
  });

  it('treats a Sunday "now" as the end of its Monday-start week', () => {
    const stats = buildWeeklyStats(rollupEvents([ev(local(2026, 3, 9))]), local(2026, 3, 15, 22));
    expect(stats.buckets[11]?.key).toBe('2026-03-09');
    expect(stats.current.totalReels).toBe(1);
  });

  it('spans the year boundary with ISO week numbers', () => {
    const now = local(2026, 1, 1); // Thursday; ISO week 1 of 2026 starts Mon 2025-12-29
    const rows = rollupEvents([ev(local(2025, 12, 29, 10)), ev(local(2025, 12, 28, 10))]);
    const stats = buildWeeklyStats(rows, now);
    const last = stats.buckets[11];
    expect(last?.key).toBe('2025-12-29');
    expect(last?.label).toBe('W1');
    expect(stats.buckets[10]?.label).toBe('W52');
    expect(stats.current.totalReels).toBe(1);
    expect(stats.previous.totalReels).toBe(1);
  });
});

describe('monthly stats', () => {
  it('splits exactly at local midnight on the 1st', () => {
    const now = local(2026, 3, 1, 10);
    const rows = rollupEvents([
      ev(local(2026, 2, 28, 23, 59, 59)),
      ev(local(2026, 3, 1, 0, 0, 1)),
      ev(local(2026, 1, 31, 12)),
    ]);
    const stats = buildMonthlyStats(rows, now);
    expect(stats.buckets).toHaveLength(12);
    expect(stats.buckets[0]?.key).toBe('2025-04');
    expect(stats.buckets[11]?.key).toBe('2026-03');
    expect(stats.buckets.find((b) => b.key === '2026-02')?.count).toBe(1);
    expect(stats.buckets.find((b) => b.key === '2026-01')?.count).toBe(1);
    expect(stats.current.totalReels).toBe(1);
    expect(stats.previous.totalReels).toBe(1);
  });

  it('assigns a UTC-next-month instant to the local month', () => {
    // 2026-04-01 02:00Z is still March 31 in New York.
    const rows = rollupEvents([{ viewedAt: Date.UTC(2026, 3, 1, 2), durationMs: 1 }]);
    const stats = buildMonthlyStats(rows, local(2026, 4, 2));
    expect(stats.buckets.find((b) => b.key === '2026-03')?.count).toBe(1);
    expect(stats.current.totalReels).toBe(0);
  });

  it('covers the whole monthly range with one query window', () => {
    const now = local(2026, 3, 15);
    expect(statsRangeStart(now)).toEqual(local(2025, 4, 1, 0));
    expect(statsRangeEnd(now)).toEqual(local(2026, 3, 16, 0));
  });
});

describe('summaries', () => {
  it('computes totals, average over timed views, and peak hour', () => {
    const rows = rollupEvents([
      ev(local(2026, 6, 1, 21, 0), 10_000),
      ev(local(2026, 6, 1, 21, 10), 20_000),
      ev(local(2026, 6, 1, 21, 20), null),
      ev(local(2026, 6, 1, 8, 0), 30_000),
    ]);
    expect(summarize(rows)).toEqual({
      totalReels: 4,
      totalWatchMs: 60_000,
      avgMsPerReel: 20_000,
      peakHour: 21,
    });
  });

  it('returns nulls for an empty period', () => {
    expect(summarize([])).toEqual({
      totalReels: 0,
      totalWatchMs: 0,
      avgMsPerReel: null,
      peakHour: null,
    });
  });

  it('computes percent change', () => {
    expect(percentChange(0, 0)).toBe(0);
    expect(percentChange(5, 0)).toBeNull();
    expect(percentChange(15, 10)).toBeCloseTo(50);
    expect(percentChange(5, 10)).toBeCloseTo(-50);
  });
});
