import { RefreshControl, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { HourlyChart, PeriodBarChart } from '@/components/Charts';
import { ServiceBanner } from '@/components/ServiceBanner';
import { HeroCard, StatCard } from '@/components/StatCards';
import { Muted, Segmented } from '@/components/ui';
import { usePeriodStats, useStatsRefresh, useTodayHourly } from '@/features/stats/hooks';
import type { Period } from '@/features/stats/types';
import { formatDuration, formatHour, formatSeconds, PERIOD_LABELS } from '@/lib/format';
import { usePalette } from '@/lib/theme';
import { useStatsStore } from '@/store/statsStore';
import { useUiStore } from '@/store/uiStore';

const PERIODS = [
  { value: 'daily', label: 'Daily' },
  { value: 'weekly', label: 'Weekly' },
  { value: 'monthly', label: 'Monthly' },
] as const satisfies readonly { value: Period; label: string }[];

const CHART: Record<Period, { title: string; labelEvery: number }> = {
  daily: { title: 'Last 30 days', labelEvery: 5 },
  weekly: { title: 'Last 12 weeks', labelEvery: 3 },
  monthly: { title: 'Last 12 months', labelEvery: 2 },
};

export default function Dashboard() {
  const p = usePalette();
  const period = useUiStore((s) => s.period);
  const setPeriod = useUiStore((s) => s.setPeriod);
  const stats = usePeriodStats(period);
  const hourly = useTodayHourly();
  const { refreshing, refresh } = useStatsRefresh();
  const error = useStatsStore((s) => s.error);
  const labels = PERIOD_LABELS[period];

  return (
    <SafeAreaView edges={['top']} className="flex-1 bg-bg dark:bg-bg-dark">
      <ScrollView
        contentContainerClassName="px-gutter pb-10 pt-4"
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={() => void refresh()}
            tintColor={p.accent}
            colors={[p.accent]}
            progressBackgroundColor={p.card}
          />
        }
      >
        <Text className="mb-4 text-3xl font-bold text-ink dark:text-ink-dark">Reels</Text>
        <ServiceBanner />

        <Segmented options={PERIODS} value={period} onChange={setPeriod} />

        <View className="mt-4 gap-3">
          <HeroCard
            label={labels.current}
            count={stats.current.totalReels}
            changePct={stats.changePct}
            previousLabel={labels.previous}
          />

          <View className="flex-row gap-3">
            <StatCard label="Watch time" value={formatDuration(stats.current.totalWatchMs)} />
            <StatCard label="Avg / reel" value={formatSeconds(stats.current.avgMsPerReel)} />
            <StatCard label="Peak hour" value={formatHour(stats.current.peakHour)} />
          </View>

          <PeriodBarChart
            title={CHART[period].title}
            buckets={stats.buckets}
            labelEvery={CHART[period].labelEvery}
          />

          {period === 'daily' ? <HourlyChart hours={hourly} /> : null}

          {error ? <Muted className="text-center">Couldn’t load stats: {error}</Muted> : null}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
