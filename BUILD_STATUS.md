# Build status

## Local generation environment

- Repository preflight: PASS
- Android XML parsing: PASS
- Manifest component file checks: PASS
- GitHub workflow file present: PASS
- Full Android compile in generation sandbox: NOT EXECUTED

Reason: the sandbox has a JDK but no Android SDK, and DNS/outbound dependency downloads for Gradle/Maven are unavailable.

## Authoritative build path

Run `.github/workflows/build-apk.yml` on GitHub Actions. It installs/pins the required toolchain, runs `scripts/preflight.py`, `:app:lintDebug`, and `:app:assembleDebug`, then uploads the installable debug APK.

## Final static audit

- `scripts/preflight.py`: PASS after the v0.3 source freeze.
- Project-local Java import/duplicate-class check: PASS (17 top-level source classes).
- Java parser-shape scan: PASS after fixing a color literal typo; remaining local `javac` errors are only expected missing Android/Shizuku SDK classes because this sandbox has no Android SDK.
- Deprecated `Shizuku.newProcess`: not used.
- Full `assembleDebug`: intentionally delegated to `.github/workflows/build-apk.yml`, because the generation sandbox cannot reach Gradle/Maven/Android SDK repositories.

The GitHub Actions result is the authoritative build result. If CI finds an OEM/API compile issue, use the Action log as the exact failure source.
