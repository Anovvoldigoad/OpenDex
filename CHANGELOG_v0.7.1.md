# OpenDex ScrcpyEngine v0.7.1

- Fix Android lint `QueryAllPackagesPermission`.
- Remove broad `QUERY_ALL_PACKAGES` permission.
- Add launcher-intent package visibility via manifest `<queries>`.
- Preserve app drawer discovery through `PackageManager.queryIntentActivities(MAIN + LAUNCHER)`.
- Add CI/preflight regression guard.
