# Dynamic Island (Android)

An Android recreation of the iPhone **Dynamic Island** — a floating, morphing pill anchored to the
phone's camera cutout that reacts to system events (now playing, calls, timers, live activities).

> Status: **Phase 0–1 (foundation)** — project scaffold, permission onboarding, foreground overlay
> service, and the core morphing-pill animation with demo triggers. Now Playing, call/timer wiring,
> and live activities land in later phases (see the roadmap below).

## How it works

Android has no public API to "own" the camera cutout, so the island is drawn as a system overlay:

- **`IslandOverlayService`** — a foreground service (type `specialUse`) that adds a single
  `ComposeView` to the `WindowManager` using `TYPE_APPLICATION_OVERLAY`, anchored to the top center.
- **`ComposeOverlayHost`** — supplies the `Lifecycle` / `ViewModelStore` / `SavedStateRegistry`
  owners that Compose needs when there is no `Activity` (the service is not one).
- **`IslandView`** — the morphing pill. Animates size and corner radius with spring physics and
  cross-fades its contents with `AnimatedContent`.
- **`IslandController`** — a process-wide `StateFlow` that is the single source of truth for what the
  island shows, with a priority model (call > timer > media > live activity).
- **`IslandNotificationListener`** — a `NotificationListenerService`; its `ComponentName` unlocks
  `MediaSessionManager.getActiveSessions(...)` for now-playing (Phase 2) and surfaces notifications
  for live activities (Phase 4).

### Required permissions (all user-granted at runtime)

| Permission | Why | How it's granted |
|---|---|---|
| `SYSTEM_ALERT_WINDOW` | draw the pill over other apps | Settings → Draw over other apps |
| Notification access | read now-playing media + notifications | Settings → Notification access |
| `POST_NOTIFICATIONS` (13+) | the persistent service notification | runtime prompt |
| `READ_PHONE_STATE` | reflect call state | runtime prompt |

The app's home screen walks the user through each of these.

## Building

Requires the **Android SDK** (compileSdk 35) and JDK 17+. The Gradle wrapper is checked in.

```bash
# point Gradle at your SDK (or set ANDROID_HOME)
echo "sdk.dir=/path/to/Android/sdk" > local.properties

./gradlew :app:assembleDebug      # build the APK
./gradlew :app:installDebug       # install on a connected device/emulator
```

> **Note on CI / cloud build environments:** building this project downloads the Android Gradle
> Plugin and all AndroidX/Compose artifacts from **Google's Maven** (`dl.google.com` /
> `maven.google.com`). If you build in a sandboxed environment, that host must be allow-listed —
> Maven Central alone is not sufficient.

## Running / verifying

1. Install on a device (or an emulator configured with a display cutout).
2. Open the app and grant **Draw over other apps** + **Notification access**.
3. Tap **Start island** — the pill appears at the top.
4. Use the **Demo states** buttons to push Music / Call / Timer / Live activity states and watch the
   morph + expand-on-tap behaviour. **Clear** returns it to the idle pill.

## Stack

Kotlin · Jetpack Compose · `minSdk 26` / `targetSdk 35` · Coroutines `StateFlow` · DataStore (settings).

## Roadmap

- [x] **Phase 0** — scaffold, permissions onboarding, foreground service
- [x] **Phase 1** — core morphing pill + demo triggers
- [ ] **Phase 2** — Now Playing via `MediaSessionManager` (real media + transport controls)
- [ ] **Phase 3** — timer + call states from real system sources
- [ ] **Phase 4** — live activities driven by notifications (split layout)
- [ ] **Phase 5** — settings (cutout calibration, toggles), boot-restart, Play Store prep
