# OpenDex Mi Engine v0.6.1

This version intentionally drops the DroidUP launcher and stops using Android/OEM native
freeform windows as the desktop engine.

## v0.6.1 nested-window fix

Each application now runs fullscreen inside a **private trusted virtual display**. The display is not PUBLIC/PRESENTATION and is explicitly kept in windowing mode 1. This prevents Android 16 desktop policy from adding a second AOSP freeform titlebar inside the OpenDex custom window. OpenDex itself supplies the desktop frame.


## What changed

The engine now follows the core idea used by Mi-Freeform 3:

1. OpenDex itself is one fullscreen desktop Activity.
2. Every opened Android application gets its **own trusted virtual display**.
3. The app runs fullscreen inside that private display.
4. The virtual display renders into a `TextureView` inside an OpenDex-owned desktop window.
5. OpenDex draws the title bar, move/resize/maximize/minimize/close controls itself.
6. Touch is forwarded to the correct virtual display through a Shizuku UserService.

This means there is no AOSP freeform caption bar and no dependency on
`wm set-display-windowing-mode`, `am task resize`, or DroidUP.

## Why this is a better base

Native freeform behavior is heavily OEM-dependent. On the tested phone the previous engine could
create trusted displays but child tasks still resolved to fullscreen or inherited unwanted AOSP
window decorations. In this engine the Android application is intentionally fullscreen inside its
own virtual display; only the OpenDex `TextureView` moves and resizes on the desktop.

A desktop window therefore looks conceptually like:

```
+------------------------------------------------+
| App icon   Chrome                       _  [] X |
+------------------------------------------------+
|                                                |
|       trusted virtual display surface          |
|       Chrome is fullscreen *inside here*       |
|                                                |
+---------------------------------------------///+
```

## Current prototype features

- Fullscreen phone desktop, landscape.
- Start menu listing launchable Android apps.
- Multiple desktop windows.
- Custom title bar; no native AOSP freeform title bar.
- Drag windows.
- Resize windows from the bottom-right handle.
- Maximize / restore.
- Minimize / restore from taskbar.
- Close window.
- Taskbar running-app buttons.
- Direct touch injection to each app display, with shell-input fallback.
- Trusted virtual displays created by Shizuku shell UID.
- `TextureView` composition so overlapping windows follow normal Android view Z-order.
- No hardcoded 16:9 desktop resolution: the desktop uses the phone's actual landscape viewport.
  Each app display automatically matches that window's content size.

## Requirements

- Android 8.0+ for the APK build target; the trusted-display path is primarily intended for newer Android.
- Shizuku v11+; current testing should use the current Shizuku release.
- Shizuku must be running and OpenDex must be authorized.

No root is required by this OpenDex implementation.

## Build from a phone with GitHub Actions

1. Extract this ZIP.
2. Upload/push the project contents to a GitHub repository.
3. Open **Actions -> Build OpenDex Mi Engine v0.6.1 -> Run workflow**.
4. Download artifact **OpenDex-MiEngine-v0.6.1-debug**.
5. Extract the artifact ZIP and install `OpenDex-MiEngine-v0.6.1-debug.apk`.

The CI intentionally builds and uploads the APK before running advisory lint so a non-runtime lint
finding cannot hide the installable artifact during early engine testing.

## First test

1. Start Shizuku.
2. Open OpenDex Desktop.
3. Grant Shizuku permission.
4. Tap Start (`⊞`).
5. Open Chrome.
6. Chrome should render **inside an OpenDex-owned movable window**.
7. Open a second app. It should get a second independent window.
8. Move one window over the other and verify the front window renders correctly.
9. Resize the window from its bottom-right grip.

If a window says `Display gagal`, screenshot the full text. If it says `Launch gagal`, screenshot the
full launch result. Those two diagnostics distinguish display-policy failures from app-launch-policy
failures.

## Important architecture note

The upstream Mi-Freeform 3 project injects a display adapter/service into Android `system_server`
and ships system/Magisk-oriented components. This project does **not** install that module. The
OpenDex implementation keeps the desktop/window UI in a normal APK and moves only display creation
and input injection into a Shizuku UserService running with shell privileges.

## License

GPL-3.0. See `LICENSE` and `THIRD_PARTY_NOTICES.md`.
