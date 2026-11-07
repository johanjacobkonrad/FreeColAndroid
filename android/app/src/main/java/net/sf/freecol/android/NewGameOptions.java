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
import net.sf.freecol.common.model.Nation;
import net.sf.freecol.common.model.NationOptions;
import net.sf.freecol.common.model.NationOptions.NationState;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.option.IntegerOption;
import net.sf.freecol.common.option.MapGeneratorOptions;


/**
 * The choices for a new game: nation, difficulty, map size and how
 * much of it is land.  No Android code, so the session check uses it
 * too.  Kept as plain strings and ints so it can travel in an Intent.
 */
public final class NewGameOptions {

    /** A labelled choice. */
    public static final class Choice {
        public final String id, label, detail;
        /** For nations: the advantage's name, what it does, the units
         *  it starts with ("" otherwise). */
        public String kind = "", about = "", units = "";

        Choice(String id, String label, String detail) {
            this.id = id;
            this.label = label;
            this.detail = detail;
        }
    }

    public static final String[] DIFFICULTIES = {
        "model.difficulty.veryEasy", "model.difficulty.easy",
        "model.difficulty.medium", "model.difficulty.hard",
        "model.difficulty.veryHard" };

    /** Map sizes: id, label, width, height (in tiles). */
    public static final Object[][] MAP_SIZES = {
        { "small", "Small", 40, 100 },
        { "medium", "Medium", 50, 125 },
        { "large", "Large", 60, 150 } };

    /** Land: id, label, percentage of the map that is land. */
    public static final Object[][] LAND = {
        { "islands", "Islands", 18 },
        { "normal", "Normal", 25 },
        { "continents", "Continents", 35 } };

    /**
     * The European nations a player can choose or meet: the original
     * four, and the Portuguese and Swedish (FreeCol's rules have the
     * Danish and Russians too, but not here).
     */
    public static final List<String> PLAYABLE = java.util.Arrays.asList(
        "model.nation.dutch", "model.nation.french", "model.nation.english",
        "model.nation.spanish", "model.nation.portuguese", "model.nation.swedish");

    /** Rival European nations: the fewest, the most and the usual. */
    public static final int MIN_RIVALS = 1, MAX_RIVALS = 5, DEFAULT_RIVALS = 3;

    /** The nation to play, or null for whichever the game gives. */
    public String nationId = null;
    /** How many other European nations join (picked at random). */
    public int rivals = DEFAULT_RIVALS;
    public String difficulty = "model.difficulty.medium";
    public String mapSize = "small";
    public String land = "normal";


    /**
     * The nations a player can choose ({@link #PLAYABLE}), with their
     * national advantage.
     *
     * @param spec A loaded {@code Specification}.
     * @return The choices, in the rules' order.
     */
    public static List<Choice> nations(Specification spec) {
        List<Choice> result = new ArrayList<>();
        for (Nation n : spec.getEuropeanNations()) {
            if (!PLAYABLE.contains(n.getId())) continue;
            String type = Messages.getName(n.getType());
            String about = Messages.message(n.getType().getId() + ".shortDescription");
            Choice c = new Choice(n.getId(), Messages.getName(n), type + ": " + about);
            c.kind = type;
            c.about = about + ".";
            if (n.getType() instanceof net.sf.freecol.common.model.EuropeanNationType) {
                List<String> units = new ArrayList<>();
                for (net.sf.freecol.common.model.AbstractUnit au
                         : ((net.sf.freecol.common.model.EuropeanNationType)n.getType())
                             .getStartingUnits()) {
                    units.add(Messages.message(au.getSingleLabel()));
                }
                java.util.Collections.sort(units);
                c.units = "Starts with " + String.join(", ", units) + ".";
            }
            result.add(c);
        }
        return result;
    }

    /**
     * Which European nations take part: the player's, and
     * {@link #rivals} others picked at random from the rest of
     * {@link #PLAYABLE}.  The rest are left out of the game.  Set on
     * the server's game before anyone logs in.
     *
     * @param no The game's nation options, changed in place.
     * @param random For picking the rivals.
     * @return The nations in the game, the player's first.
     */
    public List<String> applyNations(NationOptions no, java.util.Random random) {
        String mine = PLAYABLE.contains(nationId) ? nationId : PLAYABLE.get(0);
        List<String> others = new ArrayList<>(PLAYABLE);
        others.remove(mine);
        java.util.Collections.shuffle(others, random);
        List<String> in = new ArrayList<>();
        in.add(mine);
        int n = Math.max(MIN_RIVALS, Math.min(MAX_RIVALS, rivals));
        in.addAll(others.subList(0, Math.min(n, others.size())));
        for (java.util.Map.Entry<Nation, NationState> e
                 : new ArrayList<>(no.getNations().entrySet())) {
            Nation nation = e.getKey();
            if (!nation.getType().isEuropean() || nation.getType().isREF()) continue;
            no.setNationState(nation, in.contains(nation.getId())
                ? NationState.AVAILABLE : NationState.NOT_AVAILABLE);
        }
        return in;
    }

    /** The difficulty levels, easiest first. */
    public static List<Choice> difficulties() {
        List<Choice> result = new ArrayList<>();
        for (String d : DIFFICULTIES) {
            result.add(new Choice(d, Messages.getName(d), ""));
        }
        return result;
    }

    /**
     * Put the map choices into the rules, before the server is made.
     *
     * @param spec The {@code Specification} the game will use.
     */
    public void applyTo(Specification spec) {
        for (Object[] m : MAP_SIZES) {
            if (m[0].equals(mapSize)) {
                set(spec, MapGeneratorOptions.MAP_WIDTH, (Integer)m[2]);
                set(spec, MapGeneratorOptions.MAP_HEIGHT, (Integer)m[3]);
            }
        }
        for (Object[] l : LAND) {
            if (l[0].equals(land)) set(spec, MapGeneratorOptions.LAND_MASS, (Integer)l[2]);
        }
    }

    private static void set(Specification spec, String id, int value) {
        Object o = spec.getMapGeneratorOptions().getOption(id);
        if (o instanceof IntegerOption) ((IntegerOption)o).setValue(value);
    }
}
