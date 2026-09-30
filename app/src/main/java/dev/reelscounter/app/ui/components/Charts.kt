package dev.reelscounter.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.reelscounter.app.stats.Bucket
import dev.reelscounter.app.stats.HourBucket
import dev.reelscounter.app.ui.theme.ReelsTheme
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** Rounds the axis maximum up to a readable number (4, 10, 15, 50, 150…). */
internal fun niceMax(maxValue: Int): Int {
  if (maxValue <= 4) return 4
  val magnitude = 10.0.pow(floor(log10(maxValue.toDouble())))
  val step = magnitude / 2
  return (ceil(maxValue / step) * step).toInt()
}

/**
 * Simple vertical bar chart drawn on a Canvas: 4 dashed grid lines with
 * y labels, one bar per value, every [labelEvery]-th x label, highlighted
 * bar at [highlightIndex]. Bars grow in when the data changes.
 */
@Composable
fun BarChart(
  values: List<Int>,
  labels: List<String>,
  labelEvery: Int,
  highlightIndex: Int,
  height: Dp,
  modifier: Modifier = Modifier,
) {
  val c = ReelsTheme.colors
  val measurer = rememberTextMeasurer()
  val labelStyle = TextStyle(color = c.muted, fontSize = 10.sp)
  val progress = remember { Animatable(0f) }
  LaunchedEffect(values) {
    progress.snapTo(0f)
    progress.animateTo(1f, tween(450))
  }
  val top = niceMax(values.maxOrNull() ?: 0)
  val summary = "Bar chart, ${values.size} bars, total ${values.sum()}, max ${values.maxOrNull() ?: 0}"

  Canvas(
    modifier
      .fillMaxWidth()
      .height(height)
      .semantics { contentDescription = summary },
  ) {
    val yAxisWidth = 28.dp.toPx()
    val xLabelHeight = 16.dp.toPx()
    val plotLeft = yAxisWidth
    val plotBottom = size.height - xLabelHeight
    val plotHeight = plotBottom
    val plotWidth = size.width - plotLeft

    drawGrid(measurer, labelStyle, top, plotLeft, plotWidth, plotHeight, c.line)

    val n = max(1, values.size)
    val slot = plotWidth / n
    val barWidth = max(3.dp.toPx(), slot * 0.6f)
    val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
    values.forEachIndexed { i, v ->
      val h = if (top == 0) 0f else plotHeight * (v.toFloat() / top) * progress.value
      val x = plotLeft + slot * i + (slot - barWidth) / 2
      if (h > 0f) {
        drawRoundRect(
          color = if (i == highlightIndex) c.accent else c.accentSoft,
          topLeft = Offset(x, plotBottom - h),
          size = Size(barWidth, h),
          cornerRadius = radius,
        )
      }
      val label = labels.getOrNull(i)
      if (label != null && (i % labelEvery == 0 || i == values.lastIndex)) {
        val layout = measurer.measure(label, labelStyle)
        drawText(layout, topLeft = Offset(x + barWidth / 2 - layout.size.width / 2, plotBottom + 2.dp.toPx()))
      }
    }
  }
}

private fun DrawScope.drawGrid(
  measurer: TextMeasurer,
  style: TextStyle,
  top: Int,
  left: Float,
  width: Float,
  height: Float,
  lineColor: Color,
) {
  val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
  for (s in 0..4) {
    val y = height - height * s / 4f
    drawLine(
      lineColor,
      Offset(left, y),
      Offset(left + width, y),
      strokeWidth = 1.dp.toPx(),
      pathEffect = if (s == 0) null else dash,
    )
    val value = top * s / 4
    val layout = measurer.measure("$value", style)
    drawText(layout, topLeft = Offset(left - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
  }
}

@Composable
fun PeriodChartCard(title: String, buckets: List<Bucket>, labelEvery: Int) {
  Card(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
      Text(title, color = ReelsTheme.colors.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
      Muted("${buckets.sumOf { it.count }} total")
    }
    BarChart(
      values = buckets.map { it.count },
      labels = buckets.map { it.label },
      labelEvery = labelEvery,
      highlightIndex = buckets.lastIndex,
      height = 190.dp,
    )
  }
}

@Composable
fun HourlyChartCard(hours: List<HourBucket>, currentHour: Int) {
  Card(Modifier.fillMaxWidth()) {
    Text(
      "Today by hour",
      color = ReelsTheme.colors.ink,
      fontSize = 16.sp,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(bottom = 12.dp),
    )
    BarChart(
      values = hours.map { it.count },
      labels = hours.map { "${it.hour}" },
      labelEvery = 6,
      highlightIndex = currentHour,
      height = 130.dp,
    )
  }
}
