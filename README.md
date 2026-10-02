# OpenDex DroidUP Host v0.5.2

This revision fixes the desktop itself being decorated like a floating AOSP window.

## Important architecture correction

The virtual display now stays **WINDOWING_MODE_FULLSCREEN (1)**. DroidUP is therefore the fullscreen desktop background. The original DroidUP launcher already uses `ActivityOptions.setLaunchBounds()` when it opens apps. With Android freeform support enabled, those bounded app launches are promoted to **FREEFORM per task** instead of turning the whole display into freeform.

This matches the DroidUP/scrcpy design more closely than v0.5.0/v0.5.1, where the entire TaskDisplayArea was forced to mode 5 and the launcher itself received AOSP window decorations.

## Resolution

`Auto Native (panel HP)` reads the physical display mode and rotates it to landscape. It supports tall phone panels automatically, including 20:9 resolutions such as **2800×1260** and **2400×1080**. Manual presets include 20:9, 19.5:9 and 16:9, plus Custom mode.

The SurfaceView preserves aspect ratio rather than stretching the desktop.

## Test

1. Start Shizuku.
2. Install/repair the bundled DroidUP launcher.
3. Select `Auto Native (panel HP)`.
4. Start DroidUP Dex.
5. Confirm the desktop has **no AOSP title bar**.
6. Open Chrome from DroidUP. Chrome should open as a bounded/freeform task over the fullscreen desktop.

Artifact name: `DroidUP-AndroidHost-v0.5.2-fullscreen-native-debug`
