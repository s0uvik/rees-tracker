package expo.modules.reelstracker.badge

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import expo.modules.reelstracker.R
import expo.modules.reelstracker.ReelsPrefs

/**
 * Draggable "🎬 42" pill drawn over other apps.
 *
 * Uses TYPE_ACCESSIBILITY_OVERLAY from inside the accessibility service, so no
 * SYSTEM_ALERT_WINDOW permission is needed. The window is WRAP_CONTENT and
 * FLAG_NOT_FOCUSABLE, so it only ever receives touches inside its own bounds.
 *
 * All public methods must be called on the main thread.
 */
class FloatingBadgeController(
  private val service: AccessibilityService,
  private val prefs: ReelsPrefs,
  private val openApp: () -> Unit,
) {
  private val windowManager = service.getSystemService(WindowManager::class.java)
  private val main = Handler(Looper.getMainLooper())
  private val viewConfig = ViewConfiguration.get(service)
  private val classifier = TapDragClassifier(
    touchSlopPx = viewConfig.scaledTouchSlop,
    longPressMs = ViewConfiguration.getLongPressTimeout().toLong(),
  )

  private var config = prefs.badgeConfig
  private var trackedAppInForeground = false
  private var count = 0
  private var watchMs = 0L

  private var root: View? = null
  private var pill: View? = null
  private var countText: TextView? = null
  private var detailText: TextView? = null
  private var params: WindowManager.LayoutParams? = null
  private var currentEdge = BadgeGeometry.DEFAULT_EDGE

  private var dismissView: View? = null
  private var dismissParams: WindowManager.LayoutParams? = null
  private var overDismiss = false

  private var snapAnimator: ValueAnimator? = null
  private var downRawX = 0f
  private var downRawY = 0f
  private var downX = 0
  private var downY = 0

  private val collapseRunnable = Runnable { setExpanded(false) }
  private val longPressRunnable = Runnable {
    if (classifier.checkLongPress(SystemClock.uptimeMillis())) {
      root?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
      suppressUntilReenabled()
    }
  }

  // region Public API

  fun setTrackedAppInForeground(inForeground: Boolean) {
    if (trackedAppInForeground == inForeground) return
    trackedAppInForeground = inForeground
    refreshVisibility()
  }

  /** Re-read settings from prefs (called from the prefs change listener). */
  fun onSettingsChanged() {
    config = prefs.badgeConfig
    applyAppearance()
    refreshVisibility()
  }

  fun onPositionReset() {
    val view = root ?: return
    placeFromPrefs(view)
  }

  fun update(count: Int, watchMs: Long, pulse: Boolean) {
    this.count = count
    this.watchMs = watchMs
    renderContent()
    if (pulse) pulse()
  }

  fun onConfigurationChanged(@Suppress("UNUSED_PARAMETER") newConfig: Configuration) {
    val view = root ?: return
    // Wait for the new display metrics, then restore from the orientation-independent position.
    view.post { placeFromPrefs(view) }
    hideDismissTarget()
  }

  /** Removes the windows without touching settings (used on interrupt / unbind / destroy). */
  fun destroy() {
    main.removeCallbacks(collapseRunnable)
    main.removeCallbacks(longPressRunnable)
    snapAnimator?.cancel()
    snapAnimator = null
    hideDismissTarget()
    detach()
  }

  // endregion

  private fun shouldShow(): Boolean =
    config.enabled &&
      !prefs.badgeSuppressed &&
      (config.visibility == BadgeVisibility.ALWAYS || trackedAppInForeground)

  private fun refreshVisibility() {
    if (shouldShow()) attach() else detach()
  }

  @SuppressLint("InflateParams", "ClickableViewAccessibility")
  private fun attach() {
    if (root != null) return
    val view = LayoutInflater.from(service).inflate(R.layout.floating_badge, null)
    root = view
    pill = view.findViewById(R.id.reels_badge_pill)
    countText = view.findViewById(R.id.reels_badge_count)
    detailText = view.findViewById(R.id.reels_badge_detail)

    val lp = WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      }
      title = "ReelsCounterBadge"
    }
    params = lp

    applyAppearance()
    renderContent()
    view.setOnTouchListener { _, event -> onTouch(event) }
    view.addOnLayoutChangeListener { v, _, _, right, _, _, _, oldRight, _ ->
      // Width changes when expanding/collapsing or when the count gains a digit:
      // stay glued to the current edge.
      if (right != oldRight && !classifier.isDragging && snapAnimator == null) {
        keepOnEdge(v)
      }
    }

    view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
    positionFromPrefs(view.measuredWidth, view.measuredHeight)

    try {
      windowManager.addView(view, lp)
    } catch (t: Throwable) {
      Log.w(TAG, "Could not add badge window", t)
      clearViewRefs()
    }
  }

  private fun detach() {
    val view = root ?: return
    main.removeCallbacks(collapseRunnable)
    main.removeCallbacks(longPressRunnable)
    snapAnimator?.cancel()
    snapAnimator = null
    classifier.cancel()
    hideDismissTarget()
    try {
      windowManager.removeViewImmediate(view)
    } catch (t: Throwable) {
      Log.w(TAG, "Badge window already removed", t)
    }
    clearViewRefs()
  }

  private fun clearViewRefs() {
    root = null
    pill = null
    countText = null
    detailText = null
    params = null
  }

  // region Rendering

  private fun applyAppearance() {
    val view = root ?: return
    val size = config.size
    view.alpha = config.opacity
    countText?.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.textSp)
    detailText?.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.detailSp)
    pill?.setPadding(dp(size.padHDp), dp(size.padVDp), dp(size.padHDp), dp(size.padVDp))
    applyColor()
  }

  private fun applyColor() {
    val bg = pill?.background?.mutate() as? GradientDrawable ?: return
    bg.setColor(BadgeColors.colorFor(BadgeColors.levelFor(count, config.dailyLimit)))
  }

  private fun renderContent() {
    countText?.text = BadgeText.count(count)
    detailText?.text = BadgeText.duration(watchMs)
    val limit = config.dailyLimit
    root?.contentDescription = if (limit != null) {
      "$count of $limit reels today"
    } else {
      "$count reels today"
    }
    applyColor()
  }

  private fun pulse() {
    val target = pill ?: return
    target.animate().cancel()
    target.scaleX = 1f
    target.scaleY = 1f
    target.animate()
      .scaleX(PULSE_SCALE)
      .scaleY(PULSE_SCALE)
      .setDuration(90)
      .withEndAction {
        target.animate().scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(DecelerateInterpolator()).start()
      }
      .start()
  }

  private fun setExpanded(expanded: Boolean) {
    main.removeCallbacks(collapseRunnable)
    detailText?.visibility = if (expanded) View.VISIBLE else View.GONE
    if (expanded) main.postDelayed(collapseRunnable, EXPAND_DURATION_MS)
  }

  // endregion

  // region Touch

  private fun onTouch(event: MotionEvent): Boolean {
    val lp = params ?: return false
    val view = root ?: return false
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        snapAnimator?.cancel()
        snapAnimator = null
        classifier.onDown(event.rawX, event.rawY, SystemClock.uptimeMillis())
        downRawX = event.rawX
        downRawY = event.rawY
        downX = lp.x
        downY = lp.y
        main.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
      }
      MotionEvent.ACTION_MOVE -> {
        if (classifier.onMove(event.rawX, event.rawY)) {
          main.removeCallbacks(longPressRunnable)
          showDismissTarget()
          val area = usableArea()
          val p = BadgeGeometry.clamp(
            downX + (event.rawX - downRawX).toInt(),
            downY + (event.rawY - downRawY).toInt(),
            view.width,
            view.height,
            area,
          )
          lp.x = p.x
          lp.y = p.y
          safeUpdate(view, lp)
          updateDismissHighlight(view, lp)
        }
      }
      MotionEvent.ACTION_UP -> {
        main.removeCallbacks(longPressRunnable)
        when (classifier.onUp(SystemClock.uptimeMillis())) {
          TapDragClassifier.Gesture.DRAG -> {
            val dismiss = overDismiss
            hideDismissTarget()
            if (dismiss) suppressUntilReenabled() else snapToEdge(view, lp)
          }
          TapDragClassifier.Gesture.TAP -> onTap()
          TapDragClassifier.Gesture.LONG_PRESS -> if (!prefs.badgeSuppressed) suppressUntilReenabled()
          TapDragClassifier.Gesture.NONE -> Unit
        }
      }
      MotionEvent.ACTION_CANCEL -> {
        main.removeCallbacks(longPressRunnable)
        val wasDragging = classifier.isDragging
        classifier.cancel()
        hideDismissTarget()
        if (wasDragging) snapToEdge(view, lp)
      }
    }
    return true
  }

  private fun onTap() {
    when (config.tapAction) {
      BadgeTapAction.EXPAND -> setExpanded(detailText?.visibility != View.VISIBLE)
      BadgeTapAction.OPEN_APP -> openApp()
    }
  }

  private fun suppressUntilReenabled() {
    prefs.badgeSuppressed = true
    detach()
  }

  // endregion

  // region Positioning

  private fun positionFromPrefs(width: Int, height: Int) {
    val lp = params ?: return
    val pos = prefs.badgePosition
    currentEdge = pos.edge
    val p = BadgeGeometry.fromPersisted(pos.edge, pos.yFraction, width, height, usableArea(), dp(EDGE_MARGIN_DP))
    lp.x = p.x
    lp.y = p.y
  }

  private fun placeFromPrefs(view: View) {
    val lp = params ?: return
    positionFromPrefs(view.width.takeIf { it > 0 } ?: view.measuredWidth, view.height.takeIf { it > 0 } ?: view.measuredHeight)
    safeUpdate(view, lp)
  }

  private fun keepOnEdge(view: View) {
    val lp = params ?: return
    val p = BadgeGeometry.positionOnEdge(currentEdge, lp.y, view.width, view.height, usableArea(), dp(EDGE_MARGIN_DP))
    if (p.x != lp.x || p.y != lp.y) {
      lp.x = p.x
      lp.y = p.y
      safeUpdate(view, lp)
    }
  }

  private fun snapToEdge(view: View, lp: WindowManager.LayoutParams) {
    val area = usableArea()
    val snap = BadgeGeometry.snapToEdge(lp.x, lp.y, view.width, view.height, area, dp(EDGE_MARGIN_DP))
    currentEdge = snap.edge
    val startX = lp.x
    val startY = lp.y
    snapAnimator?.cancel()
    snapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
      duration = SNAP_DURATION_MS
      interpolator = DecelerateInterpolator()
      addUpdateListener { anim ->
        if (root !== view) return@addUpdateListener
        val t = anim.animatedValue as Float
        lp.x = (startX + (snap.x - startX) * t).toInt()
        lp.y = (startY + (snap.y - startY) * t).toInt()
        safeUpdate(view, lp)
      }
      addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) {
          if (snapAnimator === animation) snapAnimator = null
          prefs.badgePosition = ReelsPrefs.Position(snap.edge, BadgeGeometry.yFraction(snap.y, view.height, area))
        }
      })
      start()
    }
  }

  private fun safeUpdate(view: View, lp: WindowManager.LayoutParams) {
    if (root !== view) return
    try {
      windowManager.updateViewLayout(view, lp)
    } catch (t: Throwable) {
      Log.w(TAG, "updateViewLayout failed", t)
    }
  }

  /** Screen bounds minus status bar, navigation bar and display cutout, in screen coordinates. */
  private fun usableArea(): Area {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      try {
        val metrics = windowManager.currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
          WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
        )
        val b = metrics.bounds
        return Area(b.left + insets.left, b.top + insets.top, b.right - insets.right, b.bottom - insets.bottom)
      } catch (t: Throwable) {
        Log.w(TAG, "currentWindowMetrics unavailable, using legacy metrics", t)
      }
    }
    return legacyUsableArea()
  }

  @Suppress("DEPRECATION")
  private fun legacyUsableArea(): Area {
    val dm = DisplayMetrics()
    windowManager.defaultDisplay.getRealMetrics(dm)
    val statusBar = systemDimen("status_bar_height")
    val navBar = systemDimen("navigation_bar_height")
    val landscape = dm.widthPixels > dm.heightPixels
    return if (landscape) {
      Area(0, statusBar, dm.widthPixels - navBar, dm.heightPixels)
    } else {
      Area(0, statusBar, dm.widthPixels, dm.heightPixels - navBar)
    }
  }

  @SuppressLint("DiscouragedApi", "InternalInsetResource")
  private fun systemDimen(name: String): Int {
    val id = service.resources.getIdentifier(name, "dimen", "android")
    return if (id > 0) service.resources.getDimensionPixelSize(id) else 0
  }

  // endregion

  // region Dismiss target ("✕" drop zone, chat-head style)

  @SuppressLint("InflateParams")
  private fun showDismissTarget() {
    if (dismissView != null) return
    val view = LayoutInflater.from(service).inflate(R.layout.floating_badge_dismiss, null)
    val size = dp(DISMISS_SIZE_DP)
    val area = usableArea()
    val lp = WindowManager.LayoutParams(
      size,
      size,
      WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = (area.left + area.right) / 2 - size / 2
      y = area.bottom - dp(DISMISS_BOTTOM_MARGIN_DP) - size
      title = "ReelsCounterBadgeDismiss"
    }
    view.alpha = 0f
    try {
      windowManager.addView(view, lp)
      dismissView = view
      dismissParams = lp
      view.animate().alpha(1f).setDuration(120).start()
    } catch (t: Throwable) {
      Log.w(TAG, "Could not show dismiss target", t)
    }
  }

  private fun updateDismissHighlight(badge: View, lp: WindowManager.LayoutParams) {
    val target = dismissView ?: return
    val tp = dismissParams ?: return
    val size = tp.width
    val over = BadgeGeometry.isOverTarget(
      lp.x, lp.y, badge.width, badge.height,
      tp.x + size / 2, tp.y + size / 2,
      dp(DISMISS_CAPTURE_RADIUS_DP),
    )
    if (over != overDismiss) {
      overDismiss = over
      val scale = if (over) 1.25f else 1f
      target.animate().scaleX(scale).scaleY(scale).setDuration(100).start()
      if (over) badge.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
  }

  private fun hideDismissTarget() {
    overDismiss = false
    val view = dismissView ?: return
    dismissView = null
    dismissParams = null
    try {
      windowManager.removeViewImmediate(view)
    } catch (t: Throwable) {
      Log.w(TAG, "Dismiss target already removed", t)
    }
  }

  // endregion

  private fun dp(value: Int): Int = (value * service.resources.displayMetrics.density).toInt()

  companion object {
    private const val TAG = "ReelsBadge"
    private const val EDGE_MARGIN_DP = 4
    private const val SNAP_DURATION_MS = 180L
    private const val EXPAND_DURATION_MS = 2500L
    private const val PULSE_SCALE = 1.12f
    private const val DISMISS_SIZE_DP = 56
    private const val DISMISS_BOTTOM_MARGIN_DP = 40
    private const val DISMISS_CAPTURE_RADIUS_DP = 64
  }
}
