# v0.5.5

- Keep WINDOWING_MODE_UNDEFINED (0).
- Fix GitHub Actions javac error in HostShellService.
- Replace direct hidden `RunningTaskInfo.getDisplayId()` references with runtime reflection.
- Reflection fallback reads hidden `displayId` field if method lookup is unavailable on the OEM framework.
- No DroidUP launcher UI changes.
