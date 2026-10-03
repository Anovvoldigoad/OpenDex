# v0.6.1 — Private per-app displays

This build fixes the nested-window failure seen in v0.6.0.

- Per-app virtual displays are now PRIVATE (no PUBLIC / PRESENTATION flags).
- Every per-app display is explicitly kept in WINDOWING_MODE_FULLSCREEN (1).
- Removed the global `force_resizable_activities=1` mutation.
- App launch uses a separate task and fullscreen mode inside its private display.
- The desktop custom frame remains the only visible window decoration.
- DroidUP is not used.

Why: on Android 16 a public/presentation virtual display can participate in desktop-windowing policy. That caused the app to receive an AOSP freeform caption inside OpenDex's own custom window, producing the nested-window screenshot.
