# OpenDex DroidUP Host v0.5.1

This build keeps the bundled original DroidUP launcher APK byte-identical and fixes two problems seen on-device:

1. The DroidUP launcher/desktop is explicitly launched in **FULLSCREEN windowing mode (1)** while the virtual display itself remains **FREEFORM (5)**. Normal apps therefore stay freeform above the desktop instead of the launcher itself becoming a floating window.
2. The virtual desktop resolution is no longer hardcoded to 1920x1080. The host supports automatic phone-screen detection, common presets, and custom width/height/DPI.

## Resolution modes

- Auto (phone screen/current rendering resolution)
- 1920x1080
- 1600x900
- 1366x768
- 1280x720
- Custom width / height / DPI

Auto DPI keeps roughly the same UI scale as 1080p at 240 dpi. Manual resolutions preserve aspect ratio in the host view (letterboxing instead of stretching).

## Architecture

- Original DroidUP launcher UI: unchanged
- Shizuku UserService creates a trusted virtual display
- Display TaskDisplayArea is set to `WINDOWING_MODE_FREEFORM (5)`
- DroidUP launcher is started with `am start --display <id> --windowingMode 1 ...`
- Apps opened by DroidUP inherit/use freeform behavior on that display
- Input uses direct InputManager injection with shell-command fallback

## About the AOSP freeform title bar

The native title bar/border shown around third-party apps is drawn by Android's WindowManager Shell/SystemUI, not by the DroidUP launcher. A normal APK+Shizuku host cannot simply reskin those decorations. Replacing them requires either:

- a SystemUI/framework/ROM patch, or
- a custom task-organizer/window-shell implementation that owns task surfaces and draws its own decorations.

v0.5.1 deliberately does not fake custom borders on top of native windows; first priority is correct desktop/task behavior.

## GitHub Actions

Run **Build DroidUP Android Host v0.5.1 Fullscreen + Resolution**. Artifact:

`DroidUP-AndroidHost-v0.5.1-fullscreen-resolution-debug`
