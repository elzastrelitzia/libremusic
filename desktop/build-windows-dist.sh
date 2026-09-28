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
# Named after nativeDistributions.packageName, not the project. The cfg the launcher
# reads is named after it too, so a mismatch here means the pulse.native.dir line below
# is about to be written to a file nobody reads.
[ "$(basename "${LAUNCHERS[0]}")" = "$APP_ID.exe" ] || {
    echo "expected launcher $APP_ID.exe, found $(basename "${LAUNCHERS[0]}")" >&2
    exit 1
}
[ -f "$APP_DIR/app/$APP_ID.cfg" ] || {
    echo "no $APP_ID.cfg in $APP_DIR/app, so the launcher will not read it" >&2
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

# Same trick as the AppImage, different hook. There is no AppRun on Windows, but the
# jpackage launcher reads app/<name>.cfg and expands $APPDIR in it, so one java-options
# line points pulse.native.dir at in-tree copies. Without it NativeBinaries finds ffmpeg
# and yt-dlp only as jar resources and unpacks 42 MB into %USERPROFILE%\.libremusic\native
# on first run. jpackage's own AppLauncherSubstTest covers this substitution.
#
# One python3 block for all three jobs. MSYS2 base-devel does not ship unzip, and the
# sqlite rewrite above already made python3 a dependency of this script.
python3 - "$APP_DIR/app/data-jvm-"*.jar "$APP_DIR/app/$APP_ID.cfg" <<'PY'
import os, sys, zipfile

data_jar, cfg = sys.argv[1], sys.argv[-1]
KEEP = "native/windows/"
BINS = [KEEP + "ffmpeg.exe", KEEP + "yt-dlp.exe"]

# The override is a leaf directory holding the binaries, not the parent: NativeBinaries
# joins the bare file name onto it, unlike the jar resource path, which carries the OS
# segment.
native_dir = os.path.join(os.path.dirname(data_jar), *KEEP.split("/"))
os.makedirs(native_dir, exist_ok=True)

# Keep only native/windows/, minus the two binaries now living on disk. A checkout that
# also built the Linux ffmpeg would otherwise ship it to Windows, and the AppImage has the
# same problem in reverse. Directory entries and .gitkeep files stay.
def keep(name):
    if name in BINS:
        return False
    return not name.startswith("native/") or name.startswith(KEEP)


with zipfile.ZipFile(data_jar) as src:
    found = {n: src.read(n) for n in src.namelist() if n in BINS}
    entries = [(i, src.read(i.filename)) for i in src.infolist() if keep(i.filename)]

missing = sorted(set(BINS) - found.keys())
if missing:
    raise SystemExit(f"{data_jar} has no {missing}, the build_ffmpeg artifact never landed")
for name, data in found.items():
    with open(os.path.join(native_dir, os.path.basename(name)), "wb") as f:
        f.write(data)

with zipfile.ZipFile(data_jar, "w", zipfile.ZIP_DEFLATED) as out:
    for info, data in entries:
        out.writestr(info, data)

with zipfile.ZipFile(data_jar) as check:
    left = [n for n in check.namelist()
            if n.startswith("native/") and not n.startswith(KEEP)]
    gone = [n for n in BINS if n in check.namelist()]
if gone:
    raise SystemExit(f"{data_jar} still holds {gone}")
if [n for n in left if not n.endswith("/") and not n.endswith(".gitkeep")]:
    raise SystemExit(f"{data_jar} still holds foreign natives {[n for n in left]}")

line = "java-options=-Dpulse.native.dir=$APPDIR/native/windows"
with open(cfg) as f:
    text = f.read()
if line not in text:
    if "[JavaOptions]" not in text:
        raise SystemExit(f"{cfg} has no [JavaOptions] section, refusing to guess")
    with open(cfg, "w") as f:
        f.write(text.rstrip("\n") + "\n" + line + "\n")

print(f"  natives in {native_dir}")
print(f"  {os.path.basename(cfg)}: {line}")
PY

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
