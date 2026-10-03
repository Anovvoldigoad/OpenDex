# Build status — v0.5.6

Static source audit prepared for GitHub Actions.

Expected CI artifact: `DroidUP-AndroidHost-v0.5.6-windowing0-auto-freeform-debug`

Runtime target:
- DroidUP launcher: inherited/fullscreen background (display mode 0)
- Child apps: detected after launch and converted to bounded/resizable tasks using `am task resize`
- No hidden `setTaskWindowingMode` call is required for launcher startup
