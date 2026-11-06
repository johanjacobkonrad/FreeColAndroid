#!/usr/bin/env bash
#
# Make the game music small enough for the app: FreeCol's tracks are
# about 500 kbit/s (87 MB); at Vorbis quality 2 (about 96 kbit/s) all
# twelve come to about 20 MB.  Writes them to
# android/app/build/generated/music-assets/music/, which the app build
# packs as assets.  Needs ffmpeg with libvorbis; without it the app is
# built without music.
#
# Usage: android/encode-music.sh   (from the repository root)

set -euo pipefail

cd "$(dirname "$0")/.."

src=data/default/resources/music/default
out=android/app/build/generated/music-assets/music
if ! command -v ffmpeg >/dev/null; then
    echo "WARNING: no ffmpeg; the app will have no music." >&2
    exit 0
fi
mkdir -p "$out"
cp "$src/README.md" "$out/README.md" # The tracks' licence and authors
for f in "$src"/*.ogg; do
    dest="$out/$(basename "$f")"
    if [ ! -s "$dest" ] || [ "$f" -nt "$dest" ]; then
        ffmpeg -loglevel error -y -i "$f" -vn -c:a libvorbis -q:a 2 "$dest"
    fi
done
du -sh "$out"
