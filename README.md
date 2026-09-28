# Reels Counter

Android app that counts how many Instagram Reels you watch and how long you watch them, and shows daily, weekly and monthly stats. You can also turn on a floating badge that shows today's count on top of other apps.

Instagram has no API for watch history, so counting happens on the device through an Android **AccessibilityService**. **All data stays on the phone.** The app makes no network calls and stores only a timestamp, the app name, and watch duration for each reel.

> v1 is meant for sideloading. Read [Play Store policy](#play-store-policy) before you publish it.

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
Instagram UI ──events──▶ ReelsAccessibilityService (Kotlin, runs while the app is closed)
                           │  early package filter → 250 ms coalesced inspection
                           ▼
                         ReelDetector ── view IDs from TrackedAppConfig.kt
                           │  "viewer open?" + in-memory fingerprint of the visible reel
                           ▼
                         ReelViewTracker (pure) ── new fingerprint + 500 ms debounce
                           │  Started / Closed(duration)
               ┌───────────┴─────────────┐
               ▼                         ▼
     ReelEventStore (reels_native.db)   FloatingBadgeController
     framework SQLite, WAL              today's count cached in memory,
               │                         reset at local midnight
               │ drain + ack (versioned)
               ▼
     expo-sqlite reels.db ──▶ SQL day×hour rollup ──▶ TS aggregation ──▶ dashboard
```

**Why two databases.** expo-sqlite ships its own copy of SQLite, and the service uses Android's built-in copy. If two SQLite libraries open the same file in one process, they can release each other's POSIX locks, which can corrupt the database. So the service writes to its own WAL-mode buffer, and the JS side copies new rows into its database on launch, on foreground, and after every new event. Each copy is an upsert on `native_id`, so running it twice changes nothing. JS acknowledges rows by `(id, version)`, which means a watch duration written during a copy is never lost. Synced rows stay in the native buffer for 3 days, so the badge can compute today's totals while the RN app is closed.

**Stats.** SQL groups views by *local* day and hour (SQLite's `localtime` handles DST). Pure TypeScript then rolls those rows up into 30 days, 12 ISO weeks starting on Monday, and 12 months, and fills empty buckets with zero. For each period it computes total reels, watch time, average time per reel (over reels that have a duration), peak hour, and the % change from the previous period.

---

## Build and install

### Prerequisites

- Node 20+ and [Bun](https://bun.sh) (npm works too)
- **JDK 17** and the **Android SDK** (Android Studio installs both). Set `ANDROID_HOME` and put `platform-tools` on `PATH`.
- A phone running Android 8.0+ (API 26) with USB debugging on

### Development build

```bash
bun install
bun run prebuild          # expo prebuild --platform android --clean → generates ./android
bunx expo run:android     # builds the dev client, installs it and starts Metro
```

After the first install, `bun start` is enough for JS-only changes. Kotlin changes need `bunx expo run:android` again.

> Expo Go **won't work**: the app contains a custom native module. Always use the dev build.

### Release APK (local)

```bash
bun run prebuild
cd android && ./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

The generated project signs release builds with the debug keystore. That's fine for sideloading onto your own phone. For anything else, set up a real keystore in `android/app/build.gradle`, or use EAS.

### Release APK (cloud, no Android SDK needed)

```bash
bunx eas-cli build -p android --profile preview   # eas.json builds an .apk
```

---

## Enable the service

1. Open the app. The onboarding screen explains what access it needs and why.
2. Tap **Open Accessibility settings** → **Reels Counter** → turn it on → accept the system dialog.
3. Return to the app. It checks every 1.5 s and shows **Service is on**, then lets you into the dashboard.

**Toggle greyed out / "Restricted setting" (Android 13+).** Android blocks accessibility access for apps installed outside an app store. Open **Settings → Apps → Reels Counter → ⋮ (top right) → Allow restricted settings**, then enable the service. The onboarding screen has an **Open App info** shortcut. On some phones the ⋮ menu appears only after you've tried to enable the service once.

**Skip for now** takes you to the dashboard without the service. A banner stays on screen until the service is on.

Some phone makers (Xiaomi, Oppo, Vivo, Samsung and others) kill background services aggressively. If counting stops after a while, set the app's battery usage to **Unrestricted**.

---

## Floating badge

A small pill (`🎬 42`) with today's count that floats over other apps.

- **Turn it on:** Settings → Floating badge → *Show floating badge*. The accessibility service draws the badge (as `TYPE_ACCESSIBILITY_OVERLAY`), so the service must be on. No "display over other apps" permission is needed.
- **Visible:** *In Instagram* (default) shows the badge only while a tracked app is in the foreground. *Always* keeps it visible everywhere.
- **Move it:** drag it. On release it snaps to the nearest side of the screen and stays clear of the status bar, navigation bar and cutout. Its position is saved as *side + vertical fraction*, so it lands in the same place after rotation or a restart. **Reset badge position** returns it to the default spot.
- **Tap:** either expands the pill for 2.5 s to show today's watch time, or opens the app (choose in *On tap*).
- **Hide:** long-press it, or drag it onto the **✕** at the bottom. It stays hidden until you switch it off and on again in Settings.
- **Colours** (when a daily limit is set): neutral under 75% of the limit, amber from 75% to 99%, red at or over the limit.
- **Size** S/M/L and **opacity** 40–100% have a live preview in Settings.
- The badge window only takes touches inside its own bounds (`WRAP_CONTENT`, `FLAG_NOT_FOCUSABLE`). The service removes it in `onInterrupt`, `onUnbind` and `onDestroy`.

**Daily limit.** When set, the service posts one notification per day as soon as you reach the limit, even if the app is closed. The app asks for notification permission when you save a limit.

---

## Updating Instagram view identifiers

Instagram renames its internal view IDs from time to time. When that happens, counting stops, but the app **doesn't crash**: the detector finds no known IDs and reports "not in viewer". Every Instagram-specific detail lives in one file:

```
modules/reels-tracker/android/src/main/java/expo/modules/reelstracker/detection/TrackedAppConfig.kt
```

| Field | Meaning |
|---|---|
| `viewerContainerIds` | If any one of these is visible, the full-screen Reels viewer is open |
| `fingerprintIds` | Nodes whose text or content description identify the reel on screen (author, caption…). They're hashed in memory and never stored |
| `debugIdHints` | Substrings used only for debug logging |

### Find the new IDs

1. Install a **debug** build and open Reels in Instagram.
2. Watch the detector log. When no known viewer ID is on screen, it prints matching resource IDs and class names at most every 10 s. It logs IDs only, never text.
   ```bash
   adb logcat -s ReelsDetector
   # D ReelsDetector: No known viewer id in com.instagram.android. Candidate ids: clips_viewer_pager<ViewPager>, ...
   ```
3. To see more, dump the view hierarchy while a reel is playing:
   ```bash
   adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
   ```
   Look for `resource-id="com.instagram.android:id/…"` on the full-screen pager (the viewer) and on the author label (the fingerprint). Android Studio's **Layout Inspector** works too.
4. Put the new names in `TrackedAppConfig.kt` (entry name only, without the `com.instagram.android:id/` prefix). Keep the old ones as well, because different Instagram versions may be installed.
5. Rebuild with `bunx expo run:android`, swipe through a few reels, and check that `ReelsDetector`/`ReelsService` logs show `reel #N`.

If the reel still can't be identified, detection falls back in this order: fingerprint IDs → a bounded walk (depth 6, 60 nodes) of content descriptions on the most visible page → the pager index from the last scroll event.

---

## Testing

```bash
bun run test          # Jest: aggregation, week/month/DST/timezone boundaries, history grouping, CSV
bun run typecheck
bun run lint
bun run test:native   # JUnit (after prebuild, needs JDK 17): tracker, fingerprint, badge geometry,
                      # tap/drag, midnight reset, colour thresholds
```

Jest runs with `TZ=America/New_York` (see `jest.global-setup.js`), so the DST and UTC-offset cases give the same result on any machine.

**Debug screen** (dev builds only): Settings → Developer → *Open debug tools*. It can add 1 reel now, add 25 reels spread over today, or seed 400 days of history. The fake reels go through the native buffer, so both the dashboard **and the floating badge** update, and you can test everything without Instagram.

---

## Project layout

```
app/                         expo-router screens
  _layout.tsx                root stack, bootstrap, stats auto-refresh
  (tabs)/_layout.tsx         tab bar + onboarding gate
  (tabs)/index.tsx           dashboard
  (tabs)/history.tsx         per-day event log (FlashList)
  (tabs)/settings.tsx        service, tracked apps, badge, limit, export, reset
  onboarding.tsx             permission flow
  debug.tsx                  dev-only fake events
modules/reels-tracker/       local Expo module
  index.ts                   typed JS API
  android/src/main/java/expo/modules/reelstracker/
    ReelsAccessibilityService.kt
    ReelsTrackerModule.kt    JS bridge
    ReelEventStore.kt        native buffer (WAL)
    ReelsPrefs.kt            settings readable while the app is closed
    ReelsEventBus.kt         service → module events
    detection/               TrackedAppConfig, ReelDetector, ReelViewTracker, Fingerprint
    badge/                   FloatingBadgeController + pure geometry/gesture/counter logic
  android/src/main/res/      accessibility config XML, badge layouts/drawables, strings
  android/src/test/          JUnit tests
src/
  db/                        client, migrations (PRAGMA user_version), queries, native sync
  features/stats/            pure aggregation + hooks
  features/history|badge|service/
  components/                cards, charts, segmented control, badge preview
  store/                     Zustand (UI state, stats cache)
  lib/                       formatting, theme palette, CSV
__tests__/                   Jest
```

---

## Privacy

- **Stored:** for each reel, `app`, `viewed_at` and `duration_ms`. Nothing else.
- **Never stored or sent:** reel content, usernames, captions, messages, screenshots. The service reads on-screen labels only long enough to hash them in memory and tell whether the reel changed.
- **No network:** the app has no backend, no analytics and no crash reporting.
- **Your control:** Settings → *Export as CSV* / *Delete all data*.
- Android backups are turned off (`allowBackup: false`).

---

## Play Store policy

Google Play strictly limits use of `AccessibilityService`. Publishing this app would need:

- The **Accessibility API declaration** in Play Console, explaining the core use case (a digital-wellbeing counter) and why accessibility is required.
- A **prominent in-app disclosure** and consent *before* sending users to settings. The onboarding screen is a starting point, but check it against the current policy wording.
- A privacy policy URL, and a Data safety form stating that no data is collected or shared.
- Possibly `android:isAccessibilityTool="false"` plus the non-accessibility-tool declaration. Apps that aren't assistive tools get more scrutiny.

Policies change, so read the current [Play Console Help: Use of the AccessibilityService API](https://support.google.com/googleplay/android-developer/answer/10964491) before you submit. **v1 is meant for sideloading.**

---

## Known limitations

- **Detection depends on Instagram's private UI.** The IDs in `TrackedAppConfig.kt` are best guesses and **must be checked on a real device** with your Instagram version. See [Updating Instagram view identifiers](#updating-instagram-view-identifiers).
- **YouTube Shorts** is behind a toggle and its IDs haven't been checked. Treat it as experimental.
- A reel is counted when you **land on it** (after the 500 ms debounce). Reels you skip in under 500 ms aren't counted. Coming back to the same reel continues its duration without adding to the count.
- A single view is capped at 10 minutes. Turning the screen off or leaving the Reels viewer ends the current view.
- The badge's live count includes the reel you're watching right now. Its duration is added when the view ends.
- If Android kills the service (OEM battery savers), nothing is counted until the system restarts it.
- Watch time is attributed to the day the view **started**.
- The history screen's day header totals cover only the rows loaded so far (the list pages in 100 at a time).
- Android only; no iOS equivalent exists.
