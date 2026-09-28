package expo.modules.reelstracker

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class ReelsTrackerModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("ReelsTracker")

    Events("onChange")

    AsyncFunction("setValueAsync") { value: String ->
      sendEvent("onChange", mapOf(
        "value" to value
      ))
    }
  }
}
