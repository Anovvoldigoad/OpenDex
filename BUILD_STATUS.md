# OpenDex v0.3.1-alpha — Build status

## GitHub Actions failure fixed

The first CI run did **not** reach Java/AIDL compilation. It failed while `android-actions/setup-android@v4` attempted to install `platforms;android-37`:

```text
Warning: Failed to find package 'platforms;android-37'
Error: sdkmanager failed with exit code 1
```

This revision changes the build target to the stable Android 16 SDK platform:

- compileSdk: **36**
- targetSdk: **36**
- build-tools: **36.0.0**
- Android Gradle Plugin: **9.3.2**
- Gradle: **9.5.0**
- JDK: **17**

AGP 9.3 supports API levels up to 37, so API 36 is fully supported. AGP 9.3.2 is used because it includes fixes over 9.3.0, including a documented JDK 17 lint crash fix.

## Local validation performed in the generation environment

- repository preflight: PASS
- XML parse: PASS
- manifest structure: PASS
- workflow YAML parse: PASS
- Termux publish script syntax: PASS
- duplicate Java class scan: PASS
- project-local import scan: PASS
- source hash manifest regenerated

A full Android compile cannot be executed in the generation sandbox because the Android SDK/Maven toolchain is not installed there. The next GitHub Actions run is therefore the authoritative compile test.
