#!/usr/bin/env bash
# Build a Windows zip on lokal Windows machine
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"

NATIVE_DIR="core/data/src/jvmMain/resources/native/windows"
OUT_DIR="desktop/build/compose/binaries/windows"

# Put your own ffmpeg.exe in place and skip step 1:
#   FFMPEG_EXE=/path/to/ffmpeg.exe bash desktop/build-windows-local.sh
FFMPEG_EXE="${FFMPEG_EXE:-}"

# MSYS2 python, not the msys one, because the shell is MINGW64 and build-windows-dist.sh
# rewrites two jars with it. The AppImage job sets the same thing.
export PYTHONUTF8=1

step() { printf '\n==> [%s/4] %s\n' "$1" "$2"; }

# ---------------------------------------------------------------- toolchain
# Checked up front, because a missing tool otherwise surfaces as a confusing failure deep
# inside gradle or inside configure.
missing=()
for tool in java python3 curl gradle; do
    command -v "$tool" >/dev/null 2>&1 || missing+=("$tool")
done
# ffmpeg's own build needs these; step 1 is skipped when FFMPEG_EXE is given, so only
# require nasm when we are actually going to compile.
if [ -z "$FFMPEG_EXE" ]; then
    for tool in gcc make nasm; do
        command -v "$tool" >/dev/null 2>&1 || missing+=("$tool")
    done
fi
if [ ${#missing[@]} -gt 0 ]; then
    echo "FATAL: missing ${missing[*]}" >&2
    echo "  pacman -S --needed base-devel mingw-w64-x86_64-python \\" >&2
    echo "                 mingw-w64-x86_64-toolchain mingw-w64-x86_64-nasm \\" >&2
    echo "                 mingw-w64-x86_64-pkgconf" >&2
    echo "  JDK 21 is separate: install Temurin 21 and put java on PATH." >&2
    exit 1
fi

JAVA_MAJOR="$(java -version 2>&1 | head -1 | sed -n 's/.*version "\([0-9]*\).*/\1/p')"
[ "$JAVA_MAJOR" -ge 21 ] 2>/dev/null || {
    echo "FATAL: JDK 21 or newer required, found '${JAVA_MAJOR:-unknown}'" >&2
    echo "  Compose Desktop and this project's jvm target both need it." >&2
    exit 1
}
echo "==> java:  $(java -version 2>&1 | head -1)"
echo "==> python: $(python3 --version 2>&1)"
echo "==> cwd:    $ROOT"

# ---------------------------------------------------------------- 1. ffmpeg.exe
# Both binaries must be in the resource dir before gradle runs, because that is where
# Gradle picks them up from. A clean checkout has neither: the whole native tree is
# gitignored.
if [ -n "$FFMPEG_EXE" ]; then
    mkdir -p "$NATIVE_DIR"
    install -m 755 "$FFMPEG_EXE" "$NATIVE_DIR/ffmpeg.exe"
    echo "==> using FFMPEG_EXE=$FFMPEG_EXE ($(wc -c < "$NATIVE_DIR/ffmpeg.exe") bytes)"
elif [ -f "$NATIVE_DIR/ffmpeg.exe" ]; then
    echo "==> reusing existing ffmpeg.exe ($(wc -c < "$NATIVE_DIR/ffmpeg.exe") bytes)"
else
    step 1 "build ffmpeg.exe"
    bash desktop/build-ffmpeg-minimal.sh
fi

# The bundled build ships whatever ffmpeg linked against, so the missing-mingw-DLL class
# of failure is invisible until a user without MSYS2 runs it. Assert system-only imports
# here, where the fix is still cheap, and name the offender rather than failing silently.
if objdump -p "$NATIVE_DIR/ffmpeg.exe" 2>/dev/null \
    | sed -n 's/.*DLL Name: //p' | tr -d '\r' | sed 's/[[:space:]]*$//' | sort -u \
    | grep -qviE '^(bcrypt|bcryptprimitives|gdi32|imm32|kernel32|msvcrt|ntdll|ole32|psapi|secur32|shell32|shlwapi|user32|version|winmm|ws2_32)\.dll$'; then
    echo "==> WARNING: ffmpeg.exe imports non-system libraries:" >&2
    objdump -p "$NATIVE_DIR/ffmpeg.exe" | sed -n 's/.*DLL Name: //p' \
        | tr -d '\r' | sed 's/[[:space:]]*$//' | sort -u >&2
    echo "==> It will not start on a machine without MSYS2. Link it with -static." >&2
fi

# ---------------------------------------------------------------- 2. yt-dlp.exe
if [ -f "$NATIVE_DIR/yt-dlp.exe" ]; then
    echo "==> reusing existing yt-dlp.exe ($(wc -c < "$NATIVE_DIR/yt-dlp.exe") bytes)"
else
    step 2 "fetch yt-dlp.exe"
    mkdir -p "$NATIVE_DIR"
    # -fL because the release asset redirects, and the script is fatal-on-error so a failed
    # download cannot leave a 14-byte file that looks like a binary.
    curl -fsSL https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe \
        -o "$NATIVE_DIR/yt-dlp.exe"
fi
[ "$(wc -c < "$NATIVE_DIR/yt-dlp.exe")" -gt 1000000 ] || {
    echo "FATAL: yt-dlp.exe is $(wc -c < "$NATIVE_DIR/yt-dlp.exe") bytes, that is not yt-dlp" >&2
    exit 1
}

# ---------------------------------------------------------------- 3. distributable
step 3 "gradle :desktop:createReleaseDistributable"
# No configuration cache: it is a single-shot packaging build, and the cache only gets in
# the way when native resources changed underneath it.
./gradlew :desktop:createReleaseDistributable --no-configuration-cache

# ---------------------------------------------------------------- 4. zip
step 4 "package zip"
bash desktop/build-windows-dist.sh

# The zip is the deliverable, so prove it is a real archive and not a 4 KB empty one. The
# packaging script checks this too, but it checks before anyone has a chance to trust it.
ZIP="$(ls "$OUT_DIR"/*.zip 2>/dev/null | head -1 || true)"
[ -n "$ZIP" ] || { echo "FATAL: no zip in $OUT_DIR" >&2; exit 1; }

echo ""
echo "==> Ready to test: $ZIP"
echo "==> Unpack it somewhere with no MSYS2 installed and run:"
echo "      libremusic.exe"
echo "==> Log lands in %USERPROFILE%\\.libremusic\\log.txt"
echo "==> Unsigned, so Windows SmartScreen will warn on first run."
