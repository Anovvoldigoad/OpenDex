#!/usr/bin/env python3
from pathlib import Path
import hashlib, sys, xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
errors=[]
required=[
 'app/src/main/AndroidManifest.xml',
 'app/src/main/java/com/opendex/desktop/ScrcpySession.java',
 'app/src/main/java/com/opendex/desktop/AppWindowView.java',
 'app/src/main/java/com/opendex/desktop/ShizukuBridge.java',
 'app/src/main/java/com/opendex/desktop/shell/ScrcpyShellService.java',
 'app/src/main/aidl/com/opendex/desktop/IScrcpyShellService.aidl',
 '.github/workflows/build-apk.yml',
]
for rel in required:
    if not (ROOT/rel).is_file(): errors.append('missing '+rel)
for p in (ROOT/'app/src/main/res').rglob('*.xml'):
    try: ET.parse(p)
    except Exception as e: errors.append(f'bad XML {p.relative_to(ROOT)}: {e}')
try: ET.parse(ROOT/'app/src/main/AndroidManifest.xml')
except Exception as e: errors.append('bad manifest: '+str(e))
server=ROOT/'app/src/main/assets/scrcpy-server-v4.1'
if not server.is_file():
    errors.append('scrcpy-server-v4.1 asset missing (workflow must download it before preflight)')
else:
    sha=hashlib.sha256(server.read_bytes()).hexdigest()
    exp='deacb991ed2509715160ffdc7907e47b4160eb30d1566217e9047fd5b8850cae'
    if sha != exp: errors.append(f'scrcpy server SHA mismatch: {sha}')

# Package visibility must use <queries>, never QUERY_ALL_PACKAGES.
manifest_text=(ROOT/'app/src/main/AndroidManifest.xml').read_text(errors='ignore')
if 'android.permission.QUERY_ALL_PACKAGES' in manifest_text:
    errors.append('QUERY_ALL_PACKAGES must not be requested; use launcher <queries> visibility')
for needle in ['<queries>', 'android.intent.action.MAIN', 'android.intent.category.LAUNCHER']:
    if needle not in manifest_text:
        errors.append('package visibility manifest guard missing: '+needle)

alljava='\n'.join(p.read_text(errors='ignore') for p in (ROOT/'app/src/main/java').rglob('*.java'))
for bad in ['createVirtualDisplay(', 'set-display-windowing-mode -d " + displayId + " 5', 'am task resize ']:
    if bad in alljava: errors.append('old native-freeform path found: '+bad)
if 'com.genymobile.scrcpy.Server' not in alljava: errors.append('scrcpy server launch missing')
if 'CTRL_RESIZE_DISPLAY' not in alljava: errors.append('scrcpy flex resize control missing')

checks = {
    'scrcpy v4.1 START_APP id': 'CTRL_START_APP = 16',
    'scrcpy v4.1 RESIZE_DISPLAY id': 'CTRL_RESIZE_DISPLAY = 21',
    'new-display option': 'new_display=',
    'system decorations disabled': 'vd_system_decorations=false',
    'flex display enabled': 'flex_display=true',
    'persistent server for multi-session': 'cleanup=false',
    'force-stop start app': 'sendStartApp("+" + packageName)',
    'H264 decoder': 'MediaFormat.MIMETYPE_VIDEO_AVC',
}
for label, needle in checks.items():
    if needle not in alljava:
        errors.append(f'missing protocol guard: {label}')

# v0.7.2 multi-session guards
service=(ROOT/'app/src/main/java/com/opendex/desktop/shell/ScrcpyShellService.java').read_text(errors='ignore')
session=(ROOT/'app/src/main/java/com/opendex/desktop/ScrcpySession.java').read_text(errors='ignore')
if '"cleanup=true"' in service:
    errors.append('scrcpy cleanup=true deletes/reclaims the shared server and breaks concurrent sessions')
if '"cleanup=false"' not in service:
    errors.append('scrcpy cleanup=false missing for concurrent sessions')
if 'sendStartApp("+" + packageName)' not in session:
    errors.append('START_APP must force-stop existing phone task before launching on virtual display')

if errors:
    print('PREFLIGHT FAIL')
    for e in errors: print(' -',e)
    sys.exit(1)
print('PREFLIGHT PASS')
