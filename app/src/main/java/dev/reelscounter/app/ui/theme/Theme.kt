package dev.reelscounter.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Semantic colour tokens. Dark is the primary design; light mirrors it. */
@Immutable
data class ReelsColors(
  val bg: Color,
  val card: Color,
  val line: Color,
  val ink: Color,
  val muted: Color,
  val accent: Color,
  val accentSoft: Color,
  val good: Color,
  val warn: Color,
  val bad: Color,
)

private val Dark = ReelsColors(
  bg = Color(0xFF0B0B0F),
  card = Color(0xFF16161D),
  line = Color(0xFF26262F),
  ink = Color(0xFFF4F4F6),
  muted = Color(0xFFA1A1AE),
  accent = Color(0xFFFF4F93),
  accentSoft = Color(0xFF5A2140),
  good = Color(0xFF3DD68C),
  warn = Color(0xFFFFB224),
  bad = Color(0xFFFF6369),
)

private val Light = ReelsColors(
  bg = Color(0xFFF6F6F8),
  card = Color(0xFFFFFFFF),
  line = Color(0xFFE4E4EA),
  ink = Color(0xFF101014),
  muted = Color(0xFF5E5E6B),
  accent = Color(0xFFD6246E),
  accentSoft = Color(0xFFF4B8CF),
  good = Color(0xFF12805C),
  warn = Color(0xFFA55E00),
  bad = Color(0xFFC62828),
)

val LocalReelsColors = staticCompositionLocalOf { Dark }

/** Spacing scale (4-pt based). */
object Space {
  val xs = 4.dp
  val sm = 8.dp
  val md = 12.dp
  val gutter = 16.dp
  val section = 24.dp
}

object ReelsTheme {
  val colors: ReelsColors
    @Composable get() = LocalReelsColors.current
}

@Composable
fun ReelsTheme(content: @Composable () -> Unit) {
  val dark = isSystemInDarkTheme()
  val c = if (dark) Dark else Light
  val scheme = if (dark) {
    darkColorScheme(
      primary = c.accent, onPrimary = Color.White, background = c.bg, onBackground = c.ink,
      surface = c.card, onSurface = c.ink, surfaceVariant = c.line, onSurfaceVariant = c.muted,
      outline = c.line, error = c.bad,
    )
  } else {
    lightColorScheme(
      primary = c.accent, onPrimary = Color.White, background = c.bg, onBackground = c.ink,
      surface = c.card, onSurface = c.ink, surfaceVariant = c.line, onSurfaceVariant = c.muted,
      outline = c.line, error = c.bad,
    )
  }
  val base = Typography()
  val typography = base.copy(
    displayLarge = TextStyle(fontSize = 60.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
  )
  CompositionLocalProvider(LocalReelsColors provides c) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
  }
}
