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

import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * Plays a few turns of a {@link GameSession} as a human would, with
 * no user interface: sail west towards land, put a colonist ashore,
 * found a colony and give it some colony screen orders, ending each turn and waiting for the AI to hand
 * the turn back.  Runs on a desktop JVM
 * with only the engine on the class path.
 *
 * Usage: SessionCheck DATA_DIR [TURNS]
 */
public final class SessionCheck {

    private SessionCheck() {} // Static only

    public static void main(String[] args) {
        // Any failure must end the run: the game's threads would
        // otherwise keep the process alive.
        try {
            run(args);
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("FAILED: " + t);
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        final int turns = (args.length > 1) ? Integer.parseInt(args[1]) : 8;
        Engine.init(new File(args[0]));
        GameSession s = new GameSession(new GameSession.Listener() {
                @Override public void onChanged() {}
                @Override public void onStatus(String text) {
                    System.out.println("  > " + text);
                }
                @Override public void onChoose(String title, String text,
                        java.util.List<String> options,
                        java.util.function.IntConsumer answer) {
                    int pick = pickLike(options);
                    System.out.println("  ? " + title + ": " + text.replace('\n', ' ')
                        + " " + options + " -> " + options.get(pick));
                    if (title.startsWith("Trade at")) traded = true;
                    answer.accept(pick);
                }
            });
        File save = File.createTempFile("freecol-check", ".fsg");
        save.deleteOnExit();
        new File(save.getPath() + ".txt").deleteOnExit();
        s.setSaveFile(save);
        s.setMovePause(0); // No need to watch
        // Not the default nation, difficulty or map, to check the
        // new-game options take.
        NewGameOptions opts = new NewGameOptions();
        opts.difficulty = "model.difficulty.hard";
        opts.mapSize = "medium";
        opts.land = "continents";
        net.sf.freecol.common.model.Specification spec
            = Engine.loadSpecification(opts.difficulty);
        java.util.List<NewGameOptions.Choice> nations = NewGameOptions.nations(spec);
        for (NewGameOptions.Choice c : nations) {
            System.out.println("  nation choice: " + c.label + " - " + c.kind + " | " + c.about
                + " | " + c.units);
        }
        opts.nationId = nations.get(1).id;
        opts.applyTo(spec);
        s.start(spec, opts.nationId);
        Unit ship = null, colonist = null;
        Tile shipStart = null, colonyTile = null;
        boolean landed = false, founded = false, colonyOk = false;
        boolean shipMoved = false;
        boolean optionsOk = false;
        int played = 0;
        for (int turn = 0; turn < turns && !colonyOk; turn++) {
            waitForTurn(s, 120_000);
            s.sync();
            Tile target = null, land = null;
            synchronized (s.lock()) {
                Map map = s.getGame().getMap();
                if (ship == null) {
                    // Our ship (the game may have selected someone else
                    // first, such as a colonist aboard it).
                    ship = s.getSelectedUnit();
                    if (ship == null || !ship.isNaval()) {
                        ship = null;
                        for (Unit u : s.getMyPlayer().getUnitSet()) {
                            if (u.isNaval() && u.hasTile()) ship = u;
                        }
                    }
                    if (ship == null) {
                        System.out.println("No ship to sail; selected: " + s.getSelectedUnit());
                        break;
                    }
                    shipStart = ship.getTile();
                }
                if (turn == 0) {
                    String diff = s.getGame().getSpecification().getDifficultyLevel();
                    optionsOk = opts.nationId.equals(s.getMyPlayer().getNationId())
                        && map.getWidth() == 50 && map.getHeight() == 125
                        && "model.difficulty.hard".equals(diff);
                    System.out.println("Options: nation " + s.getMyPlayer().getNationId()
                        + " (wanted " + opts.nationId + "), map " + map.getWidth() + "x"
                        + map.getHeight() + ", " + diff + ": " + optionsOk);
                }
                System.out.println("Turn " + s.getGame().getTurn()
                    + ": " + s.getMyPlayer().getUnitCount() + " units, map "
                    + map.getWidth() + "x" + map.getHeight()
                    + ", ship at " + ship.getTile());
                Tile t = ship.getTile();
                if (t != shipStart) shipMoved = true;
                for (Tile n : t.getSurroundingTiles(1)) {
                    if (n.isLand() && n.getSettlement() == null
                        && n.getUnitCount() == 0) land = n;
                }
                // Head west towards land, staying on the same row.
                target = map.getTile(Math.max(0, t.getX() - 30), t.getY());
            }
            if (founded) {
                colonyOk = checkColony(s, colonyTile);
            } else if (landed) {
                // Found a colony where the colonist landed.
                select(s, colonist);
                s.buildColony();
                s.sync();
                synchronized (s.lock()) {
                    colonyTile = colonist.getTile();
                    founded = colonyTile.getColony() != null;
                }
            } else if (land != null) {
                // Next to land: put the first colonist ashore.
                select(s, ship);
                s.selectOnTile(ship.getTile()); // the first passenger
                s.sync();
                colonist = s.getSelectedUnit();
                System.out.println("  landing " + colonist + " on " + land);
                s.moveSelectedTo(land);
                s.sync();
                synchronized (s.lock()) {
                    landed = colonist.getTile() == land;
                }
            } else {
                select(s, ship);
                s.moveSelectedTo(target);
                s.sync();
            }
            s.endTurn();
            s.sync();
            waitForOthers(s, 120_000);
            played++;
        }
        // Sail the ship to Europe, trade if we can, and come back.
        // Equip a pioneer, start work, clear it, and set a build queue.
        boolean pioneerOk = colonyOk && checkPioneer(s, colonyTile);

        boolean europeOk = colonyOk && checkEurope(s, ship, colonyTile);

        // Trade with a coastal native village, if one is in sight.
        boolean nativesOk = !europeOk || checkNatives(s, ship);

        // A trade route between the colony and Europe, with the ship.
        boolean routeOk = !colonyOk || checkRoute(s, ship, colonyTile);

        // The menu's reports and the Colopedia.
        boolean infoOk = checkInfo(s);

        // Autosave happened at the start of this turn; load it into a
        // fresh session and check the game carries on.
        boolean reloaded = checkLoad(s, save);
        boolean moved = shipMoved;
        System.out.println("Played " + played + " turns; ship moved: " + moved
            + "; landed: " + landed + "; colony founded: " + founded
            + "; colony orders: " + colonyOk + "; pioneer: " + pioneerOk
            + "; Europe: " + europeOk + "; natives: " + nativesOk
            + (traded ? " (traded)" : "")
            + "; save and load: " + reloaded + "; new-game options: " + optionsOk
            + "; reports and Colopedia: " + infoOk + "; trade route: " + routeOk);
        boolean ok = optionsOk && moved && (colonyOk || played == turns) && reloaded
            && (europeOk || !colonyOk) && (pioneerOk || !colonyOk) && nativesOk
            && infoOk && routeOk;
        System.out.println(ok ? "OK" : "FAILED");
        System.exit(ok ? 0 : 1);
    }

    /**
     * Colony screen orders: move the founder into a building and back
     * out to a tile, and change what the colony builds.
     *
     * @return True if it all worked.
     */
    private static boolean checkColony(GameSession s, Tile colonyTile)
        throws Exception {
        Unit founder;
        net.sf.freecol.common.model.Colony colony;
        net.sf.freecol.common.model.Building building = null;
        net.sf.freecol.common.model.ColonyTile tile = null;
        java.util.List<net.sf.freecol.common.model.BuildableType> buildable;
        synchronized (s.lock()) {
            colony = colonyTile.getColony();
            if (colony == null || colony.getUnitCount() == 0) {
                System.out.println("  the colony is gone");
                return false;
            }
            founder = colony.getUnitList().get(0);
            System.out.println("Colony " + colony.getName() + ": founder at "
                + founder.getLocation() + ", building "
                + colony.getCurrentlyBuilding());
            for (net.sf.freecol.common.model.Building b : colony.getBuildings()) {
                if (b.getNoAddReason(founder)
                    == net.sf.freecol.common.model.UnitLocation.NoAddReason.NONE
                    && b.canBeWorked()) {
                    building = b;
                    break;
                }
            }
            for (net.sf.freecol.common.model.ColonyTile ct : colony.getColonyTiles()) {
                if (!ct.isColonyCenterTile() && ct != founder.getLocation()
                    && ct.getWorkTile().isLand() && ct.isEmpty()
                    && (s.getMyPlayer().owns(ct.getWorkTile())
                        || s.getMyPlayer().canClaimForSettlement(ct.getWorkTile()))) {
                    tile = ct;
                    break;
                }
            }
            buildable = s.getBuildable(colony);
        }
        System.out.println("  can build " + buildable.size() + " things");
        s.work(founder, building);
        s.sync();
        boolean inBuilding;
        synchronized (s.lock()) {
            inBuilding = founder.getLocation() == building;
            System.out.println("  moved to " + building + ": " + inBuilding);
        }
        // There may be no free land near the colony (natives own it).
        if (tile != null) s.work(founder, tile);
        s.sync();
        boolean onTile;
        synchronized (s.lock()) {
            onTile = tile == null || founder.getLocation() == tile;
            System.out.println("  moved to " + tile + ": " + onTile
                + " producing " + founder.getWorkType());
            // It starts on the crop the land yields most of.
            if (tile != null && onTile) {
                java.util.List<ColonyInfo.Choice<net.sf.freecol.common.model.GoodsType>> ch
                    = ColonyInfo.workChoices(tile, founder);
                int best = ch.isEmpty() ? 0
                    : tile.getPotentialProduction(ch.get(0).value, founder.getType());
                int got = tile.getPotentialProduction(founder.getWorkType(), founder.getType());
                System.out.println("  best crop " + (ch.isEmpty() ? "-" : ch.get(0).label)
                    + ", working for " + got);
                onTile = got >= best;
            }
        }
        net.sf.freecol.common.model.BuildableType want = null;
        synchronized (s.lock()) {
            for (net.sf.freecol.common.model.BuildableType bt : buildable) {
                if (bt != colony.getCurrentlyBuilding()) {
                    want = bt;
                    break;
                }
            }
        }
        s.setBuild(colony, want);
        s.sync();
        boolean built;
        synchronized (s.lock()) {
            built = colony.getCurrentlyBuilding() == want;
            System.out.println("  now building " + colony.getCurrentlyBuilding()
                + ": " + built);
        }
        // What the colony screen would show.
        boolean summary;
        synchronized (s.lock()) {
            net.sf.freecol.android.map.Art noArt
                = new net.sf.freecol.android.map.Art() {
                    @Override protected java.io.InputStream open(String p)
                        throws java.io.IOException {
                        throw new java.io.FileNotFoundException(p);
                    }
                    @Override protected String[] list(String d) { return null; }
                    @Override protected Img decode(java.io.InputStream in) { return null; }
                    @Override protected Img applyMask(Img i, Img m) { return null; }
                };
            ColonyInfo info = new ColonyInfo(colony, s.getMyPlayer(),
                new net.sf.freecol.android.map.IsometricRenderer(noArt));
            System.out.println("  screen: " + info.title + " | " + info.stats
                + " | " + info.building);
            System.out.println("  land " + info.land.size() + ", buildings "
                + info.buildings.size() + ", outside " + info.outside.size()
                + ", ships " + info.carriers.size() + ", goods "
                + info.warehouse.size());
            int choices = 0;
            for (ColonyInfo.LandItem li : info.land) {
                if (li.worker != null && !li.centre) {
                    choices = ColonyInfo.workChoices(li.colonyTile, li.worker.unit).size();
                    System.out.println("  " + li.worker.name + " could produce "
                        + choices + " things on " + li.tile);
                }
            }
            ColonyListInfo list = new ColonyListInfo(s.getMyPlayer(),
                new net.sf.freecol.android.map.IsometricRenderer(noArt));
            for (ColonyListInfo.Row r : list.rows) {
                System.out.println("  list: " + r.name + " size " + r.size + ", food "
                    + r.food + ", " + r.building + ", image " + r.imageKey
                    + (r.warnings.isEmpty() ? "" : ", " + r.warnings));
            }
            int builds = ColonyInfo.buildChoices(s, colony).size();
            summary = info.land.size() == 9 && !info.buildings.isEmpty()
                && info.warehouse.size() > 10 && builds > 0
                && (tile == null || choices > 0)
                && list.rows.size() == 1 && list.rows.get(0).colony == colony;
        }
        // Join: put whoever is outside the colony (or aboard a ship
        // in port) into it with the Join colony button's order.
        Unit joiner = null;
        synchronized (s.lock()) {
            for (Unit u : s.getMyPlayer().getUnitSet()) {
                if (u.isInColony()) continue;
                if (s.joinableColony(u) == colony) {
                    joiner = u;
                    break;
                }
            }
        }
        boolean joined = true;
        if (joiner != null) {
            select(s, joiner);
            s.joinColony();
            s.sync();
            synchronized (s.lock()) {
                joined = joiner.isInColony();
                System.out.println("  " + joiner + " joined: " + joined);
            }
        }
        return inBuilding && onTile && built && !buildable.isEmpty() && summary
            && joined;
    }

    /** Set when a native trade session reached the player. */
    private static volatile boolean traded = false;

    /** Answer like a curious player: trade, speak, learn; else the default. */
    private static int pickLike(java.util.List<String> options) {
        for (String want : new String[] { "Sell to them", "Sell",
                "Speak with the chief", "Learn", "Make peace" }) {
            int i = options.indexOf(want);
            if (i >= 0 && !traded2(want)) return i;
        }
        if ("Nothing".equals(options.get(0)) && options.size() > 1) return 1;
        return 0;
    }

    /** Sell once, then leave, so the session ends. */
    private static int sold = 0;
    private static boolean traded2(String want) {
        if (want.startsWith("Sell")) return sold++ >= 2;
        return false;
    }

    /**
     * Sail the ship to the nearest coastal native settlement in sight
     * and trade.  Passes if there is none in reach; fails only on an
     * error or a hang.
     */
    private static boolean checkNatives(GameSession s, Unit ship) throws Exception {
        Tile target = null;
        for (int turn = 0; turn < 10 && !traded; turn++) {
            waitForTurn(s, 120_000);
            s.sync();
            synchronized (s.lock()) {
                if (ship.isDisposed()) return true;
                if (ship.getTile() == null) {
                    // Still at sea on the way back.
                } else if (target == null) {
                    net.sf.freecol.common.model.Map map = s.getGame().getMap();
                    int best = Integer.MAX_VALUE;
                    for (int y = 0; y < map.getHeight(); y++) {
                        for (int x = 0; x < map.getWidth(); x++) {
                            Tile t = map.getTile(x, y);
                            if (t == null || !t.isExplored()
                                || t.getIndianSettlement() == null
                                || !t.isCoastland()) continue;
                            int dist = t.getDistanceTo(ship.getTile());
                            if (dist < best && ship.findPath(t) != null) {
                                best = dist;
                                target = t;
                            }
                        }
                    }
                    System.out.println("  native village by the sea: " + target
                        + ", cargo " + ship.getCompactGoodsList());
                    if (target == null || ship.getCompactGoodsList().isEmpty()) {
                        return true;
                    }
                }
            }
            if (target != null && ship.getTile() != null) {
                int before;
                synchronized (s.lock()) {
                    before = s.getMyPlayer().getGold();
                }
                select(s, ship);
                s.moveSelectedTo(target);
                s.sync();
                if (traded) {
                    Thread.sleep(1500);
                    synchronized (s.lock()) {
                        System.out.println("  gold " + before + " -> "
                            + s.getMyPlayer().getGold() + ", cargo now "
                            + ship.getCompactGoodsList());
                    }
                }
            }
            if (traded) break;
            s.endTurn();
            s.sync();
            waitForOthers(s, 120_000);
        }
        return true;
    }

    /**
     * Equip a colonist from the colony's warehouse as a pioneer, start
     * work on the land, clear the order, start again, and give the
     * colony a two-item build queue.
     *
     * @return True if it all worked (or there was nobody to spare).
     */
    private static boolean checkPioneer(GameSession s, Tile colonyTile)
        throws Exception {
        waitForTurn(s, 120_000);
        s.sync();
        net.sf.freecol.common.model.Colony colony;
        Unit unit = null;
        GameSession.EquipChoice pioneer = null;
        synchronized (s.lock()) {
            colony = colonyTile.getColony();
            if (colony == null) return false;
            if (colony.getUnitCount() < 2) {
                System.out.println("  only one colonist; no pioneer to spare");
                return true;
            }
            for (Unit u : colony.getUnitList()) {
                for (GameSession.EquipChoice c : s.equipChoices(u)) {
                    System.out.println("  " + u.getDescription() + ": " + c.label);
                    if (c.role.getId().contains("pioneer")) {
                        unit = u;
                        pioneer = c;
                    }
                }
                if (pioneer != null) break;
            }
        }
        if (pioneer == null) {
            System.out.println("  no tools for a pioneer");
            return false;
        }
        s.equip(unit, pioneer);
        s.sync();
        synchronized (s.lock()) {
            System.out.println("  now " + unit + " at " + unit.getLocation());
            if (unit.getRole() != pioneer.role) return false;
        }
        select(s, unit);
        java.util.List<ColonyInfo.Choice<net.sf.freecol.common.model.TileImprovementType>> work;
        synchronized (s.lock()) {
            work = s.improvementChoices(unit);
        }
        if (work.isEmpty()) {
            // Nothing to do on the colony tile: try next door, next turn.
            Tile next = null;
            synchronized (s.lock()) {
                for (Tile n : colonyTile.getSurroundingTiles(1)) {
                    if (n.isLand() && n.getSettlement() == null
                        && (s.getMyPlayer().owns(n)
                            || s.getMyPlayer().canClaimForSettlement(n))) {
                        next = n;
                        break;
                    }
                }
            }
            if (next == null) {
                System.out.println("  nowhere to work");
                return true;
            }
            s.moveSelectedTo(next);
            s.sync();
            s.endTurn();
            s.sync();
            waitForOthers(s, 120_000);
            waitForTurn(s, 120_000);
            s.sync();
            select(s, unit);
            synchronized (s.lock()) {
                work = s.improvementChoices(unit);
            }
        }
        synchronized (s.lock()) {
            for (ColonyInfo.Choice<?> c : work) System.out.println("  could: " + c.label);
        }
        if (work.isEmpty()) return false;
        s.improve(unit, work.get(0).value);
        s.sync();
        boolean started, cleared, restarted;
        synchronized (s.lock()) {
            started = unit.getWorkImprovement() != null;
            System.out.println("  working: " + s.describeOrders(unit));
        }
        s.clearOrders(unit);
        s.sync();
        synchronized (s.lock()) {
            cleared = unit.getState() == Unit.UnitState.ACTIVE;
            System.out.println("  after clearing: " + unit.getState());
        }
        // Out of moves now, so it should refuse (quickly) to restart,
        // and offer nothing; next turn it can start again.
        long t0 = System.currentTimeMillis();
        s.improve(unit, work.get(0).value);
        s.sync();
        boolean refusedFast = System.currentTimeMillis() - t0 < 5000;
        s.endTurn();
        s.sync();
        waitForOthers(s, 120_000);
        waitForTurn(s, 120_000);
        s.sync();
        select(s, unit);
        s.improve(unit, work.get(0).value);
        s.sync();
        synchronized (s.lock()) {
            restarted = refusedFast && unit.getWorkImprovement() != null;
            System.out.println("  refused quickly: " + refusedFast
                + "; restarted next turn: " + s.describeOrders(unit));
        }

        // A two-item build queue.
        java.util.List<net.sf.freecol.common.model.BuildableType> two
            = new java.util.ArrayList<>();
        synchronized (s.lock()) {
            for (net.sf.freecol.common.model.BuildableType bt : s.getBuildable(colony)) {
                if (two.size() < 2) two.add(bt);
            }
            System.out.println("  pay to finish now: " + s.payToFinishPrice(colony));
        }
        s.setBuildQueue(colony, two);
        s.sync();
        boolean queued;
        synchronized (s.lock()) {
            queued = colony.getBuildQueue().equals(two);
            System.out.println("  build queue " + colony.getBuildQueue() + ": " + queued);
        }
        return started && cleared && restarted && queued;
    }

    /**
     * Sail to Europe, look at it, trade if there is gold, board anyone
     * on the docks, and sail back to the New World.
     *
     * @return True if the ship got there and back.
     */
    private static boolean checkEurope(GameSession s, Unit ship, Tile colonyTile)
        throws Exception {
        net.sf.freecol.android.map.IsometricRenderer r
            = new net.sf.freecol.android.map.IsometricRenderer(noArt());
        waitForTurn(s, 120_000);
        s.sync();
        // The ship should be listed on the colony screen while next to
        // the colony, and come into port when loaded.  If it has
        // wandered off, sail it in.
        boolean listedOutside = false;
        synchronized (s.lock()) {
            ColonyInfo ci = new ColonyInfo(colonyTile.getColony(), s.getMyPlayer(),
                new net.sf.freecol.android.map.IsometricRenderer(noArt()));
            for (ColonyInfo.CarrierItem c : ci.carriers) {
                if (c.carrier.unit == ship && !c.inPort) listedOutside = true;
            }
            System.out.println("  ship next to the colony and listed: " + listedOutside);
        }
        if (listedOutside) {
            s.bringIntoPort(ship, colonyTile.getColony());
        } else {
            select(s, ship);
            s.moveSelectedTo(colonyTile);
        }
        s.sync();
        synchronized (s.lock()) {
            System.out.println("  ship in port: " + (ship.getTile() == colonyTile));
        }
        synchronized (s.lock()) {
            net.sf.freecol.common.model.Colony c = (ship.getTile() == null) ? null
                : ship.getTile().getColony();
            if (c != null) {
                net.sf.freecol.common.model.GoodsType best = null;
                for (net.sf.freecol.common.model.GoodsType gt
                         : c.getSpecification().getStorableGoodsTypeList()) {
                    if (best == null || c.getGoodsCount(gt) > c.getGoodsCount(best)) best = gt;
                }
                int n = Math.min(100, c.getGoodsCount(best));
                if (n > 0) {
                    System.out.println("  loading " + n + " " + best + " to sell");
                    s.loadGoods(ship, best, n);
                }
            }
        }
        s.sync();
        s.sailToEurope(ship);
        s.sync();
        int turns = 0;
        boolean arrived = false;
        while (turns++ < 20) {
            synchronized (s.lock()) {
                if (ship.isInEurope()) {
                    arrived = true;
                    break;
                }
                System.out.println("  ship at " + ship.getLocation()
                    + ", " + ship.getWorkLeft() + " turns left");
            }
            s.endTurn();
            s.sync();
            waitForOthers(s, 120_000);
            waitForTurn(s, 120_000);
            s.sync();
        }
        if (!arrived) {
            System.out.println("  ship never reached Europe");
            return false;
        }
        EuropeInfo info;
        synchronized (s.lock()) {
            info = new EuropeInfo(s.getMyPlayer(), r);
            System.out.println("  In " + info.title + ": gold " + info.gold
                + ", tax " + info.tax + "%, " + info.inPort.size()
                + " ships in port, " + info.docks.size() + " on the docks, "
                + info.recruits.size() + " recruits at " + info.recruitPrice
                + ", " + info.training.size() + " to train, "
                + info.market.size() + " goods on the market");
        }
        // Sell anything aboard.
        for (ColonyInfo.Amount a : info.inPort.get(0).cargo) {
            s.sell(ship, a.type, a.amount);
        }
        s.sync();
        synchronized (s.lock()) {
            info = new EuropeInfo(s.getMyPlayer(), r);
            System.out.println("  after selling: gold " + info.gold);
        }
        // Recruit if we can afford it.
        if (info.gold >= info.recruitPrice && !info.recruits.isEmpty()) {
            s.recruit(0);
        }
        s.sync();
        // Buy something cheap if we can afford any.
        synchronized (s.lock()) {
            info = new EuropeInfo(s.getMyPlayer(), r);
        }
        EuropeInfo.Price cheapest = null;
        for (EuropeInfo.Price p : info.market) {
            if (!p.boycott && p.buy > 0
                && (cheapest == null || p.buy < cheapest.buy)) cheapest = p;
        }
        if (cheapest != null && info.gold >= cheapest.buy) {
            s.buy(ship, cheapest.type, 100);
        }
        // Board everyone on the docks.
        synchronized (s.lock()) {
            for (ColonyInfo.UnitItem u : info.docks) {
                System.out.println("  in Europe " + u.name + " could be equipped "
                    + s.equipChoices(u.unit).size() + " ways");
            }
        }
        for (ColonyInfo.UnitItem u : info.docks) s.board(u.unit, ship);
        s.sync();
        synchronized (s.lock()) {
            info = new EuropeInfo(s.getMyPlayer(), r);
            EuropeInfo.Ship sh = info.inPort.get(0);
            System.out.println("  ship now has " + sh.passengers.size()
                + " passengers and " + sh.cargo.size() + " kinds of cargo; "
                + "gold " + info.gold + ", docks " + info.docks.size());
        }
        // Sail straight back to the colony this time.
        s.sailToAmerica(ship, colonyTile.getColony());
        s.sync();
        boolean left;
        synchronized (s.lock()) {
            left = !ship.isInEurope();
            System.out.println("  sailed back: " + left + " (" + ship.getLocation() + ")");
        }
        // Follow the ship home: it should arrive and sail on into the
        // colony's port by itself.
        boolean home = false;
        for (int turn = 0; turn < 12 && left; turn++) {
            s.endTurn();
            s.sync();
            waitForOthers(s, 120_000);
            waitForTurn(s, 120_000);
            s.sync();
            synchronized (s.lock()) {
                System.out.println("  homeward: " + ship.getLocation()
                    + ", destination " + ship.getDestination()
                    + ", moves " + ship.getMovesLeft());
                if (ship.getTile() == colonyTile) {
                    home = true;
                    break;
                }
            }
        }
        System.out.println("  ship back in the colony: " + home);
        return left && info.docks.isEmpty() && home;
    }

    private static net.sf.freecol.android.map.Art noArt() {
        return new net.sf.freecol.android.map.Art() {
            @Override protected java.io.InputStream open(String p)
                throws java.io.IOException {
                throw new java.io.FileNotFoundException(p);
            }
            @Override protected String[] list(String d) { return null; }
            @Override protected Img decode(java.io.InputStream in) { return null; }
            @Override protected Img applyMask(Img i, Img m) { return null; }
        };
    }

    /** Told how the loaded game ended (retiring, in checkLoad). */
    private static volatile java.util.function.Consumer<EndGameInfo.Kind> retireListener = null;

    /**
     * Make a trade route (the colony, then Europe), check the rules
     * accept it, set a reserve, put the ship on it and take it off.
     */
    private static boolean checkRoute(GameSession s, Unit ship, Tile colonyTile)
        throws Exception {
        waitForTurn(s, 120_000);
        s.sync();
        final java.util.concurrent.CompletableFuture<net.sf.freecol.common.model.TradeRoute> made
            = new java.util.concurrent.CompletableFuture<>();
        s.newTradeRoute(made::complete);
        net.sf.freecol.common.model.TradeRoute tr = made.get(60, java.util.concurrent.TimeUnit.SECONDS);
        if (tr == null) {
            System.out.println("Trade route: could not make one");
            return false;
        }
        net.sf.freecol.common.model.Colony colony;
        net.sf.freecol.common.model.GoodsType furs;
        java.util.List<GameSession.StopDraft> stops = new java.util.ArrayList<>();
        synchronized (s.lock()) {
            colony = colonyTile.getColony();
            // Carry whatever the colony has most of (furs if nothing).
            furs = s.getGame().getSpecification().getGoodsType("model.goods.furs");
            int most = 0;
            for (net.sf.freecol.common.model.GoodsType gt
                     : s.getGame().getSpecification().getStorableGoodsTypeList()) {
                if (gt.isFoodType()) continue;
                if (colony.getGoodsCount(gt) > most) {
                    most = colony.getGoodsCount(gt);
                    furs = gt;
                }
            }
            System.out.println("Trade route cargo: " + furs.getSuffix() + " (" + most + " in store)");
            GameSession.StopDraft a = new GameSession.StopDraft(colony);
            a.cargo.add(furs);
            stops.add(a);
            stops.add(new GameSession.StopDraft(s.getMyPlayer().getEurope()));
        }
        // An empty route is refused, with a reason.
        java.util.concurrent.CompletableFuture<String> bad = new java.util.concurrent.CompletableFuture<>();
        s.updateTradeRoute(tr, "Test", new java.util.ArrayList<>(), bad::complete);
        String why = bad.get(60, java.util.concurrent.TimeUnit.SECONDS);
        java.util.concurrent.CompletableFuture<String> good = new java.util.concurrent.CompletableFuture<>();
        s.updateTradeRoute(tr, "Furs to Europe", stops, good::complete);
        String err = good.get(60, java.util.concurrent.TimeUnit.SECONDS);
        System.out.println("Trade route: empty refused (" + why + "); saved: "
            + (err == null ? "yes" : err));
        s.setReserve(colony, furs, 0);
        s.assignTradeRoute(ship, tr);
        s.sync();
        boolean on, reserve;
        String orders;
        synchronized (s.lock()) {
            on = ship.getTradeRoute() == tr;
            orders = s.describeOrders(ship);
            reserve = GameSession.getReserve(colony, furs) == 0;
        }
        System.out.println("  ship on route: " + on + " (" + orders + "), reserve set: " + reserve);
        // Let it run: load at the colony, sail to Europe, sell there.
        boolean loaded = false, inEurope = false, sold = false;
        for (int turn = 0; turn < 14 && !sold; turn++) {
            s.endTurn();
            s.sync();
            waitForOthers(s, 120_000);
            waitForTurn(s, 120_000);
            s.sync();
            synchronized (s.lock()) {
                int n = ship.getGoodsCount(furs);
                if (n > 0) loaded = true;
                if (ship.isInEurope()) inEurope = true;
                // It sells in Europe at the start of a turn and sails
                // straight back, so look for the cargo gone.
                if (loaded && n == 0) sold = true;
                System.out.println("  route turn " + turn + ": " + s.describeOrders(ship)
                    + ", " + (ship.isInEurope() ? "in Europe" : ship.hasTile()
                              ? String.valueOf(ship.getTile()) : "at sea")
                    + ", carrying " + n);
            }
        }
        System.out.println("  route ran: loaded " + loaded + ", sold " + sold);
        s.clearOrders(ship);
        s.sync();
        boolean off;
        synchronized (s.lock()) {
            off = ship.getTradeRoute() == null;
        }
        // Loading needs something in store; without it the ship waits.
        return why != null && err == null && on && reserve && off
            && (sold || !loaded);
    }

    /**
     * Build every report and Colopedia page, as the menu does.
     *
     * @return True if they all have content.
     */
    private static boolean checkInfo(GameSession s) throws Exception {
        java.util.concurrent.CountDownLatch got = new java.util.concurrent.CountDownLatch(1);
        s.refreshNationSummaries(got::countDown);
        got.await(60, java.util.concurrent.TimeUnit.SECONDS);
        net.sf.freecol.android.map.IsometricRenderer r
            = new net.sf.freecol.android.map.IsometricRenderer(noArt());
        boolean ok = true;
        synchronized (s.lock()) {
            net.sf.freecol.common.model.Player me = s.getMyPlayer();
            for (ReportInfo.Kind k : ReportInfo.Kind.values()) {
                InfoPage page = ReportInfo.page(k, me, r);
                System.out.println("  report " + k.label + ": " + page.size()
                    + " entries; " + page.summary);
                for (InfoPage.Section sec : page.sections) {
                    for (InfoPage.Entry e : sec.entries.subList(0, Math.min(2, sec.entries.size()))) {
                        System.out.println("    " + e.title + " | "
                            + e.text.replace('\n', ' ') + " | " + e.right);
                    }
                }
                ok &= page.size() > 0;
            }
            // Diplomacy: who we could talk to, and an offer's agreement.
            for (DiplomacyInfo.Nation n : DiplomacyInfo.nations(me)) {
                System.out.println("  diplomacy: " + n.name + " met " + n.met + ", "
                    + n.stance + ", " + (n.where == null ? "no contact" : n.where));
                DiplomacyInfo.Offer o = DiplomacyInfo.offer(me, n.player);
                ok &= DiplomacyInfo.agreement(s.getGame(), me, o) == null;
                o.stance = o.stances.get(0);
                o.askGold = 100;
                net.sf.freecol.common.model.DiplomaticTrade dt
                    = DiplomacyInfo.agreement(s.getGame(), me, o);
                System.out.println("    offer: " + o.describe().replace('\n', ';')
                    + " -> " + dt.getItems().size() + " items");
                ok &= dt.getItems().size() == 2;
            }
            net.sf.freecol.common.model.Specification spec = s.getGame().getSpecification();
            for (ColopediaInfo.Kind k : ColopediaInfo.Kind.values()) {
                InfoPage list = ColopediaInfo.list(spec, k);
                int pages = 0;
                InfoPage sample = null;
                for (InfoPage.Section sec : list.sections) {
                    for (InfoPage.Entry e : sec.entries) {
                        InfoPage p = ColopediaInfo.page(spec, e.link);
                        if (p != null) pages++;
                        if (sample == null) sample = p;
                    }
                }
                System.out.println("  Colopedia " + k.label + ": " + list.size()
                    + " items, " + pages + " pages; e.g. " + (sample == null ? "-"
                    : sample.title + ": " + sample.size() + " facts, "
                    + sample.body.length() + " chars"));
                ok &= list.size() > 0 && pages == list.size();
            }
        }
        return ok;
    }

    /**
     * Load the autosave into a new session and play a turn.
     *
     * @return True if the loaded game matches and plays.
     */
    private static boolean checkLoad(GameSession old, File save) throws Exception {
        waitForTurn(old, 120_000);
        old.sync();
        int units, colonies;
        String turn;
        synchronized (old.lock()) {
            units = old.getMyPlayer().getUnitCount();
            colonies = old.getMyPlayer().getColonyList().size();
            turn = old.getGame().getTurn().toString();
        }
        // Save into a slot, as the menu's Save game does, and load that.
        File slot = new File(save.getParentFile(), "slot1.fsg");
        java.util.concurrent.CompletableFuture<String> saved
            = new java.util.concurrent.CompletableFuture<>();
        old.saveTo(slot, err -> saved.complete(err == null ? "" : err));
        String err = saved.get(60, java.util.concurrent.TimeUnit.SECONDS);
        System.out.println("Save to slot: " + (err.isEmpty() ? "ok" : err));
        if (!err.isEmpty() || !slot.isFile()) return false;
        save = slot;
        String about = GameSession.describeSave(save);
        old.stop();
        Thread.sleep(500);
        System.out.println("Saved game: " + about + " (" + save.length() + " bytes)");

        GameSession s = new GameSession(new GameSession.Listener() {
                @Override public void onChanged() {}
                @Override public void onStatus(String text) {
                    System.out.println("  >> " + text);
                }
                @Override public void onGameOver(EndGameInfo.Kind kind, String text) {
                    if (retireListener != null) retireListener.accept(kind);
                }
            });
        s.load(save);
        waitForTurn(s, 120_000);
        s.sync();
        boolean same;
        synchronized (s.lock()) {
            same = s.getMyPlayer().getUnitCount() == units
                && s.getMyPlayer().getColonyList().size() == colonies
                && s.getGame().getTurn().toString().equals(turn);
            System.out.println("Loaded: " + s.getGame().getTurn() + ", "
                + s.getMyPlayer().getUnitCount() + " units (was " + units
                + "), " + s.getMyPlayer().getColonyList().size()
                + " colonies (was " + colonies + ")");
        }
        s.endTurn();
        s.sync();
        waitForOthers(s, 120_000);
        waitForTurn(s, 120_000);
        s.sync();
        synchronized (s.lock()) {
            System.out.println("Played on to " + s.getGame().getTurn());
        }
        // Retire, as the menu does, and read the end-of-game screen.
        final java.util.concurrent.CompletableFuture<EndGameInfo.Kind> end
            = new java.util.concurrent.CompletableFuture<>();
        retireListener = end::complete;
        s.retire();
        EndGameInfo.Kind kind = end.get(60, java.util.concurrent.TimeUnit.SECONDS);
        s.sync();
        EndGameInfo info;
        synchronized (s.lock()) {
            info = new EndGameInfo(kind, "", s.getMyPlayer());
        }
        System.out.println("Retired: " + info.title + ", score " + info.score
            + ", place " + info.rank + ", " + info.highScores.size() + " high scores");
        s.stop();
        return same && about != null && kind == EndGameInfo.Kind.RETIRED;
    }

    /** Tap a unit's tile until that unit is selected. */
    private static void select(GameSession s, Unit unit) throws Exception {
        for (int i = 0; i < 10 && s.getSelectedUnit() != unit; i++) {
            s.selectOnTile(unit.getTile());
            s.sync();
        }
    }

    private static void waitForTurn(GameSession s, long millis)
        throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        while (!s.isMyTurn()) {
            if (System.currentTimeMillis() > end) {
                throw new RuntimeException("timed out waiting for our turn");
            }
            Thread.sleep(100);
        }
    }

    private static void waitForOthers(GameSession s, long millis)
        throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        while (s.isMyTurn()) {
            if (System.currentTimeMillis() > end) {
                throw new RuntimeException("turn did not end");
            }
            Thread.sleep(100);
        }
    }
}
