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
import net.sf.freecol.common.model.AbstractGoods;
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
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileImprovement;
import net.sf.freecol.common.model.TileImprovementType;
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
import net.sf.freecol.common.model.DiplomaticTrade;
import net.sf.freecol.common.model.DiplomaticTrade.TradeStatus;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.NativeTrade;
import net.sf.freecol.common.model.NativeTrade.NativeTradeAction;
import net.sf.freecol.common.model.NativeTradeItem;
import net.sf.freecol.common.model.TradeItem;
import net.sf.freecol.common.networking.DiplomacyMessage;
import net.sf.freecol.common.networking.InciteMessage;
import net.sf.freecol.common.networking.LootCargoMessage;
import net.sf.freecol.common.networking.NativeTradeMessage;
import net.sf.freecol.common.networking.ScoutSpeakToChiefMessage;
import net.sf.freecol.common.networking.SpySettlementMessage;
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

        /**
         * Ask the player to pick one of some options.  Call the
         * answer exactly once, from any thread, with the index
         * chosen, or -1 if the player backed out.  Without a user
         * interface the first option is taken.
         *
         * @param title A short title.
         * @param text The question.
         * @param options The choices, the safest first.
         * @param answer Receives the choice.
         */
        default void onChoose(String title, String text, List<String> options,
                              java.util.function.IntConsumer answer) {
            answer.accept(0);
        }

        /**
         * Ask the player for a name.  Call the answer exactly once
         * with the name, or null to keep the suggestion.
         *
         * @param title A short title.
         * @param text The question.
         * @param suggestion The suggested name.
         * @param answer Receives the name.
         */
        default void onAskName(String title, String text, String suggestion,
                               java.util.function.Consumer<String> answer) {
            answer.accept(suggestion);
        }
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
    /** Pause after a unit's last move, in ms (see setMovePause). */
    private volatile int movePause = 900;
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
                // Line 1 for people, line 2 the nation (for its flag).
                about = Messages.message(game.getTurn().getLabel()) + ", "
                    + Messages.message(me.getNationLabel())
                    + "\n" + me.getNationId();
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
        String[] lines = readSaveInfo(file);
        return (lines.length > 0 && !lines[0].isEmpty()) ? lines[0] : "Saved game";
    }

    /**
     * The nation of a saved game.
     *
     * @param file The save file.
     * @return Its nation id, or null if not known.
     */
    public static String saveNation(File file) {
        String[] lines = readSaveInfo(file);
        return (lines.length > 1 && !lines[1].trim().isEmpty()) ? lines[1].trim() : null;
    }

    private static String[] readSaveInfo(File file) {
        File txt = new File(file.getPath() + ".txt");
        try {
            return new String(java.nio.file.Files.readAllBytes(txt.toPath()),
                              StandardCharsets.UTF_8).split("\n");
        } catch (Exception e) {
            return new String[0];
        }
    }

    /**
     * Start a new game: the server, then our login, then the launch.
     * Runs on the worker; progress is reported through the listener.
     *
     * @param spec The {@code Specification} to play with.
     */
    public void start(Specification spec) {
        start(spec, null);
    }

    /**
     * Start a new game as a particular nation.
     *
     * @param spec The {@code Specification} to play with.
     * @param nationId The nation to play, or null for the default.
     */
    public void start(Specification spec, String nationId) {
        worker.execute(() -> {
            try {
                status("Starting the game...");
                server = new FreeColServer(false, true, spec,
                    InetAddress.getLoopbackAddress(), -1, "android");
                if (!connectAndLogin()) return;
                String playing = Messages.message(me.getNationLabel());
                if (nationId != null && !nationId.equals(me.getNationId())) {
                    net.sf.freecol.common.model.Nation nation = spec.getNation(nationId);
                    if (nation != null) {
                        api.setNation(nation);
                        playing = Messages.getName(nation);
                    }
                }
                status("Playing as " + playing + ". Creating the New World...");
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
     * Replace what a colony builds, in order.
     *
     * @param colony The {@code Colony}.
     * @param queue The new build queue (may be empty).
     */
    public void setBuildQueue(Colony colony, List<BuildableType> queue) {
        if (colony == null || queue == null) return;
        final List<BuildableType> copy = new ArrayList<>(queue);
        worker.execute(() -> {
            if (!isMyTurn()) return;
            api.setBuildQueue(colony, copy);
            changed();
        });
    }

    /**
     * What it would cost to finish the current build now, or -1 if
     * that is not possible (the game option is off, nothing is being
     * built, or it is too dear).  Call while holding {@link #lock()}.
     *
     * @param colony The {@code Colony}.
     * @return The price in gold, or -1.
     */
    public int payToFinishPrice(Colony colony) {
        if (colony == null || colony.getCurrentlyBuilding() == null
            || !game.getSpecification().getBoolean(
                net.sf.freecol.common.option.GameOptions.PAY_FOR_BUILDING)) {
            return -1;
        }
        int price = colony.getPriceForBuilding();
        return (price > 0) ? price : -1;
    }

    /**
     * Pay gold to finish the current build next turn.
     *
     * @param colony The {@code Colony}.
     */
    public void payForBuilding(Colony colony) {
        if (colony == null) return;
        worker.execute(() -> {
            if (!isMyTurn()) return;
            synchronized (lock) {
                if (!colony.canPayToFinishBuilding()) {
                    status("You can not afford to finish it.");
                    return;
                }
            }
            api.payForBuilding(colony);
            synchronized (lock) {
                if (colony.getPriceForBuilding() == 0) {
                    status("Paid. It will be finished next turn.");
                }
            }
            changed();
        });
    }

    /**
     * Have a teacher in the schoolhouse teach a particular student.
     *
     * @param student The student, working in the same colony.
     * @param teacher The teacher.
     */
    public void assignTeacher(Unit student, Unit teacher) {
        if (student == null || teacher == null) return;
        worker.execute(() -> {
            if (!isMyTurn()) return;
            synchronized (lock) {
                if (!student.canBeStudent(teacher)) {
                    status(student.getDescription() + " can not learn from "
                        + teacher.getDescription() + ".");
                    return;
                }
            }
            api.assignTeacher(student, teacher);
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
     * Bring a ship or wagon next to a colony into it, so it can load
     * and unload.  Does nothing if it is there already.  Uses up a
     * move; with none left it says so and stays put.
     *
     * @param carrier The carrier {@code Unit}.
     * @param colony The {@code Colony}.
     */
    public void bringIntoPort(Unit carrier, Colony colony) {
        if (carrier == null || colony == null) return;
        worker.execute(() -> {
            if (!isMyTurn()) return;
            Direction d;
            synchronized (lock) {
                Tile t = carrier.getTile();
                if (t == null || t == colony.getTile()) return;
                d = t.getDirection(colony.getTile());
                if (d == null) {
                    status(carrier.getDescription() + " is not next to "
                        + colony.getName() + ".");
                    return;
                }
                if (carrier.getMovesLeft() <= 0) {
                    status(carrier.getDescription() + " has no moves left;"
                        + " it can come into port next turn.");
                    return;
                }
                if (carrier.getDestination() != null) carrier.setDestination(null);
            }
            if (!step(carrier, d, true)) {
                status(carrier.getDescription() + " could not enter "
                    + colony.getName() + ".");
            }
            synchronized (lock) {
                selected = carrier;
            }
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
            synchronized (lock) {
                // Outside a colony (or Europe) unloading would dump the
                // cargo overboard.
                boolean inPort = carrier.isInEurope()
                    || (carrier.getTile() != null && carrier.getTile().getColony() != null);
                if (!inPort) return;
            }
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

    // Europe

    /**
     * Can a unit set sail for Europe from where it is?  Call while
     * holding {@link #lock()}.
     *
     * @param unit The {@code Unit}.
     * @return True for a ship in the New World.
     */
    public boolean canSailToEurope(Unit unit) {
        return unit != null && unit.isNaval() && unit.hasTile()
            && me != null && me.getEurope() != null
            && unit.getType().canMoveToHighSeas();
    }

    /**
     * Send a ship to Europe: out to the high seas, then across.  If
     * it can not reach the high seas this turn it carries on next
     * turn.
     *
     * @param unit The ship.
     */
    public void sailToEurope(Unit unit) {
        if (unit == null) return;
        worker.execute(() -> {
            if (!isMyTurn()) return;
            Europe europe;
            synchronized (lock) {
                if (!canSailToEurope(unit)) return;
                europe = me.getEurope();
            }
            api.setDestination(unit, europe);
            headForEurope(unit);
            afterOrders(null);
        });
    }

    /** Move a ship along its way to Europe, as far as it gets. */
    private void headForEurope(Unit unit) {
        final Europe europe;
        synchronized (lock) {
            europe = me.getEurope();
        }
        for (int guard = 0; guard < 50; guard++) {
            Direction d = null;
            boolean cross = false;
            synchronized (lock) {
                Tile t = unit.getTile();
                if (unit.isDisposed() || t == null) return;
                if (t.isDirectlyHighSeasConnected()) {
                    cross = true;
                } else {
                    if (unit.getMovesLeft() <= 0) return;
                    PathNode path = unit.findPath(europe);
                    if (path == null || path.next == null) {
                        status("This ship can not find a way to Europe.");
                        unit.setDestination(null);
                        return;
                    }
                    PathNode next = path.next;
                    if (next.getTile() == null) {
                        cross = true;
                    } else {
                        if (next.getTurns() > 0) return;
                        d = next.getDirection();
                    }
                }
            }
            if (cross) {
                api.moveTo(unit, europe);
                synchronized (lock) {
                    if (!unit.hasTile()) {
                        status(unit.getDescription() + " sails for Europe ("
                            + unit.getWorkLeft() + " turns).");
                    }
                }
                return;
            }
            if (d == null || !step(unit, d, false)) return;
        }
    }

    /**
     * Send a ship in Europe back to the New World.
     *
     * @param unit The ship.
     */
    public void sailToAmerica(Unit unit) {
        sailToAmerica(unit, null);
    }

    /**
     * Send a ship in Europe back to the New World, to one of our
     * coastal colonies, or (with null) to where it last arrived.
     *
     * @param unit The ship.
     * @param colony The {@code Colony} to sail to, or null.
     */
    public void sailToAmerica(Unit unit, Colony colony) {
        if (unit == null) return;
        worker.execute(() -> {
            if (!isMyTurn()) return;
            synchronized (lock) {
                if (!unit.isInEurope() || !unit.isNaval()) return;
            }
            if (colony != null) {
                api.moveTo(unit, colony);
            } else {
                api.moveTo(unit, game.getMap());
            }
            synchronized (lock) {
                if (!unit.isInEurope()) {
                    status(unit.getDescription() + " sets sail for the New World.");
                }
            }
            changed();
        });
    }

    /**
     * Recruit one of the three emigrants waiting in Europe.
     *
     * @param index 0, 1 or 2.
     */
    public void recruit(int index) {
        worker.execute(() -> {
            if (!isMyTurn()) return;
            synchronized (lock) {
                if (!me.checkGold(me.getEuropeanRecruitPrice())) {
                    status("You can not afford to recruit.");
                    return;
                }
            }
            api.emigrate(Europe.MigrationType.migrantIndexToSlot(index));
            changed();
        });
    }

    /**
     * Train (or buy) a unit in Europe.
     *
     * @param type The {@code UnitType}.
     */
    public void train(UnitType type) {
        if (type == null) return;
        worker.execute(() -> {
            if (!isMyTurn()) return;
            synchronized (lock) {
                int price = me.getEurope().getUnitPrice(type);
                if (price < 0 || !me.checkGold(price)) {
                    status("You can not afford that.");
                    return;
                }
            }
            api.trainUnitInEurope(type);
            changed();
        });
    }

    /**
     * Buy goods in Europe onto a ship there.
     *
     * @param carrier The ship.
     * @param type The {@code GoodsType}.
     * @param amount How much to buy (it buys less if you can not
     *     afford it all).
     */
    public void buy(Unit carrier, GoodsType type, int amount) {
        if (carrier == null || type == null) return;
        worker.execute(() -> {
            if (!isMyTurn()) return;
            int n = amount;
            synchronized (lock) {
                if (!carrier.isInEurope()) return;
                if (!me.canTrade(type)) {
                    status("That is under boycott.");
                    return;
                }
                int each = me.getMarket().getCostToBuy(type);
                if (each > 0) n = Math.min(n, me.getGold() / each);
                if (n <= 0) {
                    status("You can not afford any.");
                    return;
                }
            }
            api.loadGoods(me.getEurope(), type, n, carrier);
            changed();
        });
    }

    /**
     * Sell goods from a ship in Europe.
     *
     * @param carrier The ship.
     * @param type The {@code GoodsType}.
     * @param amount How much to sell.
     */
    public void sell(Unit carrier, GoodsType type, int amount) {
        if (carrier == null || type == null || amount <= 0) return;
        worker.execute(() -> {
            if (!isMyTurn()) return;
            synchronized (lock) {
                if (!carrier.isInEurope()) return;
                if (!me.canTrade(type)) {
                    status("That is under boycott.");
                    return;
                }
            }
            api.unloadGoods(type, amount, carrier);
            changed();
        });
    }

    /**
     * Put a unit aboard a ship in the same place (Europe's docks, or
     * a colony).
     *
     * @param unit The unit.
     * @param carrier The ship.
     */
    public void board(Unit unit, Unit carrier) {
        if (unit == null || carrier == null) return;
        worker.execute(() -> {
            if (!isMyTurn()) return;
            synchronized (lock) {
                boolean together = (unit.isInEurope() && carrier.isInEurope())
                    || (unit.getTile() != null && unit.getTile() == carrier.getTile());
                if (!together) return; // e.g. the ship could not come in
                if (!carrier.canAdd(unit)) {
                    status("There is no room aboard.");
                    return;
                }
            }
            api.embark(unit, carrier, null);
            changed();
        });
    }

    // Equipment

    /** A way to equip a unit, and what it costs. */
    public static final class EquipChoice {
        public final Role role;
        public final int count;
        public final String label;

        EquipChoice(Role role, int count, String label) {
            this.role = role;
            this.count = count;
            this.label = label;
        }
    }

    /**
     * How a unit could be equipped where it is: in a colony (from its
     * warehouse) or in Europe (bought at market prices).  Each role is
     * offered at the most the colony has goods for, or you have gold
     * for.  Call while holding {@link #lock()}.
     *
     * @param unit The {@code Unit}.
     * @return The choices, empty if it can not be equipped here.
     */
    public List<EquipChoice> equipChoices(Unit unit) {
        List<EquipChoice> result = new ArrayList<>();
        if (unit == null || me == null || !unit.isPerson()) return result;
        final Colony colony = unit.getColony();
        final boolean europe = unit.isInEurope() && !unit.isOnCarrier();
        if (colony == null && !europe) return result;
        for (Role role : game.getSpecification().getRolesList()) {
            if (!unit.roleIsAvailable(role)) continue;
            if (role == unit.getRole() && !role.isDefaultRole()
                && unit.getRoleCount() >= role.getMaximumCount()) continue;
            int best = -1;
            String cost = "";
            int max = role.isDefaultRole() ? 0 : role.getMaximumCount();
            for (int n = max; n >= 0 && best < 0; n--) {
                if (n == 0 && !role.isDefaultRole()) break;
                List<AbstractGoods> need = unit.getGoodsDifference(role, n);
                if (europe) {
                    try {
                        int price = me.getEurope().priceGoods(need);
                        if (price >= 0 && me.checkGold(price)) {
                            best = n;
                            if (price > 0) cost = " (" + price + " gold)";
                        }
                    } catch (FreeColException fce) {} // Boycott etc.
                } else {
                    boolean ok = true;
                    StringBuilder sb = new StringBuilder();
                    for (AbstractGoods ag : need) {
                        if (ag.getAmount() <= 0) continue;
                        if (colony.getGoodsCount(ag.getType()) < ag.getAmount()) ok = false;
                        sb.append((sb.length() == 0) ? " (" : ", ")
                          .append(ag.getAmount()).append(' ')
                          .append(Messages.getName(ag.getType()));
                    }
                    if (ok) {
                        best = n;
                        if (sb.length() > 0) cost = sb.append(')').toString();
                    }
                }
            }
            if (best < 0) continue;
            if (role == unit.getRole() && best == unit.getRoleCount()) continue;
            String name = role.isDefaultRole() ? "No equipment"
                : Messages.getName(role)
                + ((role.getMaximumCount() > 1) ? " x" + best : "");
            result.add(new EquipChoice(role, best, name + cost));
        }
        return result;
    }

    /**
     * Equip a unit.
     *
     * @param unit The {@code Unit}.
     * @param choice One of {@link #equipChoices}.
     */
    public void equip(Unit unit, EquipChoice choice) {
        if (unit == null || choice == null) return;
        worker.execute(() -> {
            if (!isMyTurn()) return;
            api.equipUnitForRole(unit, choice.role, choice.count);
            synchronized (lock) {
                if (unit.getRole() == choice.role) {
                    status(unit.getDescription() + " is ready.");
                } else {
                    status("Could not equip " + unit.getDescription() + ".");
                }
            }
            changed();
        });
    }


    // Pioneer work

    /**
     * What a unit could build on its tile: roads, ploughing, clearing
     * forest.  Call while holding {@link #lock()}.
     *
     * @param unit The {@code Unit}.
     * @return The improvements, with labels.
     */
    public List<ColonyInfo.Choice<TileImprovementType>> improvementChoices(Unit unit) {
        List<ColonyInfo.Choice<TileImprovementType>> result = new ArrayList<>();
        if (unit == null || !unit.hasTile() || unit.isOnCarrier()
            || !unit.checkSetState(UnitState.IMPROVING)) return result;
        Tile t = unit.getTile();
        for (TileImprovementType ti : game.getSpecification()
                 .getTileImprovementTypeList()) {
            if (ti.isNatural() || !t.isImprovementTypeAllowed(ti)
                || !ti.isWorkerAllowed(unit)) continue;
            int turns = t.getType().getBasicWorkTurns() + ti.getAddWorkTurns();
            String label = Messages.getName(ti);
            if (ti.isChangeType() && ti.getChange(t.getType()) != null) {
                label += " (to " + Messages.getName(ti.getChange(t.getType())) + ")";
            }
            result.add(new ColonyInfo.Choice<>(ti, label + ", about " + turns + " turns"));
        }
        return result;
    }

    /**
     * Start building an improvement on the unit's tile, buying the
     * land first if it belongs to natives and we can afford it.
     *
     * @param unit The pioneer.
     * @param type The {@code TileImprovementType}.
     */
    public void improve(Unit unit, TileImprovementType type) {
        if (unit == null || type == null) return;
        worker.execute(() -> {
            if (!isMyTurn()) return;
            Tile t;
            int price = 0;
            synchronized (lock) {
                t = unit.getTile();
                if (t == null) return;
                // The server throws (and the request then hangs) if
                // the unit can not start work, e.g. no moves left.
                if (!unit.checkSetState(UnitState.IMPROVING)
                    || !t.isImprovementTypeAllowed(type)
                    || !type.isWorkerAllowed(unit)) {
                    status("This unit can not start that work now.");
                    return;
                }
                if (!me.owns(t)) {
                    price = me.getLandPrice(t);
                    if (price < 0) {
                        status("That land can not be claimed.");
                        return;
                    }
                    if (price > me.getGold()) {
                        status("The natives want " + price
                            + " gold for that land, which you do not have.");
                        return;
                    }
                }
            }
            if (price >= 0) {
                boolean owned;
                synchronized (lock) {
                    owned = me.owns(t);
                }
                if (!owned) api.claimTile(t, unit, price);
            }
            api.changeWorkImprovementType(unit, type);
            synchronized (lock) {
                TileImprovement ti = unit.getWorkImprovement();
                if (ti != null && ti.getType() == type) {
                    status(unit.getDescription() + " starts work: "
                        + Messages.getName(type) + ", "
                        + unit.getWorkTurnsLeft() + " turns.");
                } else {
                    status("Could not start that work.");
                }
            }
            afterOrders(null);
        });
    }


    // Other unit orders

    /**
     * Clear a unit's orders: its destination, fortifying, sentry or
     * pioneer work.
     *
     * @param unit The {@code Unit}.
     */
    public void clearOrders(Unit unit) {
        if (unit == null) return;
        worker.execute(() -> {
            if (!isMyTurn()) return;
            boolean dest;
            synchronized (lock) {
                dest = unit.getDestination() != null;
            }
            if (dest) api.setDestination(unit, null);
            synchronized (lock) {
                if (unit.getState() != UnitState.ACTIVE
                    && unit.checkSetState(UnitState.ACTIVE)) {
                    // Fall through to the server below.
                } else {
                    changed();
                    return;
                }
            }
            api.changeState(unit, UnitState.ACTIVE);
            synchronized (lock) {
                selected = unit;
            }
            changed();
        });
    }

    /**
     * Disband a unit for good.
     *
     * @param unit The {@code Unit}.
     */
    public void disband(Unit unit) {
        if (unit == null) return;
        worker.execute(() -> {
            if (!isMyTurn()) return;
            String name;
            synchronized (lock) {
                name = unit.getDescription();
            }
            api.disbandUnit(unit);
            synchronized (lock) {
                if (unit.isDisposed()) status(name + " has been disbanded.");
            }
            afterOrders(null);
        });
    }

    /**
     * Describe what a unit is doing, for the map's info line.  Call
     * while holding {@link #lock()}.
     *
     * @param unit The {@code Unit}.
     * @return A short description, or "" if it is just waiting.
     */
    public String describeOrders(Unit unit) {
        if (unit == null) return "";
        TileImprovement ti = unit.getWorkImprovement();
        if (unit.getState() == UnitState.IMPROVING && ti != null) {
            return Messages.getName(ti.getType()) + ", "
                + unit.getWorkTurnsLeft() + " turns left";
        }
        if (unit.getDestination() instanceof Europe) return "sailing to Europe";
        if (destinationTile(unit) != null) {
            Tile t = destinationTile(unit);
            return (t.getSettlement() != null)
                ? "on its way to " + t.getSettlement().getName() : "on its way";
        }
        switch (unit.getState()) {
        case FORTIFIED: case FORTIFYING: return "fortified";
        case SENTRY: return "on sentry";
        default: return "";
        }
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
        case MOVE: case MOVE_HIGH_SEAS:
            api.move(unit, d);
            break;
        case EXPLORE_LOST_CITY_RUMOUR:
            if (!exploreRumour(unit, d, to)) return false;
            break;
        case EMBARK:
            if (!last || carrier == null) return false;
            api.embark(unit, carrier, d);
            break;
        case ATTACK_UNIT:
            if (!last) return false;
            attackUnit(unit, d, to);
            return false;
        case ATTACK_SETTLEMENT:
            if (!last) return false;
            armedAtSettlement(unit, d, to);
            return false;
        case ENTER_INDIAN_SETTLEMENT_WITH_FREE_COLONIST:
            if (last) learnSkill(unit, d, to);
            return false;
        case ENTER_INDIAN_SETTLEMENT_WITH_SCOUT:
            if (last) scoutSettlement(unit, d, to);
            return false;
        case ENTER_INDIAN_SETTLEMENT_WITH_MISSIONARY:
            if (last) missionary(unit, d, to);
            return false;
        case ENTER_FOREIGN_COLONY_WITH_SCOUT:
            if (last) scoutColony(unit, d, to);
            return false;
        case ENTER_SETTLEMENT_WITH_CARRIER_AND_GOODS:
            if (last) tradeAt(unit, to);
            return false;
        case MOVE_NO_MOVES:
            status("No moves left this turn.");
            return false;
        case MOVE_NO_ACCESS_CONTACT:
            status("Make contact with these natives first (move next to them).");
            return false;
        case MOVE_NO_ACCESS_WAR:
            status("They will not trade while you are at war.");
            return false;
        case MOVE_NO_ACCESS_MISSION_BAN:
            status("They have banned your missionaries.");
            return false;
        case MOVE_NO_ACCESS_SKILL:
            status("This unit can not learn their skill.");
            return false;
        case MOVE_NO_ACCESS_GOODS:
            status("Bring goods to trade.");
            return false;
        case MOVE_NO_ATTACK_CIVILIAN:
            status("Only soldiers and dragoons can attack.");
            return false;
        case MOVE_NO_ACCESS_SETTLEMENT:
            status("That settlement will not let this unit in.");
            return false;
        default:
            status("Can not move there.");
            return false;
        }
        synchronized (lock) {
            return !unit.isDisposed() && unit.getTile() != from;
        }
    }

    // Moves that ask the player first (worker only).

    /** Ask before exploring a rumour; burial mounds ask twice. */
    private boolean exploreRumour(Unit unit, Direction d, Tile to) {
        int c = choose("Lost City Rumour", "There are rumours of something"
            + " here. It could be treasure, or trouble. Explore it?",
            "Explore", "Leave it");
        if (c != 0) {
            api.setDestination(unit, null);
            return false;
        }
        boolean mounds;
        synchronized (lock) {
            mounds = to != null && to.getLostCityRumour() != null
                && to.getLostCityRumour().getType()
                    == net.sf.freecol.common.model.LostCityRumour.RumourType.MOUNDS;
        }
        if (mounds && choose("Burial mounds", "These are burial mounds. "
                + "Digging might anger the natives. Dig anyway?",
                "Dig", "Leave them") != 0) {
            api.declineMounds(unit, d);
            return false;
        }
        api.move(unit, d);
        reportMessages();
        return true;
    }

    private void attackUnit(Unit unit, Direction d, Tile to) {
        String who;
        synchronized (lock) {
            Unit def = (to == null) ? null : to.getDefendingUnit(unit);
            who = (def == null) ? "them" : "the "
                + Messages.message(def.getOwner().getNationLabel()) + " "
                + def.getDescription();
        }
        if (choose("Attack", "Attack " + who + "?", "Attack", "Cancel") != 0) return;
        api.attack(unit, d);
        reportMessages();
    }

    /** A soldier or dragoon at someone else's settlement. */
    private void armedAtSettlement(Unit unit, Direction d, Tile to) {
        Settlement st;
        boolean natives;
        synchronized (lock) {
            st = (to == null) ? null : to.getSettlement();
            if (st == null) return;
            natives = st instanceof IndianSettlement;
        }
        int c = natives
            ? choose(st.getName(), "Your soldiers stand before " + st.getName() + ".",
                     "Demand tribute", "Attack", "Leave")
            : choose(st.getName(), "Your soldiers stand before " + st.getName() + ".",
                     "Attack", "Leave");
        if (natives && c == 0) {
            api.demandTribute(unit, d);
            reportMessages();
        } else if ((natives && c == 1) || (!natives && c == 0)) {
            if (choose("Attack", "Really attack " + st.getName() + "?",
                       "Attack", "Cancel") != 0) return;
            api.attack(unit, d);
            reportMessages();
        }
    }

    /** A colonist asks to learn the settlement's skill. */
    private void learnSkill(Unit unit, Direction d, Tile to) {
        api.askSkill(unit, d); // Refresh what they teach
        IndianSettlement is;
        net.sf.freecol.common.model.UnitType skill;
        boolean canLearn;
        synchronized (lock) {
            is = (to == null) ? null : to.getIndianSettlement();
            if (is == null) return;
            skill = is.getLearnableSkill();
            canLearn = unit.getUnitChange(
                net.sf.freecol.common.model.UnitChangeType.NATIVES) != null;
        }
        if (skill == null) {
            status(is.getName() + " has nothing more to teach.");
            return;
        }
        final String skillName = Messages.getName(skill);
        if (!canLearn) {
            status("This colonist can not learn to be a " + skillName + ".");
            return;
        }
        if (choose(is.getName(), "The people of " + is.getName()
                + " will teach your colonist to be a " + skillName + ".",
                "Learn", "Leave") != 0) return;
        api.learnSkill(unit, d);
        synchronized (lock) {
            if (unit.isDisposed()) {
                status("Your colonist was killed!");
            } else if (unit.getType() == skill) {
                status("Your colonist is now a " + skillName + ".");
            } else {
                status("They would not teach your colonist.");
            }
        }
        reportMessages();
    }

    /** A scout at a native settlement. */
    private void scoutSettlement(Unit unit, Direction d, Tile to) {
        api.scoutSettlement(unit, d); // Learn about it
        IndianSettlement is;
        String about;
        synchronized (lock) {
            is = (to == null) ? null : to.getIndianSettlement();
            if (is == null) return;
            StringBuilder sb = new StringBuilder("Your scout reaches " + is.getName()
                + ", a settlement of the "
                + Messages.message(is.getOwner().getNationLabel()) + ".");
            if (is.getLearnableSkill() != null) {
                sb.append(" They teach: ").append(Messages.getName(is.getLearnableSkill()))
                  .append('.');
            }
            List<GoodsType> wanted = is.getWantedGoods();
            if (wanted != null && !wanted.isEmpty() && wanted.get(0) != null) {
                sb.append(" They want: ");
                for (int k = 0; k < wanted.size(); k++) {
                    if (wanted.get(k) == null) continue;
                    if (k > 0) sb.append(", ");
                    sb.append(Messages.getName(wanted.get(k)));
                }
                sb.append('.');
            }
            about = sb.toString();
        }
        int c = choose(is.getName(), about, "Speak with the chief",
                       "Demand tribute", "Attack", "Leave");
        switch (c) {
        case 0:
            api.scoutSpeakToChief(unit, is);
            break;
        case 1:
            api.demandTribute(unit, d);
            break;
        case 2:
            if (choose("Attack", "Really attack " + is.getName() + "?",
                       "Attack", "Cancel") == 0) api.attack(unit, d);
            break;
        default:
            return;
        }
        reportMessages();
    }

    /** A missionary at a native settlement. */
    private void missionary(Unit unit, Direction d, Tile to) {
        IndianSettlement is;
        boolean establish, denounce;
        synchronized (lock) {
            is = (to == null) ? null : to.getIndianSettlement();
            if (is == null) return;
            establish = !is.hasMissionary();
            denounce = !establish && !is.hasMissionary(me);
        }
        List<String> opts = new ArrayList<>();
        if (establish) opts.add("Establish a mission");
        if (denounce) opts.add("Denounce the other mission as heresy");
        opts.add("Incite them against another nation");
        opts.add("Leave");
        int c = choose(is.getName(), "Your missionary reaches " + is.getName() + ".",
                       opts.toArray(new String[0]));
        if (c < 0) return;
        String pick = opts.get(c);
        if (pick.startsWith("Establish") || pick.startsWith("Denounce")) {
            api.missionary(unit, d, pick.startsWith("Denounce"));
            synchronized (lock) {
                if (is.hasMissionary(me)) {
                    status("Your mission at " + is.getName() + " is established.");
                    me.invalidateCanSeeTiles();
                }
            }
            reportMessages();
        } else if (pick.startsWith("Incite")) {
            List<Player> enemies = new ArrayList<>();
            List<String> names = new ArrayList<>();
            names.add("Never mind");
            synchronized (lock) {
                game.getLiveEuropeanPlayers(me).forEach(p -> {
                        enemies.add(p);
                        names.add(Messages.message(p.getNationLabel()));
                    });
            }
            int e = choose("Incite", "Against whom?", names.toArray(new String[0]));
            // The reply says what it costs; see InciteMessage in answer().
            if (e > 0) api.incite(unit, is, enemies.get(e - 1), -1);
        }
    }

    /** A scout at another European colony. */
    private void scoutColony(Unit unit, Direction d, Tile to) {
        Settlement st;
        synchronized (lock) {
            st = (to == null) ? null : to.getSettlement();
            if (st == null) return;
        }
        int c = choose(st.getName(), "Your scout reaches " + st.getName() + ".",
                       "Spy on the colony", "Attack", "Leave");
        if (c == 0) {
            api.spy(unit, st);
        } else if (c == 1) {
            if (choose("Attack", "Really attack " + st.getName() + "?",
                       "Attack", "Cancel") == 0) {
                api.attack(unit, d);
                reportMessages();
            }
        }
    }

    /** A ship or wagon train with goods at a settlement. */
    private void tradeAt(Unit unit, Tile to) {
        IndianSettlement is;
        synchronized (lock) {
            is = (to == null) ? null : to.getIndianSettlement();
        }
        if (is == null) {
            status("Trading with other Europeans is not in the game yet.");
            return;
        }
        // Trade round by round, waiting here for each reply, so that
        // nothing else (such as ending the turn, which the server
        // will not do mid-trade) gets in between.
        tradeReplies.clear();
        tradeOpen = true;
        try {
            api.newNativeTradeSession(unit, is);
            while (true) {
                Object[] reply = tradeReplies.poll(20, java.util.concurrent.TimeUnit.SECONDS);
                if (reply == null) {
                    status("The natives did not answer.");
                    break;
                }
                if (!nativeTrade((NativeTradeAction)reply[0], (NativeTrade)reply[1])) break;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            tradeOpen = false;
        }
        reportMessages();
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
                Tile dest = destinationTile(unit);
                if (dest == null) return;
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
            synchronized (lock) {
                Tile dest = destinationTile(unit);
                if (dest != null && unit.getTile() == dest) {
                    unit.setDestination(null);
                    break; // Arrived
                }
            }
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

    /**
     * Where on the map a unit is heading: a tile, or a settlement's
     * tile (ships sent from Europe to a colony).  Call while holding
     * {@link #lock()}.
     *
     * @param unit The {@code Unit}.
     * @return The {@code Tile}, or null if it is not heading anywhere
     *     on the map.
     */
    private static Tile destinationTile(Unit unit) {
        net.sf.freecol.common.model.Location dest = unit.getDestination();
        if (dest instanceof Tile) return (Tile)dest;
        if (dest instanceof Settlement) return ((Settlement)dest).getTile();
        return null;
    }

    /** After a unit's orders: keep it selected while it can move. */
    private void afterOrders(Unit unit) {
        boolean pause = false;
        synchronized (lock) {
            if (unit != null && !unit.isDisposed() && unit.getOwner() == me
                && unit.getMovesLeft() > 0 && unit.getDestination() == null) {
                selected = unit;
            } else if (unit != null && !unit.isDisposed() && movePause > 0) {
                // Show where the unit got to, and what it found, before
                // moving on to the next one.
                selected = unit;
                pause = true;
            } else {
                selectNextLocked();
            }
        }
        changed();
        if (!pause) return;
        try {
            Thread.sleep(movePause);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        synchronized (lock) {
            // Unless the player picked another unit meanwhile.
            if (selected == unit) selectNextLocked();
        }
        changed();
    }

    /**
     * How long to show a unit that has used its moves before
     * selecting the next one.
     *
     * @param millis The pause in milliseconds (0 for none).
     */
    public void setMovePause(int millis) {
        this.movePause = millis;
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
        List<Unit> cleared = new ArrayList<>();
        synchronized (lock) {
            me.invalidateCanSeeTiles();
            me.resetIterators();
            for (Unit u : me.getUnitSet()) {
                if (!u.hasTile()) continue;
                if (destinationTile(u) != null
                    || u.getDestination() instanceof Europe) {
                    going.add(u);
                } else if (u.getDestination() instanceof net.sf.freecol.common.model.Map) {
                    // "The New World": arrived, so nothing left to do.
                    u.setDestination(null);
                    cleared.add(u);
                }
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
        for (Unit u : cleared) api.setDestination(u, null);
        for (Unit u : going) {
            boolean toEurope;
            synchronized (lock) {
                toEurope = u.getDestination() instanceof Europe;
            }
            if (toEurope) headForEurope(u); else followPath(u);
        }
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
     * Questions from the server.  They arrive on the connection's
     * thread with the lock held, so the asking (which waits for the
     * player) and the replies happen on the worker.
     */
    private void answer(Message m) {
        if (m instanceof FirstContactMessage) {
            FirstContactMessage fm = (FirstContactMessage)m;
            final Player p = fm.getPlayer(game), o = fm.getOtherPlayer(game);
            final Tile t = fm.getTile(game);
            final int n = fm.getSettlementCount();
            if (p != me || o == null) return;
            final String nation = Messages.message(o.getNationLabel());
            worker.execute(() -> {
                int c = choose("First contact", "You meet the " + nation
                    + ". They have " + ((n > 0) ? n + " settlements" : "many settlements")
                    + " and welcome you in peace. Their land is yours to use only "
                    + "if you buy it, or take it.",
                    "Make peace", "Refuse their friendship");
                boolean peace = c != 1;
                api.firstContact(p, o, t, peace);
                status(peace ? "You make peace with the " + nation + "."
                       : "You refuse the " + nation + "'s friendship.");
            });
        } else if (m instanceof ChooseFoundingFatherMessage) {
            final List<FoundingFather> ffs
                = ((ChooseFoundingFatherMessage)m).getFathers(game);
            if (ffs == null || ffs.isEmpty()) return;
            final String[] labels = new String[ffs.size()];
            for (int k = 0; k < labels.length; k++) {
                FoundingFather ff = ffs.get(k);
                labels[k] = Messages.getName(ff) + " ("
                    + Messages.message(ff.getTypeKey()) + "): "
                    + Messages.getDescription(ff);
            }
            worker.execute(() -> {
                int c = choose("Continental Congress",
                    "Which founding father should Congress seek next?", labels);
                FoundingFather ff = ffs.get(Math.max(0, c));
                api.chooseFoundingFather(ffs, ff);
                status("Congress will seek " + Messages.getName(ff) + ".");
            });
        } else if (m instanceof MonarchActionMessage) {
            MonarchActionMessage mm = (MonarchActionMessage)m;
            final MonarchAction action = mm.getAction();
            final String text = (mm.getTemplate() == null) ? ""
                : Messages.message(mm.getTemplate());
            if (action == MonarchAction.RAISE_TAX_ACT
                || action == MonarchAction.RAISE_TAX_WAR) {
                worker.execute(() -> {
                    int c = choose("The King", text, "Accept the tax",
                        "Refuse (the goods will be boycotted)");
                    api.answerMonarch(action, c != 1);
                    status((c != 1) ? "You accept the new tax."
                           : "You refuse the tax. Those goods are now boycotted.");
                });
            } else if (action == MonarchAction.MONARCH_MERCENARIES
                || action == MonarchAction.HESSIAN_MERCENARIES) {
                worker.execute(() -> {
                    int c = choose("Mercenaries", text, "No thanks", "Hire them");
                    api.answerMonarch(action, c == 1);
                });
            } else if (!text.isEmpty()) {
                status(text);
            }
        } else if (m instanceof IndianDemandMessage) {
            IndianDemandMessage im = (IndianDemandMessage)m;
            final Unit u = im.getUnit(game);
            final Colony c = im.getColony(game);
            final GoodsType gt = im.getType(game);
            final int amount = im.getAmount();
            if (u == null || c == null) return;
            final String what = (gt == null) ? amount + " gold"
                : amount + " " + Messages.getName(gt);
            final String who = Messages.message(u.getOwner().getNationLabel());
            worker.execute(() -> {
                int ch = choose("A demand", "The " + who + " demand " + what
                    + " from " + c.getName() + ".", "Give it to them", "Refuse");
                api.indianDemand(u, c, gt, amount, (ch != 1)
                    ? IndianDemandAction.INDIAN_DEMAND_ACCEPT
                    : IndianDemandAction.INDIAN_DEMAND_REJECT);
                status((ch != 1) ? "You give the " + who + " " + what + "."
                       : "You refuse the " + who + ". They will not be pleased.");
            });
        } else if (m instanceof NewLandNameMessage) {
            NewLandNameMessage nm = (NewLandNameMessage)m;
            final Unit u = nm.getUnit(me);
            final String suggestion = nm.getNewLandName();
            if (u == null || suggestion == null) return;
            worker.execute(() -> {
                String name = askName("The New World",
                    "You have landed in the New World. What will you call it?",
                    suggestion);
                api.newLandName(u, name);
                status("You name the new land " + name + ".");
            });
        } else if (m instanceof NewRegionNameMessage) {
            // Regions are named automatically: asking for every one
            // would be a lot of typing on a phone.
            NewRegionNameMessage nm = (NewRegionNameMessage)m;
            final Region r = nm.getRegion(game);
            final Tile t = nm.getTile(game);
            final Unit u = nm.getUnit(me);
            final String name = nm.getNewRegionName();
            if (r != null && name != null) {
                status((r.isPacific()) ? "You have reached the Pacific Ocean!"
                       : "You discover " + name + ".");
                worker.execute(() -> api.newRegionName(r, t, u, name));
            }
        } else if (m instanceof FountainOfYouthMessage) {
            final int n = ((FountainOfYouthMessage)m).getMigrants();
            status("The Fountain of Youth! " + n + " colonists will join you.");
            worker.execute(() -> {
                // The server wants a specific dock slot for these.
                for (int k = 0; k < n; k++) {
                    api.emigrate(Europe.MigrationType.getDefaultSlot());
                }
            });
        } else if (m instanceof LootCargoMessage) {
            LootCargoMessage lm = (LootCargoMessage)m;
            final Unit winner = lm.getWinner(game);
            final String loser = lm.getLoserId();
            final List<Goods> goods = lm.getGoods();
            if (winner == null || goods == null || goods.isEmpty()) return;
            StringBuilder sb = new StringBuilder();
            for (Goods g : goods) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(g.getAmount()).append(' ').append(Messages.getName(g.getType()));
            }
            final String list = sb.toString();
            worker.execute(() -> {
                int c = choose("Captured cargo", "The enemy ship carried " + list
                    + ". Take what fits?", "Take it", "Leave it");
                api.loot(winner, loser, (c != 1) ? goods : new ArrayList<Goods>());
            });
        } else if (m instanceof InciteMessage) {
            InciteMessage im = (InciteMessage)m;
            final Unit u = im.getUnit(me);
            final IndianSettlement is = (u == null) ? null : im.getSettlement(u);
            final Player enemy = im.getEnemy(game);
            final int gold = im.getGold();
            if (u == null || is == null || enemy == null || gold < 0) return;
            final String foe = Messages.message(enemy.getNationLabel());
            if (!me.checkGold(gold)) {
                status("The natives want " + gold + " gold to attack the "
                    + foe + ", which you do not have.");
                return;
            }
            worker.execute(() -> {
                int c = choose("Incite the natives", "They will attack the "
                    + foe + " for " + gold + " gold.", "Never mind", "Pay them");
                if (c == 1) api.incite(u, is, enemy, gold);
            });
        } else if (m instanceof ScoutSpeakToChiefMessage) {
            ScoutSpeakToChiefMessage sm = (ScoutSpeakToChiefMessage)m;
            final Unit u = sm.getUnit(game);
            final String result = sm.getResult();
            if (result == null) return;
            switch (result) {
            case "": break;
            case "die":
                status("The chief has your scout put to death!");
                break;
            case "expert":
                status("The chief shares the tribe's knowledge. Your scout "
                    + "is now a seasoned scout.");
                break;
            case "tales":
                status("The chief tells tales of nearby lands, which now "
                    + "appear on your map.");
                break;
            case "nothing":
                status("The chief has nothing to say to you.");
                break;
            default:
                status("The chief gives you " + result + " gold as a gift.");
                break;
            }
        } else if (m instanceof SpySettlementMessage) {
            Tile t = ((SpySettlementMessage)m).getSpyTile();
            Colony c = (t == null) ? null : t.getColony();
            if (c != null) {
                status("Your scout reports: " + c.getName() + " has "
                    + c.getUnitCount() + " colonists and "
                    + t.getUnitCount() + " units outside.");
            }
        } else if (m instanceof NativeTradeMessage) {
            NativeTradeMessage nm = (NativeTradeMessage)m;
            final NativeTrade nt = nm.getNativeTrade();
            final NativeTradeAction action = nm.getAction();
            if (nt != null && nt.getUnit() != null && me.owns(nt.getUnit())) {
                if (tradeOpen) {
                    tradeReplies.offer(new Object[] { action, nt });
                } else {
                    worker.execute(() -> nativeTrade(action, nt));
                }
            }
        } else if (m instanceof DiplomacyMessage) {
            diplomacy((DiplomacyMessage)m);
        } else {
            // Animations, sounds, chat and the rest: nothing to do yet.
            logger.fine("Ignored message: " + m.getType());
        }
    }

    /** A proposal from another European nation. */
    private void diplomacy(DiplomacyMessage dm) {
        final DiplomaticTrade agreement = dm.getAgreement();
        final FreeColGameObject our = dm.getOurFCGO(game);
        final FreeColGameObject other = dm.getOtherFCGO(game);
        if (agreement == null || our == null || other == null) return;
        final Player them = agreement.getOtherPlayer(me);
        final String nation = (them == null) ? "Another nation"
            : "The " + Messages.message(them.getNationLabel());
        switch (agreement.getStatus()) {
        case PROPOSE_TRADE: {
            StringBuilder sb = new StringBuilder(nation + " propose:");
            for (TradeItem ti : agreement.getItems()) {
                sb.append("\n- ").append(Messages.message(ti.getLabel()));
                if (ti.getSource() != null
                    && !(ti instanceof net.sf.freecol.common.model.StanceTradeItem)) {
                    sb.append(" (from ").append(
                        (ti.getSource() == me) ? "you"
                        : Messages.message(ti.getSource().getNationLabel())).append(')');
                }
            }
            final String text = sb.toString();
            worker.execute(() -> {
                int c = choose("Diplomacy", text, "Reject", "Accept");
                agreement.setStatus((c == 1) ? TradeStatus.ACCEPT_TRADE
                                    : TradeStatus.REJECT_TRADE);
                api.diplomacy(our, other, agreement);
                status((c == 1) ? "You accept the proposal." : "You reject the proposal.");
            });
            break;
        }
        case ACCEPT_TRADE:
            status(nation + " accept.");
            me.invalidateCanSeeTiles();
            break;
        case REJECT_TRADE:
            status(nation + " reject the proposal.");
            break;
        default:
            break;
        }
    }

    /** True while tradeAt() waits for the natives' replies. */
    private volatile boolean tradeOpen = false;
    private final java.util.concurrent.BlockingQueue<Object[]> tradeReplies
        = new java.util.concurrent.LinkedBlockingQueue<>();

    /**
     * One round of trading with a native settlement, after the
     * server's reply to the last offer.  Runs on the worker; asks the
     * player and sends the next offer, or closes the session.
     *
     * @return True if an offer was sent and a reply should follow.
     */
    private boolean nativeTrade(NativeTradeAction action, NativeTrade nt) {
        final IndianSettlement is = nt.getIndianSettlement();
        final Unit unit = nt.getUnit();
        String prompt;
        NativeTradeAction act = null; // BUY or SELL when haggling
        NativeTradeItem nti = null;
        synchronized (lock) {
            String goods = (nt.getItem() == null || nt.getItem().getGoods() == null) ? ""
                : Messages.getName(nt.getItem().getGoods().getType());
            switch (action) {
            case ACK_OPEN: prompt = null; break;
            case ACK_BUY: prompt = "You bought the " + goods + "."; break;
            case ACK_SELL: prompt = "You sold the " + goods + "."; break;
            case ACK_GIFT: prompt = "They accept your gift of " + goods + "."; break;
            case ACK_BUY_HAGGLE: act = NativeTradeAction.BUY; nti = nt.getItem(); prompt = null; break;
            case ACK_SELL_HAGGLE: act = NativeTradeAction.SELL; nti = nt.getItem(); prompt = null; break;
            case NAK_GOODS: prompt = "They have no room for " + goods + "."; break;
            case NAK_HAGGLE:
                status("They tire of your haggling and will not trade any more.");
                return false;
            case NAK_HOSTILE:
                status("They are too angry with you to trade.");
                return false;
            case NAK_NOSALE:
                status("They have nothing to trade with you.");
                return false;
            default:
                return false;
            }
            nt.limitSettlementToUnit(3);
        }
        final String place = is.getName();
        while (true) {
            final boolean canBuy, canSell, canGift;
            synchronized (lock) {
                if (nt.getDone()) {
                    if (prompt != null) status(prompt); // e.g. "You sold..."
                    break;
                }
                canBuy = nt.canBuy();
                canSell = nt.canSell();
                canGift = nt.canGift();
            }
            if (act == null) {
                List<String> opts = new ArrayList<>();
                List<NativeTradeAction> acts = new ArrayList<>();
                opts.add("Leave");
                acts.add(null);
                if (canBuy) { opts.add("Buy from them"); acts.add(NativeTradeAction.BUY); }
                if (canSell) { opts.add("Sell to them"); acts.add(NativeTradeAction.SELL); }
                if (canGift) { opts.add("Give a gift"); acts.add(NativeTradeAction.GIFT); }
                int c = choose("Trade at " + place, (prompt == null)
                    ? "The people of " + place + " welcome you. What would you like to do?"
                    : prompt, opts.toArray(new String[0]));
                prompt = null;
                if (c <= 0) break;
                act = acts.get(c);
            }
            if (act == NativeTradeAction.BUY || act == NativeTradeAction.SELL) {
                final boolean buy = act == NativeTradeAction.BUY;
                if (nti == null) {
                    List<NativeTradeItem> items = new ArrayList<>();
                    List<String> labels = new ArrayList<>();
                    labels.add("Nothing");
                    synchronized (lock) {
                        for (NativeTradeItem i : buy ? nt.getSettlementToUnit()
                                 : nt.getUnitToSettlement()) {
                            if (!i.priceIsValid()) continue;
                            items.add(i);
                            labels.add(Messages.message(i.getGoods().getLabel(true)));
                        }
                    }
                    int c = choose(buy ? "Buy" : "Sell", buy ? "What will you buy?"
                        : "What will you sell?", labels.toArray(new String[0]));
                    if (c <= 0) { act = null; continue; }
                    nti = items.get(c - 1);
                    synchronized (lock) {
                        nt.setItem(nti);
                    }
                }
                final String what, price;
                synchronized (lock) {
                    what = Messages.message(nti.getGoods().getLabel(true));
                    price = nti.getPrice() + " gold";
                }
                int c = choose(buy ? "Buy" : "Sell", (buy ? "They will sell " : "They offer ")
                    + price + (buy ? " for " : " for your ") + what + ".",
                    "Never mind", buy ? "Buy" : "Sell", "Haggle");
                if (c == 1 || c == 2) {
                    synchronized (lock) {
                        if (buy && !me.checkGold(nti.getPrice()) && c == 1) {
                            status("You can not afford that.");
                            act = null;
                            nti = null;
                            continue;
                        }
                        if (c == 2) nti.setPrice(NativeTradeItem.PRICE_UNSET);
                    }
                    api.nativeTrade(act, nt);
                    return true; // The reply starts the next round.
                }
            } else if (act == NativeTradeAction.GIFT) {
                List<NativeTradeItem> items = new ArrayList<>();
                List<String> labels = new ArrayList<>();
                labels.add("Nothing");
                synchronized (lock) {
                    for (NativeTradeItem i : nt.getUnitToSettlement()) {
                        items.add(i);
                        labels.add(Messages.message(i.getGoods().getLabel(true)));
                    }
                }
                int c = choose("Gift", "What will you give them?",
                               labels.toArray(new String[0]));
                if (c > 0) {
                    synchronized (lock) {
                        nt.setItem(items.get(c - 1));
                    }
                    api.nativeTrade(NativeTradeAction.GIFT, nt);
                    return true;
                }
            }
            act = null;
            nti = null;
        }
        api.nativeTrade(NativeTradeAction.CLOSE, nt);
        changed();
        return false;
    }


    // Asking the player (on the worker only: these wait)

    /**
     * Ask the player to choose, and wait for the answer.
     *
     * @return The index chosen, or -1 if they backed out.
     */
    private int choose(String title, String text, String... options) {
        final java.util.concurrent.CompletableFuture<Integer> f
            = new java.util.concurrent.CompletableFuture<>();
        try {
            listener.onChoose(title, text, java.util.Arrays.asList(options),
                              f::complete);
            return f.get();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Question failed", e);
            return -1;
        }
    }

    /**
     * Ask the player for a name, and wait for it.
     *
     * @return The name, or the suggestion if none was given.
     */
    private String askName(String title, String text, String suggestion) {
        final java.util.concurrent.CompletableFuture<String> f
            = new java.util.concurrent.CompletableFuture<>();
        try {
            listener.onAskName(title, text, suggestion, f::complete);
            String name = f.get();
            return (name == null || name.trim().isEmpty()) ? suggestion : name.trim();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Question failed", e);
            return suggestion;
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
