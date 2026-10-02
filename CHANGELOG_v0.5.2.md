# v0.5.2

- Critical fix: virtual display TDA stays FULLSCREEN (1), not FREEFORM (5).
- DroidUP desktop no longer intentionally inherits AOSP freeform decoration.
- Third-party apps rely on DroidUP's existing ActivityOptions.setLaunchBounds() for per-task freeform launch.
- Auto resolution now prefers physical panel mode, not only current logical/render size.
- Added 2800×1260 (20:9), 2400×1080 (20:9), and 2340×1080 (19.5:9) presets.
- Auto mode supports native phone panel ratios and rotates dimensions to landscape.
