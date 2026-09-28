import { Text, View } from 'react-native';

import { formatChange } from '@/lib/format';

import { Card, Muted } from './ui';

export function HeroCard({
  label,
  count,
  changePct,
  previousLabel,
}: {
  label: string;
  count: number;
  changePct: number | null;
  previousLabel: string;
}) {
  const change = formatChange(changePct);
  // More reels is the "bad" direction for a wellbeing app.
  const tone =
    change.tone === 'up'
      ? 'text-bad dark:text-bad-dark'
      : change.tone === 'down'
        ? 'text-good dark:text-good-dark'
        : 'text-muted dark:text-muted-dark';
  return (
    <Card>
      <Muted>{label}</Muted>
      <View className="mt-1 flex-row items-end justify-between">
        <Text
          accessibilityLabel={`${count} reels`}
          className="text-6xl font-bold tracking-tight text-ink dark:text-ink-dark"
          style={{ fontVariant: ['tabular-nums'] }}
        >
          {count}
        </Text>
        <View className="mb-2 items-end">
          <Text className={`text-lg font-semibold ${tone}`}>{change.text}</Text>
          <Muted>vs {previousLabel}</Muted>
        </View>
      </View>
      <Muted className="mt-1">reels watched</Muted>
    </Card>
  );
}

export function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <Card className="flex-1">
      <Muted>{label}</Muted>
      <Text
        className="mt-1 text-2xl font-bold text-ink dark:text-ink-dark"
        style={{ fontVariant: ['tabular-nums'] }}
        numberOfLines={1}
        adjustsFontSizeToFit
      >
        {value}
      </Text>
    </Card>
  );
}
