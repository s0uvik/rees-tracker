/** Display helpers shared by screens and components. */

export function formatDuration(ms: number): string {
  const totalSeconds = Math.max(0, Math.round(ms / 1000));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) return `${hours}h ${minutes}m`;
  if (minutes > 0) return `${minutes}m`;
  return `${seconds}s`;
}

export function formatSeconds(ms: number | null): string {
  if (ms == null) return '—';
  return `${(ms / 1000).toFixed(ms < 10_000 ? 1 : 0)}s`;
}

/** 0–23 → "9 PM" style label. */
export function formatHour(hour: number | null): string {
  if (hour == null) return '—';
  const suffix = hour < 12 ? 'AM' : 'PM';
  const h = hour % 12 === 0 ? 12 : hour % 12;
  return `${h} ${suffix}`;
}

export function formatChange(pct: number | null): { text: string; tone: 'up' | 'down' | 'flat' } {
  if (pct == null) return { text: 'new', tone: 'up' };
  if (Math.abs(pct) < 0.5) return { text: '0%', tone: 'flat' };
  const rounded = Math.round(pct);
  return { text: `${rounded > 0 ? '+' : ''}${rounded}%`, tone: rounded > 0 ? 'up' : 'down' };
}

export const PERIOD_LABELS = {
  daily: { current: 'Today', previous: 'yesterday' },
  weekly: { current: 'This week', previous: 'last week' },
  monthly: { current: 'This month', previous: 'last month' },
} as const;
