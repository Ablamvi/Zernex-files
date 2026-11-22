#!/usr/bin/env python3
"""Static release checks for ZERNEX Files 1.0.3 (no Android SDK required)."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
checks = []

def check(name, condition, detail=""):
    checks.append((name, bool(condition), detail))

build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
main = (ROOT / "app/src/main/java/com/zernex/files/MainActivity.kt").read_text(encoding="utf-8")
local = (ROOT / "app/src/main/java/com/zernex/files/AdvancedFileManager.kt").read_text(encoding="utf-8")
saf = (ROOT / "app/src/main/java/com/zernex/files/SafFileManager.kt").read_text(encoding="utf-8")

check("version 1.0.4", 'versionName = "1.0.4"' in build)
check("Play target API 36", 'create("play")' in build and 'targetSdk = 36' in build)
check("Android 10 legacy target API 29", 'create("android10Legacy")' in build and 'targetSdk = 29' in build and 'LEGACY_STORAGE", "true"' in build)
check("manifest legacy placeholder", 'android:requestLegacyExternalStorage="${requestLegacyExternalStorage}"' in manifest)
check("special all-files permission", 'android.permission.MANAGE_EXTERNAL_STORAGE' in manifest)
check("API 30+ direct access check", 'Environment.isExternalStorageManager()' in local)
check("API 29 legacy access check", 'Environment.isExternalStorageLegacy()' in local)
check("SAF optional / not default on modern Android", 'ActivityResultContracts.OpenDocumentTree()' in local and 'if (mustUseSaf || useSafMode)' in local)
check("visible breadcrumb and search", 'Text("ZERNEX Files"' in local and 'Icons.Default.Search' in local)
check("multi-selection and bulk rename", 'Tout sélectionner' in local and 'bulk_rename' in local and 'renameLocalSelection' in local)
check("copy/cut/paste protections", 'transferLocalFiles' in local and 'clipboardPaths.isEmpty()' in local)
check("ZIP create and extraction guards", 'createZipArchive' in local and 'extractZipSafely' in local)
check("colorful UI theme", all(token in main for token in ['0xFFFFB45C', '0xFF45D1C1', '0xFF9A8CFF']))
check("high-contrast text palette", '0xFFFFFFFF' in main and '0xFFD0DAEA' in main and 'LocalContentColor provides MaterialTheme.colorScheme.onSurface' in main)
check("colorful file-type icons", 'private fun fileAccent' in local and 'private fun safAccent' in saf and 'Brush.linearGradient' in saf)
check("SAF persisted permission", 'takePersistableUriPermission' in local)
check("SAF document browser", 'DocumentFile.fromTreeUri' in saf)
check("no forced SAF API 29 in legacy build", 'Build.VERSION.SDK_INT == 29 && !BuildConfig.LEGACY_STORAGE' in local)

# Release-fix checks: API-specific theme attribute and first-class screens.
base_styles = (ROOT / "app/src/main/res/values/styles.xml").read_text(encoding="utf-8")
api27_styles = (ROOT / "app/src/main/res/values-v27/styles.xml").read_text(encoding="utf-8")
check("windowLightNavigationBar API-qualified", "windowLightNavigationBar" not in base_styles and 'name="android:windowLightNavigationBar"' in api27_styles)
check("local editor is a page", "private fun FileEditorScreen" in local and "TopAppBar(" in local)
check("local settings is a page", 'if (dialog == "settings")' in local and 'Text("Paramètres"' in local)
check("local utility sections are pages", all(name in local for name in ["RecentFilesPage", "FavoritesPage", "TrashPage", "DuplicatesPage", "StoragePage", "PermissionsPage"]))
check("SAF editor and preview are pages", "private fun SafTextEditorPage" in saf and "private fun SafPreviewPage" in saf)
check("SAF settings, favorites, trash and details are pages", all(name in saf for name in ["SafSettingsPage", "SafFavoritesPage", "SafTrashPage", "SafPropertiesPage"]))
check("no floating primary action buttons", "FloatingActionButton(" not in local and "FloatingActionButton(" not in saf)

# Parse all XML resources and manifest.
xml_files = list((ROOT / "app/src/main").rglob("*.xml"))
xml_ok = True
for path in xml_files:
    try:
        ET.parse(path)
    except Exception as exc:
        xml_ok = False
        checks.append((f"XML {path.relative_to(ROOT)}", False, str(exc)))
check("all Android XML parses", xml_ok, f"{len(xml_files)} fichiers")

# Basic source integrity: delimiter balance after removing comments and quoted strings.
def strip_kotlin(text):
    # Strip string/char literals first so MIME strings such as "*/*" are not
    # mistaken for block comments. This is a lightweight structural check,
    # not a Kotlin compiler.
    text = re.sub(r'""".*?"""', '""', text, flags=re.S)
    text = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)
    text = re.sub(r"'(?:\\.|[^'\\])'", "''", text)
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"//[^\n]*", " ", text)
    return text

for path in sorted((ROOT / "app/src/main/java").rglob("*.kt")):
    src = strip_kotlin(path.read_text(encoding="utf-8"))
    stack = []
    pairs = {')': '(', ']': '[', '}': '{'}
    valid = True
    for i, ch in enumerate(src):
        if ch in "([{": stack.append((ch, i))
        elif ch in ")]}":
            if not stack or stack[-1][0] != pairs[ch]:
                valid = False
                break
            stack.pop()
    valid = valid and not stack
    check(f"Kotlin delimiters {path.name}", valid, "balanced" if valid else "unbalanced")

failed = [c for c in checks if not c[1]]
for name, ok, detail in checks:
    print(("PASS" if ok else "FAIL") + " | " + name + (" | " + detail if detail else ""))
print(f"\n{len(checks)-len(failed)}/{len(checks)} checks passed")
if failed:
    sys.exit(1)
