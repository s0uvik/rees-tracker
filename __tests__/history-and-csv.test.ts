import { groupByDay } from '@/features/history/groupByDay';
import { toCsv } from '@/lib/csv';

const HOUR = 3_600_000;

describe('groupByDay', () => {
  it('emits a header per local day with totals, newest first', () => {
    const today = new Date();
    today.setHours(12, 0, 0, 0);
    const t = today.getTime();
    const rows = [
      { id: 3, app: 'instagram', viewedAt: t, durationMs: 4000 },
      { id: 2, app: 'instagram', viewedAt: t - HOUR, durationMs: null },
      { id: 1, app: 'instagram', viewedAt: t - 24 * HOUR, durationMs: 6000 },
    ];
    const items = groupByDay(rows);
    expect(items.map((i) => i.type)).toEqual(['header', 'view', 'view', 'header', 'view']);
    const [first, , , second] = items;
    expect(first).toMatchObject({ type: 'header', title: 'Today', count: 2, durationMs: 4000 });
    expect(second).toMatchObject({
      type: 'header',
      title: 'Yesterday',
      count: 1,
      durationMs: 6000,
    });
  });

  it('returns nothing for no rows', () => {
    expect(groupByDay([])).toEqual([]);
  });
});

describe('toCsv', () => {
  it('writes a header and local timestamps with offset', () => {
    const csv = toCsv([
      { id: 7, app: 'instagram', viewedAt: Date.UTC(2026, 0, 15, 17, 0, 0), durationMs: 1234 },
      { id: 8, app: 'youtube_shorts', viewedAt: Date.UTC(2026, 6, 15, 17, 0, 0), durationMs: null },
    ]);
    expect(csv.split('\n')).toEqual([
      'id,app,viewed_at_local,viewed_at_ms,duration_ms',
      `7,instagram,2026-01-15T12:00:00.000-05:00,${Date.UTC(2026, 0, 15, 17)},1234`,
      `8,youtube_shorts,2026-07-15T13:00:00.000-04:00,${Date.UTC(2026, 6, 15, 17)},`,
      '',
    ]);
  });
});
