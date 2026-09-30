import { useCallback, useEffect, useState } from 'react';
import {
  DEFAULT_BADGE_STATE,
  getBadgeConfig,
  resetBadgePosition,
  setBadgeConfig,
  type BadgeConfigPatch,
  type BadgeState,
} from 'reels-tracker';

/** Badge settings live natively (the service reads them with the app closed). */
export function useBadgeSettings() {
  const [state, setState] = useState<BadgeState>(DEFAULT_BADGE_STATE);
  const [loaded, setLoaded] = useState(false);

  const reload = useCallback(async () => {
    setState(await getBadgeConfig());
    setLoaded(true);
  }, []);

  useEffect(() => {
    let alive = true;
    void getBadgeConfig().then((s) => {
      if (!alive) return;
      setState(s);
      setLoaded(true);
    });
    return () => {
      alive = false;
    };
  }, []);

  /** Optimistic update, then persist and re-read the canonical values. */
  const update = useCallback(
    async (patch: BadgeConfigPatch) => {
      setState((prev) => {
        const { dailyLimit, ...rest } = patch;
        const next: BadgeState = { ...prev, ...rest };
        if (dailyLimit !== undefined) {
          if (dailyLimit == null || dailyLimit <= 0) delete next.dailyLimit;
          else next.dailyLimit = dailyLimit;
        }
        if (patch.enabled) next.suppressed = false;
        return next;
      });
      await setBadgeConfig(patch);
      await reload();
    },
    [reload],
  );

  return { state, loaded, update, reload, resetPosition: resetBadgePosition };
}
