package dev.reelscounter.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.reelscounter.app.stats.Format
import dev.reelscounter.app.stats.History
import dev.reelscounter.app.stats.HistoryItem
import dev.reelscounter.app.ui.HistoryViewModel
import dev.reelscounter.app.ui.components.Divider
import dev.reelscounter.app.ui.components.Muted
import dev.reelscounter.app.ui.theme.ReelsTheme
import dev.reelscounter.app.ui.theme.Space
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
private val APP_LABELS = mapOf("instagram" to "Instagram", "youtube_shorts" to "YouTube Shorts")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(modifier: Modifier = Modifier, vm: HistoryViewModel = viewModel()) {
  val c = ReelsTheme.colors
  val state by vm.state.collectAsStateWithLifecycle()
  val zone = remember { ZoneId.systemDefault() }
  val items = remember(state.rows) { History.groupByDay(state.rows, LocalDate.now(zone), zone) }
  val listState = rememberLazyListState()

  // Load the next page when the last visible item is near the end.
  val nearEnd by remember {
    derivedStateOf {
      val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      last >= listState.layoutInfo.totalItemsCount - 20
    }
  }
  LaunchedEffect(nearEnd, state.rows.size) { if (nearEnd) vm.loadMore() }

  Column(modifier.fillMaxSize().statusBarsPadding()) {
    Text(
      "History",
      color = c.ink,
      fontSize = 30.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.gutter),
    )
    when {
      state.loading -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = c.accent)
      }
      items.isEmpty() -> Muted(
        "No reels recorded yet. Open Instagram Reels and swipe.",
        Modifier.fillMaxWidth().padding(top = 40.dp),
        textAlign = TextAlign.Center,
      )
      else -> LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 40.dp)) {
        items.forEach { item ->
          when (item) {
            is HistoryItem.Header -> stickyHeader(key = item.key) {
              Row(
                Modifier.fillMaxWidth().background(c.bg).padding(start = Space.gutter, end = Space.gutter, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
              ) {
                Text(item.title, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Muted("${item.count} reels · ${Format.duration(item.durationMs)}")
              }
            }
            is HistoryItem.View -> item(key = item.key) {
              val r = item.row
              Column(Modifier.padding(horizontal = Space.gutter)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                  Text(TIME.format(Instant.ofEpochMilli(r.viewedAt).atZone(zone)), color = c.ink, fontSize = 16.sp, modifier = Modifier.width(88.dp))
                  Muted(APP_LABELS[r.app] ?: r.app, Modifier.weight(1f))
                  Text(r.durationMs?.let { "${it / 1000}s" } ?: "—", color = c.ink, fontSize = 16.sp)
                }
                Divider()
              }
            }
          }
        }
      }
    }
  }
}
