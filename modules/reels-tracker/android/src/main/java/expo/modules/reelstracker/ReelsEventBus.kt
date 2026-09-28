package expo.modules.reelstracker

import java.util.concurrent.CopyOnWriteArraySet

/**
 * In-process bridge between the accessibility service and the JS module.
 * Both run in the app's process; when the RN runtime is not alive there are
 * simply no listeners and events are dropped (the data is already persisted
 * in [ReelEventStore]).
 */
object ReelsEventBus {
  data class ReelViewed(val nativeId: Long, val app: String, val viewedAt: Long, val durationMs: Long?)

  interface Listener {
    fun onReelViewed(event: ReelViewed) {}

    /** Durations updated, data cleared, debug rows inserted: anything that invalidates stats. */
    fun onDataChanged() {}
  }

  private val listeners = CopyOnWriteArraySet<Listener>()

  fun register(listener: Listener) {
    listeners += listener
  }

  fun unregister(listener: Listener) {
    listeners -= listener
  }

  fun emitReelViewed(event: ReelViewed) {
    for (l in listeners) runCatching { l.onReelViewed(event) }
  }

  fun emitDataChanged() {
    for (l in listeners) runCatching { l.onDataChanged() }
  }
}
