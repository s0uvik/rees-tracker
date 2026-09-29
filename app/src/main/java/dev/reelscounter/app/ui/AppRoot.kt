package dev.reelscounter.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.reelscounter.app.BuildConfig
import dev.reelscounter.app.ui.screens.DashboardScreen
import dev.reelscounter.app.ui.screens.DebugScreen
import dev.reelscounter.app.ui.screens.HistoryScreen
import dev.reelscounter.app.ui.screens.OnboardingScreen
import dev.reelscounter.app.ui.screens.SettingsScreen
import dev.reelscounter.app.ui.theme.ReelsTheme

private enum class Tab(val label: String, val icon: String) {
  DASHBOARD("Dashboard", "📊"),
  HISTORY("History", "🕘"),
  SETTINGS("Settings", "⚙️"),
}

/**
 * Top-level navigation: onboarding gate, then three tabs. Plain state instead
 * of a navigation library; the only nested screen is the debug tools page.
 */
@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
  val serviceEnabled by vm.serviceEnabled.collectAsStateWithLifecycle()
  val skipped by vm.onboardingSkipped.collectAsStateWithLifecycle()
  var tab by rememberSaveable { mutableStateOf(Tab.DASHBOARD) }
  var showDebug by rememberSaveable { mutableStateOf(false) }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }

  // Dashboard is gated on the service unless the user explicitly skipped.
  if (!serviceEnabled && !skipped) {
    OnboardingScreen(vm)
    return
  }

  if (showDebug && BuildConfig.DEBUG) {
    BackHandler { showDebug = false }
    DebugScreen(vm, onBack = { showDebug = false })
    return
  }

  val c = ReelsTheme.colors
  Scaffold(
    containerColor = c.bg,
    bottomBar = {
      NavigationBar(containerColor = c.card) {
        for (t in Tab.entries) {
          NavigationBarItem(
            selected = tab == t,
            onClick = { tab = t },
            icon = { Text(t.icon, fontSize = 18.sp) },
            label = { Text(t.label) },
            colors = NavigationBarItemDefaults.colors(
              selectedTextColor = c.accent,
              unselectedTextColor = c.muted,
              indicatorColor = c.accent.copy(alpha = 0.15f),
            ),
          )
        }
      }
    },
  ) { padding ->
    val m = Modifier.padding(padding)
    when (tab) {
      Tab.DASHBOARD -> DashboardScreen(vm, m)
      Tab.HISTORY -> HistoryScreen(m)
      Tab.SETTINGS -> SettingsScreen(vm, m, onOpenDebug = { showDebug = true })
    }
  }
}
