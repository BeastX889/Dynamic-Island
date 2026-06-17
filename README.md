# Dynamic Island (Android)

An Android recreation of the iPhone **Dynamic Island** — a floating, morphing pill anchored to the
phone's camera cutout that reacts to system events (now playing, calls, timers, live activities).

> **Web demo:** a browser preview of the UI lives in [`docs/index.html`](docs/index.html) and is
> deployed to GitHub Pages (see the Pages link in the repo's Environments / Actions). It shows the
> morph + states; real media/calls only work in the native Android app.

> Status: **Phase 0–5 — feature-complete for v1.** Morphing overlay pill, permission onboarding,
> real Now Playing (live metadata + transport controls), real timer + call states, notification-
> driven live activities, and a settings screen (cutout calibration, per-feature toggles) with
> boot-restore. Remaining work is release signing, a real app icon, and Play Store listing assets
> (see `docs/PLAY_STORE.md`).

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
- [x] **Phase 2** — Now Playing via `MediaSessionManager` (real media + transport controls)
- [x] **Phase 3** — timer + call states from real system sources
- [x] **Phase 4** — live activities driven by notifications (split layout)
- [x] **Phase 5** — settings (cutout calibration, toggles), boot-restart, Play Store prep
  (`PRIVACY.md`, `docs/PLAY_STORE.md`)
