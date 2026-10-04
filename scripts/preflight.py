#!/usr/bin/env python3
from pathlib import Path
import hashlib
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors = []

required = [
    'app/src/main/AndroidManifest.xml',
    'app/src/main/java/com/opendex/desktop/ScrcpySession.java',
    'app/src/main/java/com/opendex/desktop/AppWindowView.java',
    'app/src/main/java/com/opendex/desktop/ShizukuBridge.java',
    'app/src/main/java/com/opendex/desktop/shell/ScrcpyShellService.java',
    'app/src/main/java/com/opendex/desktop/shell/I2405Freeform.java',
    'app/src/main/aidl/com/opendex/desktop/IScrcpyShellService.aidl',
    '.github/workflows/build-apk.yml',
]
for rel in required:
    if not (ROOT / rel).is_file():
        errors.append('missing ' + rel)

for p in (ROOT / 'app/src/main/res').rglob('*.xml'):
    try:
        ET.parse(p)
    except Exception as e:
        errors.append(f'bad XML {p.relative_to(ROOT)}: {e}')
try:
    ET.parse(ROOT / 'app/src/main/AndroidManifest.xml')
except Exception as e:
    errors.append('bad manifest: ' + str(e))

server = ROOT / 'app/src/main/assets/scrcpy-server-v4.1'
if not server.is_file():
    errors.append('scrcpy-server-v4.1 asset missing (workflow downloads it before preflight)')
else:
    sha = hashlib.sha256(server.read_bytes()).hexdigest()
    exp = 'deacb991ed2509715160ffdc7907e47b4160eb30d1566217e9047fd5b8850cae'
    if sha != exp:
        errors.append(f'scrcpy server SHA mismatch: {sha}')

manifest_text = (ROOT / 'app/src/main/AndroidManifest.xml').read_text(errors='ignore')
if 'android.permission.QUERY_ALL_PACKAGES' in manifest_text:
    errors.append('QUERY_ALL_PACKAGES must not be requested')
for needle in ['<queries>', 'android.intent.action.MAIN', 'android.intent.category.LAUNCHER']:
    if needle not in manifest_text:
        errors.append('package visibility manifest guard missing: ' + needle)

java_files = list((ROOT / 'app/src/main/java').rglob('*.java'))
alljava = '\n'.join(p.read_text(errors='ignore') for p in java_files)
service = (ROOT / 'app/src/main/java/com/opendex/desktop/shell/ScrcpyShellService.java').read_text(errors='ignore')
helper = (ROOT / 'app/src/main/java/com/opendex/desktop/shell/I2405Freeform.java').read_text(errors='ignore')
session = (ROOT / 'app/src/main/java/com/opendex/desktop/ScrcpySession.java').read_text(errors='ignore')
gradle = (ROOT / 'app/build.gradle').read_text(errors='ignore')
workflow = (ROOT / '.github/workflows/build-apk.yml').read_text(errors='ignore')

checks = {
    'scrcpy server launch': 'com.genymobile.scrcpy.Server',
    'scrcpy v4.1 START_APP id': 'CTRL_START_APP = 16',
    'scrcpy v4.1 RESIZE_DISPLAY id': 'CTRL_RESIZE_DISPLAY = 21',
    'new-display option': 'new_display=',
    'flex display': 'flex_display=true',
    'persistent multi-session server': 'cleanup=false',
    'shell launch bridge': 'launchAppOnSessionDisplay',
    'H264 decoder': 'MediaFormat.MIMETYPE_VIDEO_AVC',
}
for label, needle in checks.items():
    if needle not in alljava:
        errors.append(f'missing protocol guard: {label}')

# I2405 WCT integration guards.
for needle in [
    'I2405Freeform.prepareDisplay(displayId)',
    'I2405Freeform.prepareLaunchedTask(displayId, packageName)',
    'requestedMode = I2405Freeform.enabled() ? I2405Freeform.FREEFORM : 1',
    'vd_system_decorations=" + (I2405Freeform.enabled() ? "true" : "false")',
]:
    if needle not in service:
        errors.append('I2405 service integration missing: ' + needle)

for needle in [
    'FEATURE_DEFAULT_TASK_CONTAINER = 1',
    'WindowContainerTransaction',
    'registerOrganizer',
    'setWindowingMode',
    'window_organizer',
    'prepareLaunchedTask',
]:
    if needle not in helper:
        errors.append('WCT helper guard missing: ' + needle)

if 'settings delete global enable_freeform_support' in service:
    errors.append('v0.7.4 freeform-delete regression is still present')
if 'set-display-windowing-mode -d " + displayId + " 5' in alljava:
    errors.append('I2405 must use WCT, not the ineffective wm mode=5 shell hack')
if 'org.lsposed.hiddenapibypass:hiddenapibypass:6.1' not in gradle:
    errors.append('HiddenApiBypass dependency missing')
if 'launchAppOnSessionDisplayBlocking' not in session:
    errors.append('shell session launch path missing')
if '.pid()' in alljava:
    errors.append('Process.pid() unavailable in Android SDK compile stubs')

# CI must use setup-android v4 and must never request the removed legacy SDK package "tools".
if 'android-actions/setup-android@v4' not in workflow:
    errors.append('CI must use android-actions/setup-android@v4')
if "packages: 'platform-tools platforms;android-36 build-tools;36.0.0'" not in workflow:
    errors.append('CI SDK package list is not pinned to modern SDK 36 packages')
if 'sdkmanager tools' in workflow or 'sdkmanager "tools"' in workflow:
    errors.append('obsolete sdkmanager tools package found')

if errors:
    print('PREFLIGHT FAIL')
    for e in errors:
        print(' -', e)
    sys.exit(1)

print('PREFLIGHT PASS')
