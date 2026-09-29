package expo.modules.reelstracker.detection

/**
 * Pure state machine that turns "this reel is visible now" observations into
 * view start / close actions. No Android dependencies so it can be unit-tested
 * on the JVM.
 *
 * Rules:
 *  - A view is recorded only when the fingerprint differs from the current one.
 *  - Changes within [debounceMs] of the last recorded view are ignored (the
 *    current reel stays open), which filters out pager jitter and half swipes.
 *  - Leaving the viewer closes the open view. Coming back to the very same reel
 *    resumes it (no new count; the extra watch time is added to that view).
 *  - A single view never accrues more than [maxViewDurationMs]; this guards
 *    against the phone being left on a reel for hours.
 */
class ReelViewTracker(
  private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
  private val maxViewDurationMs: Long = DEFAULT_MAX_VIEW_DURATION_MS,
) {
  sealed interface Action {
    /** A new reel view started. [isNewView] is false when resuming the previous reel. */
    data class Started(val app: String, val at: Long, val isNewView: Boolean) : Action

    /** The open segment ended; add [durationMs] to the view that is currently open. */
    data class Closed(val app: String, val startedAt: Long, val durationMs: Long) : Action
  }

  private data class OpenSegment(val app: String, val fingerprint: String, val startedAt: Long)

  private var open: OpenSegment? = null
  private var lastFingerprint: String? = null
  private var lastApp: String? = null
  private var lastRecordedAt: Long? = null

  val isViewOpen: Boolean
    get() = open != null

  fun onReelVisible(app: String, fingerprint: String, now: Long): List<Action> {
    val current = open
    if (current != null && current.app == app && current.fingerprint == fingerprint) {
      return emptyList()
    }

    val recordedAt = lastRecordedAt
    if (current != null && recordedAt != null && now - recordedAt < debounceMs) {
      return emptyList()
    }

    val actions = ArrayList<Action>(2)
    if (current != null) {
      actions += close(current, now)
    }

    val resuming = current == null && lastFingerprint == fingerprint && lastApp == app
    open = OpenSegment(app, fingerprint, now)
    lastFingerprint = fingerprint
    lastApp = app
    if (!resuming) {
      lastRecordedAt = now
    }
    actions += Action.Started(app, now, isNewView = !resuming)
    return actions
  }

  /** User left the reels viewer (other screen, other app, screen off). */
  fun onLeftViewer(now: Long): List<Action> {
    val current = open ?: return emptyList()
    open = null
    return listOf(close(current, now))
  }

  /** Forget everything, e.g. after the user wipes their data. */
  fun reset() {
    open = null
    lastFingerprint = null
    lastApp = null
    lastRecordedAt = null
  }

  private fun close(segment: OpenSegment, now: Long): Action.Closed {
    val duration = (now - segment.startedAt).coerceIn(0L, maxViewDurationMs)
    return Action.Closed(segment.app, segment.startedAt, duration)
  }

  companion object {
    const val DEFAULT_DEBOUNCE_MS = 500L
    const val DEFAULT_MAX_VIEW_DURATION_MS = 10 * 60 * 1000L
  }
}
