export type TrackedApp = 'instagram' | 'youtube_shorts';

export type ReelViewEvent = {
  app: TrackedApp;
  /** epoch ms */
  viewedAt: number;
  durationMs?: number;
  /** Row id in the native buffer; used to upsert into the JS database. */
  nativeId: number;
};

export type BadgeVisibility = 'always' | 'in_tracked_apps';
export type BadgeSize = 'sm' | 'md' | 'lg';
export type BadgeTapAction = 'expand' | 'open_app';

export type BadgeConfig = {
  enabled: boolean;
  visibility: BadgeVisibility;
  size: BadgeSize;
  /** 0.4 – 1.0 */
  opacity: number;
  tapAction: BadgeTapAction;
  /** Shared with the daily-limit setting. Undefined = no limit. */
  dailyLimit?: number;
};

/** Pass `dailyLimit: null` to clear the limit. */
export type BadgeConfigPatch = Partial<Omit<BadgeConfig, 'dailyLimit'>> & {
  dailyLimit?: number | null;
};

export type BadgeState = BadgeConfig & {
  /** Hidden by long-press / drop-to-dismiss until re-enabled from the app. */
  suppressed: boolean;
};

/** A row from the native buffer that has not been copied to the JS database yet. */
export type PendingEvent = {
  nativeId: number;
  app: TrackedApp;
  viewedAt: number;
  durationMs: number | null;
  version: number;
};

export type AckItem = { id: number; version: number };

export type DebugEventSpec = {
  app: TrackedApp;
  viewedAt: number;
  durationMs?: number;
};

export type TodayTotals = { count: number; durationMs: number };

export type ReelsTrackerModuleEvents = {
  onReelViewed: (event: ReelViewEvent) => void;
  onDataChanged: () => void;
};
