# Build status - v0.6.0

## Static checks completed in the generation environment

- Project structure present.
- AndroidManifest XML parsed successfully.
- Resource XML parsed successfully.
- Java source syntax scan: no syntax-only error patterns found.
- No oversized Java hex literals.
- No DroidUP package/asset dependency.
- No `wm set-display-windowing-mode` dependency.
- No `am task resize` dependency.
- Trusted virtual-display path present.
- Per-window TextureView path present.
- Direct display input-injection path present.
- GitHub Actions workflow YAML parsed successfully.
- Source preflight passes locally.

## Not yet claimed

`assembleDebug` cannot be executed in the generation container because an Android SDK/Gradle
Android build environment is not available there. The included GitHub Actions workflow is the first
real Android compiler test. Do not interpret this file as an `assembleDebug PASS` claim until CI has
run.
