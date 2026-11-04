#!/usr/bin/env bash
#
# Check that the shared game engine can be built for Android.
#
# The engine is everything outside the desktop client: the FreeCol
# launcher class, net.sf.freecol.common and net.sf.freecol.server
# (including the AI).  This script compiles it on its own, so any
# reference to client code fails, and then uses jdeps to make sure it
# does not use Java APIs that Android lacks (AWT, Swing, ImageIO,
# javax.sound, ...).
#
# Note: java.beans (PropertyChangeSupport and friends) is part of the
# java.desktop module on the JDK, but Android provides it, so it is
# allowed.  javax.xml.stream (StAX) is not built into Android; the
# Android build adds a StAX library for it.
#
# Usage: android/check-engine.sh   (from the repository root)

set -euo pipefail

cd "$(dirname "$0")/.."

out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT

{
    echo src/net/sf/freecol/FreeCol.java
    find src/net/sf/freecol/common src/net/sf/freecol/server -name '*.java'
} > "$out/sources.txt"

echo "Compiling the engine without the client..."
if ! javac --release 11 -encoding UTF-8 -nowarn -Xmaxerrs 1000 \
        -cp "jars/*" -d "$out/classes" @"$out/sources.txt"; then
    echo "FAIL: the engine does not compile without the client." >&2
    exit 1
fi

echo "Checking for Java APIs that Android lacks..."
forbidden='java\.awt|javax\.swing|javax\.imageio|javax\.sound|java\.lang\.management|java\.rmi|javax\.naming|javax\.print'
if jdeps -verbose:class -cp "jars/*" "$out/classes" \
        | grep -E '^\s+net\.sf' | grep -E -- "-> ($forbidden)"; then
    echo "FAIL: the engine uses the APIs listed above." >&2
    exit 1
fi

echo "OK: the engine builds without the client and uses no desktop-only APIs."
