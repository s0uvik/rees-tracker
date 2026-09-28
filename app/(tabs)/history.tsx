import { FlashList } from '@shopify/flash-list';
import { format } from 'date-fns';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Muted } from '@/components/ui';
import { getDb } from '@/db/client';
import { getViewsPage, type ReelViewRow } from '@/db/queries';
import { groupByDay, type HistoryItem } from '@/features/history/groupByDay';
import { formatDuration } from '@/lib/format';
import { usePalette } from '@/lib/theme';
import { useStatsStore } from '@/store/statsStore';

const PAGE = 100;

const APP_LABEL: Record<string, string> = {
  instagram: 'Instagram',
  youtube_shorts: 'YouTube Shorts',
};

export default function History() {
  const p = usePalette();
  const [rows, setRows] = useState<ReelViewRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [done, setDone] = useState(false);
  const loadingMore = useRef(false);
  // The stats store re-syncs the native buffer; reuse its timestamp as our invalidation signal.
  const loadedAt = useStatsStore((s) => s.loadedAt);
  const refresh = useStatsStore((s) => s.refresh);
  const refreshing = useStatsStore((s) => s.refreshing);

  useEffect(() => {
    let alive = true;
    void getDb()
      .then((db) => getViewsPage(db, PAGE))
      .then((page) => {
        if (!alive) return;
        setRows(page);
        setDone(page.length < PAGE);
        setLoading(false);
      });
    return () => {
      alive = false;
    };
  }, [loadedAt]);

  const loadMore = useCallback(async () => {
    if (done || loadingMore.current) return;
    const last = rows[rows.length - 1];
    if (!last) return;
    loadingMore.current = true;
    try {
      const db = await getDb();
      const page = await getViewsPage(db, PAGE, { viewedAt: last.viewedAt, id: last.id });
      setRows((prev) => [...prev, ...page]);
      if (page.length < PAGE) setDone(true);
    } finally {
      loadingMore.current = false;
    }
  }, [done, rows]);

  const items = useMemo(() => groupByDay(rows), [rows]);

  const renderItem = useCallback(({ item }: { item: HistoryItem }) => {
    if (item.type === 'header') {
      return (
        <View className="flex-row items-baseline justify-between bg-bg px-gutter pb-2 pt-5 dark:bg-bg-dark">
          <Text className="text-base font-semibold text-ink dark:text-ink-dark">{item.title}</Text>
          <Muted>
            {item.count} reels · {formatDuration(item.durationMs)}
          </Muted>
        </View>
      );
    }
    const { row } = item;
    return (
      <View className="mx-gutter flex-row items-center border-b border-line py-3 dark:border-line-dark">
        <Text
          className="w-20 text-base text-ink dark:text-ink-dark"
          style={{ fontVariant: ['tabular-nums'] }}
        >
          {format(new Date(row.viewedAt), 'HH:mm:ss')}
        </Text>
        <Muted className="flex-1">{APP_LABEL[row.app] ?? row.app}</Muted>
        <Text
          className="text-base text-ink dark:text-ink-dark"
          style={{ fontVariant: ['tabular-nums'] }}
        >
          {row.durationMs != null ? `${Math.round(row.durationMs / 1000)}s` : '—'}
        </Text>
      </View>
    );
  }, []);

  return (
    <SafeAreaView edges={['top']} className="flex-1 bg-bg dark:bg-bg-dark">
      <Text className="px-gutter pb-2 pt-4 text-3xl font-bold text-ink dark:text-ink-dark">
        History
      </Text>
      {loading ? (
        <ActivityIndicator className="mt-10" color={p.accent} />
      ) : (
        <FlashList
          data={items}
          renderItem={renderItem}
          keyExtractor={(item) => item.key}
          getItemType={(item) => item.type}
          onEndReached={() => void loadMore()}
          onEndReachedThreshold={0.5}
          refreshing={refreshing}
          onRefresh={() => void refresh()}
          ListEmptyComponent={
            <Muted className="mt-10 text-center">
              No reels recorded yet. Open Instagram Reels and swipe.
            </Muted>
          }
          contentContainerStyle={{ paddingBottom: 40 }}
        />
      )}
    </SafeAreaView>
  );
}
