# Build status - v0.5.1

Static/source checks performed locally:

- original DroidUP launcher SHA-256 guard retained
- launcher fullscreen (`--windowingMode 1`) guard added
- freeform display (`wm set-display-windowing-mode ... 5`) guard retained
- dynamic resolution guard added
- automatic phone-resolution detection guard added
- custom resolution mode guard added
- XML parse checks
- GitHub workflow pinned toolchain retained

Full Android compilation is performed by the included GitHub Actions workflow.
