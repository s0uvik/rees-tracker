package expo.modules.reelstracker.badge

import expo.modules.reelstracker.badge.TapDragClassifier.Gesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapDragClassifierTest {
  private fun classifier() = TapDragClassifier(touchSlopPx = 16, longPressMs = 500)

  @Test
  fun quickPressWithoutMovementIsATap() {
    val c = classifier()
    c.onDown(100f, 100f, 0)
    assertFalse(c.onMove(105f, 108f)) // inside slop (√(25+64) ≈ 9.4)
    assertEquals(Gesture.TAP, c.onUp(120))
  }

  @Test
  fun movementBeyondSlopIsADrag() {
    val c = classifier()
    c.onDown(100f, 100f, 0)
    assertTrue(c.onMove(100f, 117f))
    assertEquals(Gesture.DRAG, c.onUp(80))
  }

  @Test
  fun slopIsMeasuredDiagonally() {
    val c = classifier()
    c.onDown(0f, 0f, 0)
    assertFalse(c.onMove(11f, 11f)) // ≈ 15.6 px
    assertTrue(c.onMove(12f, 12f)) // ≈ 17.0 px
  }

  @Test
  fun dragIsStickyEvenIfPointerReturns() {
    val c = classifier()
    c.onDown(0f, 0f, 0)
    c.onMove(40f, 0f)
    assertTrue(c.onMove(0f, 0f))
    assertEquals(Gesture.DRAG, c.onUp(50))
  }

  @Test
  fun holdingStillFiresLongPressOnce() {
    val c = classifier()
    c.onDown(0f, 0f, 0)
    assertFalse(c.checkLongPress(499))
    assertTrue(c.checkLongPress(500))
    assertFalse(c.checkLongPress(700))
    assertEquals(Gesture.LONG_PRESS, c.onUp(900))
  }

  @Test
  fun longPressDoesNotFireAfterDragStarts() {
    val c = classifier()
    c.onDown(0f, 0f, 0)
    c.onMove(50f, 0f)
    assertFalse(c.checkLongPress(1_000))
    assertEquals(Gesture.DRAG, c.onUp(1_100))
  }

  @Test
  fun movingAfterLongPressDoesNotBecomeADrag() {
    val c = classifier()
    c.onDown(0f, 0f, 0)
    assertTrue(c.checkLongPress(600))
    assertFalse(c.onMove(200f, 0f))
    assertEquals(Gesture.LONG_PRESS, c.onUp(700))
  }

  @Test
  fun lateReleaseWithoutTimerIsStillALongPress() {
    val c = classifier()
    c.onDown(0f, 0f, 0)
    assertEquals(Gesture.LONG_PRESS, c.onUp(800))
  }

  @Test
  fun upWithoutDownOrAfterCancelIsNone() {
    val c = classifier()
    assertEquals(Gesture.NONE, c.onUp(10))
    c.onDown(0f, 0f, 0)
    c.cancel()
    assertEquals(Gesture.NONE, c.onUp(10))
  }
}
