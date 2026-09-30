This is a native Android app written in Kotlin with Jetpack Compose (single `app` module, Gradle Kotlin DSL). It counts Instagram Reels on-device through an AccessibilityService.

## Commands

```bash
./gradlew assembleDebug       # build app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug        # build + install on a connected device
./gradlew testDebugUnitTest   # JVM unit tests
./gradlew lintDebug           # Android lint
```

Run the unit tests before declaring a task done.

## Layout

- `app/src/main/java/dev/reelscounter/app/` — `service/`, `detection/`, `badge/`, `data/`, `stats/`, `ui/`
- `detection/TrackedAppConfig.kt` is the only file that should change when Instagram's view IDs change.
- Keep pure logic (tracker, geometry, gestures, stats, formatting) free of Android imports so it stays JVM-testable.

## Rules

- Dependencies and versions live in `gradle/libs.versions.toml`; add new ones there.
- Keep the dependency list small (no chart, navigation or DI libraries without a clear need).
- Never store or log reel content, usernames or captions. Only timestamps, app key and duration are persisted.
- The app has no `INTERNET` permission; don't add network code.
