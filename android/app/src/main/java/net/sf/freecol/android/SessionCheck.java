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
 * no user interface: sail west towards land, put a colonist ashore
 * and found a colony, ending each turn and waiting for the AI to hand
 * the turn back.  Runs on a desktop JVM
 * with only the engine on the class path.
 *
 * Usage: SessionCheck DATA_DIR [TURNS]
 */
public final class SessionCheck {

    private SessionCheck() {} // Static only

    public static void main(String[] args) throws Exception {
        final int turns = (args.length > 1) ? Integer.parseInt(args[1]) : 8;
        Engine.init(new File(args[0]));
        GameSession s = new GameSession(new GameSession.Listener() {
                @Override public void onChanged() {}
                @Override public void onStatus(String text) {
                    System.out.println("  > " + text);
                }
            });
        s.start(Engine.loadSpecification());
        Unit ship = null, colonist = null;
        Tile shipStart = null;
        boolean landed = false, founded = false;
        int played = 0;
        for (int turn = 0; turn < turns && !founded; turn++) {
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
            if (landed) {
                // Found a colony where the colonist landed.
                select(s, colonist);
                s.buildColony();
                s.sync();
                synchronized (s.lock()) {
                    founded = colonist.getTile().getColony() != null;
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
        s.stop();
        boolean moved = ship != null && shipStart != null
            && ship.getTile() != shipStart;
        System.out.println("Played " + played + " turns; ship moved: " + moved
            + "; landed: " + landed + "; colony founded: " + founded);
        boolean ok = moved && (founded || played == turns);
        System.out.println(ok ? "OK" : "FAILED");
        System.exit(ok ? 0 : 1);
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
