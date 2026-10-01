#!/usr/bin/env python3
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors = []

# CI intentionally uses gradle/actions/setup-gradle with an installed Gradle
# distribution. A custom/untrusted gradle-wrapper.jar would be rejected by
# setup-gradle wrapper validation, so fail early if one is ever reintroduced.
wrapper_jars = list(ROOT.rglob('gradle-wrapper.jar'))
for jar in wrapper_jars:
    errors.append(f'Unexpected Gradle wrapper JAR: {jar.relative_to(ROOT)}')

for p in ROOT.rglob('*.xml'):
    try:
        ET.parse(p)
    except Exception as e:
        errors.append(f'XML parse failed: {p.relative_to(ROOT)}: {e}')

for p in ROOT.rglob('*.java'):
    text = p.read_text(encoding='utf-8')
    if text.count('{') != text.count('}'):
        errors.append(f'Brace mismatch: {p.relative_to(ROOT)}')
    if 'import android.app.BatteryManager;' in text:
        errors.append(f'Invalid BatteryManager import (use android.os.BatteryManager): {p.relative_to(ROOT)}')

manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
for cls in re.findall(r'android:name="\.([A-Za-z0-9_$.]+)"', manifest):
    if cls.startswith('permission.'):
        continue
    java = ROOT / 'app/src/main/java/com/opendex/launcher' / (cls.replace('.', '/') + '.java')
    if not java.exists() and cls not in {'app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE'}:
        errors.append(f'Manifest class missing: .{cls} -> {java.relative_to(ROOT)}')

required = [
    '.github/workflows/build-apk.yml',
    'app/build.gradle',
    'app/src/main/aidl/com/opendex/launcher/IUserShellService.aidl',
    'app/src/main/java/com/opendex/launcher/ShizukuController.java',
    'app/src/main/java/com/opendex/launcher/shell/UserShellService.java',
    'app/src/main/java/com/opendex/launcher/ui/WindowsDesktopView.java',
]
for item in required:
    if not (ROOT / item).exists():
        errors.append(f'Required file missing: {item}')

if errors:
    print('PREFLIGHT FAIL')
    for e in errors:
        print(' -', e)
    sys.exit(1)
print('PREFLIGHT PASS')
print('XML/source/repository structure looks consistent.')

# Crash-safe startup guards added in v0.3.5
main_activity = (ROOT / "app/src/main/java/com/opendex/launcher/MainActivity.java").read_text(encoding="utf-8")
if "showFatal(" not in main_activity or "startOpenDexSafely" not in main_activity:
    fail("MainActivity crash-safe startup guard missing")
print("[preflight] crash-safe startup: PASS")
