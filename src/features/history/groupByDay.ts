import { format, isToday, isYesterday } from 'date-fns';

import type { ReelViewRow } from '@/db/queries';

export type HistoryItem =
  | { type: 'header'; key: string; title: string; count: number; durationMs: number }
  | { type: 'view'; key: string; row: ReelViewRow };

function dayTitle(d: Date): string {
  if (isToday(d)) return 'Today';
  if (isYesterday(d)) return 'Yesterday';
  return format(d, 'EEEE, MMM d');
}

/** Flattens newest-first rows into header + row items for a virtualized list. */
export function groupByDay(rows: readonly ReelViewRow[]): HistoryItem[] {
  const items: HistoryItem[] = [];
  let header: Extract<HistoryItem, { type: 'header' }> | null = null;
  for (const row of rows) {
    const d = new Date(row.viewedAt);
    const key = format(d, 'yyyy-MM-dd');
    if (!header || header.key !== `h-${key}`) {
      header = { type: 'header', key: `h-${key}`, title: dayTitle(d), count: 0, durationMs: 0 };
      items.push(header);
    }
    header.count += 1;
    header.durationMs += row.durationMs ?? 0;
    items.push({ type: 'view', key: `v-${row.id}`, row });
  }
  return items;
}
