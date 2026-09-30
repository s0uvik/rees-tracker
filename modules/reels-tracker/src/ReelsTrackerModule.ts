import { NativeModule, requireOptionalNativeModule } from 'expo';

import type {
  AckItem,
  BadgeState,
  DebugEventSpec,
  PendingEvent,
  ReelsTrackerModuleEvents,
  TodayTotals,
} from './ReelsTracker.types';

type NativeBadgePatch = {
  enabled?: boolean;
  visibility?: string;
  size?: string;
  opacity?: number;
  tapAction?: string;
  dailyLimit?: number;
};

type NativeBadgeState = Omit<BadgeState, 'dailyLimit'> & { dailyLimit: number | null };

export declare class ReelsTrackerNativeModule extends NativeModule<ReelsTrackerModuleEvents> {
  isAccessibilityServiceEnabled(): Promise<boolean>;
  isAccessibilityServiceRunning(): boolean;
  openAccessibilitySettings(): void;
  openAppDetailsSettings(): void;
  getTrackedPackages(): Promise<string[]>;
  setTrackedPackages(packages: string[]): Promise<void>;
  setBadgeEnabled(enabled: boolean): Promise<void>;
  getBadgeConfig(): Promise<NativeBadgeState>;
  setBadgeConfig(patch: NativeBadgePatch): Promise<void>;
  resetBadgePosition(): Promise<void>;
  drainPendingEvents(limit: number): Promise<PendingEvent[]>;
  ackEvents(items: AckItem[]): Promise<number>;
  getTodayTotals(): Promise<TodayTotals>;
  clearAllEvents(): Promise<void>;
  insertDebugEvents(events: DebugEventSpec[]): Promise<number>;
}

/**
 * Null outside an Android dev build (Expo Go, web, Jest). The public API in
 * index.ts degrades to safe defaults instead of crashing.
 */
export default requireOptionalNativeModule<ReelsTrackerNativeModule>('ReelsTracker');
