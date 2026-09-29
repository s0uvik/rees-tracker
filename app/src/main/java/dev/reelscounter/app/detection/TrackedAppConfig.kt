package dev.reelscounter.app.detection

/**
 * Everything that depends on the tracked apps' private UI lives here.
 *
 * When Instagram ships a redesign and counting stops, this is the ONLY file
 * that should need changes. Debug builds log the view IDs of unknown layouts
 * under the "ReelsDetector" logcat tag to help find the new identifiers
 * (see README → "Updating Instagram view identifiers").
 *
 * IDs are the entry name only; the package prefix is added at lookup time,
 * e.g. "clips_viewer_view_pager" → "com.instagram.android:id/clips_viewer_view_pager".
 */
data class TrackedAppUi(
  /** Value written to the database `app` column and sent to JS. */
  val appKey: String,
  val packageName: String,
  /** Any of these present on screen ⇒ the full-screen reels viewer is open. */
  val viewerContainerIds: List<String>,
  /** Nodes whose text / content description identify the visible reel. */
  val fingerprintIds: List<String>,
  /** Substrings used only for debug logging of possibly-relevant unknown layouts. */
  val debugIdHints: List<String>,
)

object TrackedAppConfig {
  val INSTAGRAM = TrackedAppUi(
    appKey = "instagram",
    packageName = "com.instagram.android",
    viewerContainerIds = listOf(
      "clips_viewer_view_pager",
      "clips_viewer_container",
      "clips_video_container",
    ),
    fingerprintIds = listOf(
      "clips_author_username",
      "clips_caption_component",
      "clips_media_component",
      "clips_single_media_component",
    ),
    debugIdHints = listOf("clips", "reel"),
  )

  val YOUTUBE_SHORTS = TrackedAppUi(
    appKey = "youtube_shorts",
    packageName = "com.google.android.youtube",
    viewerContainerIds = listOf(
      "reel_recycler",
      "reel_player_page_container",
      "reel_watch_player",
    ),
    fingerprintIds = listOf(
      "reel_channel_name",
      "reel_multi_format_title",
      "reel_main_title",
    ),
    debugIdHints = listOf("reel", "shorts"),
  )

  val ALL: List<TrackedAppUi> = listOf(INSTAGRAM, YOUTUBE_SHORTS)

  val DEFAULT_TRACKED_PACKAGES: Set<String> = setOf(INSTAGRAM.packageName)

  fun forPackage(packageName: CharSequence?): TrackedAppUi? {
    if (packageName == null) return null
    return ALL.firstOrNull { it.packageName.contentEquals(packageName) }
  }

  /** Bounded traversal limits for the fallback fingerprint walk. */
  const val MAX_TRAVERSAL_DEPTH = 6
  const val MAX_TRAVERSAL_NODES = 60

  /** Coalesce bursts of events; inspect the tree at most this often. */
  const val INSPECT_SETTLE_MS = 250L
  /** Continuous content changes (progress bars) must not starve inspection. */
  const val INSPECT_MAX_WAIT_MS = 1000L
}
