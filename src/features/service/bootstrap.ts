import { useEffect } from 'react';
import { AppState } from 'react-native';
import { isAccessibilityServiceEnabled } from 'reels-tracker';

import { getDb } from '@/db/client';
import { getSetting, setSetting } from '@/db/queries';
import { useUiStore } from '@/store/uiStore';

export const ONBOARDING_SKIPPED_KEY = 'onboarding_skipped';

export async function checkServiceEnabled(): Promise<boolean> {
  const enabled = await isAccessibilityServiceEnabled().catch(() => false);
  useUiStore.getState().setServiceEnabled(enabled);
  return enabled;
}

export async function setOnboardingSkipped(skipped: boolean): Promise<void> {
  useUiStore.getState().setOnboardingSkipped(skipped);
  const db = await getDb();
  await setSetting(db, ONBOARDING_SKIPPED_KEY, skipped ? '1' : '0');
}

/** Root-level: load persisted flags, check the service now and on every foreground. */
export function useAppBootstrap(): void {
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const db = await getDb();
        const skipped = (await getSetting(db, ONBOARDING_SKIPPED_KEY)) === '1';
        if (!cancelled) useUiStore.getState().setOnboardingSkipped(skipped);
      } catch {
        // A DB failure must not block the app; treat as not skipped.
      }
      await checkServiceEnabled();
      if (!cancelled) useUiStore.getState().setBootstrapped();
    })();

    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') void checkServiceEnabled();
    });
    return () => {
      cancelled = true;
      sub.remove();
    };
  }, []);
}
