# OpenDex v0.7.3 — scrcpy rootless engine
## v0.7.3 runtime fix

The scrcpy server now runs with `cleanup=false` so concurrent windows do not delete the shared server binary. App launch uses scrcpy's `+package` force-stop form to prevent task reuse from the phone display.


This branch is a clean rootless pivot. It does **not** use DroidUP, AOSP freeform windows, `am task resize`, or OpenDex-created `VirtualDisplay` surfaces.

## Architecture

For every OpenDex desktop window:

1. OpenDex asks a Shizuku UserService (shell UID) to run the official scrcpy-server v4.1.
2. scrcpy-server creates its own new virtual display with `vd_system_decorations=false` and `flex_display=true`.
3. The UserService connects to scrcpy's abstract Unix socket as shell and passes duplicated socket file descriptors back to the normal OpenDex app over Binder.
4. OpenDex decodes the H.264 video stream with Android `MediaCodec` directly into a `TextureView` inside the custom desktop frame.
5. Touch, keyboard, app-start, and display-resize messages use scrcpy's matching v4.1 control protocol.

There is no AOSP floating-window caption around the OpenDex frame because the host is just a normal fullscreen Activity rendering decoded video.

## Why this is different from v0.6

v0.6 created normal Android virtual displays from the Shizuku service and then embedded their surfaces. Android 16/OEM desktop policy could still decorate tasks inside those displays, producing nested windows.

v0.7 delegates creation/capture/control to the official scrcpy server instead of reimplementing that machinery.

## Requirements

- Android 8.0+ (API 26); Android 16 is the main test target.
- Shizuku running and permission granted.
- No root/Magisk.
- Hardware H.264 decoder.

## Phone-only build

Upload this folder to GitHub. Open **Actions → Build OpenDex scrcpy engine → Run workflow**. The workflow downloads the official scrcpy-server v4.1, verifies its SHA-256, builds the APK, runs lint, and uploads:

`OpenDex-ScrcpyEngine-v0.7.3-debug`

Install the APK inside that artifact ZIP.

## First runtime test

1. Start Shizuku.
2. Open OpenDex and grant permission.
3. Wait until the taskbar says `Ready · scrcpy 4.1 · shell UID 2000`.
4. Open Chrome (or another lightweight app) from Apps.
5. Expected: app content appears directly inside the OpenDex custom window.
6. Drag the custom title bar, resize from the bottom-right handle, maximize/minimize, then test touch.

If the window shows an error, screenshot the complete text. The error includes scrcpy-server logs and its detected display ID.

## Important technical note

scrcpy's client/server protocol is internal and version-specific. This implementation is deliberately pinned to scrcpy-server **v4.1** and CI verifies the exact official server checksum.


## v0.7.3 package visibility fix

`QUERY_ALL_PACKAGES` has been removed. OpenDex now declares only a launcher-activity `<queries>` intent (`MAIN` + `LAUNCHER`), which matches `AppRepository.queryIntentActivities()` and avoids the Android lint `QueryAllPackagesPermission` error while preserving the app drawer.
