# OpenDex ScrcpyEngine v0.7.2

Runtime fixes from the first real multi-window test:

- Fixes the second-window `scrcpy-server missing · missing session ...` failure.
  - scrcpy unlinks the on-device server shortly after startup when `cleanup=true`.
  - OpenDex now runs every scrcpy session with `cleanup=false` so multiple concurrent windows can reuse the same verified server binary.
  - the bridge also performs one automatic server reinstall+retry if an older session already removed the file.
- START_APP now sends `+<package>` (the official scrcpy force-stop-before-start form).
  - this prevents an already-running phone task from being reused on the wrong display.
  - the app starts fresh inside the session's virtual display.
- No DroidUP, native AOSP freeform, root, or Magisk path was reintroduced.
- GitHub artifact: `OpenDex-ScrcpyEngine-v0.7.2-debug`.
- Distribution ZIP is root-flat: extracting it directly into a Git repository produces `.github/`, `app/`, `scripts/`, `build.gradle`, etc. with no wrapper directory.
