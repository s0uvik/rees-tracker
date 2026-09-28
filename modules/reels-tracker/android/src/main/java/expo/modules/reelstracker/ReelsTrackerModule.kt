package expo.modules.reelstracker

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record
import expo.modules.reelstracker.badge.BadgeSize
import expo.modules.reelstracker.badge.BadgeTapAction
import expo.modules.reelstracker.badge.BadgeVisibility
import expo.modules.reelstracker.detection.TrackedAppConfig

class BadgeConfigPatch : Record {
  @Field val enabled: Boolean? = null
  @Field val visibility: String? = null
  @Field val size: String? = null
  @Field val opacity: Double? = null
  @Field val tapAction: String? = null

  /** 0 (or negative) clears the limit. */
  @Field val dailyLimit: Int? = null
}

class AckItem : Record {
  @Field val id: Long = 0
  @Field val version: Int = 0
}

class DebugEventSpec : Record {
  @Field val app: String = TrackedAppConfig.INSTAGRAM.appKey
  @Field val viewedAt: Long = 0
  @Field val durationMs: Long? = null
}

class ReelsTrackerModule : Module() {
  private val context: Context
    get() = appContext.reactContext ?: throw Exceptions.ReactContextLost()

  private val prefs by lazy { ReelsPrefs(context) }
  private val store by lazy { ReelEventStore.get(context) }

  private val busListener = object : ReelsEventBus.Listener {
    override fun onReelViewed(event: ReelsEventBus.ReelViewed) {
      sendEvent(
        EVENT_REEL_VIEWED,
        mapOf(
          "nativeId" to event.nativeId.toDouble(),
          "app" to event.app,
          "viewedAt" to event.viewedAt.toDouble(),
          "durationMs" to event.durationMs?.toDouble(),
        ),
      )
    }

    override fun onDataChanged() {
      sendEvent(EVENT_DATA_CHANGED, emptyMap<String, Any>())
    }
  }

  override fun definition() = ModuleDefinition {
    Name("ReelsTracker")

    Events(EVENT_REEL_VIEWED, EVENT_DATA_CHANGED)

    OnStartObserving { ReelsEventBus.register(busListener) }
    OnStopObserving { ReelsEventBus.unregister(busListener) }
    OnDestroy { ReelsEventBus.unregister(busListener) }

    // region Accessibility service

    AsyncFunction("isAccessibilityServiceEnabled") { isServiceEnabled(context) }

    Function("isAccessibilityServiceRunning") { ReelsAccessibilityService.isRunning }

    Function("openAccessibilitySettings") {
      val ctx = context
      val component = ComponentName(ctx, ReelsAccessibilityService::class.java).flattenToString()
      // Deep link straight to our service where supported, else the generic list.
      val details = Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS)
        .putExtra(Intent.EXTRA_COMPONENT_NAME, component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      try {
        ctx.startActivity(details)
      } catch (_: ActivityNotFoundException) {
        ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      } catch (_: SecurityException) {
        ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      }
    }

    /** App info screen: needed on Android 13+ to "Allow restricted settings" for sideloaded APKs. */
    Function("openAppDetailsSettings") {
      val ctx = context
      ctx.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
      )
    }

    // endregion

    // region Tracked packages

    AsyncFunction("getTrackedPackages") { prefs.trackedPackages.toList().sorted() }

    AsyncFunction("setTrackedPackages") { packages: List<String> ->
      prefs.trackedPackages = packages.toSet()
    }

    // endregion

    // region Floating badge

    AsyncFunction("setBadgeEnabled") { enabled: Boolean ->
      prefs.updateBadgeConfig(enabled = enabled)
    }

    AsyncFunction("getBadgeConfig") {
      val c = prefs.badgeConfig
      mapOf(
        "enabled" to c.enabled,
        "visibility" to c.visibility.key,
        "size" to c.size.key,
        "opacity" to c.opacity.toDouble(),
        "tapAction" to c.tapAction.key,
        "dailyLimit" to c.dailyLimit,
        "suppressed" to prefs.badgeSuppressed,
      )
    }

    AsyncFunction("setBadgeConfig") { patch: BadgeConfigPatch ->
      prefs.updateBadgeConfig(
        enabled = patch.enabled,
        visibility = patch.visibility?.let { BadgeVisibility.from(it) },
        size = patch.size?.let { BadgeSize.from(it) },
        opacity = patch.opacity?.toFloat(),
        tapAction = patch.tapAction?.let { BadgeTapAction.from(it) },
        dailyLimit = patch.dailyLimit,
      )
    }

    AsyncFunction("resetBadgePosition") { prefs.resetBadgePosition() }

    // endregion

    // region Event buffer (drained by JS into its own database)

    AsyncFunction("drainPendingEvents") { limit: Int ->
      store.unsynced(limit.coerceIn(1, 5_000)).map { row ->
        mapOf(
          "nativeId" to row.id.toDouble(),
          "app" to row.app,
          "viewedAt" to row.viewedAt.toDouble(),
          "durationMs" to row.durationMs?.toDouble(),
          "version" to row.version,
        )
      }
    }

    AsyncFunction("ackEvents") { items: List<AckItem> ->
      store.ack(items.map { it.id to it.version })
    }

    AsyncFunction("getTodayTotals") {
      val startOfDay = java.time.LocalDate.now()
        .atStartOfDay(java.time.ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
      val totals = store.totalsSince(startOfDay)
      mapOf("count" to totals.count, "durationMs" to totals.durationMs.toDouble())
    }

    AsyncFunction("clearAllEvents") {
      store.clearAll()
      ReelsAccessibilityService.notifyDataChanged(cleared = true)
      ReelsEventBus.emitDataChanged()
    }

    /** Debug screen only: inserts synthetic views so the dashboard and badge can be tested without Instagram. */
    AsyncFunction("insertDebugEvents") { events: List<DebugEventSpec> ->
      store.insertMany(events.map { Triple(it.app, it.viewedAt, it.durationMs) })
      ReelsAccessibilityService.notifyDataChanged(cleared = false)
      ReelsEventBus.emitDataChanged()
      events.size
    }

    // endregion
  }

  companion object {
    private const val EVENT_REEL_VIEWED = "onReelViewed"
    private const val EVENT_DATA_CHANGED = "onDataChanged"
    private const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

    fun isServiceEnabled(context: Context): Boolean {
      val expected = ComponentName(context, ReelsAccessibilityService::class.java)
      val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
      ) ?: return false
      val splitter = TextUtils.SimpleStringSplitter(':')
      splitter.setString(enabled)
      for (entry in splitter) {
        if (ComponentName.unflattenFromString(entry) == expected) return true
      }
      return false
    }
  }
}
