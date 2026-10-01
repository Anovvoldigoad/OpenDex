# OpenDex v0.3.3-alpha — Build status

## GitHub Actions wrapper-validation failure fixed

The v0.3.1 CI reached `gradle/actions/setup-gradle@v6` and failed **before Android compilation** because the repository contained a custom bootstrap file named `gradle/wrapper/gradle-wrapper.jar`.

`setup-gradle` automatically validates every `gradle-wrapper.jar` in the checked-out repository against Gradle's known official checksums. The custom OpenDex bootstrap JAR was therefore correctly rejected as unknown.

v0.3.3 removes the custom wrapper completely:

- no `gradle-wrapper.jar`
- no bootstrap wrapper source
- no `gradlew` / `gradlew.bat`
- GitHub Actions installs **Gradle 9.5.0** directly through `gradle/actions/setup-gradle@v6`
- build commands use `gradle`, not `./gradlew`
- `scripts/preflight.py` now fails if any `gradle-wrapper.jar` is reintroduced

## CI toolchain

- Android Gradle Plugin: **9.3.2**
- Gradle: **9.5.0**
- JDK: **17**
- compileSdk / targetSdk: **36**
- Android Build Tools: **36.0.0**
- Shizuku API/provider: **13.1.5**

AGP 9.3 requires Gradle 9.5.0 and JDK 17, so these versions are intentionally paired.

## Validation performed in this environment

- repository preflight: PASS
- no Gradle wrapper JAR present: PASS
- XML parse: PASS
- manifest structure: PASS
- workflow YAML parse: PASS
- Termux publish script syntax: PASS
- duplicate Java class scan: PASS
- project-local import scan: PASS
- Java parser/syntax smoke check: no source syntax/literal errors detected before expected missing Android SDK symbols
- source hash manifest regenerated

A full Android build is still authoritative only in GitHub Actions because this sandbox has no Android SDK/Maven build environment.

## v0.3.3 compile fix

GitHub Actions reached `compileDebugJavaWithJavac` and exposed a real source error: `BatteryManager` was imported from `android.app`. Android defines it in `android.os`. v0.3.3 fixes that import. The `Scope.LIBRARY_GROUP_PREFIX` messages are compiler warnings from annotation metadata and are not the build-stopping error.

The workflow now builds and uploads the APK before running lint. Lint remains enabled as an advisory check so a non-critical lint finding cannot prevent a successfully compiled APK from being downloaded.
