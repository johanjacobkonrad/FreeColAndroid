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

Still to do: confirm the all-AI game runs on a real phone.

## Phase 2: touch UI

Map view, colony screen, Europe screen and dialogs, designed for touch.
The map renderer sits behind an interface, so a classic top-down view can be
added later.
