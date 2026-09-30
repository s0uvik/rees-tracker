import '../global.css';

import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { useAppBootstrap } from '@/features/service/bootstrap';
import { useStatsAutoRefresh } from '@/features/stats/hooks';
import { useIsDark, usePalette } from '@/lib/theme';

export default function RootLayout() {
  useAppBootstrap();
  useStatsAutoRefresh();
  const p = usePalette();
  const isDark = useIsDark();

  return (
    <SafeAreaProvider>
      <StatusBar style={isDark ? 'light' : 'dark'} />
      <Stack
        screenOptions={{
          headerShown: false,
          contentStyle: { backgroundColor: p.bg },
          headerStyle: { backgroundColor: p.bg },
          headerTintColor: p.ink,
        }}
      >
        <Stack.Screen name="(tabs)" />
        <Stack.Screen name="onboarding" options={{ gestureEnabled: false }} />
        <Stack.Screen name="debug" options={{ headerShown: true, title: 'Debug tools' }} />
      </Stack>
    </SafeAreaProvider>
  );
}
