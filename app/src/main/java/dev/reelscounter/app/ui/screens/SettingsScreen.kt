package dev.reelscounter.app.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.reelscounter.app.BuildConfig
import dev.reelscounter.app.badge.BadgeSize
import dev.reelscounter.app.badge.BadgeTapAction
import dev.reelscounter.app.badge.BadgeVisibility
import dev.reelscounter.app.detection.TrackedAppConfig
import dev.reelscounter.app.stats.Stats
import dev.reelscounter.app.ui.AppViewModel
import dev.reelscounter.app.ui.components.BadgePreview
import dev.reelscounter.app.ui.components.Card
import dev.reelscounter.app.ui.components.Divider
import dev.reelscounter.app.ui.components.Muted
import dev.reelscounter.app.ui.components.PrimaryButton
import dev.reelscounter.app.ui.components.SecondaryButton
import dev.reelscounter.app.ui.components.SectionTitle
import dev.reelscounter.app.ui.components.Segmented
import dev.reelscounter.app.ui.components.SettingRow
import dev.reelscounter.app.ui.components.StatusDot
import dev.reelscounter.app.ui.components.ToggleRow
import dev.reelscounter.app.ui.theme.ReelsTheme
import dev.reelscounter.app.ui.theme.Space
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val VISIBILITY = listOf(BadgeVisibility.IN_TRACKED_APPS to "In Instagram", BadgeVisibility.ALWAYS to "Always")
private val SIZES = listOf(BadgeSize.SM to "S", BadgeSize.MD to "M", BadgeSize.LG to "L")
private val TAP = listOf(BadgeTapAction.EXPAND to "Show watch time", BadgeTapAction.OPEN_APP to "Open app")

@Composable
fun SettingsScreen(vm: AppViewModel, modifier: Modifier = Modifier, onOpenDebug: () -> Unit) {
  val c = ReelsTheme.colors
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val serviceEnabled by vm.serviceEnabled.collectAsStateWithLifecycle()
  val badgeState by vm.badge.collectAsStateWithLifecycle()
  val tracked by vm.tracked.collectAsStateWithLifecycle()
  val data by vm.stats.collectAsStateWithLifecycle()
  val today = remember(data) { Stats.daily(data.rows, data.today).current }

  val b = badgeState.config
  var opacityDraft by remember { mutableStateOf<Float?>(null) }
  // null = not editing: show the saved limit.
  var limitDraft by remember { mutableStateOf<String?>(null) }
  val limitText = limitDraft ?: (b.dailyLimit?.toString() ?: "")
  var confirmDelete by remember { mutableStateOf(false) }
  var exporting by remember { mutableStateOf(false) }

  val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

  fun saveLimit() {
    val n = limitText.toIntOrNull()
    if (n != null && n > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    vm.setDailyLimit(n?.takeIf { it > 0 })
    limitDraft = null
  }

  val badgeControlsEnabled = serviceEnabled && b.enabled

  Column(
    modifier
      .fillMaxSize()
      .statusBarsPadding()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = Space.gutter, vertical = Space.gutter),
  ) {
    Text("Settings", color = c.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)

    SectionTitle("Tracking")
    Card(Modifier.fillMaxWidth()) {
      SettingRow(
        "Accessibility service",
        if (serviceEnabled) "On: reels are being counted" else "Off: nothing is counted",
      ) { StatusDot(serviceEnabled) }
      if (serviceEnabled) {
        SecondaryButton("Open Accessibility settings", onClick = vm::openAccessibilitySettings)
      } else {
        PrimaryButton("Turn on in settings", onClick = vm::openAccessibilitySettings)
      }
      ToggleRow(
        "Instagram Reels",
        TrackedAppConfig.INSTAGRAM.packageName,
        TrackedAppConfig.INSTAGRAM.packageName in tracked,
        { vm.setTracked(TrackedAppConfig.INSTAGRAM.packageName, it) },
      )
      ToggleRow(
        "YouTube Shorts",
        "Experimental: view ids not yet verified",
        TrackedAppConfig.YOUTUBE_SHORTS.packageName in tracked,
        { vm.setTracked(TrackedAppConfig.YOUTUBE_SHORTS.packageName, it) },
      )
    }

    SectionTitle("Daily limit")
    Card(Modifier.fillMaxWidth()) {
      Muted("Get a notification when you pass this many reels in a day. The floating badge also turns amber at 75% and red at the limit.")
      Row(Modifier.padding(top = Space.md), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
          value = limitText,
          onValueChange = { t -> limitDraft = t.filter(Char::isDigit).take(4) },
          placeholder = { Text("No limit") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.line),
          modifier = Modifier.weight(1f),
        )
        Column(Modifier.width(110.dp).padding(start = Space.md)) {
          PrimaryButton("Save", onClick = ::saveLimit)
        }
      }
    }

    SectionTitle("Floating badge")
    Card(Modifier.fillMaxWidth()) {
      if (!serviceEnabled) {
        Muted("The badge is drawn by the accessibility service, so turn the service on first.", Modifier.padding(bottom = Space.sm))
      }
      BadgePreview(
        count = today.totalReels,
        watchMs = today.totalWatchMs,
        size = b.size,
        opacity = opacityDraft ?: b.opacity,
        dailyLimit = b.dailyLimit,
        dimmed = !badgeControlsEnabled,
      )
      ToggleRow(
        "Show floating badge",
        if (badgeState.suppressed && b.enabled) "Hidden by long-press. Toggle off and on to bring it back." else "Today’s count on top of other apps",
        b.enabled,
        vm::setBadgeEnabled,
        enabled = serviceEnabled,
      )
      Divider()
      Column(
        Modifier.alpha(if (badgeControlsEnabled) 1f else 0.4f).padding(top = Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.gutter),
      ) {
        Column {
          Muted("Visible", Modifier.padding(bottom = Space.sm))
          Segmented(VISIBILITY, b.visibility, vm::setBadgeVisibility, badgeControlsEnabled)
        }
        Column {
          Muted("Size", Modifier.padding(bottom = Space.sm))
          Segmented(SIZES, b.size, vm::setBadgeSize, badgeControlsEnabled)
        }
        Column {
          Muted("Opacity ${((opacityDraft ?: b.opacity) * 100).roundToInt()}%")
          Slider(
            value = opacityDraft ?: b.opacity,
            onValueChange = { opacityDraft = it },
            onValueChangeFinished = {
              opacityDraft?.let(vm::setBadgeOpacity)
              opacityDraft = null
            },
            valueRange = 0.4f..1f,
            steps = 11,
            enabled = badgeControlsEnabled,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.line),
          )
        }
        Column {
          Muted("On tap", Modifier.padding(bottom = Space.sm))
          Segmented(TAP, b.tapAction, vm::setBadgeTapAction, badgeControlsEnabled)
        }
        SecondaryButton("Reset badge position", onClick = vm::resetBadgePosition, enabled = badgeControlsEnabled)
        Muted("Drag to move; it snaps to the nearest edge. Long-press, or drag onto ✕, to hide it until you switch it back on here.")
      }
    }

    SectionTitle("Your data")
    Card(Modifier.fillMaxWidth()) {
      Muted("Stored only on this phone. Nothing is ever uploaded.", Modifier.padding(bottom = Space.md))
      SecondaryButton(
        if (exporting) "Exporting…" else "Export as CSV",
        enabled = !exporting,
        onClick = {
          exporting = true
          scope.launch {
            try {
              val intent = vm.exportCsv()
              if (intent == null) {
                Toast.makeText(context, "No reel views recorded yet", Toast.LENGTH_SHORT).show()
              } else {
                context.startActivity(intent)
              }
            } catch (e: Exception) {
              Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
              exporting = false
            }
          }
        },
      )
      SecondaryButton("Delete all data", onClick = { confirmDelete = true }, danger = true, modifier = Modifier.padding(top = Space.sm))
    }

    if (BuildConfig.DEBUG) {
      SectionTitle("Developer")
      Card(Modifier.fillMaxWidth()) {
        SecondaryButton("Open debug tools", onClick = onOpenDebug)
      }
    }
  }

  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text("Delete all data?") },
      text = { Text("Every recorded reel view will be permanently deleted from this phone. This cannot be undone.") },
      confirmButton = {
        TextButton(onClick = {
          confirmDelete = false
          vm.deleteAllData()
        }) { Text("Delete everything", color = c.bad) }
      },
      dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
  }
}
