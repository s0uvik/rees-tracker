import { format } from 'date-fns';
import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';

import { getDb } from '@/db/client';
import { getAllViewsAscending, type ReelViewRow } from '@/db/queries';
import { syncFromNative } from '@/db/sync';

const HEADER = 'id,app,viewed_at_local,viewed_at_ms,duration_ms';

export function toCsv(rows: readonly ReelViewRow[]): string {
  const lines = rows.map((r) =>
    [
      r.id,
      r.app,
      format(new Date(r.viewedAt), "yyyy-MM-dd'T'HH:mm:ss.SSSxxx"),
      r.viewedAt,
      r.durationMs ?? '',
    ].join(','),
  );
  return [HEADER, ...lines].join('\n') + '\n';
}

/** Writes all views to a CSV in the cache dir and opens the share sheet. Returns row count. */
export async function exportCsv(): Promise<number> {
  await syncFromNative();
  const db = await getDb();
  const rows = await getAllViewsAscending(db);
  const file = new File(Paths.cache, `reels-${format(new Date(), 'yyyyMMdd-HHmmss')}.csv`);
  file.create({ overwrite: true });
  file.write(toCsv(rows));
  if (await Sharing.isAvailableAsync()) {
    await Sharing.shareAsync(file.uri, {
      mimeType: 'text/csv',
      dialogTitle: 'Export reel history',
      UTI: 'public.comma-separated-values-text',
    });
  }
  return rows.length;
}
