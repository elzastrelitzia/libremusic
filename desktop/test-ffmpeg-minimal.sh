#!/usr/bin/env bash
# Fails the build if the minimal ffmpeg cannot decode a format the app might meet.
#
# This exists because --disable-everything removes hundreds of components, and a
# missing one is a runtime failure, not a configure failure. Three were caught
# before this script existed: the fd protocol (broke all uncached playback), the
# mov demuxer, and the pcm_s16le and alac decoders (broke m4a and wav).
#
# Needs a full ffmpeg on PATH to generate the fixtures, and the binary under test
# as $1 (default: the one just built into the native dir).
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

case "$(uname -s)" in
  Linux*)  OS=linux ;;
  Darwin*) OS=macos ;;
  MINGW*|MSYS*|CYGWIN*) OS=windows ;;
  *) echo "unsupported host" >&2; exit 1 ;;
esac

case "$OS" in
  windows) BIN=ffmpeg.exe ;;
  *)       BIN=ffmpeg ;;
esac

FFMPEG="${1:-$REPO_DIR/core/data/src/jvmMain/resources/native/$OS/$BIN}"
command -v ffmpeg >/dev/null || { echo "need a full ffmpeg on PATH to make fixtures" >&2; exit 1; }
# On Windows canExecute is not the test, the extension is, same as NativeBinaries.
if [ "$OS" = windows ]; then
  [ -f "$FFMPEG" ] || { echo "no binary at $FFMPEG" >&2; exit 1; }
else
  [ -x "$FFMPEG" ] || { echo "no binary at $FFMPEG" >&2; exit 1; }
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

size_of() { wc -c < "$1" | tr -d ' '; }

echo "testing $FFMPEG"
"$FFMPEG" -version | head -1
echo

# One second of tone, so the fixtures stay small.
gen() { ffmpeg -hide_banner -loglevel error -f lavfi -i "sine=f=440:d=1" "$@" 2>/dev/null; }
gen -c:a libopus   -f webm "$WORK/opus.webm"
gen -c:a aac       -f ipod "$WORK/aac.m4a"
gen -c:a alac      -f ipod "$WORK/alac.m4a"
gen -c:a aac       -f adts "$WORK/aac.aac"
gen -c:a libmp3lame -f mp3  "$WORK/mp3.mp3"
gen -c:a flac      -f flac  "$WORK/flac.flac"
gen -c:a libvorbis -f ogg   "$WORK/vorbis.ogg"
gen -c:a libopus   -f ogg   "$WORK/opus.ogg"
gen -c:a pcm_s16le -f wav   "$WORK/pcm.wav"

pass=0
fail=0
ok()   { printf '  ok    %-14s %s bytes PCM\n' "$1" "$2"; pass=$((pass + 1)); }
bad()  { printf '  FAIL  %-14s %s\n' "$1" "$2"; fail=$((fail + 1)); }

report() { # label file [pre-input args...]
  local label="$1" file="$2"; shift 2
  "$FFMPEG" -hide_banner -loglevel error "$@" -i "$file" \
    -acodec pcm_s16le -f wav - >"$WORK/out.pcm" 2>"$WORK/err.txt"
  local n; n=$(size_of "$WORK/out.pcm")
  if [ "$n" -gt 20000 ]; then ok "$label" "$n"; else bad "$label" "$(head -1 "$WORK/err.txt")"; fi
}

echo "decode -i FILE (cache hit path)"
report "opus/webm" "$WORK/opus.webm"
report "aac/m4a"    "$WORK/aac.m4a"
report "alac/m4a"   "$WORK/alac.m4a"
report "aac/adts"   "$WORK/aac.aac"
report "mp3"        "$WORK/mp3.mp3"
report "flac"       "$WORK/flac.flac"
report "vorbis/ogg" "$WORK/vorbis.ogg"
report "opus/ogg"   "$WORK/opus.ogg"
report "pcm_s16le"  "$WORK/pcm.wav"

# The tee path. PlayerService.kt:548 and :588 pass -i -, which ffmpeg 8.x resolves
# through the fd protocol, so this is the check that matters most: it is every
# uncached track, which is normal playback.
echo "decode -i - (tee path, every uncached track)"
cat "$WORK/opus.webm" | "$FFMPEG" -hide_banner -loglevel error -i - \
  -acodec pcm_s16le -f wav - >"$WORK/out.pcm" 2>"$WORK/err.txt"
n=$(size_of "$WORK/out.pcm")
if [ "$n" -gt 20000 ]; then ok "stdin webm" "$n"; else bad "stdin webm" "$(head -1 "$WORK/err.txt")"; fi

echo "seek -ss (PlayerService.kt:510)"
report "seek 0.5s" "$WORK/opus.webm" -ss 0.5

echo
echo "size: $(du -h "$FFMPEG" | cut -f1)"
echo "pass=$pass fail=$fail"
[ "$fail" -eq 0 ] || exit 1
