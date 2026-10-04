# Build / test status — OpenDex v0.7.5 I2405 WCT

## Source baseline

This tree is based directly on `OpenDex-ScrcpyEngine-v0.7.4-NO-ANIM-SOURCE.zip`, not Dextop and not the standalone WCT probe project.

## Validation completed here

- Base source recovered from the persistent project source archive.
- `I2405Freeform.java` compiled with Java 17 against focused Android/Shizuku compile stubs: PASS.
- Modified `ScrcpyShellService.java` + `I2405Freeform.java` compiled together with Java 17 focused stubs: PASS.
- XML/source layout retained from the v0.7.4 source.
- Source preflight reaches only the expected missing `scrcpy-server-v4.1` asset locally; GitHub Actions downloads and SHA-256 verifies that asset before running preflight.
- CI workflow no longer uses `setup-android@v3` and does not request the obsolete SDK package `tools`.

## Runtime target

Device: iQOO I2405, Android 16 / SDK 36.

Expected session diagnostics should include `wct=` and `taskWct=`. A successful task fallback ends with `after=5`. If WCT reports applied but `after=1`, OriginOS is coercing the task back to fullscreen after the framework transaction.
