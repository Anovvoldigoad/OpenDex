# v0.5.6 — Windowing 0 + Auto Freeform

- Keeps the trusted virtual display at `WINDOWING_MODE_UNDEFINED (0)`.
- Stops forcing the DroidUP launcher task through hidden `setTaskWindowingMode` APIs.
- Removes the runtime error banner caused by OEM builds that do not expose that hidden method.
- Adds a shell-side task watcher that detects newly opened non-launcher tasks on the DroidUP display.
- Uses AOSP `am task resizeable` + `am task resize` to force only app tasks into bounded/resizable windows.
- Preserves DroidUP requested task bounds when the OEM exposes them; otherwise uses centered staggered desktop bounds.
- Keeps original DroidUP launcher APK byte-identical.
