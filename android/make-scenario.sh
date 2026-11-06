#!/usr/bin/env bash
#
# Make the test game "Start near independence" (see
# android/app/.../ScenarioMaker.java): the AI plays every nation for a
# while, then one nation is set up to declare independence.  Writes
# android/app/src/main/assets/scenarios/independence.fsg, which the
# hidden start-screen option loads.
#
# Usage: android/make-scenario.sh [TURNS]   (from the repository root)

set -euo pipefail

cd "$(dirname "$0")/.."

out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT

app=android/app/src/main/java/net/sf/freecol/android
{
    echo src/net/sf/freecol/FreeCol.java
    find src/net/sf/freecol/common src/net/sf/freecol/server -name '*.java'
    echo $app/Engine.java $app/ScenarioMaker.java
} > "$out/sources.txt"

echo "Compiling..."
javac --release 11 -encoding UTF-8 -nowarn -Xmaxerrs 100 \
    -cp "jars/*" -d "$out/classes" @"$out/sources.txt"

dest=android/app/src/main/assets/scenarios
mkdir -p "$dest"
echo "Playing..."
java -cp "jars/*:$out/classes" net.sf.freecol.android.ScenarioMaker \
    data "$dest/independence.fsg" "${1:-60}" 2>&1 | grep -v "^INFO\|^[A-Z][a-z][a-z] [0-9]* 20" || true
ls -la "$dest"
