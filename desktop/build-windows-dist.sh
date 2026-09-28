#!/usr/bin/env bash
# Package the Compose Desktop release build for Windows.
#
# Run this on Windows, under MSYS2 (mingw-w64), same as build-ffmpeg-minimal.sh. It is
# bash rather than PowerShell so it shares the jar-rewriting helper shape with
# build-appimage.sh and needs no extra tooling on a runner that already has MSYS2.
#
# Prerequisite: the distributable must exist already.
#   ./gradlew :desktop:createReleaseDistributable
set -euo pipefail

cd "$(dirname "$0")/.."

APP_ID="libremusic"
VERSION="$(./gradlew :desktop:properties --no-configuration-cache -q 2>/dev/null | awk -F': ' '/^version: /{print $2}')"
RESOURCES="core/data/src/jvmMain/resources/native/windows"
STAGE="desktop/build/compose/binaries/main-release/app"
OUT_DIR="${OUT_DIR:-desktop/build/compose/binaries/windows}"

[ -n "$VERSION" ] || { echo "could not read project version" >&2; exit 1; }

# Both binaries have to be in the resource dir before the jar is built, because that is
# where Gradle picks them up from. A clean checkout has neither: the whole native tree is
# gitignored, so the build_ffmpeg workflow artifact is the only source for ffmpeg.exe.
for bin in ffmpeg.exe yt-dlp.exe; do
    if [ ! -f "$RESOURCES/$bin" ]; then
        echo "ERROR: $RESOURCES/$bin is missing." >&2
        echo "  ffmpeg.exe: run build-ffmpeg-minimal.sh on Windows, or download the" >&2
        echo "             ffmpeg-windows artifact from the Build Minimal ffmpeg workflow." >&2
        echo "  yt-dlp.exe: run 'bash desktop/download-binaries.sh' with a Windows host, or" >&2
        echo "             curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe \\" >&2
        echo "                  -o $RESOURCES/yt-dlp.exe" >&2
        exit 1
    fi
done

# Compose names the app image directory after the project, not after anything set here,
# so find it rather than hardcoding it. One candidate or the script stops.
mapfile -t APPS < <(find "$STAGE" -mindepth 1 -maxdepth 1 -type d)
[ "${#APPS[@]}" -eq 1 ] || {
    echo "expected exactly one app dir under $STAGE, found ${#APPS[@]}: ${APPS[*]}" >&2
    echo "run ./gradlew :desktop:createReleaseDistributable first" >&2
    exit 1
}
APP_DIR="${APPS[0]}"

# If the glob below does not match, python gets the literal pattern and dies with a
# FileNotFoundError that names nothing useful.
SQLITE_JAR="$(ls "$APP_DIR"/app/sqlite-jdbc-*.jar 2>/dev/null || true)"
[ -n "$SQLITE_JAR" ] || { echo "no sqlite-jdbc jar in $APP_DIR/app" >&2; exit 1; }

# The launcher is the only .exe at the top level. Everything else lives in app/ or
# runtime/, so this fails loudly if Compose ever changes the layout.
mapfile -t LAUNCHERS < <(find "$APP_DIR" -maxdepth 1 -type f -name '*.exe')
[ "${#LAUNCHERS[@]}" -eq 1 ] || {
    echo "expected exactly one launcher exe in $APP_DIR, found ${#LAUNCHERS[@]}" >&2
    exit 1
}

# Drop the sqlite-jdbc natives for the platforms this build cannot run on.
#
# sqlite-jdbc ships 24.36 MB of .so and .dll files across 24 platform/arch directories.
# A Windows x86_64 build only ever loads Windows/x86_64, which is 0.94 MB, so the rest
# is 23 MB of dead weight in every download.
#
# Everything outside org/sqlite/native/ has to stay, including every .class file. A
# prefix-keep filter that keeps only the native paths silently empties the jar and the
# app then dies on startup with ClassNotFoundException: org.sqlite.JDBC.
#
# The AppImage does the same thing for Linux/x86_64. It is done here rather than in
# Gradle because the jar is shared: Android needs the full sqlite-jdbc.
python3 - "$SQLITE_JAR" <<'PY'
import sys, zipfile

SQLITE_NATIVE = "org/sqlite/native/"
KEEP = SQLITE_NATIVE + "Windows/x86_64/"

for jar in sys.argv[1:]:
    with zipfile.ZipFile(jar) as src:
        entries = [
            (i, src.read(i.filename)) for i in src.infolist()
            if not i.filename.startswith(SQLITE_NATIVE) or i.filename.startswith(KEEP)
        ]
    with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as out:
        for info, data in entries:
            out.writestr(info, data)

    with zipfile.ZipFile(jar) as check:
        names = check.namelist()
    classes = [n for n in names if n.endswith(".class")]
    natives = [n for n in names if n.startswith(SQLITE_NATIVE)]
    if len(classes) < 100:
        raise SystemExit(f"{jar}: only {len(classes)} class files left, refusing to ship it")
    print(f"  sqlite-jdbc: {len(classes)} classes, {len(natives)} native entries")
    for n in natives:
        print(f"    {n}")
PY

# ffmpeg and yt-dlp stay in the jar here, unlike the AppImage. NativeBinaries extracts
# them to %USERPROFILE%\.libremusic\native on first run, which costs 42 MB of home disk
# once. The AppImage avoids that by pointing pulse.native.dir at the in-image copies from
# AppRun, and there is no equivalent hook on Windows: the jpackage launcher is a bare
# .exe the user double-clicks, with nothing to inject an environment variable from. Not
# worth a .bat wrapper the user has to launch instead of the app.

mkdir -p "$OUT_DIR"
ZIP="$(cd "$OUT_DIR" && pwd)/$APP_ID-$VERSION-windows-x64.zip"
rm -f "$ZIP"

# python3 rather than zip(1). It is already a dependency of the rewrite above, and MSYS2
# base does not ship zip installed. ZipInfo.from_file carries st_mode into external_attr,
# so the exe keeps its exec bit when the archive is unpacked.
python3 - "$ZIP" "$APP_DIR" <<'PY'
import os, sys, zipfile

out, app_dir = sys.argv[1], os.path.abspath(sys.argv[2])
prefix = os.path.basename(app_dir)
root = os.path.dirname(app_dir)

written = 0
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for dirpath, dirnames, filenames in os.walk(app_dir):
        dirnames.sort()
        for name in sorted(filenames):
            full = os.path.join(dirpath, name)
            z.write(full, os.path.relpath(full, root).replace(os.sep, "/"))
            written += 1

# An empty zip is a 4 KB file that reports success, so never let one through.
if written == 0:
    raise SystemExit(f"archive is empty, nothing walked under {app_dir}")
with zipfile.ZipFile(out) as check:
    if len(check.namelist()) != written:
        raise SystemExit(f"wrote {written} entries but the archive holds {len(check.namelist())}")
print(f"  {written} entries")
PY

echo ""
echo "==> Built $ZIP"
du -h "$ZIP"
echo "==> launcher: $(basename "${LAUNCHERS[0]}")"
