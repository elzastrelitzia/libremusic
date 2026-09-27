#!/usr/bin/env bash
# Builds an audio-only ffmpeg for libremusic Desktop and drops it in the native dir.
#
# The BtbN builds are ~166 MB because they carry every video codec, filter and
# hardware encoder. The app only ever asks ffmpeg to decode to raw PCM:
#
#   ffmpeg -loglevel error [-ss N] -i FILE|- -acodec pcm_s16le -f wav -
#
# so everything below the audio path is dead weight. This configures ffmpeg with
# only that path, which is a configure-time decision and needs no source patch.
#
# Usage: bash build-ffmpeg-minimal.sh [version]
#   version - ffmpeg release to build (default: 8.1.3)
#
# Environment:
#   FFMPEG_REPO - git URL to build from instead of the ffmpeg.org tarball.
#                 Set this to a fork when you need patched source. Nothing here
#                 needs patching today, so the default is the official tarball
#                 and a fork would only add a monthly merge.
#   FFMPEG_REF  - tag or commit to check out (default: n$VERSION).
#
# Per-OS: run this on each target OS. The result is dynamically linked against
# that OS's libc, so a Linux build is not portable to macOS or Windows.
set -euo pipefail

VERSION="${1:-8.1.3}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

case "$(uname -s)" in
  Linux*)  OS=linux ;;
  Darwin*) OS=macos ;;
  *) echo "ERROR: build on the target OS, not cross-compiling." >&2; exit 1 ;;
esac

NATIVE_DIR="$REPO_DIR/core/data/src/jvmMain/resources/native/$OS"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

echo "==> ffmpeg $VERSION for $OS"
cd "$WORK_DIR"

if [ -n "${FFMPEG_REPO:-}" ]; then
  git clone --depth 1 --branch "${FFMPEG_REF:-n$VERSION}" "$FFMPEG_REPO" ffmpeg
else
  curl -fsSL "https://ffmpeg.org/releases/ffmpeg-$VERSION.tar.xz" -o ffmpeg.tar.xz
  tar -xf ffmpeg.tar.xz
  mv "ffmpeg-$VERSION" ffmpeg
fi
cd ffmpeg

# --disable-everything turns off all 566 decoders, 378 demuxers and 564 filters
# at once. Each --enable below turns one back on. Keep this list in sync with the
# ffmpeg command lines in PlayerService.startPipeline and downloadFullSong.
#
# anull is not optional: ffmpeg auto-inserts it into the filter graph, and
# aresample is auto-inserted for the fltp -> s16 conversion. Both fail at runtime,
# not at configure time, if missing.
#
# fd is not optional either. In ffmpeg 8.x "-i -" resolves to the fd protocol, not
# pipe, so without it every uncached track fails with "Protocol not found". That
# is the tee path at PlayerService.kt:548 and :588, i.e. all normal playback.
#
# mov is the m4a/mp4 demuxer. yt-dlp usually serves webm/opus, but not always, and
# the cache file is named by video ID with no extension, so a single m4a download
# would be unplayable with no way to tell from the filename.
#
# pcm_s16le and alac are decoders, not just codecs to pass through. The cache is a
# directory of extensionless files, so ffmpeg has to be able to identify and decode
# whatever it finds there. Dropping these two cost 2 of 11 formats in testing.
./configure \
  --disable-everything \
  --disable-ffplay --disable-ffprobe --disable-doc \
  --disable-x86asm \
  --enable-protocol=file,pipe,fd \
  --enable-demuxer=matroska,ogg,mp3,wav,aac,flac,mov \
  --enable-parser=opus,vorbis,aac,mpegaudio,flac \
  --enable-decoder=opus,aac,vorbis,mp3,flac,alac,pcm_s16le \
  --enable-muxer=wav \
  --enable-encoder=pcm_s16le \
  --enable-filter=anull,aresample,aformat

make -j"$(nproc 2>/dev/null || sysctl -n hw.ncpu)"

mkdir -p "$NATIVE_DIR"
install -m 755 ffmpeg "$NATIVE_DIR/ffmpeg"

echo ""
echo "==> Built $("$NATIVE_DIR/ffmpeg" -version | head -1)"
echo "==> Size: $(du -h "$NATIVE_DIR/ffmpeg" | cut -f1) at $NATIVE_DIR/ffmpeg"
