import Ionicons from '@expo/vector-icons/Ionicons';
import { Redirect, Tabs } from 'expo-router';
import { ActivityIndicator, View } from 'react-native';

import { usePalette } from '@/lib/theme';
import { useUiStore } from '@/store/uiStore';

export default function TabLayout() {
  const p = usePalette();
  const bootstrapped = useUiStore((s) => s.bootstrapped);
  const serviceEnabled = useUiStore((s) => s.serviceEnabled);
  const skipped = useUiStore((s) => s.onboardingSkipped);

  if (!bootstrapped) {
    return (
      <View
        style={{ flex: 1, backgroundColor: p.bg, alignItems: 'center', justifyContent: 'center' }}
      >
        <ActivityIndicator color={p.accent} />
      </View>
    );
  }

  // Dashboard is gated on the service unless the user explicitly skipped.
  if (!serviceEnabled && !skipped) {
    return <Redirect href="/onboarding" />;
  }

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: p.accent,
        tabBarInactiveTintColor: p.muted,
        tabBarStyle: { backgroundColor: p.card, borderTopColor: p.line },
        sceneStyle: { backgroundColor: p.bg },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: 'Dashboard',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="stats-chart" color={color} size={size} />
          ),
        }}
      />
      <Tabs.Screen
        name="history"
        options={{
          title: 'History',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="time-outline" color={color} size={size} />
          ),
        }}
      />
      <Tabs.Screen
        name="settings"
        options={{
          title: 'Settings',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="settings-outline" color={color} size={size} />
          ),
        }}
      />
    </Tabs>
  );
}
