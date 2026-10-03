# OpenDex I2405 WCT Probe v0.1

Target khusus:

- vivo / iQOO
- model + device: `I2405`
- Android 16 / API 36
- tested context: OriginOS 6 family / firmware `PD2453F_EX_A_16.1.20.5.W20.V000L1`
- Shizuku, no root, no scrcpy

## Kenapa build ini ada

Dextop 1.7.0 berhasil membuat display sekunder, mengirim input, dan meluncurkan app ke display itu pada I2405. Tetapi OriginOS mengubah request freeform menjadi fullscreen. `wm set-display-windowing-mode -d <id> 5` juga dibaca kembali sebagai fullscreen.

Probe ini menguji jalur yang belum diuji: `WindowContainerTransaction` (WCT) langsung pada task yang berjalan di display Dextop.

## Cara build

Push source ini ke GitHub. Workflow **Build I2405 WCT Probe** otomatis menghasilkan artifact APK.

Tidak ada custom `gradle-wrapper.jar`; workflow menggunakan Gradle 9.5 lewat `gradle/actions/setup-gradle`.

## Cara test

1. Pastikan Shizuku running.
2. Install APK hasil GitHub Actions.
3. Buka **OpenDex I2405 WCT Probe**.
4. Tap **Grant Shizuku** dan approve.
5. Tap **START PROBE + OPEN DEXTOP**.
6. Di Dextop pakai `1920x1080 / 240 dpi`, Landscape, lalu Start.
7. Buka Chrome, lalu YouTube.
8. Tunggu sekitar 10 detik.
9. Stop session Dextop dan kembali ke Probe.
10. Tap **REFRESH RESULT**.
11. Kirim file `/storage/emulated/0/Download/OpenDex_I2405_WCT_Probe.txt`.

## Verdict yang mungkin

- `SUCCESS_NATIVE_FREEFORM`: WCT berhasil, ini jalur backend yang harus dimasukkan ke fork Dextop/OpenDex.
- `ORIGINOS_COERCED_FULLSCREEN`: WCT diterima tetapi Vivo memaksa task kembali fullscreen.
- `WCT_BLOCKED_BY_PERMISSION`: shell/Shizuku tidak diberi `MANAGE_ACTIVITY_TASKS` untuk operasi ini.
- `NO_TARGET_TASKS_SEEN`: worker tidak melihat task app pada display Dextop.
- `WCT_PROBE_EXCEPTION`: ada incompatibility API/reflection; log akan berisi penyebabnya.

## Catatan

Build ini hanya probe. Ia tidak mengganti renderer Dextop, tidak membuat stream video, dan tidak memakai scrcpy. Kalau WCT lolos, tahap berikutnya adalah memasukkan backend yang sama ke rule sempit `I2405 + SDK36` pada fork Dextop sesuai prinsip device-specific upstream.
