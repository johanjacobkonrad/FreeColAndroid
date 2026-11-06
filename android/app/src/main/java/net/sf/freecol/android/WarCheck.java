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
import java.util.ArrayList;
import java.util.List;

import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * Plays the war of independence on a desktop JVM, as the phone would:
 * loads the "Start near independence" test game, declares, then each
 * turn attacks any royal unit next to one of our soldiers, until the
 * war ends or enough turns pass.  Checks that the declaration, the
 * royal landings and the fighting all work with the app's session.
 *
 * Usage: WarCheck DATA_DIR SCENARIO [TURNS]
 */
public final class WarCheck {

    private WarCheck() {} // Static only

    /** Set when the game ends (no more turns will come). */
    private static volatile boolean over = false;
    private static volatile EndGameInfo.Kind endKind = null;
    private static volatile String endText = null;

    public static void main(String[] args) {
        try {
            System.exit(run(args) ? 0 : 1);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static boolean run(String[] args) throws Exception {
        Engine.init(new File(args[0]));
        File scenario = new File(args[1]);
        int turns = (args.length > 2) ? Integer.parseInt(args[2]) : 25;
        final List<String> news = new ArrayList<>();
        GameSession s = new GameSession(new GameSession.Listener() {
                @Override public void onChanged() {}
                @Override public void onStatus(String text) {
                    System.out.println("  >> " + text);
                    synchronized (news) {
                        news.add(text);
                    }
                }
                @Override public void onGameOver(EndGameInfo.Kind kind, String text) {
                    System.out.println("GAME OVER: " + text);
                    endKind = kind;
                    endText = text;
                    over = true;
                }
            });
        s.setMovePause(0);
        File save = File.createTempFile("war", ".fsg");
        save.deleteOnExit();
        s.setSaveFile(save);
        s.load(scenario);
        waitForTurn(s, 180_000);
        s.sync();
        String problem = s.declareProblem();
        System.out.println("Loaded " + s.getGame().getTurn() + "; can declare: "
            + (problem == null ? "yes" : problem));
        if (problem != null) return false;

        final String[] resolution = { null };
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        String[] names = s.declarationNames();
        System.out.println("Declaring as " + names[0] + " / " + names[1]);
        s.declareIndependence(names[0], names[1], r -> {
                resolution[0] = r;
                done.countDown();
            });
        done.await(120, java.util.concurrent.TimeUnit.SECONDS);
        boolean rebel, ref, europe;
        synchronized (s.lock()) {
            Player me = s.getMyPlayer();
            rebel = me.getPlayerType() == Player.PlayerType.REBEL;
            ref = me.getREFPlayer() != null;
            europe = s.hasEurope();
        }
        System.out.println("Declared: " + rebel + ", REF player " + ref
            + ", Europe kept: " + europe + ", resolution "
            + (resolution[0] == null ? "none" : resolution[0].length() + " chars"));
        if (!rebel || !ref) return false;

        int landed = 0, attacks = 0, played = 0;
        for (int t = 0; t < turns && !over; t++) {
            // Attack any royal unit next to one of our armed units.
            List<Object[]> fights = new ArrayList<>();
            synchronized (s.lock()) {
                Player me = s.getMyPlayer();
                Player king = me.getREFPlayer();
                int onMap = 0;
                for (Unit u : king.getUnitSet()) if (u.hasTile()) onMap++;
                landed = Math.max(landed, onMap);
                for (Unit u : me.getUnitSet()) {
                    if (!u.hasTile() || !u.isOffensiveUnit() || u.isNaval()
                        || u.getMovesLeft() <= 0) continue;
                    for (Tile n : u.getTile().getSurroundingTiles(1)) {
                        Unit enemy = n.getFirstUnit();
                        if (enemy != null && enemy.getOwner() == king && n.getSettlement() == null
                            && !enemy.isNaval()) {
                            fights.add(new Object[] { u, n });
                            break;
                        }
                    }
                }
            }
            for (Object[] f : fights) {
                Unit u = (Unit)f[0];
                synchronized (s.lock()) {
                    if (u.isDisposed() || !u.hasTile()) continue;
                }
                s.select(u);
                s.sync();
                s.moveSelectedTo((Tile)f[1]);
                s.sync();
                attacks++;
            }
            int mine, theirs;
            synchronized (s.lock()) {
                Player me = s.getMyPlayer();
                mine = me.getUnitCount();
                theirs = me.getREFPlayer().getUnitCount();
                System.out.println("Turn " + s.getGame().getTurn() + ": our units " + mine
                    + ", royal units " + theirs + " (" + landed + " landed so far), "
                    + fights.size() + " attacks, " + me.getColonyList().size() + " colonies");
            }
            s.endTurn();
            s.sync();
            waitForOthers(s, 180_000);
            if (over) break;
            waitForTurn(s, 180_000);
            if (over) break;
            s.sync();
            played++;
        }
        // The end-of-game screen's contents, and the high scores.
        boolean endOk = true;
        if (over) {
            Thread.sleep(500);
            EndGameInfo info;
            synchronized (s.lock()) {
                info = new EndGameInfo(endKind, endText, s.getMyPlayer());
            }
            System.out.println("End: " + info.title + " | " + info.nation + ", " + info.year
                + ", score " + info.score + ", place " + info.rank + "; "
                + info.scored.size() + " scoring events");
            for (EndGameInfo.Row r : info.highScores) {
                System.out.println("  #" + r.rank + " " + r.score + " " + r.who + " "
                    + r.when + " (" + r.detail + ")" + (r.mine ? " <- this game" : ""));
            }
            endOk = endKind == EndGameInfo.Kind.WON && !info.highScores.isEmpty();
        }
        s.stop();
        System.out.println("War: played " + played + " turns, royal units landed "
            + landed + ", attacks " + attacks + ", game over " + over);
        // The royal army must have come; the war must have run without a hang.
        boolean ok = landed > 0 && played > 0 && endOk;
        System.out.println(ok ? "OK" : "FAILED");
        return ok;
    }

    private static void waitForTurn(GameSession s, long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        while (!s.isMyTurn() && !over) {
            if (System.currentTimeMillis() > end) throw new RuntimeException("No turn");
            Thread.sleep(100);
        }
    }

    private static void waitForOthers(GameSession s, long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        Thread.sleep(300);
        while (s.isMyTurn() && !over) {
            if (System.currentTimeMillis() > end) throw new RuntimeException("Turn did not end");
            Thread.sleep(100);
        }
    }
}
