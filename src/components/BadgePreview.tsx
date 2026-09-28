import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import type { BadgeSize } from 'reels-tracker';

import { formatDuration } from '@/lib/format';
import { usePalette } from '@/lib/theme';

const SIZES: Record<BadgeSize, { text: number; detail: number; padH: number; padV: number }> = {
  // Mirrors BadgeSize in BadgeModel.kt
  sm: { text: 12, detail: 10, padH: 9, padV: 4 },
  md: { text: 14, detail: 12, padH: 12, padV: 6 },
  lg: { text: 17, detail: 14, padH: 15, padV: 8 },
};

export function badgeLevel(count: number, limit?: number): 'neutral' | 'amber' | 'red' {
  if (!limit || limit <= 0) return 'neutral';
  if (count >= limit) return 'red';
  if (count * 100 >= limit * 75) return 'amber';
  return 'neutral';
}

/** Live, in-app replica of the floating badge so settings changes are visible immediately. */
export function BadgePreview({
  count,
  watchMs,
  size,
  opacity,
  dailyLimit,
  disabled,
}: {
  count: number;
  watchMs: number;
  size: BadgeSize;
  opacity: number;
  dailyLimit?: number;
  disabled?: boolean;
}) {
  const p = usePalette();
  const [expanded, setExpanded] = useState(false);
  const s = SIZES[size];
  const level = badgeLevel(count, dailyLimit);
  const bg = level === 'red' ? p.badgeRed : level === 'amber' ? p.badgeAmber : p.badgeNeutral;

  return (
    <View className="h-24 items-end justify-center rounded-2xl bg-line/50 px-3 dark:bg-black/40">
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`Badge preview: ${count} reels today`}
        onPress={() => setExpanded((v) => !v)}
        disabled={disabled}
        style={{
          opacity: disabled ? 0.3 : opacity,
          backgroundColor: bg,
          paddingHorizontal: s.padH,
          paddingVertical: s.padV,
          borderRadius: 999,
          borderWidth: 1,
          borderColor: 'rgba(255,255,255,0.2)',
          flexDirection: 'row',
          alignItems: 'center',
        }}
      >
        <Text
          style={{
            color: '#FFFFFF',
            fontSize: s.text,
            fontWeight: '700',
            fontVariant: ['tabular-nums'],
          }}
        >
          🎬 {count}
        </Text>
        {expanded ? (
          <Text style={{ color: 'rgba(255,255,255,0.85)', fontSize: s.detail, marginLeft: 6 }}>
            {formatDuration(watchMs)}
          </Text>
        ) : null}
      </Pressable>
    </View>
  );
}
