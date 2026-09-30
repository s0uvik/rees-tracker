export type Period = 'daily' | 'weekly' | 'monthly';

/** One row of the SQL day × hour rollup (local time). */
export type HourRow = {
  /** Local calendar day, yyyy-MM-dd */
  day: string;
  /** Local hour 0–23 */
  hour: number;
  count: number;
  durationMs: number;
  /** Rows in this group that have a duration (open views have none yet). */
  timedCount: number;
};

export type Bucket = {
  /** Stable key: yyyy-MM-dd (day / ISO-week Monday) or yyyy-MM (month). */
  key: string;
  /** Short chart label. */
  label: string;
  /** Local start of the bucket, epoch ms. */
  startMs: number;
  count: number;
  durationMs: number;
};

export type PeriodSummary = {
  totalReels: number;
  totalWatchMs: number;
  /** null when no view in the period has a duration yet. */
  avgMsPerReel: number | null;
  /** Local hour 0–23 with the most views; null when empty. */
  peakHour: number | null;
};

export type PeriodStats = {
  period: Period;
  buckets: Bucket[];
  /** Today / this ISO week / this month. */
  current: PeriodSummary;
  /** Yesterday / last week / last month. */
  previous: PeriodSummary;
  /** % change of reel count vs previous; null when previous is 0 and current is not. */
  changePct: number | null;
};

export type HourBucket = { hour: number; count: number; durationMs: number };
