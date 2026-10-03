# OpenDex DroidUP Host v0.5.6

This build keeps the original DroidUP launcher UI untouched.

## What changed
The previous build tried to force the launcher task itself to windowing mode 0 through a hidden ActivityTaskManager method. On some OEM ROMs that method is absent, producing `NoSuchMethodException:setTaskWindowingMode`, while mode 0 already resolves to fullscreen naturally.

v0.5.6 therefore leaves the launcher alone and watches only child application tasks. When a new app appears on the DroidUP display, the Shizuku shell service uses Android's `am task resizeable` and `am task resize` commands to convert that app into a bounded/resizable window.

## Expected behavior
- Desktop/launcher fills the virtual display with no diagnostic banner.
- Opening Chrome/YouTube/etc. should create a bounded window instead of replacing the desktop fullscreen.
- Display resolution selection and native-panel auto detection from v0.5.x remain.

## Build from a phone
Push the repository to GitHub and run **Build DroidUP Android Host v0.5.6 Windowing 0 Auto Freeform** from Actions.
