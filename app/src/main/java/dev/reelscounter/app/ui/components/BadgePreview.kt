package dev.reelscounter.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.reelscounter.app.badge.BadgeColors
import dev.reelscounter.app.badge.BadgeSize
import dev.reelscounter.app.badge.BadgeText
import dev.reelscounter.app.ui.theme.ReelsTheme

/**
 * In-app replica of the floating badge so settings changes show immediately.
 * Uses the same size table, colours and text helpers as the real overlay.
 */
@Composable
fun BadgePreview(
  count: Int,
  watchMs: Long,
  size: BadgeSize,
  opacity: Float,
  dailyLimit: Int?,
  showTimer: Boolean,
  dimmed: Boolean,
) {
  var expanded by remember { mutableStateOf(false) }
  val fill = Color(BadgeColors.colorFor(BadgeColors.levelFor(count, dailyLimit)))
  val pill = RoundedCornerShape(999.dp)
  Box(
    Modifier
      .fillMaxWidth()
      .height(96.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(ReelsTheme.colors.line.copy(alpha = 0.6f))
      .padding(horizontal = 12.dp),
    contentAlignment = Alignment.CenterEnd,
  ) {
    Row(
      Modifier
        .alpha(if (dimmed) 0.3f else opacity)
        .clip(pill)
        .background(fill)
        .border(1.dp, Color.White.copy(alpha = 0.2f), pill)
        .clickable(enabled = !dimmed) { expanded = !expanded }
        .semantics { contentDescription = "Badge preview: $count reels today" }
        .padding(horizontal = size.padHDp.dp, vertical = size.padVDp.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(BadgeText.count(count), color = Color.White, fontSize = size.textSp.sp, fontWeight = FontWeight.Bold)
      if (showTimer) {
        Text(
          BadgeText.clock(watchMs),
          color = Color.White.copy(alpha = 0.95f),
          fontSize = (size.detailSp + 1f).sp,
          style = TextStyle(fontFeatureSettings = "tnum"),
          modifier = Modifier.padding(start = 6.dp),
        )
      }
      if (expanded) {
        Text(
          if (showTimer) "▶ 0:00" else BadgeText.duration(watchMs),
          color = Color.White.copy(alpha = 0.85f),
          fontSize = size.detailSp.sp,
          modifier = Modifier.padding(start = 6.dp),
        )
      }
    }
  }
}
