# Reels Counter

Native Android app (Kotlin + Jetpack Compose) that counts how many Instagram Reels you watch and how long you watch them, and shows daily, weekly and monthly stats. You can also turn on a floating badge that shows today's count on top of other apps.

Instagram has no API for watch history, so counting happens on the device through an Android **AccessibilityService**. **All data stays on the phone.** The app doesn't even request the internet permission, and it stores only a timestamp, the app name, and watch duration for each reel.

> v1 is meant for sideloading. Read [Play Store policy](#play-store-policy) before you publish it.
>
> An earlier React Native + Expo version of this app lives on the `feat/reels-counter` branch.

---

## Contents

- [How it works](#how-it-works)
- [Build and install](#build-and-install)
- [Enable the service](#enable-the-service)
- [Floating badge](#floating-badge)
- [Updating Instagram view identifiers](#updating-instagram-view-identifiers)
- [Testing](#testing)
- [Project layout](#project-layout)
- [Privacy](#privacy)
- [Play Store policy](#play-store-policy)
- [Known limitations](#known-limitations)

---

## How it works

```
Instagram UI ──events──▶ ReelsAccessibilityService (runs while the app is closed)
                           │  early package filter → 250 ms coalesced inspection
                           ▼
                         ReelDetector ── view IDs from TrackedAppConfig.kt
                           │  "viewer open?" + in-memory fingerprint of the visible reel
                           ▼
                         ReelViewTracker (pure) ── new fingerprint + 500 ms debounce
                           │  Started / Closed(duration)
               ┌───────────┴─────────────┐
               ▼                         ▼
     ReelEventStore (reels.db, WAL)   FloatingBadgeController
               │                       today's count cached in memory,
               │ DataEvents (StateFlow)  reset at local midnight
               ▼
     AppViewModel ──▶ SQL day×hour rollup ──▶ Stats (java.time) ──▶ Compose screens
```

The service and the UI run in the same process and share **one** SQLite database (`ReelEventStore`, WAL mode). After each write, the service bumps `DataEvents` and any open screen reloads.

**Stats.** SQL groups views by *local* day and hour; SQLite's `localtime` handles DST. `Stats` then rolls those rows up into 30 days, 12 ISO weeks starting on Monday, and 12 months, and fills empty buckets with zero. For each period it computes total reels, watch time, average time per reel (counting only reels that have a duration), peak hour, and the % change from the previous period.

**Dependencies are kept small:** AndroidX core, Activity/Lifecycle Compose, Compose Material 3 and coroutines. There's no chart library (bars are drawn on a Compose `Canvas`), no navigation library, no Room and no native C++ code, so the NDK isn't needed.

---

## Build and install

### Prerequisites

- **JDK 17** and the **Android SDK** with platform 36 and build-tools 36. Android Studio installs both. Without Android Studio, install the command-line tools and set `sdk.dir` in `local.properties`.
- A phone running Android 8.0+ (API 26)

### Debug APK (recommended for testing)

```bash
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# or build and install in one step, with the phone connected:
./gradlew installDebug
```

The debug APK needs no dev server. It also includes the **debug tools** screen and the detector's diagnostic logging.

### Release APK

```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Release builds are minified with R8 and **signed with your local debug key**. That's fine for sideloading onto your own phone. Before you share the APK, set up a real keystore in `app/build.gradle.kts`.

`gradle.properties` is tuned for machines with about 12 GB of RAM: 1.5 GB Gradle heap, 2 workers, and Kotlin compiled in-process. On a machine with more RAM you can raise these.

---

## Enable the service

1. Open the app. The onboarding screen explains what access it needs and why.
2. Tap **Open Accessibility settings** → **Reels Counter** → turn it on → accept the system dialog.
3. Return to the app. It checks every 1.5 s and moves on to the dashboard as soon as the service is on.

**Toggle greyed out / "Restricted setting" (Android 13+).** Android blocks accessibility access for apps installed outside an app store. Open **Settings → Apps → Reels Counter → ⋮ (top right) → Allow restricted settings**, then enable the service. The onboarding screen has an **Open App info** shortcut. On some phones the ⋮ menu appears only after you've tried to enable the service once.

**Skip for now** takes you to the dashboard without the service. A banner stays on screen until the service is on.

Some phone makers (Xiaomi, Oppo, Vivo, Samsung and others) kill background services aggressively. If counting stops after a while, set the app's battery usage to **Unrestricted**.

---

## Floating badge

A small pill (`🎬 42`) with today's count that floats over other apps.

- **Turn it on:** Settings → Floating badge → *Show floating badge*. The accessibility service draws the badge (as `TYPE_ACCESSIBILITY_OVERLAY`), so the service must be on. No "display over other apps" permission is needed.
- **Visible:** *In Instagram* (default) shows the badge only while a tracked app is in the foreground. *Always* keeps it visible everywhere.
- **Move it:** drag it. On release it snaps to the nearest side of the screen and stays clear of the status bar, navigation bar and cutout. The position is saved as *side + vertical fraction*, so it comes back in the same place after rotation or a restart. **Reset badge position** returns it to the default spot.
- **Tap:** either expands the pill for 2.5 s to show today's watch time, or opens the app. Choose which in *On tap*.
- **Hide:** long-press it, or drag it onto the **✕** at the bottom. It stays hidden until you switch it off and on again in Settings.
- **Colours** (when a daily limit is set): neutral under 75% of the limit, amber from 75% to 99%, red at or over the limit.
- **Size** S/M/L and **opacity** 40–100% have a live preview in Settings.
- The badge window only takes touches inside its own bounds (`WRAP_CONTENT`, `FLAG_NOT_FOCUSABLE`). The service removes it in `onInterrupt`, `onUnbind` and `onDestroy`.

**Daily limit.** When a limit is set, the service posts one notification per day as soon as you reach it, even if the app is closed. The app asks for notification permission when you save a limit.

---

## Updating Instagram view identifiers

Instagram renames its internal view IDs from time to time. When that happens, counting stops but the app **doesn't crash**: the detector finds no known IDs and reports "not in viewer". Every Instagram-specific detail lives in one file:

```
app/src/main/java/dev/reelscounter/app/detection/TrackedAppConfig.kt
```

| Field | Meaning |
|---|---|
| `viewerContainerIds` | If any one of these is visible, the full-screen Reels viewer is open |
| `fingerprintIds` | Nodes whose text or content description identifies the reel on screen (author, caption…). They're hashed in memory and never stored |
| `debugIdHints` | Substrings used only for debug logging |

### Find the new IDs

1. Install the **debug** APK and open Reels in Instagram.
2. Watch the detector log. When no known viewer ID is on screen, it prints matching resource IDs and class names at most every 10 s. It logs IDs only, never text.
   ```bash
   adb logcat -s ReelsDetector ReelsService
   # D ReelsDetector: No known viewer id in com.instagram.android. Candidate ids: clips_viewer_pager<ViewPager>, ...
   ```
3. To see more, dump the view hierarchy while a reel is playing:
   ```bash
   adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
   ```
   Look for `resource-id="com.instagram.android:id/…"` on the full-screen pager (that's the viewer) and on the author label (that's the fingerprint). Android Studio's **Layout Inspector** works too.
4. Put the new names in `TrackedAppConfig.kt`, using the entry name only, without the `com.instagram.android:id/` prefix. Keep the old ones as well, because different phones may have different Instagram versions.
5. Run `./gradlew installDebug`, swipe through a few reels, and check that the `ReelsService` log shows `reel #N`.

If the reel still can't be identified, detection falls back in this order: fingerprint IDs → a bounded walk (depth 6, 60 nodes) of content descriptions on the most visible page → the pager index from the last scroll event.

---

## Testing

```bash
./gradlew testDebugUnitTest
```

These are JVM unit tests, so no device is needed:

| Suite | Covers |
|---|---|
| `ReelViewTrackerTest` | fingerprint change, 500 ms debounce, rapid swipes, leave/resume, per-view cap, clock skew |
| `FingerprintTest` | stability, trimming, part boundaries, raw text never present |
| `BadgeGeometryTest` | clamping, edge snapping (incl. landscape insets), rotation-proof position, ✕ hit test |
| `TapDragClassifierTest` | touch slop, sticky drag, long-press fires once, cancel |
| `DailyCounterTest` | local-midnight reset, UTC vs local, 23 h / 25 h DST days |
| `BadgeModelTest` | 75% / 100% colour thresholds, defaults, text |
| `StatsTest` | zero-filled buckets, ISO weeks (incl. W52 → W1), month boundaries, DST hours, UTC vs local, summaries |
| `HistoryAndFormatTest` | day grouping and totals, CSV timestamps with offsets, formatting |

The time-zone tests pin `America/New_York`, so the DST cases give the same result on any machine.

**Debug tools** (debug builds only): Settings → Developer → *Open debug tools*. From there you can add 1 reel now, add 25 reels spread over today, or seed 400 days of history. The fake reels go into the real database, so the dashboard **and the floating badge** both update, and you can test everything without Instagram.

---

## Project layout

```
app/src/main/java/dev/reelscounter/app/
  MainActivity.kt, ReelsApp.kt
  service/      ReelsAccessibilityService, ServiceStatus (enabled check, settings deep links)
  detection/    TrackedAppConfig, ReelDetector, ReelViewTracker, Fingerprint
  badge/        FloatingBadgeController + pure geometry / gesture / counter / colour logic
  data/         ReelEventStore (SQLite), ReelsPrefs (settings), DataEvents
  stats/        Stats (aggregation), History (day grouping), Format
  ui/           AppRoot, AppViewModel, HistoryViewModel
    screens/    Onboarding, Dashboard, History, Settings, Debug
    components/ cards, buttons, segmented control, Canvas bar chart, badge preview
    theme/      colour tokens, spacing
app/src/main/res/
  xml/reels_accessibility_config.xml   service config
  layout/, drawable/                   floating badge views
app/src/test/                          JUnit tests
```

---

## Privacy

- **Stored:** for each reel, `app`, `viewed_at` and `duration_ms`. Nothing else.
- **Never stored or sent:** reel content, usernames, captions, messages, screenshots. The service reads on-screen labels only long enough to hash them in memory and tell whether the reel changed.
- **No network:** the app does not request the `INTERNET` permission. No backend, no analytics, no crash reporting.
- **Your control:** Settings → *Export as CSV* / *Delete all data*.
- Android backups are turned off (`allowBackup="false"`).

---

## Play Store policy

Google Play strictly limits use of `AccessibilityService`. To publish this app you would need:

- The **Accessibility API declaration** in Play Console, explaining the core use case (a digital-wellbeing counter) and why accessibility access is required.
- A **prominent in-app disclosure** and consent *before* sending users to settings. The onboarding screen is a starting point, but check it against the current policy wording.
- A privacy policy URL, and a Data safety form stating that no data is collected or shared.
- Possibly `android:isAccessibilityTool="false"` plus the matching declaration. Apps that aren't assistive tools get more scrutiny.

Policies change, so read the current [Play Console Help: Use of the AccessibilityService API](https://support.google.com/googleplay/android-developer/answer/10964491) before you submit. **v1 is meant for sideloading.**

---

## Known limitations

- **Detection depends on Instagram's private UI.** The IDs in `TrackedAppConfig.kt` are best guesses and **must be checked on a real device** with your Instagram version. See [Updating Instagram view identifiers](#updating-instagram-view-identifiers).
- **YouTube Shorts** is behind a toggle and its IDs haven't been checked. Treat it as experimental.
- A reel is counted when you **land on it**, after the 500 ms debounce. Reels you skip in under 500 ms aren't counted. Coming back to the same reel adds to its watch time without adding to the count.
- A single view is capped at 10 minutes. Turning the screen off or leaving the Reels viewer ends the current view.
- The badge's live count includes the reel you're watching right now. Its watch time is added when the view ends.
- If Android kills the service (OEM battery savers), nothing is counted until the system restarts it.
- Watch time is attributed to the day the view **started**.
- The history screen's day header totals cover only the rows loaded so far (the list loads 100 at a time).
- Android only; iOS has no equivalent of this API.
