#!/usr/bin/env python3
"""Static release checks for ZERNEX Files 2.0.0 (no Android SDK required)."""
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
archive = (ROOT / "app/src/main/java/com/zernex/files/ArchiveEngine.kt").read_text(encoding="utf-8")
archive_tests = ROOT / "app/src/test/java/com/zernex/files/ArchiveEngineTest.kt"
visuals = (ROOT / "app/src/main/java/com/zernex/files/FileVisuals.kt").read_text(encoding="utf-8")
visual_tests = ROOT / "app/src/test/java/com/zernex/files/FileVisualsTest.kt"

check("version 2.0.0", 'versionName = "2.0.0"' in build)
check("Play target API 36", 'create("play")' in build and 'targetSdk = 36' in build)
check("Android 10 legacy target API 29", 'create("android10Legacy")' in build and 'targetSdk = 29' in build and 'LEGACY_STORAGE", "true"' in build)
check("manifest legacy placeholder", 'android:requestLegacyExternalStorage="${requestLegacyExternalStorage}"' in manifest)
check("special all-files permission", 'android.permission.MANAGE_EXTERNAL_STORAGE' in manifest)
check("API 30+ direct access check", 'Environment.isExternalStorageManager()' in local)
check("API 29 legacy access check", 'Environment.isExternalStorageLegacy()' in local)
check("SAF optional / not default on modern Android", 'ActivityResultContracts.OpenDocumentTree()' in local and 'if (mustUseSaf || useSafMode)' in local)
check("visible brand mark, breadcrumb and search", "ZernexBrandMark()" in local and 'Icons.Default.Search' in local)
check("multi-selection and bulk rename", 'Tout sélectionner' in local and 'bulk_rename' in local and 'renameLocalSelection' in local)
check("copy/cut/paste protections", 'transferLocalFiles' in local and 'clipboardPaths.isEmpty()' in local)
check("legacy ZIP implementation retained for compatibility", 'createZipArchive' in local and 'extractZipSafely' in local)
check("ArchiveEngine ZIP/7Z/TAR formats", all(token in archive for token in ['ZIP("ZIP"', 'SEVEN_Z("7Z"', 'TAR_GZ("TAR.GZ"', 'TAR_BZ2("TAR.BZ2"', 'TAR_XZ("TAR.XZ"']))
check("archive extraction rejects traversal and links", 'startsWith(rootPrefix)' in archive and 'isSymbolicLink' in archive and 'entryCount < MAX_ENTRIES' in archive)
check("ZIP AES-256 password protection", 'AesKeyStrength.KEY_STRENGTH_256' in archive and 'EncryptionMethod.AES' in archive)
check("7z memory cap", 'setMaxMemoryLimitKiB(MAX_7Z_MEMORY_KIB)' in archive)
check("archive integration and explicit password UI", 'ArchiveEngine.create(' in local and 'ArchiveEngine.extractSafely(' in local and 'PasswordVisualTransformation()' in local)
check("archive tests cover formats and Zip Slip", archive_tests.is_file() and all(token in archive_tests.read_text(encoding="utf-8") for token in ['zipSlipEntryIsRejected', 'sevenZipRoundTrip', 'aes256ZipRequiresPassword']))
check("archive libraries pinned", all(token in build for token in ['commons-compress:1.28.0', 'xz:1.12', 'zip4j:2.11.6']))
check("strict monochrome + red/green/yellow palette", all(token in main for token in ['0xFF0B0B0C', '0xFF151516', '0xFFE34D4D', '0xFF38C978', '0xFFF4C542']))
check("high-contrast monochrome text palette", '0xFFF5F5F5' in main and '0xFFB8B8BA' in main and 'LocalContentColor provides MaterialTheme.colorScheme.onSurface' in main)
check("semantic file icon system", 'private fun fileAccent' in local and 'private fun safAccent' in saf and "ZernexCategoryRail" in local and "ZernexBottomNavigation" in local)
check("icon-led tools page", "ZernexToolsPage(" in local and all(token in visuals for token in ["Stockage", "Corbeille", "Doublons", "Dossier SAF"]))
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
check("V2 file categories and semantic accents", all(token in visuals for token in ["ZernexFileCategory", "MEDIA", "DOCUMENT", "ARCHIVE", "APPLICATION", "zernexAccentFor"]))
check("V2 file category tests", visual_tests.is_file() and all(token in visual_tests.read_text(encoding="utf-8") for token in ["directoryIsFolder", "mediaExtensionsShareRedCategory", "archiveExtensionsShareYellowCategory"]))
check("no blue purple cyan or orange accents in main explorer", not any(token in local + saf + main for token in ["0xFF54B8FF", "0xFFB18CFF", "0xFF45D1C1", "0xFFFFB45C", "0xFF83B9FF"]))

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

for path in sorted(list((ROOT / "app/src/main/java").rglob("*.kt")) + list((ROOT / "app/src/test/java").rglob("*.kt"))):
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
