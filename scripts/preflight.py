#!/usr/bin/env python3
from pathlib import Path
import sys, xml.etree.ElementTree as ET, re

root = Path(__file__).resolve().parents[1]
errors = []

def fail(msg): errors.append(msg)

required = [
    'settings.gradle', 'build.gradle', 'app/build.gradle',
    'app/src/main/AndroidManifest.xml',
    'app/src/main/aidl/com/opendex/desktop/IWindowShellService.aidl',
    'app/src/main/java/com/opendex/desktop/DesktopActivity.java',
    'app/src/main/java/com/opendex/desktop/AppWindowView.java',
    'app/src/main/java/com/opendex/desktop/ShizukuBridge.java',
    'app/src/main/java/com/opendex/desktop/shell/WindowShellService.java',
]
for rel in required:
    if not (root / rel).is_file(): fail('missing: ' + rel)

for xml in (root / 'app/src/main').rglob('*.xml'):
    try: ET.parse(xml)
    except Exception as e: fail(f'bad XML {xml.relative_to(root)}: {e}')

java = '\n'.join(p.read_text(errors='ignore') for p in (root/'app/src/main/java').rglob('*.java'))
if 'com.levelup.droiduplauncher' in java: fail('DroidUP dependency must not return')
if 'set-display-windowing-mode -d ' not in java: fail('per-app display fullscreen guard missing')
if 'am task resize' in java: fail('native task-resize freeform must not return')
if 'android.app.BatteryManager' in java: fail('invalid BatteryManager import')
if re.search(r'0x[0-9a-fA-F]{9,}', java): fail('oversized hex literal')
if 'TextureView.SurfaceTextureListener' not in java: fail('TextureView window engine missing')
if 'createWindowDisplay' not in java or 'VirtualDisplay' not in java: fail('virtual-display engine missing')
if 'VD_TRUSTED' not in java: fail('trusted display path missing')
if 'VD_PUBLIC' in java or 'VD_PRESENTATION' in java: fail('per-app display must stay private, not public/presentation')
if 'force_resizable_activities' in java: fail('global resizable/freeform setting must not be touched')
if '--windowingMode 1' not in java: fail('app must launch fullscreen inside its private display')
if 'injectPointer' not in java: fail('display input injection missing')

if list(root.rglob('gradle-wrapper.jar')): fail('custom Gradle wrapper jar must not be committed')

if errors:
    print('PREFLIGHT FAIL')
    for e in errors: print(' -', e)
    sys.exit(1)
print('PREFLIGHT PASS')
