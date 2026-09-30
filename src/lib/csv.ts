import { format } from 'date-fns';

import type { ReelViewRow } from '@/db/queries';

const HEADER = 'id,app,viewed_at_local,viewed_at_ms,duration_ms';

/** Pure CSV serialisation; values are numbers / known app keys, so no quoting is needed. */
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
