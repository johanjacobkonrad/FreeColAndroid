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

## Phase 2: touch UI (in progress)

Decisions so far:

- **Landscape** orientation.
- **Unit moves:** tap a unit to select it, then tap a destination.
- **Art:** FreeCol's existing isometric graphics. Bundle only what each
  screen needs (the full `data/` art is about 180 MB).
- **Map renderer behind an interface**, so a classic top-down square-tile
  view can be added later as a second renderer.

### First slice: the map

The start screen has **New game**, which plays as one nation against the
AI on the in-process server, with the human player connected over a
loopback socket the way the desktop client connects.

- `GameSession` is the human player's side: it logs in, launches the
  game, keeps the player's copy of the game up to date from the
  server's messages and sends orders (move, board, attack, go to a far
  tile, fortify, found a colony, end turn). It has no UI code.
- `map/MapRenderer` is the renderer interface; `map/IsometricRenderer`
  draws FreeCol's terrain, beaches, forests, hills, bonus resources,
  rumours, settlements and units, with fog of war and unexplored edges.
  It draws through `map/Surface`, so the same code draws on an Android
  canvas and, for the desktop preview, on a Java2D image.
- `MapView`: drag to scroll, pinch to zoom. Tap your unit to select it
  (tap again to cycle through a stack, including passengers on a ship),
  tap another tile to move there. A far tile becomes the unit's
  destination and it carries on in later turns. **Long press** moves
  the selected unit onto a tile with your own units (to board a ship).
- Buttons: Next unit, Wait, Fortify, Build colony, End turn.

Not in yet: rivers, roads and terrain blending on the map; dialogs.
For now first contact, founding fathers, the King's tax rises, native
demands and new land names are answered with a sensible default, and
the news line says what was chosen. Entering settlements (trade,
scouting, missions) says it is not in the game yet.

`android/session-check.sh` plays a short game on a desktop JVM the way
the app does (sail to land, put a colonist ashore, found a colony) and
runs in CI. With `--preview DIR` it also saves PNG pictures of the map
as a phone would draw it.

### Second slice: the colony screen

Tap your colony on the map to open it (long press still moves the
selected unit into it). Close, or the back gesture, returns to the map.

- Left: the colony's land, drawn by the map renderer, with each
  worker and what their tile produces.
- Middle: the buildings, with their workers, free places and output.
- Right: units outside the colony, and ships in port with their
  cargo and passengers.
- Bottom: the warehouse, with stock and change per turn (red when
  over the warehouse's capacity).
- Header: colonists, rebel percentage, hammers, bells and crosses,
  and what is being built (tap it to choose what to build next).

Moving people works like the map: tap a colonist, then tap a tile, a
building or "Outside the colony". Native land is bought automatically
if you can afford it. Tap a worked tile with nobody selected to change
its crop (or select its worker). Tap a ship, then warehouse goods to
load up to 100, or its cargo to unload.

`ColonyInfo` reads everything the screen shows in one go, with no
Android code, so the session check tests it along with the colony
orders (move into a building, onto a tile, change the build).

Not yet: equipping soldiers and pioneers, the full build queue,
teaching in the schoolhouse, and buying the current build.

### Saving

The game saves itself (in the app's private storage) at the start of
each of your turns, and when the app goes into the background during
your turn, since Android may stop it at any time after that. It
writes to a temporary file first, so a save cut short leaves the
last good one. The start screen then offers **Continue** with the
year and nation of the saved game. There is one save; a new game
replaces it once its first turn starts. The files are the desktop
game's own save format.

The session check saves, loads the save into a fresh session, checks
the colonies and units are all there, and plays on a turn.

### Europe

- On the map, the colony button reads **Sail to Europe** for a ship:
  it heads for the nearest high seas (carrying on next turn if need
  be) and crosses. The **Europe** button opens the Europe screen.
- Left: ships in port with their cargo, passengers and space, and
  ships at sea with where they are going and turns to arrival.
- Middle: colonists on the docks, and the three emigrants you can
  recruit, with the price.
- Right: units to train or buy, with prices (asks before spending).
- Bottom: the market, "sell/buy" price per unit, boycotts marked.
- Tap a ship, then market goods to buy 100 (or what you can afford),
  its cargo to sell, or a docks colonist and then the ship to board.
  **Sail to the New World** sends the selected ship back.

`EuropeInfo` reads all of it without Android code. The session check
sails into the colony, loads its biggest stock, sails to Europe,
sells, buys with the proceeds, boards whoever is on the docks and
sails back.

Not yet: equipping units (muskets, tools, horses) in Europe, and
picking which way the ship sails (it arrives where it left from).

Still to come: then dialogs (natives, diplomacy,
founding fathers, trade routes) and save/load.

Workflow: Claude writes the code on a branch and opens a PR. GitHub Actions
builds the APK and publishes it to the `android-latest` link above. Johan
playtests on his phone and merges.
