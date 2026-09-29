#!/usr/bin/env bash
# Fetches the prebuilt audio-only ffmpeg and drops it in the native dir.
#
# The binaries are built and released by elzastrelitzia/FFmpeg, one ffmpeg repo for
# everything. Nothing here configures or compiles ffmpeg: that is the whole point, because
# this file used to carry a second copy of the fork's configure list and the two drifted.
# The drift shipped a broken ffmpeg.exe twice, once needing libavcodec-61.dll and once
# needing libwinpthread-1.dll and libiconv-2.dll, both of which die in the Windows loader
# before main. One build, one place it can be wrong.
#
# Usage: bash build-ffmpeg-minimal.sh
#
# Environment:
#   FFMPEG_RELEASE - release tag to fetch, or latest (default). The fork tags builds
#                   build-16, build-17 and so on, and latest follows them with no edit
#                   here. Pin a tag to reproduce a build:
#                     FFMPEG_RELEASE=build-16 bash desktop/build-ffmpeg-minimal.sh
#   FFMPEG_REPO    - owner/repo holding the release (default: elzastrelitzia/FFmpeg)
#
# Per-OS: the release carries a Linux and a Windows binary, so each job fetches its own.
# There is no macOS asset, so Darwin fails loudly instead of shipping nothing.
set -euo pipefail

# The default is the literal path segment "latest", so the URL is built as
# /releases/latest/download/$BIN. Note the order: "download/latest/$BIN" is wrong, because
# that reads "latest" as a tag name and 404s, and it is the reason this script failed the
# first time it was run with a moving version.
FFMPEG_RELEASE="${FFMPEG_RELEASE:-latest}"
FFMPEG_REPO="${FFMPEG_REPO:-elzastrelitzia/FFmpeg}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

case "$(uname -s)" in
  Linux*)  OS=linux ;;
  MINGW*|MSYS*|CYGWIN*) OS=windows ;;
  Darwin*) OS=macos ;;
  *) echo "ERROR: build on the target OS, not cross-compiling." >&2; exit 1 ;;
esac

# NativeBinaries.exe() appends .exe on Windows, so the file has to be named to match or
# resolve never finds it. Keep this in step with the $OS case above.
case "$OS" in
  windows) BIN=ffmpeg.exe ;;
  macos)
    echo "ERROR: the $FFMPEG_RELEASE release has no macOS asset." >&2
    echo "  Add a macos entry to the matrix in the fork's build.yml, or point" >&2
    echo "  FFMPEG_RELEASE at a tag that has one." >&2
    exit 1 ;;
  *) BIN=ffmpeg ;;
esac

NATIVE_DIR="$REPO_DIR/core/data/src/jvmMain/resources/native/$OS"
if [ "$FFMPEG_RELEASE" = latest ]; then
  URL="https://github.com/$FFMPEG_REPO/releases/latest/download/$BIN"
else
  URL="https://github.com/$FFMPEG_REPO/releases/download/$FFMPEG_RELEASE/$BIN"
fi

echo "==> $BIN from $FFMPEG_REPO@$FFMPEG_RELEASE for $OS"
mkdir -p "$NATIVE_DIR"

# Download to a temp name and move into place last, so an interrupted run cannot leave a
# half-written file at the path gradle is about to package.
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
# -fL because the asset redirects to a CDN, and the script is fatal-on-error so a failed
# fetch cannot leave a 14-byte file that looks like a binary.
#
# --retry because releases/latest intermittently answers 404 for a few seconds while the
# release is being published, which would otherwise fail a build over a network hiccup
# rather than a real problem. The retry is on the failure, not on success, so a genuine
# 404 for a tag that does not exist still fails immediately after its retries.
curl -fL --retry 5 --retry-delay 3 --retry-connrefused -sS "$URL" -o "$TMP"

# A truncated download is the failure mode that matters: the file exists, gradle packages
# it, and the app dies on a user machine. 1 MB is far below the real 4.0 and 6.4 MB.
SIZE=$(wc -c < "$TMP")
[ "$SIZE" -gt 1000000 ] || {
    echo "==> FATAL: $BIN is $SIZE bytes, that is not a build" >&2
    exit 1
}
# The exec bit does not survive a fresh download on every platform, and the packaging
# scripts preserve it, so set it here rather than debugging a permission error later.
chmod +x "$TMP"
install -m 755 "$TMP" "$NATIVE_DIR/$BIN"

# A binary that cannot start is the failure worth catching, and it is what shipped twice.
# Running it here cannot prove it on Windows, because the build host has the missing DLLs
# on PATH, so read the import table instead. That does not execute anything.
if [ "$OS" = windows ] && command -v objdump >/dev/null 2>&1; then
  foreign=$(objdump -p "$NATIVE_DIR/$BIN" | sed -n 's/.*DLL Name: //p' \
            | tr -d '\r' | sed 's/[[:space:]]*$//' | sort -u \
            | grep -viE '^(bcrypt|bcryptprimitives|gdi32|imm32|kernel32|msvcrt|ntdll|ole32|psapi|secur32|shell32|shlwapi|user32|version|winmm|ws2_32)\.dll$' || true)
  if [ -n "$foreign" ]; then
    echo "==> FATAL: $BIN imports non-system libraries, it will not start without MSYS2:" >&2
    printf '%s\n' "$foreign" | sed 's/^/    /' >&2
    exit 1
  fi
  echo "==> Imports are system-only, so $BIN is standalone"
fi

if ! "$NATIVE_DIR/$BIN" -version >/dev/null 2>&1; then
  echo "==> FATAL: $BIN downloaded but will not run here." >&2
  "$NATIVE_DIR/$BIN" -version || true
  exit 1
fi

echo ""
echo "==> Got $("$NATIVE_DIR/$BIN" -version | head -1)"
# wc -c, not du -h: the exact size is the thing worth reporting, and du rounds.
echo "==> Size: $SIZE bytes at $NATIVE_DIR/$BIN"
# "latest" is a moving label, so name the tag it resolved to or the build is not
# reproducible from its own log.
#
# No -L on this request, and that is the whole trick: with -L curl follows the chain to
# the CDN and then reports an empty %{redirect_url}, because there is no final redirect
# left. Without -L it reports the first hop, which is the one that names the tag. An
# earlier version used -L here and printed "could not resolve" for every successful build.
echo "==> From: $URL"
tag=$(curl -sSI -o /dev/null -w '%{redirect_url}' "$URL" 2>/dev/null | sed -n 's#.*/releases/download/\([^/]*\)/.*#\1#p')
if [ -n "$tag" ]; then
  echo "==> Release: $tag"
  [ "$tag" = "$FFMPEG_RELEASE" ] || echo "==> Resolved from '$FFMPEG_RELEASE'; pin with FFMPEG_RELEASE=$tag to reproduce."
else
  echo "==> Release: $FFMPEG_RELEASE (could not re-resolve the tag, using it as given)"
fi
