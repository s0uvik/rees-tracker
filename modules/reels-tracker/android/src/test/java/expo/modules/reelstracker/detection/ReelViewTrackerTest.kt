package expo.modules.reelstracker.detection

import expo.modules.reelstracker.detection.ReelViewTracker.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelViewTrackerTest {
  private val ig = "instagram"

  @Test
  fun firstReelStartsANewView() {
    val t = ReelViewTracker()
    assertEquals(listOf(Action.Started(ig, 1_000, isNewView = true)), t.onReelVisible(ig, "a", 1_000))
    assertTrue(t.isViewOpen)
  }

  @Test
  fun sameFingerprintIsIgnored() {
    val t = ReelViewTracker()
    t.onReelVisible(ig, "a", 1_000)
    assertTrue(t.onReelVisible(ig, "a", 5_000).isEmpty())
    assertTrue(t.onReelVisible(ig, "a", 60_000).isEmpty())
  }

  @Test
  fun newFingerprintClosesPreviousAndStartsNext() {
    val t = ReelViewTracker()
    t.onReelVisible(ig, "a", 1_000)
    val actions = t.onReelVisible(ig, "b", 4_000)
    assertEquals(
      listOf(
        Action.Closed(ig, startedAt = 1_000, durationMs = 3_000),
        Action.Started(ig, 4_000, isNewView = true),
      ),
      actions,
    )
  }

  @Test
  fun changesWithinDebounceAreIgnored() {
    val t = ReelViewTracker(debounceMs = 500)
    t.onReelVisible(ig, "a", 1_000)
    assertTrue(t.onReelVisible(ig, "b", 1_499).isEmpty())
    // Settled on "b" after the window: counted once.
    val actions = t.onReelVisible(ig, "b", 1_500)
    assertEquals(Action.Started(ig, 1_500, isNewView = true), actions.last())
  }

  @Test
  fun rapidSwipesCountOncePerDebounceWindow() {
    val t = ReelViewTracker(debounceMs = 500)
    var started = 0
    listOf("a" to 0L, "b" to 100L, "c" to 200L, "d" to 300L, "e" to 700L).forEach { (fp, at) ->
      started += t.onReelVisible(ig, fp, at).count { it is Action.Started && it.isNewView }
    }
    assertEquals(2, started) // "a" at 0, then "e" at 700
  }

  @Test
  fun leavingClosesTheOpenView() {
    val t = ReelViewTracker()
    t.onReelVisible(ig, "a", 1_000)
    assertEquals(listOf(Action.Closed(ig, 1_000, 2_500)), t.onLeftViewer(3_500))
    assertFalse(t.isViewOpen)
    assertTrue(t.onLeftViewer(4_000).isEmpty())
  }

  @Test
  fun returningToTheSameReelResumesWithoutCounting() {
    val t = ReelViewTracker()
    t.onReelVisible(ig, "a", 1_000)
    t.onLeftViewer(2_000)
    assertEquals(listOf(Action.Started(ig, 10_000, isNewView = false)), t.onReelVisible(ig, "a", 10_000))
    assertEquals(listOf(Action.Closed(ig, 10_000, 1_000)), t.onLeftViewer(11_000))
  }

  @Test
  fun returningToADifferentReelCounts() {
    val t = ReelViewTracker()
    t.onReelVisible(ig, "a", 1_000)
    t.onLeftViewer(2_000)
    assertEquals(listOf(Action.Started(ig, 2_100, isNewView = true)), t.onReelVisible(ig, "b", 2_100))
  }

  @Test
  fun durationIsCapped() {
    val t = ReelViewTracker(maxViewDurationMs = 60_000)
    t.onReelVisible(ig, "a", 0)
    assertEquals(listOf(Action.Closed(ig, 0, 60_000)), t.onLeftViewer(3_600_000))
  }

  @Test
  fun clockGoingBackwardsNeverYieldsNegativeDuration() {
    val t = ReelViewTracker()
    t.onReelVisible(ig, "a", 10_000)
    assertEquals(listOf(Action.Closed(ig, 10_000, 0)), t.onLeftViewer(9_000))
  }

  @Test
  fun resetForgetsHistory() {
    val t = ReelViewTracker()
    t.onReelVisible(ig, "a", 1_000)
    t.reset()
    assertFalse(t.isViewOpen)
    assertEquals(listOf(Action.Started(ig, 1_100, isNewView = true)), t.onReelVisible(ig, "a", 1_100))
  }

  @Test
  fun sameFingerprintInAnotherAppIsANewView() {
    val t = ReelViewTracker()
    t.onReelVisible(ig, "idx:3", 1_000)
    val actions = t.onReelVisible("youtube_shorts", "idx:3", 2_000)
    assertEquals(Action.Started("youtube_shorts", 2_000, isNewView = true), actions.last())
  }
}
