# Build OpenDex from a phone with GitHub Actions

You do not need Android Studio or a PC.

## Easiest path

1. Create a GitHub repository, for example `OpenDexLauncher`.
2. Put this repository's files at the repository root (the `.github` folder must stay at root).
3. Open the repository in a browser and choose **Actions**.
4. Open **Build OpenDex APK**.
5. Tap **Run workflow**.
6. After the run is green, open that run.
7. Under **Artifacts**, download **OpenDex-v0.3.2-debug**.
8. Extract the artifact ZIP on the phone and install `OpenDex-v0.3.2-debug.apk`.

The APK produced by `assembleDebug` is signed with the Android debug key and is directly installable for testing.

## After installing

1. Start Shizuku.
2. Open OpenDex > Settings > Shizuku permission and grant it.
3. Tap **Enable freeform mode** once.
4. Choose OpenDex as the Home app.
5. Optional: grant **Display over other apps** and enable the persistent taskbar overlay.
6. Optional: connect HDMI/USB-C display; OpenDex will create a desktop on the presentation display when Android exposes one.

## If CI is red

Open the failed step and copy its error log. The workflow deliberately runs `preflight`, Android lint, then `assembleDebug`, so compile errors are shown before an APK is uploaded.

## Optional: one-command publish from Termux

After extracting the source ZIP and logging into GitHub CLI (`gh auth login`), run from the repository folder:

```sh
bash scripts/publish-termux.sh OpenDexLauncher public
```

The script initializes Git, commits the source, creates/pushes the GitHub repository, and leaves the build to GitHub Actions. It never asks you to put a GitHub token inside the source tree.
