# OpenDex DroidUP Host v0.5.4 — WINDOWING 0

Eksperimen ini mengikuti permintaan untuk tidak memaksa launcher/display ke FULLSCREEN (1) atau FREEFORM (5).

## Windowing policy

- Virtual display: `wm set-display-windowing-mode -d <id> 0`
- DroidUP launcher start: `--windowingMode 0`
- Host + launcher task: `setTaskWindowingMode(taskId, 0, true)`
- Remembered task bounds dibersihkan.
- Tidak ada fallback `moveToFullscreen`.
- Child apps tetap memakai launch bounds dari APK DroidUP asli.

`0` adalah `WINDOWING_MODE_UNDEFINED`, jadi mode final dapat diwarisi dari parent / kebijakan ROM. Ini sengaja untuk menguji apakah decoration desktop hilang tanpa memaksa mode 1.

## Resolution
Native-panel auto detection dan preset/custom resolution dari v0.5.2/v0.5.3 tetap dipertahankan.

## GitHub Actions artifact
`DroidUP-AndroidHost-v0.5.4-windowing0-debug`
