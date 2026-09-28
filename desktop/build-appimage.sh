#!/usr/bin/env bash
# Bundle the Compose Desktop release build into a Linux AppImage.
#
# Prerequisite: the distributable must exist already.
#   ./gradlew :desktop:createReleaseDistributable
#
# appimagetool is an AppImage itself, so it works on any x86_64 Linux without install.
set -euo pipefail

cd "$(dirname "$0")/.."

APP_ID="libremusic"
VERSION="$(./gradlew :desktop:properties --no-configuration-cache -q 2>/dev/null | awk -F': ' '/^version: /{print $2}')"
DIST="desktop/build/compose/binaries/main-release/app/desktop"
# appimagetool is a binary, not something this repo should carry. It lives next to the
# repo checkout; override if yours is elsewhere.
APPIMAGE_TOOL="${APPIMAGE_TOOL:-../appimagetool-x86_64.appimage}"
OUT_DIR="${OUT_DIR:-desktop/build/compose/binaries/appimage}"
APPDIR="$(mktemp -d "${TMPDIR:-/tmp}/libremusic.AppDir.XXXXXX")"

trap 'rm -rf "$APPDIR"' EXIT

[ -n "$VERSION" ] || { echo "could not read project version" >&2; exit 1; }
[ -x "$APPIMAGE_TOOL" ] || { echo "$APPIMAGE_TOOL not found or not executable" >&2; exit 1; }
[ -x "$DIST/bin/desktop" ] || { echo "no release build at $DIST, run ./gradlew :desktop:createReleaseDistributable" >&2; exit 1; }

# The jpackage launcher locates its own runtime relative to itself and has no RPATH, so
# the whole tree just has to stay together.
mkdir -p "$APPDIR/usr/lib/$APP_ID"
cp -r "$DIST/." "$APPDIR/usr/lib/$APP_ID/"

# Move ffmpeg and yt-dlp out of the app jar and into a real directory.
#
# Zip entries carry no unix mode, so every file inside a jar is 0644, and the AppImage
# mounts read-only so the bit cannot be set afterwards. NativeBinaries.isExecutable
# tests canExecute(), so a jar-resident ffmpeg always failed that check and the app
# copied 42 MB to ~/.libremusic/native on first run, purely to chmod it. mksquashfs
# does store modes and the mount is not noexec, so a plain file works in place.
NATIVE_DIR="$APPDIR/usr/lib/$APP_ID/native/linux"
DATA_JAR="$(ls "$APPDIR/usr/lib/$APP_ID/lib/app/"data-jvm-*.jar)"
mkdir -p "$NATIVE_DIR"
for bin in ffmpeg yt-dlp; do
    unzip -p -j "$DATA_JAR" "native/linux/$bin" > "$NATIVE_DIR/$bin"
    chmod +x "$NATIVE_DIR/$bin"
done

# Drop the natives from the jar so the image does not carry both copies, and drop the
# sqlite-jdbc natives for the 23 platforms this image cannot run on. Only the AppDir copy
# is touched, so createDistributable and runRelease still resolve them from the jar and
# the Android build still gets the full sqlite-jdbc.
python3 - "$DATA_JAR" "$APPDIR/usr/lib/$APP_ID/lib/app/sqlite-jdbc-"*.jar <<'PY'
import sys, zipfile

def rewrite(jar, keep):
    with zipfile.ZipFile(jar) as src:
        entries = [(i, src.read(i.filename)) for i in src.infolist() if keep(i.filename)]
    with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as out:
        for info, data in entries:
            out.writestr(info, data)
    raw = sum(i.file_size for i in zipfile.ZipFile(jar).infolist())
    print(f"  {jar.rsplit('/', 1)[-1]}: {len(entries)} entries, {raw/1048576:.1f} MB")

rewrite(sys.argv[1], lambda n: n not in {"native/linux/ffmpeg", "native/linux/yt-dlp"})

# sqlite-jdbc ships 24.36 MB of .so files across 24 platform/arch directories. This image
# is x86_64 Linux only, so Linux/x86_64 (1.02 MB) is the whole need. Everything outside
# org/sqlite/native/ has to stay, including every .class file.
SQLITE_NATIVE = "org/sqlite/native/"
for jar in sys.argv[2:]:
    rewrite(jar, lambda n: not n.startswith(SQLITE_NATIVE)
                          or n.startswith(SQLITE_NATIVE + "Linux/x86_64/"))
PY

cat > "$APPDIR/AppRun" <<EOF
#!/bin/sh
# pulse.native.dir is the existing override NativeBinaries checks first, so it uses the
# in-image copies above instead of extracting them to ~/.libremusic/native. The JVM
# prints one "Picked up JAVA_TOOL_OPTIONS" line to stderr in exchange. It wants the leaf
# directory holding the binaries, not the parent: the override joins the bare file name,
# unlike the jar resource path and the working-dir path, which both include the OS name.
HERE="\$(dirname "\$(readlink -f "\$0")")"
export JAVA_TOOL_OPTIONS="\${JAVA_TOOL_OPTIONS:+\$JAVA_TOOL_OPTIONS }-Dpulse.native.dir=\$HERE/usr/lib/$APP_ID/native/linux"
exec "\$HERE/usr/lib/$APP_ID/bin/desktop" "\$@"
EOF
chmod +x "$APPDIR/AppRun"

cat > "$APPDIR/$APP_ID.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=LibreMusic
Comment=Music streaming for everyone
Exec=$APP_ID
Icon=$APP_ID
Categories=AudioVideo;Audio;Player;Music;
Terminal=false
StartupWMClass=$APP_ID
X-AppImage-Name=$APP_ID
X-AppImage-Version=$VERSION
EOF

cp desktop/src/main/resources/icons/app_icon.svg "$APPDIR/$APP_ID.svg"

mkdir -p "$OUT_DIR"
ARCH=x86_64 "$APPIMAGE_TOOL" \
    --no-appstream \
    --comp="zstd" \
    "$APPDIR" "$OUT_DIR/$APP_ID-$VERSION-x86_64.AppImage"

chmod +x "$OUT_DIR/$APP_ID-$VERSION-x86_64.AppImage"
echo
echo "built $OUT_DIR/$APP_ID-$VERSION-x86_64.AppImage"
du -h "$OUT_DIR/$APP_ID-$VERSION-x86_64.AppImage"
