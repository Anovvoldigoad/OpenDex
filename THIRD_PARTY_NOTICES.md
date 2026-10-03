# Third-party notices

## scrcpy

OpenDex v0.7 uses the official `scrcpy-server` v4.1 at runtime. The server is downloaded from the official Genymobile/scrcpy GitHub release by CI and its SHA-256 is verified before the APK is built.

- Project: https://github.com/Genymobile/scrcpy
- Version: 4.1
- Server SHA-256: `deacb991ed2509715160ffdc7907e47b4160eb30d1566217e9047fd5b8850cae`
- License: Apache License 2.0

OpenDex does not bundle the desktop SDL/FFmpeg scrcpy client. It implements the small matching Android-side client protocol needed for H.264 video, touch/key control, app start, and flex-display resize.
