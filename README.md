# OpenDex Launcher v0.3.2-alpha

OpenDex is an experimental Windows-11-inspired Android desktop shell designed for phones/tablets and secondary displays. It is a real Android HOME launcher, not a web mockup.

## What is included

- Windows-inspired blue desktop wallpaper drawn by OpenDex itself.
- Bottom taskbar with centered Start button, Search, pinned Android apps, battery and clock.
- Start menu with search, pinned apps, all-apps mode and recently launched apps.
- Desktop shortcuts for files, apps, settings and the external-display touchpad.
- Long-press app menu: freeform, fullscreen and force-close.
- Optional persistent taskbar overlay above other Android apps.
- Shizuku 13.1.5 integration through a proper UserService/AIDL bridge.
- Android freeform enable command (`enable_freeform_support` + `force_resizable_activities`).
- Secondary-display desktop through Android `Presentation` / `DisplayManager`.
- App launches can target a specific display and Android windowing mode.
- Basic phone-as-touchpad mode for a connected presentation display.
- GitHub Actions CI that builds an installable debug APK without Android Studio.

All OpenDex visual assets in this repository are original. The project does not include Microsoft wallpaper files, Windows binaries, or Microsoft-owned UI assets.

## Build from a phone

The repository contains `.github/workflows/build-apk.yml`.

1. Put all repository files on GitHub with `.github` at the repository root.
2. Open **Actions** → **Build OpenDex APK** → **Run workflow**.
3. Wait for the green run.
4. Download the **OpenDex-v0.3.2-debug** artifact.
5. Extract it and install `OpenDex-v0.3.2-debug.apk`.

See [`docs/PHONE_ONLY_GITHUB.md`](docs/PHONE_ONLY_GITHUB.md) for the phone-only walkthrough.

## Gradle wrapper policy

This repository intentionally does **not** commit `gradle-wrapper.jar`. The phone-only build path uses `gradle/actions/setup-gradle@v6` with `gradle-version: 9.5.0`, then invokes the `gradle` executable supplied by the action. This avoids committing a custom binary wrapper and keeps GitHub's wrapper validation meaningful.

## CI toolchain

The workflow intentionally pins the toolchain instead of depending on whatever a runner happens to contain:

- Android Gradle Plugin: **9.3.2**
- Gradle: **9.5.0**
- JDK: **17 / Temurin**
- compileSdk / targetSdk: **36**
- Android Build Tools: **36.0.0**
- Shizuku API/provider: **13.1.5**
- Android command-line tools are set up by `android-actions/setup-android@v4`.

The CI sequence is:

```text
source preflight
    ↓
Android lintDebug
    ↓
assembleDebug
    ↓
OpenDex-v0.3.2-debug.apk
    ↓
GitHub Actions artifact
```

## First run

1. Install and start Shizuku using its normal setup method.
2. Open OpenDex → **Settings**.
3. Tap **Shizuku permission** and allow OpenDex.
4. Tap **Enable freeform mode** once.
5. Open Android's default Home-app settings and choose OpenDex.
6. Optional: grant **Display over other apps**, then enable **Desktop taskbar overlay**.
7. Optional: connect an HDMI/USB-C display. If Android exposes it as a presentation display, OpenDex will create a second desktop there.

## Interaction

- Tap an app in Start or the taskbar → launch freeform.
- Long press an app → freeform / fullscreen / close menu.
- Tap the system tray → quick settings flyout.
- Search icon → Start menu search with keyboard.
- External display + Touchpad shortcut → basic phone pointer mode.

## Shizuku implementation

OpenDex does **not** use deprecated `Shizuku.newProcess()`.

The app binds a Shizuku UserService implemented by:

```text
IUserShellService.aidl
        ↓
UserShellService
        ↓
shell/root identity supplied by Shizuku
        ↓
am / settings / input commands
```

The AIDL `destroy()` method uses the reserved Shizuku transaction identifier required for UserService cleanup.

## Important Android/OEM limitation

OpenDex can request Android freeform/windowing behavior, but Android vendors still control the actual window manager. Samsung, Xiaomi/HyperOS, OPPO/ColorOS, Vivo, and other OEMs may require their own developer/Labs multi-window options. Some apps also refuse resizing themselves.

External-display support similarly requires the device to expose the physical display to Android apps. A phone that mirrors the screen only at the hardware/vendor level cannot be turned into a true independent second Android display by an ordinary APK.

## Repository layout

```text
.github/workflows/build-apk.yml
app/
  build.gradle
  src/main/
    AndroidManifest.xml
    aidl/com/opendex/launcher/IUserShellService.aidl
    java/com/opendex/launcher/
      MainActivity.java
      SettingsActivity.java
      TouchpadActivity.java
      ShizukuController.java
      ExternalDisplayController.java
      DesktopPresentation.java
      TaskbarOverlayService.java
      BootReceiver.java
      data/
      shell/
      ui/
      util/
docs/PHONE_ONLY_GITHUB.md
scripts/preflight.py
build.gradle
settings.gradle
```

## Project status

This v0.3.2 source tree has been repository-preflight checked (XML parse, manifest component presence, required files, Java brace structure). The provided GitHub workflow is the authoritative compile/lint step because the generation sandbox does not contain an Android SDK or outbound Gradle/Maven access.

The first GitHub run therefore provides the real `lintDebug + assembleDebug` verdict and, when successful, the actual APK artifact.
