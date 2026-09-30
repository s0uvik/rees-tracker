import { router } from 'expo-router';
import { useEffect } from 'react';
import { Platform, ScrollView, Text, View } from 'react-native';
import { openAccessibilitySettings, openAppDetailsSettings } from 'reels-tracker';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button, Card, Muted } from '@/components/ui';
import { checkServiceEnabled, setOnboardingSkipped } from '@/features/service/bootstrap';
import { useUiStore } from '@/store/uiStore';

const POLL_MS = 1500;

const POINTS: { title: string; body: string }[] = [
  {
    title: 'What it does',
    body: 'Counts each new reel you swipe to in Instagram and how long you watched it, then shows daily, weekly and monthly totals.',
  },
  {
    title: 'Why Accessibility access',
    body: 'Instagram has no API for watch history. Android’s Accessibility feature lets this app notice when the reel on screen changes. That is the only thing it looks for.',
  },
  {
    title: 'What is saved',
    body: 'Only a timestamp, which app, and watch duration. Never usernames, captions, messages, videos or screenshots.',
  },
  {
    title: 'Where it goes',
    body: 'Nowhere. The app has no internet connection and all data stays on this phone. You can export or delete it any time.',
  },
];

export default function Onboarding() {
  const enabled = useUiStore((s) => s.serviceEnabled);

  // Settings is another app, so poll while this screen is up; AppState
  // 'active' re-checks are handled globally too.
  useEffect(() => {
    const id = setInterval(() => void checkServiceEnabled(), POLL_MS);
    return () => clearInterval(id);
  }, []);

  const finish = async () => {
    await setOnboardingSkipped(false);
    router.replace('/');
  };

  const skip = async () => {
    await setOnboardingSkipped(true);
    router.replace('/');
  };

  const needsRestrictedHint = Platform.OS === 'android' && Number(Platform.Version) >= 33;

  return (
    <SafeAreaView className="flex-1 bg-bg dark:bg-bg-dark">
      <ScrollView contentContainerClassName="px-gutter pb-10 pt-6">
        <Text className="text-4xl font-bold text-ink dark:text-ink-dark">🎬 Reels Counter</Text>
        <Muted className="mt-2 text-base">
          See how many reels you really watch. Private by design.
        </Muted>

        <View className="mt-section gap-3">
          {POINTS.map((pt) => (
            <Card key={pt.title}>
              <Text className="text-base font-semibold text-ink dark:text-ink-dark">
                {pt.title}
              </Text>
              <Muted className="mt-1 leading-5">{pt.body}</Muted>
            </Card>
          ))}
        </View>

        <Card className="mt-section">
          <View className="flex-row items-center">
            <View
              className={`mr-3 h-3 w-3 rounded-full ${
                enabled ? 'bg-good dark:bg-good-dark' : 'bg-warn dark:bg-warn-dark'
              }`}
            />
            <Text className="text-base font-semibold text-ink dark:text-ink-dark">
              {enabled ? 'Service is on' : 'Service is off'}
            </Text>
          </View>
          <Muted className="mt-1">
            {enabled
              ? 'All set. Reels will be counted from now on.'
              : 'Open Accessibility settings → Reels Counter → turn it on.'}
          </Muted>
        </Card>

        <View className="mt-section gap-3">
          {enabled ? (
            <Button label="Continue to dashboard" onPress={finish} />
          ) : (
            <Button label="Open Accessibility settings" onPress={openAccessibilitySettings} />
          )}

          {!enabled && needsRestrictedHint ? (
            <Card>
              <Text className="text-sm font-semibold text-ink dark:text-ink-dark">
                Toggle greyed out? (“Restricted setting”)
              </Text>
              <Muted className="mt-1 leading-5">
                Android 13+ blocks Accessibility for apps installed outside the Play Store. Open App
                info, tap ⋮ (top right) → “Allow restricted settings”, then try again.
              </Muted>
              <View className="mt-3">
                <Button
                  label="Open App info"
                  variant="secondary"
                  onPress={openAppDetailsSettings}
                />
              </View>
            </Card>
          ) : null}

          {!enabled ? <Button label="Skip for now" variant="secondary" onPress={skip} /> : null}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
