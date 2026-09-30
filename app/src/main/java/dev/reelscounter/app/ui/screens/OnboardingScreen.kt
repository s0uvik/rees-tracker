package dev.reelscounter.app.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.reelscounter.app.ui.AppViewModel
import dev.reelscounter.app.ui.components.Card
import dev.reelscounter.app.ui.components.Muted
import dev.reelscounter.app.ui.components.PrimaryButton
import dev.reelscounter.app.ui.components.SecondaryButton
import dev.reelscounter.app.ui.components.StatusDot
import dev.reelscounter.app.ui.theme.ReelsTheme
import dev.reelscounter.app.ui.theme.Space
import kotlinx.coroutines.delay

private val POINTS = listOf(
  "What it does" to "Counts each new reel you swipe to in Instagram and how long you watched it, then shows daily, weekly and monthly totals.",
  "Why Accessibility access" to "Instagram has no API for watch history. Android’s Accessibility feature lets this app notice when the reel on screen changes. That is the only thing it looks for.",
  "What is saved" to "Only a timestamp, which app, and watch duration. Never usernames, captions, messages, videos or screenshots.",
  "Where it goes" to "Nowhere. The app has no internet permission and all data stays on this phone. You can export or delete it any time.",
)

@Composable
fun OnboardingScreen(vm: AppViewModel) {
  val c = ReelsTheme.colors
  val enabled by vm.serviceEnabled.collectAsStateWithLifecycle()

  // Settings is another app, so poll while this screen is visible (resume also re-checks).
  LaunchedEffect(Unit) {
    while (true) {
      delay(1500)
      vm.refreshServiceStatus()
    }
  }

  Column(
    Modifier
      .fillMaxSize()
      .background(c.bg)
      .safeDrawingPadding()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = Space.gutter, vertical = Space.section),
    verticalArrangement = Arrangement.spacedBy(Space.md),
  ) {
    Text("🎬 Reels Counter", color = c.ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
    Muted("See how many reels you really watch. Private by design.")
    Spacer(Modifier.size(Space.sm))

    for ((title, body) in POINTS) {
      Card(Modifier.fillMaxWidth()) {
        Text(title, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Muted(body, Modifier.padding(top = 4.dp))
      }
    }

    Card(Modifier.fillMaxWidth()) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(enabled)
        Text(
          if (enabled) "Service is on" else "Service is off",
          color = c.ink,
          fontSize = 16.sp,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(start = Space.md),
        )
      }
      Muted(
        if (enabled) "All set. Reels will be counted from now on." else "Open Accessibility settings → Reels Counter → turn it on.",
        Modifier.padding(top = 4.dp),
      )
    }

    if (enabled) {
      PrimaryButton("Continue to dashboard", onClick = { vm.setOnboardingSkipped(false) })
    } else {
      PrimaryButton("Open Accessibility settings", onClick = vm::openAccessibilitySettings)

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Card(Modifier.fillMaxWidth()) {
          Text("Toggle greyed out? (“Restricted setting”)", color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
          Muted(
            "Android 13+ blocks Accessibility for apps installed outside the Play Store. Open App info, tap ⋮ (top right) → “Allow restricted settings”, then try again.",
            Modifier.padding(top = 4.dp, bottom = Space.md),
          )
          SecondaryButton("Open App info", onClick = vm::openAppDetails)
        }
      }

      SecondaryButton("Skip for now", onClick = { vm.setOnboardingSkipped(true) })
    }
  }
}
