/**
 *  Copyright (C) 2002-2024   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.freecol.android;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.FreeCol;
import net.sf.freecol.common.FreeColException;
import net.sf.freecol.common.io.FreeColSavegameFile;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.BuildableType;
import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Constants.IndianDemandAction;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.FoundingFather;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.FreeColObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.ModelMessage;
import net.sf.freecol.common.model.Monarch.MonarchAction;
import net.sf.freecol.common.model.Ownable;
import net.sf.freecol.common.model.PathNode;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Region;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.Unit.MoveType;
import net.sf.freecol.common.model.Unit.UnitState;
import net.sf.freecol.common.model.UnitLocation;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.model.WorkLocation;
import net.sf.freecol.common.networking.AddPlayerMessage;
import net.sf.freecol.common.networking.ChooseFoundingFatherMessage;
import net.sf.freecol.common.networking.Connection;
import net.sf.freecol.common.networking.ErrorMessage;
import net.sf.freecol.common.networking.FeatureChangeMessage;
import net.sf.freecol.common.networking.FirstContactMessage;
import net.sf.freecol.common.networking.FountainOfYouthMessage;
import net.sf.freecol.common.networking.GameEndedMessage;
import net.sf.freecol.common.networking.IndianDemandMessage;
import net.sf.freecol.common.networking.LoginMessage;
import net.sf.freecol.common.networking.Message;
import net.sf.freecol.common.networking.MessageHandler;
import net.sf.freecol.common.networking.MonarchActionMessage;
import net.sf.freecol.common.networking.MultipleMessage;
import net.sf.freecol.common.networking.NewLandNameMessage;
import net.sf.freecol.common.networking.NewRegionNameMessage;
import net.sf.freecol.common.networking.NewTurnMessage;
import net.sf.freecol.common.networking.PartialMessage;
import net.sf.freecol.common.networking.RemoveMessage;
import net.sf.freecol.common.networking.SetAIMessage;
import net.sf.freecol.common.networking.SetCurrentPlayerMessage;
import net.sf.freecol.common.networking.SetDeadMessage;
import net.sf.freecol.common.networking.SetStanceMessage;
import net.sf.freecol.common.networking.StartGameMessage;
import net.sf.freecol.common.networking.UpdateMessage;
import net.sf.freecol.common.networking.WrapperMessage;
import net.sf.freecol.common.util.Introspector;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.FreeColServer.ServerState;


/**
 * A single-player game with one human player, as seen from the
 * phone.
 *
 * The server runs in-process and the human connects to it over a
 * loopback socket, just as the desktop client does.  This class keeps
 * the player's copy of the game up to date from the server's
 * messages, and sends the player's orders.  It has no user interface
 * of its own, so it also runs on a desktop JVM (see SessionCheck).
 *
 * Threading: messages from the server change the game on the
 * connection's thread, while holding {@link #lock()}.  Anything
 * that reads the game (such as drawing the map) must hold the same
 * lock.  Orders are sent from one worker thread, so the caller never
 * waits on the network.
 *
 * Dialogs (first contact, founding fathers, the King's demands and
 * so on) do not exist yet, so for now this answers them with a
 * sensible default and reports what it did.
 */
public final class GameSession implements MessageHandler {

    private static final Logger logger = Logger.getLogger(GameSession.class.getName());

    /** Receives news from the session; called from background threads. */
    public interface Listener {
        /** The game changed and the map should be redrawn. */
        void onChanged();

        /** A line of news for the player. */
        void onStatus(String text);
    }

    /** The human player's user name. */
    private static final String USER = "Player";

    private final Object lock = new Object();
    private final LocalServerAPI api = new LocalServerAPI();
    private final ExecutorService worker = new ThreadPoolExecutor(1, 1,
        0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(),
        r -> new Thread(r, "GameSession")) {
            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                if (t != null) {
                    logger.log(Level.SEVERE, "Order failed", t);
                    status("Something went wrong: " + t);
                }
            }
        };
    private final Listener listener;

    private FreeColServer server = null;
    private Game game = null;
    private Player me = null;
    private volatile boolean inGame = false;
    private volatile Unit selected = null;
    /** Where to save automatically, or null not to. */
    private volatile File saveFile = null;


    public GameSession(Listener listener) {
        this.listener = listener;
    }


    // Accessors, for use while holding lock()

    public Object lock() {
        return lock;
    }

    public Game getGame() {
        return game;
    }

    public Player getMyPlayer() {
        return me;
    }

    public boolean isInGame() {
        return inGame;
    }

    public boolean isMyTurn() {
        return inGame && game != null && me != null
            && game.getCurrentPlayer() == me;
    }

    public Unit getSelectedUnit() {
        Unit u = selected;
        return (u == null || u.isDisposed() || u.getOwner() != me) ? null : u;
    }


    // Starting and stopping

    /**
     * Save automatically to this file at the start of each of our
     * turns (and when asked by {@link #saveNow}).  A small text file
     * next to it ("NAME.txt") describes the game for the start screen.
     *
     * @param file The save file.
     */
    public void setSaveFile(File file) {
        this.saveFile = file;
    }

    /**
     * Load a saved game and carry on playing it.
     *
     * @param file The saved game.
     */
    public void load(File file) {
        worker.execute(() -> {
            try {
                status("Loading your game...");
                server = new FreeColServer(new FreeColSavegameFile(file),
                    (Specification)null, InetAddress.getLoopbackAddress(), -1,
                    "android");
                if (!connectAndLogin()) return;
                if (!inGame) {
                    // A loaded game waits, like a new one, for the
                    // players to be ready; then the server resumes it.
                    api.setReady(true);
                    api.requestLaunch();
                }
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "Load failed", t);
                status("Could not load the game: " + t);
            }
        });
    }

    /**
     * Save now, if it is our turn (when nothing else is changing the
     * game).  Used when the app is put in the background.
     */
    public void saveNow() {
        if (worker.isShutdown()) return;
        worker.execute(() -> {
            if (isMyTurn()) save();
        });
    }

    /** Save to the save file, safely: to a temporary file first. */
    private void save() {
        final File file = saveFile;
        if (file == null || server == null) return;
        try {
            file.getParentFile().mkdirs();
            File tmp = new File(file.getPath() + ".tmp");
            server.saveGame(tmp, null, getSelectedUnit());
            if (!tmp.renameTo(file)) {
                throw new java.io.IOException("could not replace " + file);
            }
            String about;
            synchronized (lock) {
                about = Messages.message(game.getTurn().getLabel()) + ", "
                    + Messages.message(me.getNationLabel());
            }
            try (Writer w = new OutputStreamWriter(new FileOutputStream(
                     new File(file.getPath() + ".txt")), StandardCharsets.UTF_8)) {
                w.write(about);
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Save failed", t);
            status("Could not save the game: " + t.getMessage());
        }
    }

    /**
     * Describe a saved game for the start screen.
     *
     * @param file The save file.
     * @return Its description (year and nation), or null if there is
     *     no saved game.
     */
    public static String describeSave(File file) {
        if (!file.isFile()) return null;
        File txt = new File(file.getPath() + ".txt");
        try {
            return new String(java.nio.file.Files.readAllBytes(txt.toPath()),
                              StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Saved game";
        }
    }

    /**
     * Start a new game: the server, then our login, then the launch.
     * Runs on the worker; progress is reported through the listener.
     *
     * @param spec The {@code Specification} to play with.
     */
    public void start(Specification spec) {
        worker.execute(() -> {
            try {
                status("Starting the game...");
                server = new FreeColServer(false, true, spec,
                    InetAddress.getLoopbackAddress(), -1, "android");
                if (!connectAndLogin()) return;
                status("Playing as " + Messages.message(me.getNationLabel())
                    + ". Creating the New World...");
                api.setReady(true);
                api.requestLaunch();
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "Start failed", t);
                status("Could not start the game: " + t);
            }
        });
    }

    /**
     * Connect to the server and log in.  For a saved game the login
     * puts us straight back into the running game.
     *
     * @return True if logged in.
     * @exception java.io.IOException if the connection fails.
     */
    private boolean connectAndLogin() throws java.io.IOException {
        api.setMessageHandler(this);
        api.connect("Android", server.getHost(), server.getPort());
        if (!api.login(USER, null, FreeCol.getVersion(), true, false)
            || me == null) {
            status("Could not join the game.");
            return false;
        }
        if (inGame) {
            status("Welcome back. Playing as "
                + Messages.message(me.getNationLabel()) + ".");
            synchronized (lock) {
                if (game.getCurrentPlayer() == me) worker.execute(this::beginTurn);
            }
            changed();
        }
        return true;
    }

    /**
     * Wait until the orders given so far have been sent.  For tests;
     * never call this on a user interface thread.
     *
     * @exception Exception if waiting fails.
     */
    public void sync() throws Exception {
        worker.submit(() -> {}).get();
    }

    /** Stop the game and the server. */
    public void stop() {
        worker.execute(() -> {
            if (server != null) {
                try {
                    server.shutdown();
                } catch (Throwable t) {} // Best effort
            }
            try {
                api.disconnect();
            } catch (Throwable t) {} // Best effort
        });
        worker.shutdown();
    }


    // Orders from the player.  These return at once and do the work
    // on the worker thread.

    /**
     * Our units on a tile, ships first with their passengers after
     * them.  Call while holding {@link #lock()}.
     *
     * @param tile The {@code Tile} to look at.
     * @return The units, possibly empty.
     */
    public List<Unit> getMyUnitsAt(Tile tile) {
        List<Unit> mine = new ArrayList<>();
        if (me == null || tile == null) return mine;
        for (Unit u : tile.getUnitList()) {
            if (u.getOwner() != me) continue;
            mine.add(u);
            for (Unit c : u.getUnitList()) mine.add(c);
        }
        return mine;
    }

    /**
     * Select one of our units.
     *
     * @param unit The {@code Unit} to select.
     */
    public void select(Unit unit) {
        if (unit == null || unit.getOwner() != me) return;
        selected = unit;
        changed();
    }

    /**
     * Select a unit on a tile: one that can still move if there is
     * one, or the next one along if a unit there is already selected.
     *
     * @param tile The {@code Tile} that was tapped.
     * @return True if one of our units is now selected.
     */
    public boolean selectOnTile(Tile tile) {
        synchronized (lock) {
            List<Unit> mine = getMyUnitsAt(tile);
            if (mine.isEmpty()) return false;
            Unit cur = getSelectedUnit();
            int i = mine.indexOf(cur);
            if (i >= 0) {
                selected = mine.get((i + 1) % mine.size());
            } else {
                Unit pick = mine.get(0);
                for (Unit u : mine) {
                    if (u.getMovesLeft() > 0 && u.getState() == UnitState.ACTIVE) {
                        pick = u;
                        break;
                    }
                }
                selected = pick;
            }
        }
        changed();
        return true;
    }

    /**
     * Can a unit found a colony where it stands?  Stricter than
     * Unit.canBuildColony, which does not look at the tile.
     * Call while holding {@link #lock()}.
     *
     * @param unit The {@code Unit} to check.
     * @return True if a colony can be founded.
     */
    public boolean canBuildColonyHere(Unit unit) {
        if (unit == null || me == null || unit.isNaval()
            || unit.isOnCarrier() || !unit.canBuildColony()) return false;
        Tile t = unit.getTile();
        return t != null && t.isLand() && t.getSettlement() == null
            && me.canClaimToFoundSettlement(t);
    }

    /**
     * Move the selected unit towards a tile.  A neighbouring tile is
     * one step (which may be an attack, or boarding a ship); a tile
     * further away becomes the unit's destination, and it carries on
     * there in later turns.
     *
     * @param target The {@code Tile} to go to.
     */
    public void moveSelectedTo(Tile target) {
        final Unit unit = getSelectedUnit();
        if (unit == null || target == null) return;
        worker.execute(() -> {
            if (!isMyTurn()) {
                status("Wait for your turn.");
                return;
            }
            Tile from;
            Direction d;
            synchronized (lock) {
                from = unit.getTile();
                if (from == null || from == target) return;
                d = from.getDirection(target);
            }
            if (d != null) {
                synchronized (lock) {
                    if (unit.getDestination() != null) {
                        unit.setDestination(null);
                    }
                }
                step(unit, d, true);
            } else {
                boolean known;
                synchronized (lock) {
                    known = unit.findPath(target) != null;
                }
                if (known) {
                    api.setDestination(unit, target);
                    followPath(unit);
                } else {
                    headTowards(unit, target);
                }
            }
            afterOrders(unit);
        });
    }

    /** Select the next unit that still has moves this turn. */
    public void nextUnit() {
        worker.execute(() -> {
            selectNext();
            changed();
        });
    }

    /** Leave the selected unit for now and select the next one. */
    public void waitUnit() {
        nextUnit();
    }

    /** Fortify the selected land unit, or put a ship on sentry. */
    public void fortifyUnit() {
        final Unit unit = getSelectedUnit();
        if (unit == null) return;
        worker.execute(() -> {
            UnitState s = (unit.isNaval() || unit.isCarrier()
                || !unit.checkSetState(UnitState.FORTIFYING))
                ? UnitState.SENTRY : UnitState.FORTIFYING;
            if (unit.checkSetState(s)) api.changeState(unit, s);
            afterOrders(null);
        });
    }

    /** Found a colony with the selected unit. */
    public void buildColony() {
        final Unit unit = getSelectedUnit();
        if (unit == null) return;
        worker.execute(() -> {
            String name;
            synchronized (lock) {
                if (!canBuildColonyHere(unit)) {
                    status("This unit can not found a colony here.");
                    return;
                }
                name = me.getSettlementName(null);
            }
            api.buildColony(name, unit);
            synchronized (lock) {
                Tile t = unit.getTile();
                if (t != null && t.getSettlement() != null) {
                    status("Founded " + t.getSettlement().getName() + ".");
                }
            }
            afterOrders(null);
        });
    }

    // Colony orders

    /**
     * Put a unit to work in a colony building or on a colony tile,
     * claiming the tile first if need be (paying for native land if
     * we can afford it).
     *
     * @param unit The {@code Unit} to move.
     * @param wl The {@code WorkLocation} to work at.
     */
    public void work(Unit unit, WorkLocation wl) {
        if (unit == null || wl == null) return;
        worker.execute(() -> {
            if (!isMyTurn()) return;
            Tile claim = null;
            int price = 0;
            Colony colony;
            synchronized (lock) {
                colony = wl.getColony();
                if (unit.getLocation() == wl) return;
                UnitLocation.NoAddReason why = wl.getNoAddReason(unit);
                Tile t = wl.getWorkTile();
                if (t != null && t.hasLostCityRumour()) {
                    status("Explore the rumour on that tile first.");
                    return;
                }
                if (why == UnitLocation.NoAddReason.CLAIM_REQUIRED
                    || (t != null && !me.owns(t))) {
                    claim = t;
                    price = (me.canClaimForSettlement(t)) ? 0
                        : me.getLandPrice(t);
                    if (price < 0) {
                        status("That land can not be claimed.");
                        return;
                    }
                    if (price > me.getGold()) {
                        status("The natives want " + price
                            + " gold for that land, which you do not have.");
                        return;
                    }
                } else if (why != UnitLocation.NoAddReason.NONE) {
                    status(noAddText(why));
                    return;
                }
            }
            boolean aboard;
            synchronized (lock) {
                aboard = unit.isOnCarrier();
            }
            if (aboard) api.disembark(unit);
            if (claim != null) {
                api.claimTile(claim, colony, price);
                synchronized (lock) {
                    if (!me.owns(claim)) {
                        status("Could not claim that land.");
                        return;
                    }
                }
                if (price > 0) status("Bought the land for " + price + " gold.");
            }
            api.work(unit, wl);
            synchronized (lock) {
                if (unit.getLocation() != wl) status("Could not work there.");
            }
            changed();
        });
    }

    /**
     * Change what a unit on a colony tile produces.
     *
     * @param unit The {@code Unit}.
     * @param type The {@code GoodsType} to produce.
     */
    public void changeWorkType(Unit unit, GoodsType type) {
        if (unit == null || type == null) return;
        worker.execute(() -> {
            if (!isMyTurn()) return;
            api.changeWorkType(unit, type);
            changed();
        });
    }

    /**
     * Take a unit out of a colony onto the colony's tile.
     *
     * @param unit The {@code Unit}.
     */
    public void putOutsideColony(Unit unit) {
        if (unit == null) return;
        worker.execute(() -> {
            if (!isMyTurn()) return;
            synchronized (lock) {
                if (unit.getColony() == null || !unit.isInColony()) return;
            }
            api.putOutsideColony(unit);
            changed();
        });
    }

    /**
     * Set what a colony builds next.
     *
     * @param colony The {@code Colony}.
     * @param type The {@code BuildableType} to build.
     */
    public void setBuild(Colony colony, BuildableType type) {
        if (colony == null || type == null) return;
        worker.execute(() -> {
            if (!isMyTurn()) return;
            List<BuildableType> queue = new ArrayList<>();
            queue.add(type);
            api.setBuildQueue(colony, queue);
            changed();
        });
    }

    /**
     * What a colony could build now.  Call while holding {@link #lock()}.
     *
     * @param colony The {@code Colony}.
     * @return The buildings and units it can build.
     */
    public List<BuildableType> getBuildable(Colony colony) {
        List<BuildableType> result = new ArrayList<>();
        final Specification spec = game.getSpecification();
        for (BuildingType bt : spec.getBuildingTypeList()) {
            if (colony.getNoBuildReason(bt, null) == Colony.NoBuildReason.NONE) {
                result.add(bt);
            }
        }
        for (UnitType ut : spec.getBuildableUnitTypes()) {
            if (colony.getNoBuildReason(ut, null) == Colony.NoBuildReason.NONE
                && colony.hasAbility(Ability.BUILD, ut, game.getTurn())) {
                result.add(ut);
            }
        }
        return result;
    }

    /**
     * Load goods from a colony onto a ship (or wagon train) there.
     *
     * @param carrier The carrier {@code Unit}.
     * @param type The {@code GoodsType} to load.
     * @param amount The amount to load.
     */
    public void loadGoods(Unit carrier, GoodsType type, int amount) {
        if (carrier == null || type == null || amount <= 0) return;
        worker.execute(() -> {
            if (!isMyTurn()) return;
            Colony colony;
            synchronized (lock) {
                colony = carrier.getColony();
                if (colony == null) colony = (carrier.getTile() == null) ? null
                    : carrier.getTile().getColony();
            }
            if (colony == null) return;
            api.loadGoods(colony, type, amount, carrier);
            changed();
        });
    }

    /**
     * Unload goods from a carrier into the colony it is in.
     *
     * @param carrier The carrier {@code Unit}.
     * @param type The {@code GoodsType} to unload.
     * @param amount The amount to unload.
     */
    public void unloadGoods(Unit carrier, GoodsType type, int amount) {
        if (carrier == null || type == null || amount <= 0) return;
        worker.execute(() -> {
            if (!isMyTurn()) return;
            api.unloadGoods(type, amount, carrier);
            changed();
        });
    }

    /**
     * Take a unit off a ship in a colony's port, onto the colony tile.
     *
     * @param unit The {@code Unit} on board.
     */
    public void disembark(Unit unit) {
        if (unit == null) return;
        worker.execute(() -> {
            if (!isMyTurn()) return;
            api.disembark(unit);
            changed();
        });
    }

    private static String noAddText(UnitLocation.NoAddReason why) {
        switch (why) {
        case CAPACITY_EXCEEDED: return "There is no room there.";
        case OCCUPIED_BY_ENEMY: return "Someone else's unit is on that land.";
        case OWNED_BY_ENEMY: return "That land belongs to someone else.";
        case ANOTHER_COLONY: return "Another colony is using that land.";
        case COLONY_CENTER: return "The colony centre works itself.";
        case MISSING_SKILL: case MINIMUM_SKILL: case MAXIMUM_SKILL:
            return "This colonist does not have the right skill for that.";
        case WRONG_TYPE: return "This unit can not work there.";
        default: return "Can not work there.";
        }
    }

    /**
     * The colony a unit could join with one tap: the one it stands
     * on, or one next to it if it can still move.  Call while holding
     * {@link #lock()}.
     *
     * @param unit The {@code Unit}.
     * @return The {@code Colony}, or null if none.
     */
    public Colony joinableColony(Unit unit) {
        if (unit == null || me == null || unit.isNaval() || unit.isInColony()
            || !unit.getType().canBuildColony()) return null;
        Tile t = unit.getTile();
        if (t == null) return null;
        if (t.getColony() != null) {
            return (t.getColony().getOwner() == me) ? t.getColony() : null;
        }
        if (unit.getMovesLeft() <= 0) return null;
        for (Tile n : t.getSurroundingTiles(1)) {
            Colony c = n.getColony();
            if (c != null && c.getOwner() == me) return c;
        }
        return null;
    }

    /** Move the selected unit into a colony next to it and join it. */
    public void joinColony() {
        final Unit unit = getSelectedUnit();
        if (unit == null) return;
        worker.execute(() -> {
            if (!isMyTurn()) return;
            Colony colony;
            Direction d = null;
            synchronized (lock) {
                colony = joinableColony(unit);
                if (colony == null) {
                    status("There is no colony here to join.");
                    return;
                }
                if (unit.getTile() != colony.getTile()) {
                    d = unit.getTile().getDirection(colony.getTile());
                }
            }
            if (d != null && !step(unit, d, true)) return;
            api.joinColony(unit, colony);
            synchronized (lock) {
                if (unit.isInColony()) {
                    status(unit.getDescription() + " joins " + colony.getName() + ".");
                } else {
                    status("Could not join " + colony.getName() + ".");
                }
            }
            afterOrders(null);
        });
    }

    /** End our turn. */
    public void endTurn() {
        worker.execute(() -> {
            if (!isMyTurn()) return;
            selected = null;
            api.endTurn();
            status("The other nations are moving...");
            changed();
        });
    }


    // Order helpers, on the worker

    /**
     * Take one step, if it is a kind of move we can do yet.
     *
     * @param unit The {@code Unit} to move.
     * @param d The {@code Direction} to move in.
     * @param last True if this is the last step (so attacks and
     *     boarding ships are allowed).
     * @return True if the unit moved.
     */
    private boolean step(Unit unit, Direction d, boolean last) {
        MoveType mt;
        Tile from, to;
        Unit carrier = null;
        synchronized (lock) {
            from = unit.getTile();
            if (from == null) return false;
            to = from.getNeighbourOrNull(d);
            mt = unit.getMoveType(d);
            if (mt == MoveType.EMBARK && to != null) {
                for (Unit u : to.getUnitList()) {
                    if (u.getOwner() == me && u.canAdd(unit)) {
                        carrier = u;
                        break;
                    }
                }
            }
        }
        switch (mt) {
        case MOVE: case MOVE_HIGH_SEAS: case EXPLORE_LOST_CITY_RUMOUR:
            api.move(unit, d);
            break;
        case EMBARK:
            if (!last || carrier == null) return false;
            api.embark(unit, carrier, d);
            break;
        case ATTACK_UNIT: case ATTACK_SETTLEMENT:
            if (!last) return false;
            api.attack(unit, d);
            break;
        case MOVE_NO_MOVES:
            status("No moves left this turn.");
            return false;
        default:
            if (mt.isLegal()) {
                status("Visiting settlements is not in the game yet.");
            } else {
                status("Can not move there.");
            }
            return false;
        }
        synchronized (lock) {
            return !unit.isDisposed() && unit.getTile() != from;
        }
    }

    /**
     * Move a unit along the path to its destination tile, as far as
     * it can go this turn.
     *
     * @param unit The {@code Unit} to move.
     */
    private void followPath(Unit unit) {
        for (int guard = 0; guard < 50; guard++) {
            Direction d;
            boolean last;
            synchronized (lock) {
                if (unit.isDisposed() || unit.getMovesLeft() <= 0) return;
                if (!(unit.getDestination() instanceof Tile)) return;
                Tile dest = (Tile)unit.getDestination();
                if (unit.getTile() == dest) {
                    unit.setDestination(null);
                    break;
                }
                PathNode path = unit.findPath(dest);
                if (path == null || path.next == null) {
                    status("No way there.");
                    unit.setDestination(null);
                    break;
                }
                PathNode next = path.next;
                if (next.getTurns() > 0) return; // Wait for next turn
                d = next.getDirection();
                last = next.next == null;
            }
            if (d == null || !step(unit, d, last)) return;
        }
        // Arrived (or gave up): tell the server too.
        api.setDestination(unit, null);
    }

    /**
     * Head for a tile there is no known path to (usually because it
     * is unexplored): step to whichever neighbour is closest to it,
     * for as long as that gets closer and moves last.
     *
     * @param unit The {@code Unit} to move.
     * @param target The {@code Tile} to head for.
     */
    private void headTowards(Unit unit, Tile target) {
        for (int guard = 0; guard < 50; guard++) {
            Direction best = null;
            synchronized (lock) {
                Tile from = unit.getTile();
                if (unit.isDisposed() || from == null || from == target
                    || unit.getMovesLeft() <= 0) return;
                int bestDist = from.getDistanceTo(target);
                for (Direction d : Direction.values()) {
                    Tile t = from.getNeighbourOrNull(d);
                    if (t == null) continue;
                    MoveType mt = unit.getMoveType(d);
                    if (mt != MoveType.MOVE && mt != MoveType.MOVE_HIGH_SEAS
                        && mt != MoveType.EXPLORE_LOST_CITY_RUMOUR) continue;
                    int dist = t.getDistanceTo(target);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = d;
                    }
                }
            }
            if (best == null) {
                if (guard == 0) status("No way there.");
                return;
            }
            if (!step(unit, best, false)) return;
        }
    }

    /** After a unit's orders: keep it selected while it can move. */
    private void afterOrders(Unit unit) {
        synchronized (lock) {
            if (unit != null && !unit.isDisposed() && unit.getOwner() == me
                && unit.getMovesLeft() > 0 && unit.getDestination() == null) {
                selected = unit;
            } else {
                selectNextLocked();
            }
        }
        changed();
    }

    private void selectNext() {
        synchronized (lock) {
            selectNextLocked();
        }
    }

    private void selectNextLocked() {
        if (me == null) return;
        Unit u = me.getNextActiveUnit();
        selected = u;
        if (u == null && isMyTurn()) status("No more units to move. End the turn when ready.");
    }

    /** Our turn has started: housekeeping, then hand over. */
    private void beginTurn() {
        List<Unit> going = new ArrayList<>();
        synchronized (lock) {
            me.invalidateCanSeeTiles();
            me.resetIterators();
            for (Unit u : me.getUnitSet()) {
                if (u.hasTile() && u.getDestination() instanceof Tile) going.add(u);
            }
        }
        // New colonists at the docks in Europe.
        for (int i = 0; i < 10; i++) {
            synchronized (lock) {
                if (!me.checkEmigrate()) break;
            }
            int slot = (me.hasAbility(Ability.SELECT_RECRUIT))
                ? Europe.MigrationType.getDefaultSlot()
                : Europe.MigrationType.getUnspecificSlot();
            if (!api.emigrate(slot)) break;
        }
        for (Unit u : going) followPath(u);
        save();
        reportMessages();
        synchronized (lock) {
            me.resetIterators();
            selectNextLocked();
            Turn t = game.getTurn();
            status("Your turn: " + Messages.message(t.getLabel()) + ".");
        }
        changed();
    }

    private void reportMessages() {
        List<String> lines = new ArrayList<>();
        synchronized (lock) {
            for (ModelMessage mm : me.getNewModelMessages()) {
                lines.add(Messages.message(mm));
                mm.setDisplayed(true);
            }
        }
        for (String s : lines) status(s);
    }


    // Implement MessageHandler

    @Override
    public Message read(Connection connection)
        throws FreeColException, XMLStreamException {
        return Message.read(game, connection.getFreeColXMLReader());
    }

    @Override
    public Message handle(Connection connection, Message message)
        throws FreeColException {
        synchronized (lock) {
            handleLocked(connection, message);
        }
        return null;
    }

    private void handleLocked(Connection connection, Message m)
        throws FreeColException {
        if (m instanceof MultipleMessage) {
            for (Message sub : ((MultipleMessage)m).getMessages()) {
                handleLocked(connection, sub);
            }
        } else if (m instanceof WrapperMessage) {
            Message inner = ((WrapperMessage)m).getMessage();
            if (inner != null) handleLocked(connection, inner);
        } else if (m instanceof LoginMessage) {
            LoginMessage lm = (LoginMessage)m;
            game = lm.getGame();
            me = game.getPlayerByName(lm.getUserName());
            // Joining a game already under way (a saved game).
            if (lm.getState() == ServerState.IN_GAME) inGame = true;
        } else if (m instanceof UpdateMessage) {
            update(((UpdateMessage)m).getChildren());
        } else if (m instanceof RemoveMessage) {
            RemoveMessage rm = (RemoveMessage)m;
            remove(rm.getRemovals(game), rm.getDivertObject(game));
        } else if (m instanceof PartialMessage) {
            partial(m.peekStringAttribute(PartialMessage.ID_TAG),
                    m.peekStringAttributeMap());
        } else if (m instanceof StartGameMessage) {
            inGame = true;
            // The first player moves first without being told to.
            if (game.getCurrentPlayer() == me) worker.execute(this::beginTurn);
        } else if (m instanceof SetCurrentPlayerMessage) {
            Player p = ((SetCurrentPlayerMessage)m).getPlayer(game);
            if (p != null) {
                game.setCurrentPlayer(p);
                if (p == me) worker.execute(this::beginTurn);
            }
        } else if (m instanceof NewTurnMessage) {
            int n = ((NewTurnMessage)m).getTurnNumber();
            if (n >= 0) game.setTurn(new Turn(n));
        } else if (m instanceof AddPlayerMessage) {
            game.addPlayers(((AddPlayerMessage)m).getPlayers());
        } else if (m instanceof SetStanceMessage) {
            SetStanceMessage sm = (SetStanceMessage)m;
            Player p1 = sm.getFirstPlayer(game), p2 = sm.getSecondPlayer(game);
            if (p1 != null && p2 != null && sm.getStance() != null) {
                try {
                    p1.setStance(p2, sm.getStance());
                } catch (IllegalStateException ise) {
                    logger.log(Level.WARNING, "Illegal stance", ise);
                }
            }
        } else if (m instanceof SetDeadMessage) {
            Player p = ((SetDeadMessage)m).getPlayer(game);
            if (p == me) {
                status("Your colony has failed. The game is over.");
            } else if (p != null && me != null) {
                me.setStance(p, null);
            }
        } else if (m instanceof SetAIMessage) {
            SetAIMessage am = (SetAIMessage)m;
            Player p = am.getPlayer(game);
            if (p != null) p.setAI(am.getAI());
        } else if (m instanceof FeatureChangeMessage) {
            featureChange((FeatureChangeMessage)m);
        } else if (m instanceof ErrorMessage) {
            ErrorMessage em = (ErrorMessage)m;
            StringTemplate t = em.getTemplate();
            status((t != null) ? Messages.message(t) : em.getMessage());
        } else if (m instanceof GameEndedMessage) {
            Player winner = ((GameEndedMessage)m).getWinner(game);
            status((winner == me) ? "You have won!" : "The game is over.");
        } else {
            answer(m);
        }
        changed();
    }

    /** Apply object updates from the server. */
    private void update(List<FreeColObject> objects) {
        if (!inGame) {
            for (FreeColObject fco : objects) {
                if (fco instanceof Game) game.preGameUpdate((Game)fco);
            }
            return;
        }
        for (FreeColObject fco : objects) {
            FreeColGameObject fcgo = game.getFreeColGameObject(fco.getId());
            if (fcgo == null) {
                logger.warning("Update of missing object: " + fco.getId());
            } else if (!fcgo.copyIn(fco)) {
                logger.warning("Update copy-in failed: " + fco.getId());
            }
        }
        if (me != null) me.invalidateCanSeeTiles();
    }

    private void remove(List<FreeColGameObject> objects,
                        FreeColGameObject divert) {
        for (FreeColGameObject fcgo : objects) {
            if (divert != null && me != null) me.divertModelMessages(fcgo, divert);
            if (fcgo instanceof Settlement) {
                Settlement s = (Settlement)fcgo;
                if (s.getOwner() != null) s.getOwner().removeSettlement(s);
            } else if (fcgo instanceof Unit) {
                Unit u = (Unit)fcgo;
                if (u == selected) selected = null;
                if (u.getOwner() != null) u.getOwner().removeUnit(u);
            }
            fcgo.disposeResources();
        }
        if (me != null) me.invalidateCanSeeTiles();
    }

    private void partial(String id, Map<String, String> fields) {
        FreeColGameObject fcgo = (id == null) ? null
            : game.getFreeColGameObject(id);
        if (fcgo == null) {
            logger.warning("Partial update of missing object: " + id);
            return;
        }
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (PartialMessage.ID_TAG.equals(e.getKey())) continue;
            try {
                new Introspector(fcgo.getClass(), e.getKey())
                    .setter(fcgo, e.getValue());
            } catch (Introspector.IntrospectorException ie) {
                logger.log(Level.WARNING, "Partial update failed: " + id
                    + "/" + e.getKey(), ie);
            }
        }
        if (me != null && (fcgo == me
                || (fcgo instanceof Ownable && me.owns((Ownable)fcgo)))) {
            me.invalidateCanSeeTiles();
        }
    }

    private void featureChange(FeatureChangeMessage fm) {
        FreeColGameObject parent = fm.getParent(game);
        if (parent == null) return;
        boolean add = fm.getAdd();
        for (FreeColObject fco : fm.getChildren()) {
            if (fco instanceof net.sf.freecol.common.model.Ability) {
                if (add) parent.addAbility((Ability)fco);
                else parent.removeAbility((Ability)fco);
            } else if (fco instanceof net.sf.freecol.common.model.Modifier) {
                net.sf.freecol.common.model.Modifier mod
                    = (net.sf.freecol.common.model.Modifier)fco;
                if (add) parent.addModifier(mod);
                else parent.removeModifier(mod);
            } else if (fco instanceof net.sf.freecol.common.model.HistoryEvent
                && parent instanceof Player && add) {
                ((Player)parent).addHistory((net.sf.freecol.common.model.HistoryEvent)fco);
            } else if (fco instanceof net.sf.freecol.common.model.LastSale
                && parent instanceof Player && add) {
                ((Player)parent).addLastSale((net.sf.freecol.common.model.LastSale)fco);
            } else if (fco instanceof ModelMessage
                && parent instanceof Player && add) {
                ((Player)parent).addModelMessage((ModelMessage)fco);
            }
        }
    }

    /**
     * Questions from the server, answered with a default until the
     * dialogs exist.  Replies go from the worker, never from here.
     */
    private void answer(Message m) {
        if (m instanceof FirstContactMessage) {
            FirstContactMessage fm = (FirstContactMessage)m;
            final Player p = fm.getPlayer(game), o = fm.getOtherPlayer(game);
            final Tile t = fm.getTile(game);
            if (p == me && o != null) {
                status("You meet the " + Messages.message(o.getNationLabel())
                    + " and agree to peace.");
                worker.execute(() -> api.firstContact(p, o, t, true));
            }
        } else if (m instanceof ChooseFoundingFatherMessage) {
            final List<FoundingFather> ffs
                = ((ChooseFoundingFatherMessage)m).getFathers(game);
            if (ffs != null && !ffs.isEmpty()) {
                final FoundingFather ff = ffs.get(0);
                status("Congress will seek " + Messages.getName(ff) + ".");
                worker.execute(() -> api.chooseFoundingFather(ffs, ff));
            }
        } else if (m instanceof MonarchActionMessage) {
            MonarchActionMessage mm = (MonarchActionMessage)m;
            final MonarchAction action = mm.getAction();
            if (mm.getTemplate() != null) status(Messages.message(mm.getTemplate()));
            if (action == MonarchAction.RAISE_TAX_ACT
                || action == MonarchAction.RAISE_TAX_WAR) {
                status("You accept the new tax.");
                worker.execute(() -> api.answerMonarch(action, true));
            } else if (action == MonarchAction.MONARCH_MERCENARIES
                || action == MonarchAction.HESSIAN_MERCENARIES) {
                worker.execute(() -> api.answerMonarch(action, false));
            }
        } else if (m instanceof IndianDemandMessage) {
            IndianDemandMessage im = (IndianDemandMessage)m;
            final Unit u = im.getUnit(game);
            final Colony c = im.getColony(game);
            final GoodsType gt = im.getType(game);
            final int amount = im.getAmount();
            if (u != null && c != null) {
                status("Natives make demands of " + c.getName() + "; you agree.");
                worker.execute(() -> api.indianDemand(u, c, gt, amount,
                    IndianDemandAction.INDIAN_DEMAND_ACCEPT));
            }
        } else if (m instanceof NewLandNameMessage) {
            NewLandNameMessage nm = (NewLandNameMessage)m;
            final Unit u = nm.getUnit(me);
            final String name = nm.getNewLandName();
            if (u != null && name != null) {
                status("You name the new land " + name + ".");
                worker.execute(() -> api.newLandName(u, name));
            }
        } else if (m instanceof NewRegionNameMessage) {
            NewRegionNameMessage nm = (NewRegionNameMessage)m;
            final Region r = nm.getRegion(game);
            final Tile t = nm.getTile(game);
            final Unit u = nm.getUnit(me);
            final String name = nm.getNewRegionName();
            if (r != null && name != null) {
                status("You discover " + name + ".");
                worker.execute(() -> api.newRegionName(r, t, u, name));
            }
        } else if (m instanceof FountainOfYouthMessage) {
            final int n = ((FountainOfYouthMessage)m).getMigrants();
            status("The Fountain of Youth! " + n + " colonists will join you.");
            worker.execute(() -> {
                // The server wants a specific dock slot for these.
                for (int i = 0; i < n; i++) {
                    api.emigrate(Europe.MigrationType.getDefaultSlot());
                }
            });
        } else {
            // Animations, sounds, chat and the rest: nothing to do yet.
            logger.fine("Ignored message: " + m.getType());
        }
    }


    // Listener helpers

    private void changed() {
        try {
            listener.onChanged();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Listener failed", t);
        }
    }

    private void status(String text) {
        try {
            listener.onStatus(text);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Listener failed", t);
        }
    }
}
