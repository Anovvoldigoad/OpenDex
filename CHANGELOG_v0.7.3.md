# OpenDex ScrcpyEngine v0.7.3

- Fix GitHub Actions javac failure caused by `Process.pid()`.
- Child PID was diagnostic-only and has been removed.
- Add preflight guard so `Process.pid()` cannot regress.
- Keep v0.7.2 multi-session fixes: `cleanup=false` and `START_APP` using `+package`.
- Source ZIP remains root-flat for direct extraction into an existing Git repository.
