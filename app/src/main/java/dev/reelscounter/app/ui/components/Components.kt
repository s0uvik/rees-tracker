package dev.reelscounter.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.reelscounter.app.stats.Format
import dev.reelscounter.app.ui.theme.ReelsTheme
import dev.reelscounter.app.ui.theme.Space

private val CardShape = RoundedCornerShape(18.dp)

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  val c = ReelsTheme.colors
  Column(
    modifier
      .clip(CardShape)
      .background(c.card)
      .border(1.dp, c.line, CardShape)
      .padding(Space.gutter),
    content = content,
  )
}

@Composable
fun SectionTitle(text: String) {
  Text(
    text.uppercase(),
    color = ReelsTheme.colors.muted,
    fontSize = 12.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 1.sp,
    modifier = Modifier.padding(start = Space.xs, top = Space.section, bottom = Space.sm),
  )
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
  Text(text, color = ReelsTheme.colors.muted, fontSize = 14.sp, modifier = modifier, textAlign = textAlign)
}

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = ButtonDefaults.buttonColors(containerColor = ReelsTheme.colors.accent, contentColor = Color.White),
  ) { Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 6.dp)) }
}

@Composable
fun SecondaryButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  danger: Boolean = false,
) {
  val c = ReelsTheme.colors
  val tint = if (danger) c.bad else c.ink
  OutlinedButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, if (danger) c.bad else c.line),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = tint),
  ) { Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 6.dp)) }
}

@Composable
fun SettingRow(
  title: String,
  subtitle: String? = null,
  enabled: Boolean = true,
  trailing: @Composable RowScope.() -> Unit = {},
) {
  Row(
    Modifier
      .fillMaxWidth()
      .alpha(if (enabled) 1f else 0.4f)
      .padding(vertical = Space.md),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f).padding(end = Space.md)) {
      Text(title, color = ReelsTheme.colors.ink, fontSize = 16.sp)
      if (subtitle != null) Muted(subtitle, Modifier.padding(top = 2.dp))
    }
    trailing()
  }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
  val c = ReelsTheme.colors
  SettingRow(title, subtitle, enabled) {
    Switch(
      checked = checked,
      onCheckedChange = onChange,
      enabled = enabled,
      colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = Color.White),
    )
  }
}

@Composable
fun Divider() {
  Box(Modifier.fillMaxWidth().height(1.dp).background(ReelsTheme.colors.line))
}

@Composable
fun <T> Segmented(
  options: List<Pair<T, String>>,
  selected: T,
  onSelect: (T) -> Unit,
  enabled: Boolean = true,
) {
  val c = ReelsTheme.colors
  Row(
    Modifier
      .fillMaxWidth()
      .alpha(if (enabled) 1f else 0.4f)
      .clip(RoundedCornerShape(16.dp))
      .background(c.line)
      .padding(4.dp),
  ) {
    for ((value, label) in options) {
      val isSelected = value == selected
      Box(
        Modifier
          .weight(1f)
          .clip(RoundedCornerShape(12.dp))
          .background(if (isSelected) c.card else Color.Transparent)
          .clickable(enabled = enabled, role = Role.Tab) { onSelect(value) }
          .semantics { this.selected = isSelected }
          .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          label,
          color = if (isSelected) c.ink else c.muted,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
}

@Composable
fun HeroCard(label: String, count: Int, changePct: Double?, previousLabel: String) {
  val c = ReelsTheme.colors
  val (text, tone) = Format.change(changePct)
  // More reels is the "bad" direction for a wellbeing app.
  val toneColor = when (tone) {
    Format.Tone.UP -> c.bad
    Format.Tone.DOWN -> c.good
    Format.Tone.FLAT -> c.muted
  }
  Card(Modifier.fillMaxWidth()) {
    Muted(label)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
      Text(
        "$count",
        color = c.ink,
        fontSize = 60.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(1f).semantics { contentDescription = "$count reels" },
      )
      Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 10.dp)) {
        Text(text, color = toneColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Muted("vs $previousLabel")
      }
    }
    Muted("reels watched")
  }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
  Card(modifier) {
    Muted(label)
    Spacer(Modifier.size(4.dp))
    Text(value, color = ReelsTheme.colors.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
  }
}

@Composable
fun StatusDot(on: Boolean) {
  val c = ReelsTheme.colors
  Box(Modifier.size(12.dp).clip(CircleShape).background(if (on) c.good else c.warn))
}

/** Persistent warning while the accessibility service is off. */
@Composable
fun ServiceBanner(onClick: () -> Unit) {
  val c = ReelsTheme.colors
  Column(
    Modifier
      .fillMaxWidth()
      .padding(bottom = Space.gutter)
      .clip(RoundedCornerShape(16.dp))
      .background(c.warn.copy(alpha = 0.12f))
      .border(1.dp, c.warn.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = Space.gutter, vertical = Space.md),
  ) {
    Text("Tracking is off", color = c.warn, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    Text(
      "Reels aren’t being counted. Tap to turn on Reels Counter in Accessibility settings.",
      color = c.ink,
      fontSize = 14.sp,
    )
  }
}

@Composable
fun Gap(width: Boolean = false) {
  if (width) Spacer(Modifier.width(Space.md)) else Spacer(Modifier.size(Space.md))
}

val ColumnGap = Arrangement.spacedBy(Space.md)
