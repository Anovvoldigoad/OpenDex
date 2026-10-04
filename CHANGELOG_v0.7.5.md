# OpenDex v0.7.5 — iQOO I2405 WCT self-contained

- Base: OpenDex ScrcpyEngine v0.7.4 no-animation.
- One OpenDex APK only; no separate WCT probe application.
- Keeps the existing Shizuku UserService and official scrcpy-server v4.1 engine.
- Stops deleting the user's global freeform settings during startup.
- On I2405 / Android 16 (SDK 36):
  - requests system decorations for the scrcpy virtual display;
  - attempts to set the display's default TaskDisplayArea to FREEFORM(5) with WindowContainerTransaction;
  - launches the selected app with `--windowingMode 5`;
  - falls back to task-level WindowContainerTransaction + bounds when display-area registration is rejected or already owned by the OEM/SystemUI;
  - reports before/after task windowing mode in the existing session diagnostics.
- Other device models retain the original v0.7.4 fullscreen session behavior.
- CI uses `android-actions/setup-android@v4` with only `platform-tools`, `platforms;android-36`, and `build-tools;36.0.0`; the removed legacy SDK package `tools` is never requested.
