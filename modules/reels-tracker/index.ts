import type { EventSubscription } from 'expo-modules-core';

import NativeModule from './src/ReelsTrackerModule';
import type {
  AckItem,
  BadgeConfigPatch,
  BadgeState,
  DebugEventSpec,
  PendingEvent,
  ReelsTrackerModuleEvents,
  TodayTotals,
  TrackedApp,
} from './src/ReelsTracker.types';

export * from './src/ReelsTracker.types';

export const TRACKED_APP_PACKAGES: Record<TrackedApp, string> = {
  instagram: 'com.instagram.android',
  youtube_shorts: 'com.google.android.youtube',
};

export const DEFAULT_BADGE_STATE: BadgeState = {
  enabled: false,
  visibility: 'in_tracked_apps',
  size: 'md',
  opacity: 0.9,
  tapAction: 'expand',
  suppressed: false,
};

/** True when running in a build that contains the native module. */
export const isNativeModuleAvailable = NativeModule != null;

export async function isAccessibilityServiceEnabled(): Promise<boolean> {
  return (await NativeModule?.isAccessibilityServiceEnabled()) ?? false;
}

/** Enabled in Settings AND currently bound by the system. */
export function isAccessibilityServiceRunning(): boolean {
  return NativeModule?.isAccessibilityServiceRunning() ?? false;
}

export function openAccessibilitySettings(): void {
  NativeModule?.openAccessibilitySettings();
}

/** Android 13+: sideloaded apps need "Allow restricted settings" from App info first. */
export function openAppDetailsSettings(): void {
  NativeModule?.openAppDetailsSettings();
}

export async function getTrackedPackages(): Promise<string[]> {
  return (await NativeModule?.getTrackedPackages()) ?? [TRACKED_APP_PACKAGES.instagram];
}

export async function setTrackedPackages(pkgs: string[]): Promise<void> {
  await NativeModule?.setTrackedPackages(pkgs);
}

export function addListener<E extends keyof ReelsTrackerModuleEvents>(
  eventName: E,
  listener: ReelsTrackerModuleEvents[E],
): EventSubscription {
  if (!NativeModule) return { remove: () => {} };
  return NativeModule.addListener(eventName, listener);
}

// Floating badge

export async function setBadgeEnabled(enabled: boolean): Promise<void> {
  await NativeModule?.setBadgeEnabled(enabled);
}

export async function getBadgeConfig(): Promise<BadgeState> {
  const raw = await NativeModule?.getBadgeConfig();
  if (!raw) return DEFAULT_BADGE_STATE;
  const { dailyLimit, ...rest } = raw;
  return dailyLimit != null && dailyLimit > 0 ? { ...rest, dailyLimit } : rest;
}

export async function setBadgeConfig(config: BadgeConfigPatch): Promise<void> {
  const { dailyLimit, opacity, ...rest } = config;
  await NativeModule?.setBadgeConfig({
    ...rest,
    ...(opacity !== undefined ? { opacity: Math.min(1, Math.max(0.4, opacity)) } : {}),
    // Native treats 0 as "no limit".
    ...(dailyLimit !== undefined ? { dailyLimit: dailyLimit ?? 0 } : {}),
  });
}

export async function resetBadgePosition(): Promise<void> {
  await NativeModule?.resetBadgePosition();
}

// Native buffer sync (used by src/db/sync.ts)

export async function drainPendingEvents(limit = 500): Promise<PendingEvent[]> {
  return (await NativeModule?.drainPendingEvents(limit)) ?? [];
}

export async function ackEvents(items: AckItem[]): Promise<number> {
  if (items.length === 0) return 0;
  return (await NativeModule?.ackEvents(items)) ?? 0;
}

export async function getTodayTotals(): Promise<TodayTotals> {
  return (await NativeModule?.getTodayTotals()) ?? { count: 0, durationMs: 0 };
}

export async function clearAllEvents(): Promise<void> {
  await NativeModule?.clearAllEvents();
}

/** Dev-only helper: rows go through the native buffer, so the badge updates too. */
export async function insertDebugEvents(events: DebugEventSpec[]): Promise<number> {
  return (await NativeModule?.insertDebugEvents(events)) ?? 0;
}
