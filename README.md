# OpenDex DroidUP Host v0.4.0

Eksperimen ini **tidak mengubah UI DroidUP Dex Launcher**. APK launcher asli dari paket `DroidUP-Dex-v1.6-win-x64.zip` dibundel verbatim dan SHA-256-nya diverifikasi saat GitHub Actions build:

`d915c440bbf9d28dda5b379ce07519fe06d3904e09a1bf7b4dcfabd963a8f0f2`

Tujuan v0.4.0 cuma memindahkan peran PC/scrcpy ke Android:

1. Host meminta izin Shizuku.
2. Host menginstall APK launcher DroidUP asli melalui shell Shizuku.
3. Host menjalankan setting global yang sama dengan aplikasi Windows DroidUP:
   - `enable_freeform_support=1`
   - `force_resizable_activities=1`
   - `force_desktop_mode_on_external_displays=0`
4. Host membuat public own-content-only VirtualDisplay 1920x1080/240.
5. Host menjalankan `com.levelup.droiduplauncher/.MainActivity` pada display tersebut.
6. Surface virtual display ditampilkan fullscreen di HP.
7. Tap/swipe dari Surface diteruskan ke display virtual dengan `input -d <displayId>` melalui Shizuku.

## Yang sengaja TIDAK diubah

- `DroidUP-Dex-Launcher.apk`
- wallpaper DroidUP
- Start menu DroidUP
- taskbar DroidUP
- desktop shortcuts DroidUP
- window controls Normal/Compact/Maximize/Minimize/Close milik DroidUP

Host memiliki layar setup kecil sendiri, tetapi setelah desktop dimulai, konten yang tampil berasal dari launcher DroidUP asli.

## Build cuma pakai HP

Upload isi folder ini ke repo GitHub, lalu buka **Actions → Build DroidUP Android Host → Run workflow**.

Artifact yang harus keluar:

`DroidUP-AndroidHost-v0.4.0-debug`

Di dalam artifact ada `DroidUP-AndroidHost-v0.4.0-debug.apk`.

## Test

1. Install dan start Shizuku.
2. Install APK host hasil GitHub Actions.
3. Buka `DroidUP Dex Host`.
4. Beri izin Shizuku.
5. Tekan **INSTALL / REPAIR DROIDUP LAUNCHER**.
6. Pastikan status DroidUP Launcher = installed.
7. Tekan **START DROIDUP DEX**.
8. HP berpindah landscape dan host membuat virtual display.
9. UI DroidUP original seharusnya muncul di surface tersebut.
10. Test Start menu, buka aplikasi, Normal/Compact/Maximize, Minimize dan Close.

## Batas v0.4.0

Ini adalah proof-of-mechanism. Scrcpy asli membuat virtual display dari proses shell dan mempunyai input injection yang lebih canggih. v0.4.0 terlebih dahulu menguji apakah public VirtualDisplay yang dibuat host diterima firmware target dan apakah DroidUP dapat menjalankan aplikasi di display itu.

Input v0.4.0 meneruskan tap dan swipe setelah gesture selesai; belum ada mouse hover, continuous drag, multi-touch, clipboard, atau IME routing khusus.

Jika layar error muncul, kirim screenshot teks error-nya. Jika UI DroidUP muncul tetapi aplikasi yang dibuka fullscreen/tidak bisa resize, itu berarti virtual display berhasil dan masalah berikutnya khusus freeform/window policy OEM.
