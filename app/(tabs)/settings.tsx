import Slider from '@react-native-community/slider';
import * as Notifications from 'expo-notifications';
import { Link } from 'expo-router';
import { useEffect, useState } from 'react';
import { Alert, ScrollView, Text, TextInput, View } from 'react-native';
import {
  clearAllEvents,
  getTrackedPackages,
  openAccessibilitySettings,
  setTrackedPackages,
  TRACKED_APP_PACKAGES,
  type BadgeSize,
  type BadgeTapAction,
  type BadgeVisibility,
  type TrackedApp,
} from 'reels-tracker';
import { SafeAreaView } from 'react-native-safe-area-context';

import { BadgePreview } from '@/components/BadgePreview';
import {
  Button,
  Card,
  Divider,
  Muted,
  Row,
  SectionTitle,
  Segmented,
  ToggleRow,
} from '@/components/ui';
import { getDb } from '@/db/client';
import { deleteAllViews } from '@/db/queries';
import { useBadgeSettings } from '@/features/badge/useBadgeSettings';
import { checkServiceEnabled } from '@/features/service/bootstrap';
import { useDailyStats } from '@/features/stats/hooks';
import { exportCsv } from '@/lib/exportCsv';
import { usePalette } from '@/lib/theme';
import { useStatsStore } from '@/store/statsStore';
import { useUiStore } from '@/store/uiStore';

const VISIBILITY = [
  { value: 'in_tracked_apps', label: 'In Instagram' },
  { value: 'always', label: 'Always' },
] as const satisfies readonly { value: BadgeVisibility; label: string }[];

const SIZES = [
  { value: 'sm', label: 'S' },
  { value: 'md', label: 'M' },
  { value: 'lg', label: 'L' },
] as const satisfies readonly { value: BadgeSize; label: string }[];

const TAP = [
  { value: 'expand', label: 'Show watch time' },
  { value: 'open_app', label: 'Open app' },
] as const satisfies readonly { value: BadgeTapAction; label: string }[];

const APPS: { app: TrackedApp; title: string; subtitle: string }[] = [
  { app: 'instagram', title: 'Instagram Reels', subtitle: 'com.instagram.android' },
  {
    app: 'youtube_shorts',
    title: 'YouTube Shorts',
    subtitle: 'Experimental: view ids not yet verified',
  },
];

export default function Settings() {
  const p = usePalette();
  const serviceEnabled = useUiStore((s) => s.serviceEnabled) === true;
  const refreshStats = useStatsStore((s) => s.refresh);
  const today = useDailyStats().current;
  const badge = useBadgeSettings();

  const [tracked, setTracked] = useState<string[]>([]);
  const [previewOpacity, setPreviewOpacity] = useState<number | null>(null);
  // null = not editing; show the saved limit.
  const [limitDraft, setLimitDraft] = useState<string | null>(null);
  const limitText = limitDraft ?? (badge.state.dailyLimit ? String(badge.state.dailyLimit) : '');
  const setLimitText = setLimitDraft;
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    void getTrackedPackages().then(setTracked);
    void checkServiceEnabled();
  }, []);

  const toggleApp = async (app: TrackedApp, on: boolean) => {
    const pkg = TRACKED_APP_PACKAGES[app];
    const next = on ? [...new Set([...tracked, pkg])] : tracked.filter((x) => x !== pkg);
    setTracked(next);
    await setTrackedPackages(next);
  };

  const saveLimit = async () => {
    const n = Number.parseInt(limitText, 10);
    if (!Number.isFinite(n) || n <= 0) {
      await badge.update({ dailyLimit: null });
      setLimitDraft(null);
      return;
    }
    const perm = await Notifications.getPermissionsAsync();
    if (!perm.granted && perm.canAskAgain) {
      await Notifications.requestPermissionsAsync();
    }
    await badge.update({ dailyLimit: n });
    setLimitDraft(null);
  };

  const onExport = async () => {
    setBusy(true);
    try {
      const n = await exportCsv();
      if (n === 0) Alert.alert('Nothing to export', 'No reel views recorded yet.');
    } catch (e) {
      Alert.alert('Export failed', e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const onReset = () => {
    Alert.alert(
      'Delete all data?',
      'Every recorded reel view will be permanently deleted from this phone. This cannot be undone.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete everything',
          style: 'destructive',
          onPress: async () => {
            const db = await getDb();
            // Native first so a drain can't re-import rows we just deleted.
            await clearAllEvents();
            await deleteAllViews(db);
            await refreshStats();
          },
        },
      ],
    );
  };

  const b = badge.state;
  const badgeDisabled = !serviceEnabled;

  return (
    <SafeAreaView edges={['top']} className="flex-1 bg-bg dark:bg-bg-dark">
      <ScrollView contentContainerClassName="px-gutter pb-12 pt-4">
        <Text className="text-3xl font-bold text-ink dark:text-ink-dark">Settings</Text>

        <SectionTitle>Tracking</SectionTitle>
        <Card>
          <Row
            title="Accessibility service"
            subtitle={serviceEnabled ? 'On: reels are being counted' : 'Off: nothing is counted'}
            right={
              <View
                className={`h-3 w-3 rounded-full ${
                  serviceEnabled ? 'bg-good dark:bg-good-dark' : 'bg-warn dark:bg-warn-dark'
                }`}
              />
            }
          />
          <Button
            label={serviceEnabled ? 'Open Accessibility settings' : 'Turn on in settings'}
            variant={serviceEnabled ? 'secondary' : 'primary'}
            onPress={openAccessibilitySettings}
          />
          <View className="mt-2">
            {APPS.map((a) => (
              <ToggleRow
                key={a.app}
                title={a.title}
                subtitle={a.subtitle}
                value={tracked.includes(TRACKED_APP_PACKAGES[a.app])}
                onValueChange={(v) => void toggleApp(a.app, v)}
              />
            ))}
          </View>
        </Card>

        <SectionTitle>Daily limit</SectionTitle>
        <Card>
          <Muted>
            Get a notification when you pass this many reels in a day. The floating badge also turns
            amber at 75% and red at the limit.
          </Muted>
          <View className="mt-3 flex-row items-center gap-3">
            <TextInput
              value={limitText}
              onChangeText={(t) => setLimitText(t.replace(/[^0-9]/g, ''))}
              keyboardType="number-pad"
              placeholder="No limit"
              placeholderTextColor={p.muted}
              accessibilityLabel="Daily reel limit"
              maxLength={4}
              className="flex-1 rounded-xl border border-line px-4 py-3 text-lg text-ink dark:border-line-dark dark:text-ink-dark"
            />
            <View className="w-28">
              <Button label="Save" onPress={() => void saveLimit()} />
            </View>
          </View>
        </Card>

        <SectionTitle>Floating badge</SectionTitle>
        <Card>
          {badgeDisabled ? (
            <Muted className="mb-2">
              The badge is drawn by the accessibility service, so turn the service on first.
            </Muted>
          ) : null}

          <BadgePreview
            count={today.totalReels}
            watchMs={today.totalWatchMs}
            size={b.size}
            opacity={previewOpacity ?? b.opacity}
            dailyLimit={b.dailyLimit}
            disabled={badgeDisabled || !b.enabled}
          />

          <ToggleRow
            title="Show floating badge"
            subtitle={
              b.suppressed && b.enabled
                ? 'Hidden by long-press. Toggle off and on to bring it back.'
                : 'Today’s count on top of other apps'
            }
            value={b.enabled}
            onValueChange={(v) => void badge.update({ enabled: v })}
            disabled={badgeDisabled}
          />
          <Divider />

          <View className={`gap-4 py-3 ${badgeDisabled || !b.enabled ? 'opacity-40' : ''}`}>
            <View>
              <Muted className="mb-2">Visible</Muted>
              <Segmented
                options={VISIBILITY}
                value={b.visibility}
                onChange={(v) => void badge.update({ visibility: v })}
                disabled={badgeDisabled || !b.enabled}
              />
            </View>
            <View>
              <Muted className="mb-2">Size</Muted>
              <Segmented
                options={SIZES}
                value={b.size}
                onChange={(v) => void badge.update({ size: v })}
                disabled={badgeDisabled || !b.enabled}
              />
            </View>
            <View>
              <Muted className="mb-1">
                Opacity {Math.round((previewOpacity ?? b.opacity) * 100)}%
              </Muted>
              <Slider
                minimumValue={0.4}
                maximumValue={1}
                step={0.05}
                value={b.opacity}
                onValueChange={setPreviewOpacity}
                onSlidingComplete={(v) => {
                  setPreviewOpacity(null);
                  void badge.update({ opacity: v });
                }}
                disabled={badgeDisabled || !b.enabled}
                minimumTrackTintColor={p.accent}
                maximumTrackTintColor={p.line}
                thumbTintColor={p.accent}
                accessibilityLabel="Badge opacity"
              />
            </View>
            <View>
              <Muted className="mb-2">On tap</Muted>
              <Segmented
                options={TAP}
                value={b.tapAction}
                onChange={(v) => void badge.update({ tapAction: v })}
                disabled={badgeDisabled || !b.enabled}
              />
            </View>
            <Button
              label="Reset badge position"
              variant="secondary"
              onPress={() => void badge.resetPosition()}
              disabled={badgeDisabled || !b.enabled}
            />
            <Muted>
              Drag to move; it snaps to the nearest edge. Long-press, or drag onto ✕, to hide it
              until you switch it back on here.
            </Muted>
          </View>
        </Card>

        <SectionTitle>Your data</SectionTitle>
        <Card className="gap-3">
          <Muted>Stored only on this phone. Nothing is ever uploaded.</Muted>
          <Button
            label={busy ? 'Exporting…' : 'Export as CSV'}
            variant="secondary"
            onPress={() => void onExport()}
            disabled={busy}
          />
          <Button label="Delete all data" variant="danger" onPress={onReset} />
        </Card>

        {__DEV__ ? (
          <>
            <SectionTitle>Developer</SectionTitle>
            <Card>
              <Link href="/debug" asChild>
                <Button label="Open debug tools" variant="secondary" />
              </Link>
            </Card>
          </>
        ) : null}
      </ScrollView>
    </SafeAreaView>
  );
}
