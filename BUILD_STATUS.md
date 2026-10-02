# Build status — v0.5.5

Static patch/audit completed in the working environment.

Key invariant: launcher/display/task requests use WINDOWING_MODE_UNDEFINED (0), not 1 or 5.
Full Android build remains verified by the included GitHub Actions workflow.

- Fixed CI javac failure: direct hidden `RunningTaskInfo.getDisplayId()` reference removed.
- Task display ID is resolved reflectively at runtime; no public-SDK hidden API compile dependency.
