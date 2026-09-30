package expo.modules.reelstracker

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import expo.modules.reelstracker.badge.DailyCounter
import expo.modules.reelstracker.badge.FloatingBadgeController
import expo.modules.reelstracker.detection.ReelDetector
import expo.modules.reelstracker.detection.ReelViewTracker
import expo.modules.reelstracker.detection.TrackedAppConfig
import expo.modules.reelstracker.detection.TrackedAppUi
import java.lang.ref.WeakReference
import java.time.LocalDate

/**
 * Observes tracked apps and records one row per reel the user swipes to.
 *
 * Threading: accessibility callbacks, detection, the tracker state machine and
 * the badge run on the main thread; all database I/O runs sequentially on a
 * dedicated [HandlerThread] so ordering (close previous → insert next) holds.
 */
class ReelsAccessibilityService :
  AccessibilityService(),
  SharedPreferences.OnSharedPreferenceChangeListener {

  private val main = Handler(Looper.getMainLooper())
  private lateinit var ioThread: HandlerThread
  private lateinit var io: Handler

  private lateinit var prefs: ReelsPrefs
  private lateinit var store: ReelEventStore
  private lateinit var detector: ReelDetector
  private lateinit var badge: FloatingBadgeController
  private val tracker = ReelViewTracker()
  private val counter = DailyCounter()

  private var connected = false
  private var debugLogging = false

  @Volatile
  private var trackedPackages: Set<String> = TrackedAppConfig.DEFAULT_TRACKED_PACKAGES
  private var foregroundPackage: String? = null

  /** Row id of the view currently open. Only touched on the io thread. */
  private var activeRowId: Long? = null

  private var pendingUi: TrackedAppUi? = null
  private var inspectScheduled = false
  private var firstPendingAt = 0L
  private val inspectRunnable = Runnable { runInspection() }
  private val midnightRunnable = Runnable {
    reloadTotals()
    scheduleMidnightReset()
  }

  private val screenReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      if (intent.action == Intent.ACTION_SCREEN_OFF) {
        cancelInspection()
        apply(tracker.onLeftViewer(System.currentTimeMillis()))
      }
    }
  }

  // region Lifecycle

  override fun onServiceConnected() {
    super.onServiceConnected()
    debugLogging = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    ioThread = HandlerThread("ReelsTrackerIO").also { it.start() }
    io = Handler(ioThread.looper)
    prefs = ReelsPrefs(this)
    store = ReelEventStore.get(this)
    detector = ReelDetector(debugLogging)
    badge = FloatingBadgeController(this, prefs, ::openHostApp)
    trackedPackages = prefs.trackedPackages

    prefs.raw.registerOnSharedPreferenceChangeListener(this)
    registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    connected = true
    instance = WeakReference(this)

    badge.onSettingsChanged()
    reloadTotals()
    scheduleMidnightReset()
    if (debugLogging) Log.d(TAG, "connected; tracking $trackedPackages")
  }

  override fun onInterrupt() {
    // Called when the system wants feedback to stop. Remove the overlay so no
    // window can leak; it is re-added on the next foreground/settings change.
    if (connected) badge.destroy()
  }

  override fun onUnbind(intent: Intent?): Boolean {
    teardown()
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    teardown()
    super.onDestroy()
  }

  private fun teardown() {
    if (!connected) return
    connected = false
    instance = null
    cancelInspection()
    main.removeCallbacks(midnightRunnable)
    apply(tracker.onLeftViewer(System.currentTimeMillis()))
    badge.destroy()
    prefs.raw.unregisterOnSharedPreferenceChangeListener(this)
    runCatching { unregisterReceiver(screenReceiver) }
    // Pending writes (closing the last view) are flushed before the thread exits.
    ioThread.quitSafely()
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    if (connected) badge.onConfigurationChanged(newConfig)
  }

  // endregion

  // region Events

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null || !connected) return
    val pkg = event.packageName?.toString() ?: return
    val type = event.eventType

    if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      onWindowStateChanged(pkg)
    }

    // Early exit: everything below is only for tracked apps.
    if (pkg !in trackedPackages) return
    val ui = TrackedAppConfig.forPackage(pkg) ?: return

    if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) detector.onScrollEvent(ui, event)
    scheduleInspection(ui)
  }

  private fun onWindowStateChanged(eventPackage: String) {
    if (eventPackage in IGNORED_WINDOW_PACKAGES) return
    // Our own overlay windows are never the active window, so the active
    // window's package is the real foreground app. Fall back to the event.
    val activePackage = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
    if (activePackage == null && eventPackage == packageName) return
    val foreground = activePackage ?: eventPackage
    if (foreground in IGNORED_WINDOW_PACKAGES || foreground == foregroundPackage) return

    foregroundPackage = foreground
    val tracked = foreground in trackedPackages
    badge.setTrackedAppInForeground(tracked)
    if (!tracked) {
      cancelInspection()
      apply(tracker.onLeftViewer(System.currentTimeMillis()))
    }
  }

  /** Trailing-edge coalescing with a max wait, so bursts cost one inspection. */
  private fun scheduleInspection(ui: TrackedAppUi) {
    pendingUi = ui
    val now = SystemClock.uptimeMillis()
    if (!inspectScheduled) {
      inspectScheduled = true
      firstPendingAt = now
    }
    main.removeCallbacks(inspectRunnable)
    val waited = now - firstPendingAt
    val delay = if (waited >= TrackedAppConfig.INSPECT_MAX_WAIT_MS) 0L else TrackedAppConfig.INSPECT_SETTLE_MS
    main.postDelayed(inspectRunnable, delay)
  }

  private fun cancelInspection() {
    main.removeCallbacks(inspectRunnable)
    inspectScheduled = false
    pendingUi = null
  }

  private fun runInspection() {
    inspectScheduled = false
    val ui = pendingUi ?: return
    pendingUi = null
    if (!connected || ui.packageName !in trackedPackages) return

    val root = runCatching { rootInActiveWindow }.getOrNull()
    val now = System.currentTimeMillis()
    when (val result = detector.inspect(ui, root)) {
      is ReelDetector.Result.InViewer -> apply(tracker.onReelVisible(ui.appKey, result.fingerprint, now))
      ReelDetector.Result.NotInViewer -> apply(tracker.onLeftViewer(now))
      ReelDetector.Result.InViewerUnidentified -> Unit
    }
  }

  private fun apply(actions: List<ReelViewTracker.Action>) {
    if (actions.isEmpty()) return
    val now = System.currentTimeMillis()
    for (action in actions) {
      when (action) {
        is ReelViewTracker.Action.Closed -> {
          counter.addDuration(action.durationMs, action.startedAt, now)
          badge.update(counter.count(now), counter.durationMs(now), pulse = false)
          val duration = action.durationMs
          io.post {
            val id = activeRowId ?: return@post
            runCatching { store.addDuration(id, duration) }.onFailure { logError("addDuration", it) }
            ReelsEventBus.emitDataChanged()
          }
        }
        is ReelViewTracker.Action.Started -> {
          if (!action.isNewView) continue
          val count = counter.increment(now)
          badge.update(count, counter.durationMs(now), pulse = true)
          maybeNotifyLimit(count)
          val app = action.app
          val at = action.at
          io.post {
            runCatching { store.startView(app, at) }
              .onSuccess { id ->
                activeRowId = id
                ReelsEventBus.emitReelViewed(ReelsEventBus.ReelViewed(id, app, at, null))
              }
              .onFailure {
                activeRowId = null
                logError("startView", it)
              }
          }
          if (debugLogging) Log.d(TAG, "reel #$count ($app)")
        }
      }
    }
  }

  // endregion

  // region Totals, midnight, settings

  private fun reloadTotals() {
    val now = System.currentTimeMillis()
    val since = counter.startOfDayMs(now)
    io.post {
      val totals = runCatching {
        store.prune(now - PRUNE_AFTER_MS)
        store.totalsSince(since)
      }.getOrElse {
        logError("totals", it)
        return@post
      }
      main.post {
        if (!connected) return@post
        counter.load(totals.count, totals.durationMs, now)
        badge.update(totals.count, totals.durationMs, pulse = false)
      }
    }
  }

  private fun scheduleMidnightReset() {
    main.removeCallbacks(midnightRunnable)
    // Small cushion so the new day has definitely started in the local zone.
    main.postDelayed(midnightRunnable, counter.msUntilNextMidnight(System.currentTimeMillis()) + 1_000L)
  }

  override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
    if (!connected) return
    when (key) {
      ReelsPrefs.KEY_TRACKED -> {
        trackedPackages = prefs.trackedPackages
        val tracked = foregroundPackage in trackedPackages
        badge.setTrackedAppInForeground(tracked)
        if (!tracked) apply(tracker.onLeftViewer(System.currentTimeMillis()))
      }
      ReelsPrefs.KEY_POS_RESET_TOKEN -> badge.onPositionReset()
      in ReelsPrefs.BADGE_KEYS -> badge.onSettingsChanged()
    }
  }

  private fun onExternalDataChange(cleared: Boolean) {
    if (!connected) return
    if (cleared) {
      tracker.reset()
      detector.forget()
      io.post { activeRowId = null }
    }
    reloadTotals()
  }

  // endregion

  // region Side effects

  private fun maybeNotifyLimit(count: Int) {
    val limit = prefs.badgeConfig.dailyLimit ?: return
    if (count < limit) return
    val today = LocalDate.now().toEpochDay()
    if (prefs.limitNotifiedDay == today) return
    prefs.limitNotifiedDay = today

    try {
      val nm = getSystemService(NotificationManager::class.java)
      if (!nm.areNotificationsEnabled()) return
      nm.createNotificationChannel(
        NotificationChannel(
          LIMIT_CHANNEL_ID,
          getString(R.string.reels_tracker_limit_channel),
          NotificationManager.IMPORTANCE_DEFAULT,
        ),
      )
      val launch = packageManager.getLaunchIntentForPackage(packageName)
      val content = launch?.let {
        PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
      }
      val notification = Notification.Builder(this, LIMIT_CHANNEL_ID)
        .setSmallIcon(applicationInfo.icon)
        .setContentTitle(getString(R.string.reels_tracker_limit_title))
        .setContentText(getString(R.string.reels_tracker_limit_body, count, limit))
        .setAutoCancel(true)
        .apply { if (content != null) setContentIntent(content) }
        .build()
      nm.notify(LIMIT_NOTIFICATION_ID, notification)
    } catch (t: Throwable) {
      // Missing POST_NOTIFICATIONS grant on Android 13+ lands here.
      logError("limit notification", t)
    }
  }

  private fun openHostApp() {
    val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    runCatching { startActivity(intent) }.onFailure { logError("open app", it) }
  }

  private fun logError(what: String, t: Throwable) {
    Log.w(TAG, "$what failed", t)
  }

  // endregion

  companion object {
    private const val TAG = "ReelsService"
    private const val LIMIT_CHANNEL_ID = "reels_daily_limit"
    private const val LIMIT_NOTIFICATION_ID = 4201
    private const val PRUNE_AFTER_MS = 3L * 24 * 60 * 60 * 1000

    /** Transient system surfaces that should not count as "left the tracked app". */
    private val IGNORED_WINDOW_PACKAGES = setOf(
      "com.android.systemui",
      "android",
    )

    @Volatile
    private var instance: WeakReference<ReelsAccessibilityService>? = null

    val isRunning: Boolean
      get() = instance?.get() != null

    /** Called by the JS module after it inserts debug rows or wipes data. */
    fun notifyDataChanged(cleared: Boolean) {
      val service = instance?.get() ?: return
      service.main.post { service.onExternalDataChange(cleared) }
    }
  }
}
