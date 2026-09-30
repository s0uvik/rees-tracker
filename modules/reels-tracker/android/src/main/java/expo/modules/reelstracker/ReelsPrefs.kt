package expo.modules.reelstracker

import android.content.Context
import android.content.SharedPreferences
import expo.modules.reelstracker.badge.BadgeConfig
import expo.modules.reelstracker.badge.BadgeGeometry
import expo.modules.reelstracker.badge.BadgeSize
import expo.modules.reelstracker.badge.BadgeTapAction
import expo.modules.reelstracker.badge.BadgeVisibility
import expo.modules.reelstracker.badge.Edge
import expo.modules.reelstracker.detection.TrackedAppConfig

/**
 * Settings the service needs while the RN app is closed. SharedPreferences is
 * the source of truth for these; JS reads and writes them through the module.
 * The service observes changes via [SharedPreferences.OnSharedPreferenceChangeListener].
 */
class ReelsPrefs(context: Context) {
  val raw: SharedPreferences =
    context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

  data class Position(val edge: Edge, val yFraction: Float)

  var trackedPackages: Set<String>
    get() = raw.getStringSet(KEY_TRACKED, null)?.toSet() ?: TrackedAppConfig.DEFAULT_TRACKED_PACKAGES
    set(value) {
      val known = value.filter { TrackedAppConfig.forPackage(it) != null }.toSet()
      raw.edit().putStringSet(KEY_TRACKED, known).apply()
    }

  val badgeConfig: BadgeConfig
    get() = BadgeConfig(
      enabled = raw.getBoolean(KEY_BADGE_ENABLED, false),
      visibility = BadgeVisibility.from(raw.getString(KEY_BADGE_VISIBILITY, null)),
      size = BadgeSize.from(raw.getString(KEY_BADGE_SIZE, null)),
      opacity = BadgeConfig.clampOpacity(raw.getFloat(KEY_BADGE_OPACITY, 0.9f)),
      tapAction = BadgeTapAction.from(raw.getString(KEY_BADGE_TAP, null)),
      dailyLimit = raw.getInt(KEY_DAILY_LIMIT, 0).takeIf { it > 0 },
    )

  /** Partial update; null arguments are left untouched. A [dailyLimit] <= 0 clears the limit. */
  fun updateBadgeConfig(
    enabled: Boolean? = null,
    visibility: BadgeVisibility? = null,
    size: BadgeSize? = null,
    opacity: Float? = null,
    tapAction: BadgeTapAction? = null,
    dailyLimit: Int? = null,
  ) {
    raw.edit().apply {
      if (enabled != null) {
        putBoolean(KEY_BADGE_ENABLED, enabled)
        // Enabling from the app always lifts a long-press "hide for now".
        if (enabled) putBoolean(KEY_BADGE_SUPPRESSED, false)
      }
      visibility?.let { putString(KEY_BADGE_VISIBILITY, it.key) }
      size?.let { putString(KEY_BADGE_SIZE, it.key) }
      opacity?.let { putFloat(KEY_BADGE_OPACITY, BadgeConfig.clampOpacity(it)) }
      tapAction?.let { putString(KEY_BADGE_TAP, it.key) }
      dailyLimit?.let {
        putInt(KEY_DAILY_LIMIT, it.coerceAtLeast(0))
        remove(KEY_LIMIT_NOTIFIED_DAY)
      }
    }.apply()
  }

  /** Set by long-press / drop-to-dismiss; cleared the next time the app enables the badge. */
  var badgeSuppressed: Boolean
    get() = raw.getBoolean(KEY_BADGE_SUPPRESSED, false)
    set(value) = raw.edit().putBoolean(KEY_BADGE_SUPPRESSED, value).apply()

  var badgePosition: Position
    get() = Position(
      edge = if (raw.getString(KEY_POS_EDGE, null) == Edge.LEFT.name) Edge.LEFT else BadgeGeometry.DEFAULT_EDGE,
      yFraction = raw.getFloat(KEY_POS_Y, BadgeGeometry.DEFAULT_Y_FRACTION),
    )
    set(value) = raw.edit()
      .putString(KEY_POS_EDGE, value.edge.name)
      .putFloat(KEY_POS_Y, value.yFraction)
      .apply()

  fun resetBadgePosition() {
    raw.edit()
      .remove(KEY_POS_EDGE)
      .remove(KEY_POS_Y)
      .putLong(KEY_POS_RESET_TOKEN, System.currentTimeMillis())
      .apply()
  }

  /** Local epoch-day on which the daily-limit notification was last posted. */
  var limitNotifiedDay: Long
    get() = raw.getLong(KEY_LIMIT_NOTIFIED_DAY, Long.MIN_VALUE)
    set(value) = raw.edit().putLong(KEY_LIMIT_NOTIFIED_DAY, value).apply()

  companion object {
    private const val FILE = "reels_tracker_prefs"
    const val KEY_TRACKED = "tracked_packages"
    const val KEY_BADGE_ENABLED = "badge_enabled"
    const val KEY_BADGE_VISIBILITY = "badge_visibility"
    const val KEY_BADGE_SIZE = "badge_size"
    const val KEY_BADGE_OPACITY = "badge_opacity"
    const val KEY_BADGE_TAP = "badge_tap_action"
    const val KEY_BADGE_SUPPRESSED = "badge_suppressed"
    const val KEY_DAILY_LIMIT = "daily_limit"
    const val KEY_POS_EDGE = "badge_pos_edge"
    const val KEY_POS_Y = "badge_pos_y"
    const val KEY_POS_RESET_TOKEN = "badge_pos_reset"
    const val KEY_LIMIT_NOTIFIED_DAY = "limit_notified_day"

    /** Keys whose change should re-render / re-evaluate the badge. */
    val BADGE_KEYS = setOf(
      KEY_BADGE_ENABLED, KEY_BADGE_VISIBILITY, KEY_BADGE_SIZE, KEY_BADGE_OPACITY,
      KEY_BADGE_TAP, KEY_BADGE_SUPPRESSED, KEY_DAILY_LIMIT, KEY_POS_RESET_TOKEN,
    )
  }
}
