package dev.reelscounter.app.badge

/**
 * Classifies a touch sequence on the badge as tap, drag or long-press.
 *
 * Movement beyond [touchSlopPx] (ViewConfiguration.scaledTouchSlop) turns the
 * gesture into a drag permanently. A long-press fires once, only if the
 * pointer has not left the slop and has been down for [longPressMs].
 */
class TapDragClassifier(private val touchSlopPx: Int, private val longPressMs: Long) {

  enum class Gesture { TAP, DRAG, LONG_PRESS, NONE }

  private var downX = 0f
  private var downY = 0f
  private var downAt = 0L
  private var active = false
  var isDragging = false
    private set
  private var longPressFired = false

  fun onDown(x: Float, y: Float, timeMs: Long) {
    downX = x
    downY = y
    downAt = timeMs
    active = true
    isDragging = false
    longPressFired = false
  }

  /** Returns true while the gesture is a drag. */
  fun onMove(x: Float, y: Float): Boolean {
    if (!active || longPressFired) return isDragging
    if (!isDragging) {
      val dx = x - downX
      val dy = y - downY
      if (dx * dx + dy * dy > touchSlopPx.toFloat() * touchSlopPx) {
        isDragging = true
      }
    }
    return isDragging
  }

  /** Call from the long-press timer. True exactly once, if the gesture qualifies. */
  fun checkLongPress(timeMs: Long): Boolean {
    if (!active || isDragging || longPressFired) return false
    if (timeMs - downAt < longPressMs) return false
    longPressFired = true
    return true
  }

  fun onUp(timeMs: Long): Gesture {
    if (!active) return Gesture.NONE
    active = false
    return when {
      isDragging -> Gesture.DRAG
      longPressFired -> Gesture.LONG_PRESS
      timeMs - downAt >= longPressMs -> Gesture.LONG_PRESS
      else -> Gesture.TAP
    }
  }

  fun cancel() {
    active = false
    isDragging = false
    longPressFired = false
  }
}
