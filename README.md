# OpenDex DroidUP Host v0.5.0 — FREEFORM DISPLAY

The bundled **original DroidUP Dex Launcher APK is unchanged**. This host only replaces the PC/scrcpy side.

## Why v0.4.x looked wrong

The test video proved that the virtual display was trusted but its root TaskDisplayArea was still fullscreen. Chrome therefore occupied the entire desktop instead of becoming a movable/resizable window.

## v0.5.0

1. Create the trusted virtual display from Shizuku shell UID.
2. Run `wm set-display-windowing-mode -d <id> 5` (`5 = WINDOWING_MODE_FREEFORM`).
3. Probe the display mode.
4. Lock the desktop landscape and ignore per-app orientation requests.
5. Only then launch the original `com.levelup.droiduplauncher/.MainActivity`.

If the OEM ROM rejects the per-display freeform command, the host stops and shows the diagnostic instead of opening the known-broken fullscreen session.

## GitHub Actions artifact

`DroidUP-AndroidHost-v0.5.0-freeform-debug`
