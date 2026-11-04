#!/usr/bin/env bash
#
# Play a short single-player game on a desktop JVM, as the phone
# does: the human player sails to the New World, lands a colonist and
# founds a colony, while the AI plays the other nations.  Checks that
# the Android app's game session (android/app/.../GameSession.java)
# works with the shared engine, without needing a phone.
#
# With --preview OUT_DIR it also saves PNG pictures of what the map
# would look like on a phone (android/preview/MapPreview.java).
#
# Usage: android/session-check.sh [--preview OUT_DIR]   (from the repository root)

set -euo pipefail

cd "$(dirname "$0")/.."

out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT

app=android/app/src/main/java/net/sf/freecol/android
{
    echo src/net/sf/freecol/FreeCol.java
    find src/net/sf/freecol/common src/net/sf/freecol/server -name '*.java'
    # The platform-neutral parts of the app.
    echo $app/Engine.java $app/LocalServerAPI.java $app/GameSession.java \
         $app/SessionCheck.java
    find $app/map -name '*.java'
    if [ "${1:-}" = "--preview" ]; then
        find android/preview -name '*.java'
    fi
} > "$out/sources.txt"

echo "Compiling..."
javac --release 11 -encoding UTF-8 -nowarn -Xmaxerrs 100 \
    -cp "jars/*" -d "$out/classes" @"$out/sources.txt"

echo "Playing..."
java -cp "jars/*:$out/classes" net.sf.freecol.android.SessionCheck data 12

if [ "${1:-}" = "--preview" ]; then
    java -Djava.awt.headless=true -cp "jars/*:$out/classes" \
        net.sf.freecol.android.preview.MapPreview data "${2:-preview}" 3
fi
