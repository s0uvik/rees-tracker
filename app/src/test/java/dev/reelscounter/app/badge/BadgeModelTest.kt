package dev.reelscounter.app.badge

import org.junit.Assert.assertEquals
import org.junit.Test

class BadgeModelTest {
  @Test
  fun levelWithoutLimitIsNeutral() {
    assertEquals(BadgeLevel.NEUTRAL, BadgeColors.levelFor(999, null))
    assertEquals(BadgeLevel.NEUTRAL, BadgeColors.levelFor(999, 0))
  }

  @Test
  fun levelThresholds() {
    assertEquals(BadgeLevel.NEUTRAL, BadgeColors.levelFor(74, 100))
    assertEquals(BadgeLevel.AMBER, BadgeColors.levelFor(75, 100))
    assertEquals(BadgeLevel.AMBER, BadgeColors.levelFor(99, 100))
    assertEquals(BadgeLevel.RED, BadgeColors.levelFor(100, 100))
    assertEquals(BadgeLevel.RED, BadgeColors.levelFor(150, 100))
  }

  @Test
  fun thresholdsUseExactIntegerMath() {
    // 75% of 10 is 7.5: 7 is still neutral, 8 is amber.
    assertEquals(BadgeLevel.NEUTRAL, BadgeColors.levelFor(7, 10))
    assertEquals(BadgeLevel.AMBER, BadgeColors.levelFor(8, 10))
  }

  @Test
  fun opacityIsClamped() {
    assertEquals(0.4f, BadgeConfig.clampOpacity(0f), 0f)
    assertEquals(1f, BadgeConfig.clampOpacity(3f), 0f)
    assertEquals(0.7f, BadgeConfig.clampOpacity(0.7f), 0f)
  }

  @Test
  fun unknownKeysFallBackToDefaults() {
    assertEquals(BadgeVisibility.IN_TRACKED_APPS, BadgeVisibility.from("nope"))
    assertEquals(BadgeSize.MD, BadgeSize.from(null))
    assertEquals(BadgeTapAction.EXPAND, BadgeTapAction.from(""))
    assertEquals(BadgeSize.LG, BadgeSize.from("lg"))
  }

  @Test
  fun durationText() {
    assertEquals("0s", BadgeText.duration(0))
    assertEquals("45s", BadgeText.duration(45_900))
    assertEquals("18m", BadgeText.duration(18 * 60_000L + 5_000))
    assertEquals("1h 5m", BadgeText.duration(65 * 60_000L))
  }
}
