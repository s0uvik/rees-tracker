package dev.reelscounter.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import dev.reelscounter.app.detection.TrackedAppConfig
import dev.reelscounter.app.service.ServiceStatus
import dev.reelscounter.app.stats.Format
import dev.reelscounter.app.ui.AppViewModel
import dev.reelscounter.app.ui.components.Card
import dev.reelscounter.app.ui.components.Muted
import dev.reelscounter.app.ui.components.PrimaryButton
import dev.reelscounter.app.ui.components.SecondaryButton
import dev.reelscounter.app.ui.theme.ReelsTheme
import dev.reelscounter.app.ui.theme.Space
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 24 * HOUR_MS
private val IG = TrackedAppConfig.INSTAGRAM.appKey

/** Skewed toward short watches, like real scrolling. */
private fun randomDuration(): Long = (1_500 + Random.nextDouble().pow(2) * 45_000).toLong()

private fun startOfToday(): Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun todayEvents(n: Int): List<Triple<String, Long, Long?>> {
  val start = startOfToday()
  val span = (System.currentTimeMillis() - start).coerceAtLeast(1)
  return List(n) { Triple(IG, start + Random.nextLong(span), randomDuration()) }.sortedBy { it.second }
}

private fun historyEvents(days: Int): List<Triple<String, Long, Long?>> {
  val midnight = startOfToday()
  val out = ArrayList<Triple<String, Long, Long?>>()
  for (d in days downTo 1) {
    val dayStart = midnight - d * DAY_MS
    repeat(Random.nextInt(80)) {
      // Evenings are busier.
      val hour = min(23, (8 + Random.nextDouble().pow(0.6) * 16).toInt())
      out += Triple(IG, dayStart + hour * HOUR_MS + Random.nextLong(HOUR_MS), randomDuration())
    }
  }
  return out
}

/** Debug builds only: fake views go into the real database, so the dashboard and badge both update. */
@Composable
fun DebugScreen(vm: AppViewModel, onBack: () -> Unit) {
  val c = ReelsTheme.colors
  var totals by remember { mutableStateOf<Pair<Int, Long>?>(null) }
  var log by remember { mutableStateOf("") }
  var refreshKey by remember { mutableIntStateOf(0) }

  LaunchedEffect(refreshKey) { totals = vm.nativeTodayTotals() }

  fun run(label: String, events: List<Triple<String, Long, Long?>>) {
    vm.insertDebugEvents(events) { n ->
      log = "$label: inserted $n events"
      refreshKey++
    }
  }

  Column(
    Modifier
      .fillMaxSize()
      .safeDrawingPadding()
      .verticalScroll(rememberScrollState())
      .padding(Space.gutter),
    verticalArrangement = Arrangement.spacedBy(Space.md),
  ) {
    Text("Debug tools", color = c.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    Card(Modifier.fillMaxWidth()) {
      Muted("Service enabled: ${if (vm.serviceEnabled.value) "yes" else "no"}")
      Muted("Service bound: ${if (ServiceStatus.isRunning) "yes" else "no"}")
      Muted("Today in DB: ${totals?.let { "${it.first} reels, ${Format.duration(it.second)}" } ?: "…"}")
    }
    Muted("Logcat tags: ReelsDetector (unknown layouts), ReelsService (counted reels), ReelsBadge.")
    PrimaryButton("+1 reel now", onClick = { run("+1", listOf(Triple(IG, System.currentTimeMillis(), randomDuration()))) })
    PrimaryButton("+25 reels spread over today", onClick = { run("+25", todayEvents(25)) })
    SecondaryButton("Seed 400 days of history", onClick = { run("seed", historyEvents(400)) })
    if (log.isNotEmpty()) Muted(log, Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    SecondaryButton("Back", onClick = onBack)
  }
}
