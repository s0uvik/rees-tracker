import { Redirect } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { ScrollView, Text } from 'react-native';
import {
  getTodayTotals,
  insertDebugEvents,
  isAccessibilityServiceRunning,
  isNativeModuleAvailable,
  type DebugEventSpec,
  type TodayTotals,
} from 'reels-tracker';

import { Button, Card, Muted } from '@/components/ui';
import { formatDuration } from '@/lib/format';
import { useStatsStore } from '@/store/statsStore';

const DAY_MS = 24 * 60 * 60 * 1000;

function randomDuration(): number {
  // Skewed toward short watches, like real scrolling.
  return Math.round(1500 + Math.random() ** 2 * 45_000);
}

function todayEvents(n: number): DebugEventSpec[] {
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  const span = Date.now() - start.getTime();
  return Array.from({ length: n }, () => ({
    app: 'instagram' as const,
    viewedAt: start.getTime() + Math.floor(Math.random() * span),
    durationMs: randomDuration(),
  })).sort((a, b) => a.viewedAt - b.viewedAt);
}

function historyEvents(days: number): DebugEventSpec[] {
  const out: DebugEventSpec[] = [];
  const midnight = new Date();
  midnight.setHours(0, 0, 0, 0);
  for (let d = days; d >= 1; d--) {
    const dayStart = midnight.getTime() - d * DAY_MS;
    const count = Math.floor(Math.random() * 80);
    for (let i = 0; i < count; i++) {
      // Evenings are busier.
      const hour = Math.min(23, Math.floor(8 + Math.random() ** 0.6 * 16));
      out.push({
        app: 'instagram',
        viewedAt: dayStart + hour * 3_600_000 + Math.floor(Math.random() * 3_600_000),
        durationMs: randomDuration(),
      });
    }
  }
  return out;
}

export default function Debug() {
  const refresh = useStatsStore((s) => s.refresh);
  const [totals, setTotals] = useState<TodayTotals | null>(null);
  const [log, setLog] = useState<string>('');

  const reloadTotals = useCallback(async () => setTotals(await getTodayTotals()), []);

  useEffect(() => {
    let alive = true;
    void getTodayTotals().then((t) => {
      if (alive) setTotals(t);
    });
    return () => {
      alive = false;
    };
  }, []);

  if (!__DEV__) return <Redirect href="/" />;

  const run = async (label: string, events: DebugEventSpec[]) => {
    const n = await insertDebugEvents(events);
    await refresh();
    await reloadTotals();
    setLog(`${label}: inserted ${n} events`);
  };

  return (
    <ScrollView contentContainerClassName="gap-3 px-gutter py-4">
      <Card>
        <Muted>Native module: {isNativeModuleAvailable ? 'available' : 'missing (Expo Go?)'}</Muted>
        <Muted>Service bound: {isAccessibilityServiceRunning() ? 'yes' : 'no'}</Muted>
        <Muted>
          Native today:{' '}
          {totals ? `${totals.count} reels, ${formatDuration(totals.durationMs)}` : '…'}
        </Muted>
      </Card>
      <Muted>
        Events go through the native buffer, so the floating badge updates too (if the service is
        running).
      </Muted>
      <Button
        label="+1 reel now"
        onPress={() =>
          void run('+1', [{ app: 'instagram', viewedAt: Date.now(), durationMs: randomDuration() }])
        }
      />
      <Button
        label="+25 reels spread over today"
        onPress={() => void run('+25', todayEvents(25))}
      />
      <Button
        label="Seed 400 days of history"
        variant="secondary"
        onPress={() => void run('seed', historyEvents(400))}
      />
      {log ? <Text className="text-center text-sm text-ink dark:text-ink-dark">{log}</Text> : null}
    </ScrollView>
  );
}
