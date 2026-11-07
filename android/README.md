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

Ships and wagon trains next to the colony are listed with those in
port ("Next to the colony"); loading or unloading them, or putting a
colonist aboard, brings them into port first (using a move). On the
map, tapping your colony with an adjacent ship selected sails it in
as the colony opens.

Moving people works like the map: tap a colonist, then tap a tile, a
building, "Outside the colony", or a ship to board. Native land is bought automatically
if you can afford it. Tap a worked tile with nobody selected to change
its crop (or select its worker). Tap a ship, then warehouse goods to
load up to 100, or its cargo to unload.

`ColonyInfo` reads everything the screen shows in one go, with no
Android code, so the session check tests it along with the colony
orders (move into a building, onto a tile, change the build).

Also on the colony screen:

- **Build queue:** tap the build button to see the queue; tap an item
  to take it off, **Add...** to put something at the end, and
  **Pay N gold** to finish the first item next turn (when the game
  option allows it).
- **Equip:** select a colonist and tap **Equip** to make them a
  soldier, dragoon, scout, pioneer or missionary from the warehouse
  (each offered at the most the warehouse can supply), or to take
  their equipment off again.
- **Teaching:** teachers in the schoolhouse show their progress
  ("teaching 2/4") or "no student". The server picks a student by
  itself; to choose one, select the colonist and tap the teacher.

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

Select a colonist on the docks and tap **Equip** to buy them muskets,
horses or tools at market prices. **Sail to the New World** asks
where to: one of your coastal colonies, or back where the ship came
from.

### Unit orders

The map's **Orders** button (in place of Fortify) lists what the
selected unit can do: fortify or sentry; pioneer work on its tile
(road, plough, clear forest, with about how many turns); equip, when
it is in a colony; clear orders (destination, fortify, sentry or
pioneer work); and disband (asks first). The info line says what the
unit is busy with ("Plow, 5 turns left"). Roads and ploughed land are
drawn on the map; roads are straight lines between tile centres.

The session check equips a colonist as a pioneer from the warehouse,
starts ploughing, clears the order, checks a restart is refused once
out of moves and works next turn, sets a two-item build queue, and
sails back from Europe straight to the colony.

### Natives and dialogs

Moving a unit into a settlement now does what it does on the desktop:

- **Free colonist** at a native village: learn their skill (it says
  which first).
- **Scout**: hear what the village teaches and wants, then speak with
  the chief, demand tribute or attack. The chief's answer (gifts,
  tales, a better scout, or worse) is shown.
- **Missionary**: establish a mission, denounce a rival one, or incite
  the village against another nation (the price is asked first).
- **Ship or wagon train with goods**: trade. Buy, sell (with haggling)
  or give gifts, round by round, until you leave or they tire of it.
- **Soldier or dragoon**: demand tribute, or attack after confirming.
  Attacks on units also ask first.
- **Scout at a European colony**: spy on it, or attack.

The server's questions are real dialogs now: first contact (peace or
not), the next founding father (with what each does), the King's tax
rises (accept, or refuse and boycott) and mercenaries, native demands
(give or refuse), naming the New World, captured cargo, and offers
from other European nations (accept or reject). Lost city rumours ask
before exploring, and burial mounds ask again. Newly found rivers,
lakes, mountain ranges and seas ask for a name, with FreeCol's
suggestion filled in (Settings → New places → Automatic keeps the
suggestions without asking). The Fountain of Youth lets you pick each
colonist from Europe's three waiting emigrants, with their portraits
("Choose for me" lets the game pick the rest).

Without a user interface (the session check) every question takes its
first, safest answer; the check's scripted player logs each question
it is asked.

### Start screen and new-game options

The start screen shows FreeCol's landing engraving with the title. On
the right: **Continue** (with the saved nation's coat of arms, year
and nation) and **New game**. The Phase 1 engine test is a small link
at the bottom. The whole app is landscape now.

**New game** opens the setup:

- **Nation:** the four nations the rules make available (Dutch,
  French, English, Spanish), each with its coat of arms and national
  advantage (trade, cooperation, immigration, conquest).
- **Difficulty:** Very easy to Very hard.
- **Map size:** Small 40x100 (the old default), Medium 50x125, Large
  60x150. Bigger maps make the computer players' turns slower.
- **Land:** Islands (18% land), Normal (25%) or Continents (35%).

`NewGameOptions` holds the choices and puts the map ones into the
rules before the server starts; the nation is set after logging in,
as the desktop's start panel does. The session check starts as the
second nation on hard with a medium continents map and checks all of
it took.

### Map polish

- **Soft edges:** where two land terrains meet, the neighbour's
  terrain fades in through the base data's masks, as on the desktop.
- **Rivers:** FreeCol's per-tile river pictures, with forests that
  have a gap cut for the river.
- **Roads** curve through a tile they pass straight through.
- **Minimap** (top right): the whole map in the desktop's land
  colours with blue water, settlements in their owner's colour, your
  units as white dots, fog darker, and a frame for what the main map
  shows. Tap or drag on it to go there.

Not drawn: animated water and river mouths into the sea.

## Testing

Most of the app's game logic has no Android code in it, so it is
tested on a desktop JVM. Three checks run in CI (the "Android engine
check" workflow) on every push and pull request, and can be run
locally from the repository root with Java 11 or later:

| Check | Command | What it does |
| --- | --- | --- |
| Engine | `android/check-engine.sh` | Compiles the engine (`common`, `server`, AI) without the desktop client and fails on any Java API that Android lacks (AWT, Swing, ImageIO, javax.sound, ...). |
| Game session | `android/session-check.sh` | Plays a game as the phone does, with the AI as the other nations, then the war of independence (below). |
| FreeCol's own tests | `ant -noinput testall` | The upstream JUnit suite. |

### The game session check

`SessionCheck` (in the app's sources) drives `GameSession`, the same
class the phone's screens use, through a 12-turn game: new-game
options (nation, difficulty, map), sailing to land, founding a
colony, the colony screen's orders (workers, crops, build queue,
pay to finish), equipping a pioneer and ploughing, trading with a
native village, a round trip to Europe (sell, buy, board, sail back),
a trade route over several turns, the reports, Colopedia and
diplomacy pages, saving and loading, and retiring to the end screen.
The info classes (`ColonyInfo`, `EuropeInfo`, `ReportInfo`, ...) are
what the screens show, so checking them checks the screens' content.
Questions from the server get their first, safest answer; each is
logged with `?`, and messages with `>`.

`WarCheck` then loads the "Start near independence" test game,
declares independence, fights the royal army until the war ends, and
checks the end screen and that the game is on the high score list
exactly once.

Each check prints what it did and ends with `OK` or `FAILED`.
`android/session-check.sh --preview DIR` also saves PNG pictures of the
map as a phone would draw it.

### Test games

Long-press the **FreeCol** title on the start screen to open the
hidden **Test games** list. They are saved apart from your own games.

- **Start near independence:** England with five colonies, 70% rebels,
  an army in St. John's and a small royal army, for trying the
  declaration, the war and the end of the game without playing a
  whole game. Made by `android/make-scenario.sh [TURNS]` (the AI
  plays every nation for that many turns, 60 by default, then
  `ScenarioMaker` sets up the player's colonies); rerun it when the
  save format changes.

### On a phone

The UI itself (layouts, touch, sound, animations) is not compiled
until CI builds the APK, so it is tested by playing: install the APK
from the `android-latest` link above.

## Workflow

Claude writes the code on a branch and opens a PR. GitHub Actions
builds the APK and publishes it to the `android-latest` link above. Johan
playtests on his phone and merges.
