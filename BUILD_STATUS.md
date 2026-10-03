# Build status — OpenDex scrcpy engine v0.7.1

Static audit completed in the generation environment:

- AndroidManifest/resources XML: PASS
- GitHub Actions YAML parse: PASS
- local Java source/import audit: PASS (AIDL-generated interface excluded by design)
- scrcpy protocol constants checked against v4.1/current upstream: PASS
- old DroidUP/native-AOSP-freeform code path: absent
- initial window bounds are applied before TextureView attachment to avoid oversized first encoder session
- MediaCodec is configured with an adaptive 2800x2800 envelope for flex-display resize

A full Android compile is intentionally delegated to GitHub Actions because this environment does not have the Android SDK/Maven toolchain.

The source ZIP intentionally does not include the scrcpy-server binary. CI downloads the official `scrcpy-server-v4.1`, verifies SHA-256
`deacb991ed2509715160ffdc7907e47b4160eb30d1566217e9047fd5b8850cae`, then runs preflight, assembleDebug and lintDebug.


### v0.7.1 CI fix
- Removed `android.permission.QUERY_ALL_PACKAGES`.
- Added manifest `<queries>` for `ACTION_MAIN` + `CATEGORY_LAUNCHER`.
- Added preflight guard rejecting future `QUERY_ALL_PACKAGES` regressions.
- GitHub artifact renamed to `OpenDex-ScrcpyEngine-v0.7.1-debug`.
- CI uploads the compiled APK before lint, so a future lint-only failure will not hide a successful build artifact.
