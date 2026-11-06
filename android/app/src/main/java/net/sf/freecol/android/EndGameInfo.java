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

import java.util.ArrayList;
import java.util.List;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.HighScore;
import net.sf.freecol.common.model.HistoryEvent;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Turn;


/**
 * What the end of a game shows: how it ended, the final score and
 * what earned it, and the high scores (with this game's place).  Plain
 * Java; the high scores file is the engine's own (HighScore).
 */
public final class EndGameInfo {

    /** How the game ended. */
    public enum Kind { WON, LOST, RETIRED }

    /** One line of the high scores. */
    public static final class Row {
        public final int rank, score;
        public final String who, when, detail;
        /** True for this game's own entry. */
        public final boolean mine;

        Row(int rank, int score, String who, String when, String detail, boolean mine) {
            this.rank = rank;
            this.score = score;
            this.who = who;
            this.when = when;
            this.detail = detail;
            this.mine = mine;
        }
    }

    public final Kind kind;
    public final String title, text, nation, year;
    public final int score, colonies, units, fathers, rebels;
    /** What earned points: { year, event, points }. */
    public final List<String[]> scored = new ArrayList<>();
    public final List<Row> highScores = new ArrayList<>();
    /** This game's place in the high scores, or -1 if not in them. */
    public final int rank;


    /**
     * Read the end of a game.  Call while holding the session lock.
     *
     * @param kind How it ended.
     * @param text What happened (from the game).
     * @param me Our player.
     */
    public EndGameInfo(Kind kind, String text, Player me) {
        this.kind = kind;
        this.text = (text == null) ? "" : text;
        this.title = (kind == Kind.WON) ? "Victory!" : (kind == Kind.RETIRED)
            ? "You retire" : "Defeat";
        this.nation = Messages.message(me.getNationLabel());
        this.year = Messages.message(me.getGame().getTurn().getLabel());
        this.score = me.getScore();
        this.colonies = me.getColonyList().size();
        this.units = me.getUnitCount();
        this.fathers = me.getFoundingFathers().size();
        this.rebels = me.getSoL();
        for (HistoryEvent e : me.getHistory()) {
            if (e.getScore() == 0) continue;
            scored.add(new String[] { Messages.message(e.getTurn().getLabel()),
                                      Messages.message(e),
                                      ((e.getScore() > 0) ? "+" : "") + e.getScore() });
        }
        // The server records the score as the game ends; winning the
        // war adds points after that, so record the final score too,
        // but only if it is higher: the engine adds a second entry for
        // the same game when the score is equal.
        try {
            List<HighScore> all = HighScore.loadHighScores();
            HighScore best = null;
            if (all != null) {
                for (HighScore hs : all) {
                    if (sameGame(hs, me) && (best == null || hs.getScore() > best.getScore())) {
                        best = hs;
                    }
                }
            }
            if (best == null || me.getScore() > best.getScore()) HighScore.newHighScore(me);
        } catch (RuntimeException e) {
            // Best effort: the list still shows
        }
        this.rank = readHighScores(me);
    }

    /** Fill in the high scores; return our place in them. */
    private int readHighScores(Player me) {
        return fill(highScores, me);
    }

    /**
     * The high scores, for the start screen.
     *
     * @return Up to ten rows, best first.
     */
    public static List<Row> highScores() {
        List<Row> out = new ArrayList<>();
        fill(out, null);
        return out;
    }

    /** The same game and nation (one entry each on the list). */
    private static boolean sameGame(HighScore hs, Player me) {
        return hs.getGameUUID() != null && hs.getGameUUID().equals(me.getGame().getUUID())
            && me.getNationId().equals(hs.getNationId());
    }

    /**
     * The high scores, best first, with only the best entry of each
     * game and nation (earlier versions could record a game twice);
     * the file is tidied if it had repeats.
     */
    private static List<HighScore> load() {
        List<HighScore> all = HighScore.loadHighScores();
        if (all == null) return new ArrayList<>();
        List<HighScore> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        all.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
        for (HighScore hs : all) {
            String key = hs.getGameUUID() + "/" + hs.getNationId();
            if (hs.getGameUUID() == null || seen.add(key)) out.add(hs);
        }
        if (out.size() < all.size()) {
            try {
                HighScore.saveHighScores(out);
            } catch (RuntimeException e) {
                // Shown tidy anyway
            }
        }
        return out;
    }

    private static int fill(List<Row> out, Player me) {
        List<HighScore> all = load();
        int found = -1;
        for (int i = 0; i < all.size() && i < 10; i++) {
            HighScore hs = all.get(i);
            // This game's entry (its score was taken when it ended; a
            // victory adds points after that).
            boolean mine = me != null && sameGame(hs, me);
            if (mine) found = i + 1;
            // The nation (the player name is always the app's own).
            String who = (hs.getNationName() != null) ? hs.getNationName()
                : Messages.getName(hs.getNationId());
            if (hs.getNewLandName() != null) who += ", " + hs.getNewLandName();
            String when = yearOf(hs.getRetirementTurn());
            List<String> d = new ArrayList<>();
            if (hs.getIndependenceTurn() > 0) {
                d.add("independent " + yearOf(hs.getIndependenceTurn()));
            }
            d.add(hs.getColonyCount() + " colonies");
            if (hs.getDifficulty() != null) d.add(Messages.getName(hs.getDifficulty()));
            out.add(new Row(i + 1, hs.getScore(), who, when, String.join(" · ", d), mine));
        }
        return found;
    }

    private static String yearOf(int turn) {
        return (turn > 0) ? Messages.message(new Turn(turn).getLabel()) : "";
    }
}
