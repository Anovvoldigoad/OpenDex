#!/usr/bin/env python3
from pathlib import Path
import hashlib, sys, xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
errors=[]

payload=root/'app/src/main/assets/DroidUP-Dex-Launcher.apk'
expected='d915c440bbf9d28dda5b379ce07519fe06d3904e09a1bf7b4dcfabd963a8f0f2'
if not payload.is_file():
    errors.append('missing original DroidUP launcher payload')
else:
    got=hashlib.sha256(payload.read_bytes()).hexdigest()
    if got != expected:
        errors.append(f'DroidUP launcher payload changed: {got}')

for p in root.rglob('*.xml'):
    try: ET.parse(p)
    except Exception as e: errors.append(f'XML invalid {p.relative_to(root)}: {e}')

if list(root.rglob('gradle-wrapper.jar')):
    errors.append('gradle-wrapper.jar must not be committed; CI installs Gradle directly')

required=[
    root/'app/src/main/java/com/opendex/droiduphost/MainActivity.java',
    root/'app/src/main/java/com/opendex/droiduphost/DesktopActivity.java',
    root/'app/src/main/java/com/opendex/droiduphost/ShizukuHostBridge.java',
    root/'app/src/main/java/com/opendex/droiduphost/shell/HostShellService.java',
    root/'app/src/main/aidl/com/opendex/droiduphost/IHostShellService.aidl',
]
for p in required:
    if not p.is_file(): errors.append(f'missing {p.relative_to(root)}')

service=(root/'app/src/main/java/com/opendex/droiduphost/shell/HostShellService.java').read_text()
desktop=(root/'app/src/main/java/com/opendex/droiduphost/DesktopActivity.java').read_text()
aidl=(root/'app/src/main/aidl/com/opendex/droiduphost/IHostShellService.aidl').read_text()

checks={
    'trusted display flag':'VD_TRUSTED',
    'shell-owned display creation':'createDesktopDisplay',
    'scrcpy touch flag':'VD_SUPPORTS_TOUCH',
    'direct input injection':'injectInputEvent',
}
for label,needle in checks.items():
    if needle not in service: errors.append(f'missing {label}: {needle}')

if 'DisplayManager' in desktop:
    errors.append('DesktopActivity must not create the VirtualDisplay; it must be shell-owned')
if 'android.view.Surface' not in aidl:
    errors.append('AIDL must pass Surface to shell UserService')
if 'input touchscreen -d' not in (root/'app/src/main/java/com/opendex/droiduphost/ShizukuHostBridge.java').read_text():
    errors.append('slow input fallback missing')

if errors:
    print('PREFLIGHT FAILED')
    for e in errors: print(' -',e)
    sys.exit(1)
print('PREFLIGHT PASS')
print('Original DroidUP launcher SHA-256:', expected)
print('Architecture: shell-owned TRUSTED virtual display + direct InputManager injection')

# Predictive-back migration guard: do not reintroduce deprecated Activity.onBackPressed().
desktop = root / "app/src/main/java/com/opendex/droiduphost/DesktopActivity.java"
if desktop.exists():
    ds = desktop.read_text(encoding="utf-8")
    if "void onBackPressed(" in ds or "super.onBackPressed(" in ds:
        fail("Deprecated onBackPressed override/call detected; use OnBackPressedDispatcher")
    if "getOnBackPressedDispatcher()" not in ds:
        fail("DesktopActivity must register OnBackPressedDispatcher")
