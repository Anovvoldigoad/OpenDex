#!/usr/bin/env python3
from pathlib import Path
import hashlib
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
errors = []

def fail(message):
    errors.append(message)

payload = root / 'app/src/main/assets/DroidUP-Dex-Launcher.apk'
expected = 'd915c440bbf9d28dda5b379ce07519fe06d3904e09a1bf7b4dcfabd963a8f0f2'
if not payload.is_file():
    fail('missing original DroidUP launcher payload')
else:
    got = hashlib.sha256(payload.read_bytes()).hexdigest()
    if got != expected:
        fail(f'DroidUP launcher payload changed: {got}')

for path in root.rglob('*.xml'):
    try:
        ET.parse(path)
    except Exception as exc:
        fail(f'XML invalid {path.relative_to(root)}: {exc}')

if list(root.rglob('gradle-wrapper.jar')):
    fail('gradle-wrapper.jar must not be committed; CI installs Gradle directly')

required = [
    root / 'app/src/main/java/com/opendex/droiduphost/MainActivity.java',
    root / 'app/src/main/java/com/opendex/droiduphost/DesktopConfig.java',
    root / 'app/src/main/java/com/opendex/droiduphost/DesktopActivity.java',
    root / 'app/src/main/java/com/opendex/droiduphost/ShizukuHostBridge.java',
    root / 'app/src/main/java/com/opendex/droiduphost/shell/HostShellService.java',
    root / 'app/src/main/aidl/com/opendex/droiduphost/IHostShellService.aidl',
]
for path in required:
    if not path.is_file():
        fail(f'missing {path.relative_to(root)}')

service_path = root / 'app/src/main/java/com/opendex/droiduphost/shell/HostShellService.java'
desktop_path = root / 'app/src/main/java/com/opendex/droiduphost/DesktopActivity.java'
bridge_path = root / 'app/src/main/java/com/opendex/droiduphost/ShizukuHostBridge.java'
aidl_path = root / 'app/src/main/aidl/com/opendex/droiduphost/IHostShellService.aidl'

service = service_path.read_text(encoding='utf-8') if service_path.exists() else ''
desktop = desktop_path.read_text(encoding='utf-8') if desktop_path.exists() else ''
bridge = bridge_path.read_text(encoding='utf-8') if bridge_path.exists() else ''
aidl = aidl_path.read_text(encoding='utf-8') if aidl_path.exists() else ''

for label, needle in {
    'trusted display flag': 'VD_TRUSTED',
    'shell-owned display creation': 'createDesktopDisplay',
    'scrcpy touch flag': 'VD_SUPPORTS_TOUCH',
    'direct input injection': 'injectInputEvent',
    'per-display fullscreen mode': 'wm set-display-windowing-mode -d ',
    'landscape orientation guard': 'wm set-ignore-orientation-request -d ',
}.items():
    if needle not in service:
        fail(f'missing {label}: {needle}')

if 'DisplayManager' in desktop:
    fail('DesktopActivity must not create the VirtualDisplay; it must be shell-owned')
if 'android.view.Surface' not in aidl:
    fail('AIDL must pass Surface to shell UserService')
if 'input touchscreen -d' not in bridge:
    fail('slow input fallback missing')

if '--windowingMode 1' not in bridge:
    fail('DroidUP launcher must be launched fullscreen with --windowingMode 1')
if 'startOriginalLauncherFullscreen' not in bridge:
    fail('fullscreen launcher entry point missing')
if 'DesktopConfig' not in desktop:
    # DesktopActivity receives the selected resolution as extras; MainActivity owns DesktopConfig.
    pass
main_path = root / 'app/src/main/java/com/opendex/droiduphost/MainActivity.java'
main = main_path.read_text(encoding='utf-8') if main_path.exists() else ''
if 'DesktopConfig.detectPhysicalMode' not in main:
    fail('automatic native panel resolution detection missing')
if 'MODE_CUSTOM' not in (root / 'app/src/main/java/com/opendex/droiduphost/DesktopConfig.java').read_text(encoding='utf-8'):
    fail('custom resolution mode missing')
if 'VD_WIDTH' in desktop or 'VD_HEIGHT' in desktop:
    fail('DesktopActivity must not hardcode virtual display resolution')

# Back/key routing guards.
if 'void onBackPressed(' in desktop or 'super.onBackPressed(' in desktop:
    fail('deprecated Activity.onBackPressed detected; use OnBackPressedDispatcher')
if 'getOnBackPressedDispatcher()' not in desktop:
    fail('DesktopActivity must register OnBackPressedDispatcher')
if '@Override public boolean dispatchKeyEvent' in desktop or 'super.dispatchKeyEvent(' in desktop:
    fail('ComponentActivity dispatchKeyEvent override/call must not be used; AndroidX marks it RestrictedApi')
if 'surfaceView.setOnKeyListener' not in desktop:
    fail('hardware keyboard forwarding must use the focused SurfaceView OnKeyListener')

workflow = (root / '.github/workflows/build-apk.yml').read_text(encoding='utf-8')
if 'ubuntu-24.04' not in workflow:
    fail('CI runner must be pinned to ubuntu-24.04')
if 'actions/checkout@v7' not in workflow:
    fail('CI must use Node-24 checkout action')
if 'actions/upload-artifact@v7' not in workflow:
    fail('CI must use Node-24 upload-artifact action')

if errors:
    print('PREFLIGHT FAILED')
    for error in errors:
        print(' -', error)
    sys.exit(1)

print('PREFLIGHT PASS')
print('Original DroidUP launcher SHA-256:', expected)
print('Architecture: trusted FULLSCREEN display + per-app freeform launch bounds + native panel resolution')
print('Input: SurfaceView key listener + AndroidX OnBackPressedDispatcher')
print('CI: ubuntu-24.04 + Node-24 GitHub actions')
