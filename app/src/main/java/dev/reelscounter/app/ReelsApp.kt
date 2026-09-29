package dev.reelscounter.app

import android.app.Application
import dev.reelscounter.app.data.ReelEventStore
import dev.reelscounter.app.data.ReelsPrefs

/** Process-wide singletons shared by the UI and the accessibility service. */
class ReelsApp : Application() {
  val store: ReelEventStore by lazy { ReelEventStore.get(this) }
  val prefs: ReelsPrefs by lazy { ReelsPrefs(this) }
}
