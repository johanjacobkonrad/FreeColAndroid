# FreeCol for Android

This fork is working towards an Android version of FreeCol.

## Approach

The game is split into two parts:

- **The engine**: the `FreeCol` launcher class plus `net.sf.freecol.common`
  and `net.sf.freecol.server` (rules, game model, server and AI). This is
  shared between the desktop game and Android, and runs in-process on the
  phone for offline single-player.
- **The front end**: `net.sf.freecol.client` is the existing Swing desktop
  client. The Android app will have its own touch UI, starting with
  FreeCol's existing isometric graphics.

## Phase 1: a headless engine (in progress)

Done:

- Client-side message handling lives in `client/control/ClientMessageHandler`.
- The launcher reaches the desktop client only through `FreeCol.DesktopClient`.
- Images, sounds, fonts, animations and desktop-only options live in the client.
- Colours are ARGB ints, rectangles are `common/util/IntRectangle`, and other
  AWT/Swing uses are hooks or have moved to the client.

`android/check-engine.sh` verifies that the engine compiles without the
client and uses no Java APIs that Android lacks. It runs in CI on every push.

The Android app (`android/`) compiles the engine straight from `../src`,
adds a StAX library (Android has no `javax.xml.stream`) and has a test
screen that runs an all-AI game with `AutoPlay`. GitHub Actions builds it on
every push and publishes the APK as the `android-latest` pre-release:
https://github.com/JohanJacobKonrad/FreeColAndroid/releases/download/android-latest/FreeCol-debug.apk

`AutoPlay` also runs on a desktop JVM with only the engine on the class path.

Confirmed on a phone (Android 15): 50 all-AI turns in 67s. Phase 1 is done.

## Phase 2: touch UI (next)

Decisions so far:

- **Landscape** orientation.
- **Unit moves:** tap a unit to select it, then tap a destination.
- **Art:** FreeCol's existing isometric graphics. Bundle only what each
  screen needs (the full `data/` art is about 180 MB).
- **Map renderer behind an interface**, so a classic top-down square-tile
  view can be added later as a second renderer.

First slice: the map.

- Render the isometric map with FreeCol's terrain, unit and settlement art.
- Pinch to zoom, drag to scroll.
- Start a real game as one nation against the AI, using the in-process
  server as `AutoPlay` does, but with a human player connected.
- Select and move units; end the turn.

After that: the colony screen, Europe, then dialogs (natives, diplomacy,
founding fathers, trade routes) and save/load.

Workflow: Claude writes the code on a branch and opens a PR. GitHub Actions
builds the APK and publishes it to the `android-latest` link above. Johan
playtests on his phone and merges.
