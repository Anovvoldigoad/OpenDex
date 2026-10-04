# OpenDex v0.7.5 — I2405 WCT self-contained

Single-app OpenDex test build for iQOO I2405 / Android 16.

This is a **full source tree**, not an overlay and not a second probe app. It is based on the working OpenDex ScrcpyEngine v0.7.4 source and integrates the WindowContainerTransaction experiment directly into the existing Shizuku UserService.

## Runtime flow

```text
OpenDex
  -> existing Shizuku UserService (shell UID)
  -> official scrcpy-server v4.1 creates the virtual display
  -> OpenDex observes displayId
  -> I2405Freeform tries default TaskDisplayArea WCT -> FREEFORM(5)
  -> app launch uses --display <id> --windowingMode 5
  -> task-level WCT fallback + bounds
  -> normal OpenDex video/control pipeline continues
```

No separate WCT Probe APK is installed.

## GitHub build

The workflow is `.github/workflows/build-apk.yml`. It uses JDK 17, Android SDK 36 and Gradle 9.5. It downloads the official scrcpy-server v4.1 and checks SHA-256 before compiling.

Artifact:

`OpenDex-v0.7.5-I2405-WCT-debug`

APK inside artifact:

`OpenDex-v0.7.5-I2405-WCT-debug.apk`

## Device test

1. Start Shizuku and grant OpenDex permission.
2. Start OpenDex normally.
3. Open one app from the OpenDex desktop.
4. Check the session/error text if the window does not behave correctly.

Useful shell diagnostics:

```sh
logcat -c
logcat | grep -E 'OpenDex|scrcpy|WindowOrganizer|DisplayArea'
```

The meaningful runtime markers returned by OpenDex are:

- `wct=OK|TDA|...` — display-level WCT accepted.
- `wct=WARN|TDA|...` — display-level route was rejected/unavailable; task fallback still runs.
- `taskWct=OK|TASK|...|after=5` — the launched task verified as freeform.
- `taskWct=OK|TASK|...|after=1` — WCT was submitted but OriginOS forced the task back to fullscreen.

Other phone models keep the original v0.7.4 fullscreen-per-session behavior.
