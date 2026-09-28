import { useState } from 'react';
import { Text, View } from 'react-native';
import { BarChart } from 'react-native-gifted-charts';

import type { Bucket, HourBucket } from '@/features/stats/types';
import { usePalette } from '@/lib/theme';

import { Card, Muted } from './ui';

type BarDatum = { value: number; label?: string; frontColor?: string };

const Y_AXIS_WIDTH = 28;

function niceMax(values: number[]): number {
  const max = Math.max(0, ...values);
  if (max <= 4) return 4;
  const magnitude = 10 ** Math.floor(Math.log10(max));
  const step = magnitude / 2;
  return Math.ceil(max / step) * step;
}

function Bars({
  data,
  labelEvery,
  height = 180,
}: {
  data: BarDatum[];
  labelEvery: number;
  height?: number;
}) {
  const p = usePalette();
  const [width, setWidth] = useState(0);
  const n = Math.max(1, data.length);
  const plotWidth = Math.max(0, width - Y_AXIS_WIDTH - 8);
  // Bars take ~60% of each slot, gaps the rest; everything fits without scrolling.
  const slot = plotWidth / n;
  const barWidth = Math.max(3, slot * 0.6);
  const spacing = Math.max(1, slot - barWidth);
  const maxValue = niceMax(data.map((d) => d.value));

  const labelled = data.map((d, i) => ({
    ...d,
    label: i % labelEvery === 0 || i === data.length - 1 ? d.label : '',
  }));

  return (
    <View onLayout={(e) => setWidth(e.nativeEvent.layout.width)}>
      {width > 0 ? (
        <BarChart
          data={labelled}
          width={plotWidth}
          height={height}
          barWidth={barWidth}
          spacing={spacing}
          initialSpacing={spacing / 2}
          endSpacing={0}
          disableScroll
          roundedTop
          barBorderTopLeftRadius={3}
          frontColor={p.accent}
          maxValue={maxValue}
          noOfSections={4}
          yAxisLabelWidth={Y_AXIS_WIDTH}
          yAxisThickness={0}
          xAxisThickness={1}
          xAxisColor={p.line}
          rulesColor={p.line}
          rulesType="dashed"
          yAxisTextStyle={{ color: p.muted, fontSize: 10 }}
          xAxisLabelTextStyle={{ color: p.muted, fontSize: 10, width: slot * labelEvery }}
          labelWidth={slot * labelEvery}
          isAnimated
        />
      ) : (
        <View style={{ height: height + 30 }} />
      )}
    </View>
  );
}

export function PeriodBarChart({
  title,
  buckets,
  labelEvery,
}: {
  title: string;
  buckets: Bucket[];
  labelEvery: number;
}) {
  const p = usePalette();
  const last = buckets.length - 1;
  const data = buckets.map((b, i) => ({
    value: b.count,
    label: b.label,
    // Current bucket in full accent, history softer.
    frontColor: i === last ? p.accent : p.accentSoft,
  }));
  const total = buckets.reduce((s, b) => s + b.count, 0);
  return (
    <Card>
      <View className="mb-3 flex-row items-baseline justify-between">
        <Text className="text-base font-semibold text-ink dark:text-ink-dark">{title}</Text>
        <Muted>{total} total</Muted>
      </View>
      <Bars data={data} labelEvery={labelEvery} />
    </Card>
  );
}

export function HourlyChart({ hours }: { hours: HourBucket[] }) {
  const p = usePalette();
  const nowHour = new Date().getHours();
  const data = hours.map((h) => ({
    value: h.count,
    label: `${h.hour}`,
    frontColor: h.hour === nowHour ? p.accent : p.accentSoft,
  }));
  return (
    <Card>
      <Text className="mb-3 text-base font-semibold text-ink dark:text-ink-dark">
        Today by hour
      </Text>
      <Bars data={data} labelEvery={6} height={120} />
    </Card>
  );
}
