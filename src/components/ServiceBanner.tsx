import { Pressable, Text } from 'react-native';
import { openAccessibilitySettings } from 'reels-tracker';

import { useUiStore } from '@/store/uiStore';

/** Persistent warning shown while the accessibility service is off. */
export function ServiceBanner() {
  const enabled = useUiStore((s) => s.serviceEnabled);
  if (enabled !== false) return null;
  return (
    <Pressable
      accessibilityRole="button"
      onPress={openAccessibilitySettings}
      className="mb-4 rounded-2xl border border-warn/40 bg-warn/10 px-4 py-3 active:opacity-80 dark:border-warn-dark/40 dark:bg-warn-dark/10"
    >
      <Text className="text-sm font-semibold text-warn dark:text-warn-dark">
        Tracking is off
      </Text>
      <Text className="mt-0.5 text-sm text-ink dark:text-ink-dark">
        Reels aren’t being counted. Tap to turn on Reels Counter in Accessibility settings.
      </Text>
    </Pressable>
  );
}
