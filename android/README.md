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

Still to do:

- Add a StAX (`javax.xml.stream`) library, which Android doesn't include.
- Create the Android Gradle project and run a headless AI-vs-AI game on a
  device.

## Phase 2: touch UI

Map view, colony screen, Europe screen and dialogs, designed for touch.
The map renderer sits behind an interface, so a classic top-down view can be
added later.
