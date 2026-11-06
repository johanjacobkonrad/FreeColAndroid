#!/usr/bin/env bash
#
# Play a short single-player game on a desktop JVM, as the phone
# does: the human player sails to the New World, lands a colonist and
# founds a colony, while the AI plays the other nations.  Checks that
# the Android app's game session (android/app/.../GameSession.java)
# works with the shared engine, without needing a phone.
#
# It runs with Woodstox, the XML library the phone uses (Android has
# none built in), downloaded from Maven Central, since it behaves
# differently from the JDK's own.  If the download fails it falls back
# to the JDK's, with a warning.
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
         $app/SessionCheck.java $app/ColonyInfo.java \
         $app/EuropeInfo.java $app/NewGameOptions.java $app/ColonyListInfo.java \
         $app/InfoPage.java $app/ReportInfo.java $app/ColopediaInfo.java \
         $app/DiplomacyInfo.java $app/WarCheck.java
    find $app/map -name '*.java'
    if [ "${1:-}" = "--preview" ]; then
        find android/preview -name '*.java'
    fi
} > "$out/sources.txt"

# The same Woodstox version as android/app/build.gradle.
wstx="$out/woodstox"
mkdir -p "$wstx"
maven=https://repo1.maven.org/maven2
if curl -fsSL -o "$wstx/woodstox-core.jar" \
        "$maven/com/fasterxml/woodstox/woodstox-core/6.7.0/woodstox-core-6.7.0.jar" \
   && curl -fsSL -o "$wstx/stax2-api.jar" \
        "$maven/org/codehaus/woodstox/stax2-api/4.2.2/stax2-api-4.2.2.jar"; then
    echo "Using Woodstox, as on Android."
else
    echo "WARNING: could not download Woodstox; using the JDK's XML library." >&2
    rm -f "$wstx"/*.jar
fi

echo "Compiling..."
javac --release 11 -encoding UTF-8 -nowarn -Xmaxerrs 100 \
    -cp "jars/*" -d "$out/classes" @"$out/sources.txt"

echo "Playing..."
java -cp "jars/*:$out/classes:$wstx/*" net.sf.freecol.android.SessionCheck data 12

# The war of independence, from the "Start near independence" test game.
scenario=android/app/src/main/assets/scenarios/independence.fsg
if [ -f "$scenario" ]; then
    echo "Playing the war..."
    java -cp "jars/*:$out/classes:$wstx/*" net.sf.freecol.android.WarCheck \
        data "$scenario" 25
fi

if [ "${1:-}" = "--preview" ]; then
    java -Djava.awt.headless=true -cp "jars/*:$out/classes:$wstx/*" \
        net.sf.freecol.android.preview.MapPreview data "${2:-preview}" 3
fi
