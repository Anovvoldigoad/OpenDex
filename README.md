# OpenDex / DroidUP Android Host v0.4.2 — Trusted Display Fix

This build keeps the original DroidUP Dex Launcher APK byte-identical. No DroidUP UI, resources, wallpaper, taskbar, Start menu, or launcher DEX are modified.

## Why v0.4.0 could show the desktop but could not open Chrome/apps

v0.4.0 created the VirtualDisplay inside the normal host APK process. On modern Android that display is **untrusted**. Android blocks arbitrary third-party activities from being launched on an untrusted virtual display unless the target activity opts into embedding. DroidUP itself could be launched there by the shell command, but once DroidUP tried to open Chrome as a normal app, Android rejected the launch.

## v0.4.2 architecture

```
Normal Host Activity
      |
      | Surface over AIDL
      v
Shizuku UserService (UID shell/root)
      |
      +-- creates PUBLIC + TRUSTED virtual display
      |   using scrcpy-style display flags
      |
      +-- direct InputManager injection
      |   (shell `input` retained only as fallback)
      |
      +-- starts original DroidUP launcher on that display
      v
Original DroidUP-Dex-Launcher.apk
      |
      +-- launches Chrome / apps normally on the trusted display
```

## Expected first test

1. Start Shizuku and grant the host permission.
2. Install/repair the bundled DroidUP launcher if necessary.
3. Tap **START DROIDUP DEX**.
4. The temporary status overlay should say `Trusted display id=...` and include `uid=2000` when using Shizuku/ADB.
5. Open Chrome from DroidUP's taskbar and from Start Menu.

If display creation fails, screenshot the full `Trusted virtual display gagal:` message. Do not modify the DroidUP launcher APK.

## CI

GitHub Actions builds with JDK 17, Android SDK 36, AGP 9.3.2 and Gradle 9.5.0. Download artifact `DroidUP-AndroidHost-v0.4.2-trusted-debug`.

## Original launcher integrity

Expected SHA-256:

`d915c440bbf9d28dda5b379ce07519fe06d3904e09a1bf7b4dcfabd963a8f0f2`


## v0.4.2 lint/back-navigation fix
- Based on the trusted-display v0.4.1 branch.
- Replaced deprecated `Activity.onBackPressed()` handling with AndroidX `OnBackPressedDispatcher`.
- Back button and predictive-back gestures are routed to the DroidUP desktop display while a session is active.
- CI lint is now required to pass instead of being advisory.
- Expected artifact: `DroidUP-AndroidHost-v0.4.2-trusted-debug`.
