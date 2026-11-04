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
            });
        File save = File.createTempFile("freecol-check", ".fsg");
        save.deleteOnExit();
        new File(save.getPath() + ".txt").deleteOnExit();
        s.setSaveFile(save);
        s.start(Engine.loadSpecification());
        Unit ship = null, colonist = null;
        Tile shipStart = null, colonyTile = null;
        boolean landed = false, founded = false, colonyOk = false;
        int played = 0;
        for (int turn = 0; turn < turns && !colonyOk; turn++) {
            waitForTurn(s, 120_000);
            s.sync();
            Tile target = null, land = null;
            synchronized (s.lock()) {
                Map map = s.getGame().getMap();
                if (ship == null) {
                    ship = s.getSelectedUnit();
                    if (ship == null || !ship.isNaval()) break;
                    shipStart = ship.getTile();
                }
                System.out.println("Turn " + s.getGame().getTurn()
                    + ": " + s.getMyPlayer().getUnitCount() + " units, map "
                    + map.getWidth() + "x" + map.getHeight()
                    + ", ship at " + ship.getTile());
                Tile t = ship.getTile();
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
        // Autosave happened at the start of this turn; load it into a
        // fresh session and check the game carries on.
        boolean reloaded = checkLoad(s, save);
        boolean moved = ship != null && shipStart != null
            && ship.getTile() != shipStart;
        System.out.println("Played " + played + " turns; ship moved: " + moved
            + "; landed: " + landed + "; colony founded: " + founded
            + "; colony orders: " + colonyOk + "; save and load: " + reloaded);
        boolean ok = moved && (colonyOk || played == turns) && reloaded;
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
            int builds = ColonyInfo.buildChoices(s, colony).size();
            summary = info.land.size() == 9 && !info.buildings.isEmpty()
                && info.warehouse.size() > 10 && builds > 0
                && (tile == null || choices > 0);
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
        String about = GameSession.describeSave(save);
        old.stop();
        Thread.sleep(500);
        System.out.println("Saved game: " + about + " (" + save.length() + " bytes)");

        GameSession s = new GameSession(new GameSession.Listener() {
                @Override public void onChanged() {}
                @Override public void onStatus(String text) {
                    System.out.println("  >> " + text);
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
        s.stop();
        return same && about != null;
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
