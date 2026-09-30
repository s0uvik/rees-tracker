package dev.reelscounter.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.reelscounter.app.stats.Format
import dev.reelscounter.app.stats.Period
import dev.reelscounter.app.stats.Stats
import dev.reelscounter.app.ui.AppViewModel
import dev.reelscounter.app.ui.components.HeroCard
import dev.reelscounter.app.ui.components.HourlyChartCard
import dev.reelscounter.app.ui.components.PeriodChartCard
import dev.reelscounter.app.ui.components.Segmented
import dev.reelscounter.app.ui.components.ServiceBanner
import dev.reelscounter.app.ui.components.StatCard
import dev.reelscounter.app.ui.theme.ReelsTheme
import dev.reelscounter.app.ui.theme.Space
import java.time.LocalTime

private val PERIODS = listOf(Period.DAILY to "Daily", Period.WEEKLY to "Weekly", Period.MONTHLY to "Monthly")

private data class PeriodCopy(val current: String, val previous: String, val chartTitle: String, val labelEvery: Int)

private fun copyFor(p: Period) = when (p) {
  Period.DAILY -> PeriodCopy("Today", "yesterday", "Last 30 days", 5)
  Period.WEEKLY -> PeriodCopy("This week", "last week", "Last 12 weeks", 3)
  Period.MONTHLY -> PeriodCopy("This month", "last month", "Last 12 months", 2)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
  val c = ReelsTheme.colors
  val data by vm.stats.collectAsStateWithLifecycle()
  val period by vm.period.collectAsStateWithLifecycle()
  val refreshing by vm.refreshing.collectAsStateWithLifecycle()
  val serviceEnabled by vm.serviceEnabled.collectAsStateWithLifecycle()

  val stats = remember(data, period) { Stats.forPeriod(period, data.rows, data.today) }
  val hourly = remember(data) { Stats.hourly(data.rows, data.today) }
  val copy = copyFor(period)

  PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = modifier.fillMaxSize()) {
    Column(
      Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = Space.gutter, vertical = Space.gutter),
      verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
      Text("Reels", color = c.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
      if (!serviceEnabled) ServiceBanner(onClick = vm::openAccessibilitySettings)

      Segmented(PERIODS, period, vm::setPeriod)

      HeroCard(copy.current, stats.current.totalReels, stats.changePct, copy.previous)

      Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        StatCard("Watch time", Format.duration(stats.current.totalWatchMs), Modifier.weight(1f))
        StatCard("Avg / reel", Format.seconds(stats.current.avgMsPerReel), Modifier.weight(1f))
        StatCard("Peak hour", Format.hour(stats.current.peakHour), Modifier.weight(1f))
      }

      PeriodChartCard(copy.chartTitle, stats.buckets, copy.labelEvery)

      if (period == Period.DAILY) {
        HourlyChartCard(hourly, currentHour = LocalTime.now().hour)
      }
    }
  }
}
