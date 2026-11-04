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
import java.net.InetAddress;
import java.util.function.Consumer;

import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.networking.ChangeSet.See;
import net.sf.freecol.common.networking.SetCurrentPlayerMessage;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.model.ServerGame;


/**
 * Runs a game in which every player is an AI, with no user interface.
 *
 * This is the Phase 1 proof that the shared engine works on its own:
 * it loads the rules, starts an in-process server, and lets the AI
 * players take turns.  It uses only engine classes, so it runs both on
 * a desktop JVM and on Android.
 */
public final class AutoPlay {

    /** The outcome of a run. */
    public static final class Result {
        public final int turnsPlayed;
        public final String finalTurn;
        public final long millis;
        public final String error;

        Result(int turnsPlayed, String finalTurn, long millis, String error) {
            this.turnsPlayed = turnsPlayed;
            this.finalTurn = finalTurn;
            this.millis = millis;
            this.error = error;
        }

        public boolean ok() {
            return error == null;
        }

        @Override
        public String toString() {
            return (ok() ? "OK" : "FAILED: " + error) + " - played "
                + turnsPlayed + " turns, now " + finalTurn
                + " (" + (millis / 1000) + "s)";
        }
    }

    private AutoPlay() {} // Static only


    /**
     * Play an all-AI game.
     *
     * @param dataDir The FreeCol "data" directory (rules and strings).
     * @param turns The number of turns to play.
     * @param timeoutMillis Give up after this long.
     * @param log Receives progress messages.
     * @return The {@code Result}.
     */
    public static Result run(File dataDir, int turns, long timeoutMillis,
                             Consumer<String> log) {
        final long start = System.currentTimeMillis();
        FreeColServer server = null;
        int startTurn = -1;
        String now = "?";
        try {
            log.accept("Loading rules...");
            Engine.init(dataDir);
            Specification spec = Engine.loadSpecification();

            log.accept("Starting server...");
            server = new FreeColServer(false, true, spec,
                InetAddress.getLoopbackAddress(), -1, "autoplay");
            server.startGame();
            final ServerGame game = server.getGame();
            log.accept("Game started: " + game.getLivePlayerList().size()
                + " players, map " + game.getMap().getWidth() + "x"
                + game.getMap().getHeight());

            // With no human players, nobody starts the first turn, so
            // hand it to the first player the way endTurn() would.
            Player first = game.getCurrentPlayer();
            if (first == null) first = game.getFirstPlayer();
            game.setCurrentPlayer(first);
            ChangeSet cs = new ChangeSet();
            cs.add(See.all(), new SetCurrentPlayerMessage(first));
            game.sendToAll(cs);

            startTurn = game.getTurn().getNumber();
            int lastLogged = startTurn;
            while (game.getTurn().getNumber() - startTurn < turns) {
                if (System.currentTimeMillis() - start > timeoutMillis) {
                    now = game.getTurn().toString();
                    return new Result(game.getTurn().getNumber() - startTurn,
                        now, elapsed(start), "timed out");
                }
                Thread.sleep(200);
                int t = game.getTurn().getNumber();
                if (t != lastLogged) {
                    lastLogged = t;
                    log.accept("Turn " + game.getTurn()
                        + " (" + elapsed(start) / 1000 + "s)");
                }
            }
            now = game.getTurn().toString();
            return new Result(game.getTurn().getNumber() - startTurn, now,
                              elapsed(start), null);
        } catch (Throwable t) {
            return new Result(0, now, elapsed(start), t.toString());
        } finally {
            if (server != null) {
                try {
                    server.shutdown();
                } catch (Throwable t) {} // Best effort
            }
        }
    }

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }

    /**
     * Run from the command line: AutoPlay DATA_DIR [TURNS].
     *
     * @param args The arguments.
     */
    public static void main(String[] args) {
        int turns = (args.length > 1) ? Integer.parseInt(args[1]) : 10;
        Result r = run(new File(args[0]), turns, 30 * 60 * 1000L,
                       System.out::println);
        System.out.println(r);
        System.exit(r.ok() ? 0 : 1);
    }
}
