# Build status — v0.5.2

Static audit in this environment: PASS.

Validated:
- trusted shell-owned VirtualDisplay path present
- per-display TaskDisplayArea is forced to FULLSCREEN (1), not FREEFORM (5)
- DroidUP original launcher APK hash unchanged
- per-app freeform relies on DroidUP's existing ActivityOptions.setLaunchBounds()
- native panel detection scans supported display modes and chooses the highest pixel mode
- presets include 2800x1260 and 2400x1080 (20:9), 2340x1080 (19.5:9), plus 16:9 and Custom
- XML parse PASS
- GitHub Actions workflow present
- preflight PASS

Runtime still needs hardware verification because OEM WindowManager behaviour can differ.
