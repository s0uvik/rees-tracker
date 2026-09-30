package expo.modules.reelstracker.badge

/** Pure value types for the floating badge. No Android imports: shared with JVM tests. */

enum class BadgeVisibility(val key: String) {
  ALWAYS("always"),
  IN_TRACKED_APPS("in_tracked_apps");

  companion object {
    fun from(key: String?) = entries.firstOrNull { it.key == key } ?: IN_TRACKED_APPS
  }
}

enum class BadgeSize(val key: String, val textSp: Float, val detailSp: Float, val padHDp: Int, val padVDp: Int) {
  SM("sm", 12f, 10f, 9, 4),
  MD("md", 14f, 12f, 12, 6),
  LG("lg", 17f, 14f, 15, 8);

  companion object {
    fun from(key: String?) = entries.firstOrNull { it.key == key } ?: MD
  }
}

enum class BadgeTapAction(val key: String) {
  EXPAND("expand"),
  OPEN_APP("open_app");

  companion object {
    fun from(key: String?) = entries.firstOrNull { it.key == key } ?: EXPAND
  }
}

data class BadgeConfig(
  val enabled: Boolean = false,
  val visibility: BadgeVisibility = BadgeVisibility.IN_TRACKED_APPS,
  val size: BadgeSize = BadgeSize.MD,
  /** 0.4 – 1.0 */
  val opacity: Float = 0.9f,
  val tapAction: BadgeTapAction = BadgeTapAction.EXPAND,
  /** null = no limit. */
  val dailyLimit: Int? = null,
) {
  companion object {
    const val MIN_OPACITY = 0.4f
    const val MAX_OPACITY = 1.0f

    fun clampOpacity(value: Float) = value.coerceIn(MIN_OPACITY, MAX_OPACITY)
  }
}

enum class BadgeLevel { NEUTRAL, AMBER, RED }

object BadgeColors {
  const val NEUTRAL = 0xE6202124.toInt()
  const val AMBER = 0xF2B26A00.toInt()
  const val RED = 0xF2C62828.toInt()

  /** Neutral under 75% of the limit, amber at 75–99%, red at or over it. */
  fun levelFor(count: Int, dailyLimit: Int?): BadgeLevel {
    if (dailyLimit == null || dailyLimit <= 0) return BadgeLevel.NEUTRAL
    return when {
      count >= dailyLimit -> BadgeLevel.RED
      count.toLong() * 100 >= dailyLimit.toLong() * 75 -> BadgeLevel.AMBER
      else -> BadgeLevel.NEUTRAL
    }
  }

  fun colorFor(level: BadgeLevel) = when (level) {
    BadgeLevel.NEUTRAL -> NEUTRAL
    BadgeLevel.AMBER -> AMBER
    BadgeLevel.RED -> RED
  }
}

object BadgeText {
  fun count(count: Int) = "🎬 $count"

  fun duration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
      hours > 0 -> "${hours}h ${minutes}m"
      minutes > 0 -> "${minutes}m"
      else -> "${seconds}s"
    }
  }
}
