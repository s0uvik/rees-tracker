package dev.reelscounter.app.detection

import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

/**
 * Reads the tracked app's node tree and decides whether the reels viewer is
 * open and which reel is on screen. Everything is best-effort: any exception
 * or unknown layout yields [Result.NotInViewer] instead of crashing.
 *
 * Cost control:
 *  - Uses findAccessibilityNodeInfosByViewId, which runs the search inside the
 *    target app's process instead of streaming the whole tree to us.
 *  - The fallback walk is capped at [TrackedAppConfig.MAX_TRAVERSAL_DEPTH] /
 *    [TrackedAppConfig.MAX_TRAVERSAL_NODES].
 */
class ReelDetector(private val debugLogging: Boolean) {

  sealed interface Result {
    data class InViewer(val fingerprint: String) : Result

    /** Viewer is open but the reel could not be identified; keep the current view open. */
    data object InViewerUnidentified : Result

    data object NotInViewer : Result
  }

  /** Last pager position reported by TYPE_VIEW_SCROLLED, per package. */
  private val lastScrollIndex = HashMap<String, Int>()
  private var lastUnknownLayoutLogAt = 0L

  fun onScrollEvent(ui: TrackedAppUi, event: AccessibilityEvent) {
    val index = event.fromIndex
    if (index >= 0 && event.itemCount > 1) {
      lastScrollIndex[ui.packageName] = index
    }
  }

  fun forget() {
    lastScrollIndex.clear()
  }

  fun inspect(ui: TrackedAppUi, root: AccessibilityNodeInfo?): Result {
    if (root == null) return Result.NotInViewer
    return try {
      inspectUnsafe(ui, root)
    } catch (t: Throwable) {
      if (debugLogging) Log.w(TAG, "inspect failed for ${ui.packageName}", t)
      Result.NotInViewer
    }
  }

  private fun inspectUnsafe(ui: TrackedAppUi, root: AccessibilityNodeInfo): Result {
    val rootPackage = root.packageName ?: return Result.NotInViewer
    if (!ui.packageName.contentEquals(rootPackage)) return Result.NotInViewer

    val container = findFirstVisible(root, ui, ui.viewerContainerIds)
    if (container == null) {
      maybeLogUnknownLayout(ui, root)
      return Result.NotInViewer
    }

    val screen = Rect().also { root.getBoundsInScreen(it) }

    // 1) Preferred: identifying labels of the reel nearest the screen centre.
    val parts = ArrayList<CharSequence?>(8)
    for (id in ui.fingerprintIds) {
      val node = mostCentralNode(root, fullId(ui, id), screen) ?: continue
      parts += node.text
      parts += node.contentDescription
    }
    Fingerprint.of(parts)?.let { return Result.InViewer(it) }

    // 2) Bounded walk of the pager's most visible page, collecting descriptions.
    Fingerprint.of(boundedDescriptions(container, screen))?.let { return Result.InViewer(it) }

    // 3) Pager index from the last scroll event.
    lastScrollIndex[ui.packageName]?.let { return Result.InViewer(Fingerprint.ofIndex(it)) }

    if (debugLogging) Log.d(TAG, "viewer found but reel unidentified (${ui.packageName})")
    return Result.InViewerUnidentified
  }

  private fun fullId(ui: TrackedAppUi, id: String) = "${ui.packageName}:id/$id"

  private fun findFirstVisible(
    root: AccessibilityNodeInfo,
    ui: TrackedAppUi,
    ids: List<String>,
  ): AccessibilityNodeInfo? {
    for (id in ids) {
      val visible = root.findAccessibilityNodeInfosByViewId(fullId(ui, id)).firstOrNull { it.isVisibleToUser }
      if (visible != null) return visible
    }
    return null
  }

  /** Among nodes with [viewId], the visible one closest to the screen centre, larger area breaking ties. */
  private fun mostCentralNode(
    root: AccessibilityNodeInfo,
    viewId: String,
    screen: Rect,
  ): AccessibilityNodeInfo? {
    val matches = root.findAccessibilityNodeInfosByViewId(viewId)
    if (matches.isEmpty()) return null
    val bounds = Rect()
    var best: AccessibilityNodeInfo? = null
    var bestDistance = Long.MAX_VALUE
    var bestArea = -1L
    for (node in matches) {
      if (!node.isVisibleToUser) continue
      node.getBoundsInScreen(bounds)
      if (!bounds.intersect(screen)) continue
      val distance = abs(bounds.centerY() - screen.centerY()).toLong()
      val area = bounds.width().toLong() * bounds.height().toLong()
      if (distance < bestDistance || (distance == bestDistance && area > bestArea)) {
        bestDistance = distance
        bestArea = area
        best = node
      }
    }
    return best
  }

  private fun boundedDescriptions(container: AccessibilityNodeInfo, screen: Rect): List<CharSequence?> {
    val page = mostVisibleChild(container, screen) ?: container
    val out = ArrayList<CharSequence?>(8)
    var visited = 0
    val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
    stack.addLast(page to 0)
    while (stack.isNotEmpty() && visited < TrackedAppConfig.MAX_TRAVERSAL_NODES) {
      val (node, depth) = stack.removeLast()
      visited++
      node.contentDescription?.let { out += it }
      if (depth >= TrackedAppConfig.MAX_TRAVERSAL_DEPTH) continue
      for (i in node.childCount - 1 downTo 0) {
        val child = node.getChild(i) ?: continue
        if (child.isVisibleToUser) stack.addLast(child to depth + 1)
      }
    }
    return out
  }

  private fun mostVisibleChild(container: AccessibilityNodeInfo, screen: Rect): AccessibilityNodeInfo? {
    val bounds = Rect()
    var best: AccessibilityNodeInfo? = null
    var bestArea = 0L
    for (i in 0 until container.childCount) {
      val child = container.getChild(i) ?: continue
      child.getBoundsInScreen(bounds)
      if (!bounds.intersect(screen)) continue
      val area = bounds.width().toLong() * bounds.height().toLong()
      if (area > bestArea) {
        bestArea = area
        best = child
      }
    }
    return best
  }

  /**
   * Debug builds only: when a tracked app is open but no known viewer id is
   * on screen, log ids that look reel-related so [TrackedAppConfig] can be
   * updated. Only resource ids and class names are logged, never text.
   */
  private fun maybeLogUnknownLayout(ui: TrackedAppUi, root: AccessibilityNodeInfo) {
    if (!debugLogging) return
    val now = SystemClock.elapsedRealtime()
    if (now - lastUnknownLayoutLogAt < UNKNOWN_LAYOUT_LOG_INTERVAL_MS) return
    lastUnknownLayoutLogAt = now

    val ids = LinkedHashSet<String>()
    var visited = 0
    val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
    stack.addLast(root to 0)
    while (stack.isNotEmpty() && visited < DEBUG_DUMP_MAX_NODES) {
      val (node, depth) = stack.removeLast()
      visited++
      node.viewIdResourceName?.let { id ->
        if (ui.debugIdHints.any { hint -> id.contains(hint, ignoreCase = true) }) {
          val cls = node.className?.toString()?.substringAfterLast('.')
          ids += "${id.substringAfter(":id/")}<$cls>"
        }
      }
      if (depth >= DEBUG_DUMP_MAX_DEPTH) continue
      for (i in 0 until node.childCount) {
        node.getChild(i)?.let { stack.addLast(it to depth + 1) }
      }
    }
    if (ids.isNotEmpty()) {
      Log.d(TAG, "No known viewer id in ${ui.packageName}. Candidate ids: ${ids.joinToString()}")
    }
  }

  companion object {
    const val TAG = "ReelsDetector"
    private const val UNKNOWN_LAYOUT_LOG_INTERVAL_MS = 10_000L
    private const val DEBUG_DUMP_MAX_NODES = 400
    private const val DEBUG_DUMP_MAX_DEPTH = 14
  }
}
