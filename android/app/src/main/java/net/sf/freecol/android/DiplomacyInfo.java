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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTradeItem;
import net.sf.freecol.common.model.DiplomaticTrade;
import net.sf.freecol.common.model.DiplomaticTrade.TradeContext;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GoldTradeItem;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.StanceTradeItem;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * Diplomacy you start with another European nation.  In FreeCol a
 * proposal is carried by one of your units or colonies next to one
 * of theirs (a scout at their colony, say), so this finds where you
 * can talk, offers what can be traded, and builds the agreement.
 * Read while holding the session lock; plain Java for the desktop
 * check.
 */
public final class DiplomacyInfo {

    /** Another European nation, and where (if anywhere) you can talk. */
    public static final class Nation {
        public final Player player;
        public final String name, stance, crestKey;
        public final boolean met;
        /** Ours and theirs, next to each other; null if nowhere. */
        public final FreeColGameObject our, other;
        /** Where, in words ("your scout at Plymouth"). */
        public final String where;

        Nation(Player player, Player me, FreeColGameObject our,
               FreeColGameObject other, String where) {
            this.player = player;
            this.name = Messages.message(player.getNationLabel());
            Stance s = me.getStance(player);
            this.stance = (s == null) ? "" : Messages.message(s.getNameKey());
            this.crestKey = "image.miscicon." + player.getNationId();
            this.met = me.hasContacted(player);
            this.our = our;
            this.other = other;
            this.where = where;
        }
    }

    /** What can be offered or asked of one nation, and what was. */
    public static final class Offer {
        public final Player other;
        public final String name;
        /** Stances that can be proposed (not the current one). */
        public final List<Stance> stances = new ArrayList<>();
        public final List<String> stanceNames = new ArrayList<>();
        public final List<Colony> myColonies, theirColonies;
        public final int myGold;

        // The choices; nothing chosen means no such item.
        public Stance stance = null;
        public int giveGold = 0, askGold = 0;
        public Colony giveColony = null, askColony = null;

        Offer(Player me, Player other) {
            this.other = other;
            this.name = Messages.message(other.getNationLabel());
            Stance now = me.getStance(other);
            Stance[] next;
            switch ((now == null) ? Stance.PEACE : now) {
            case WAR: next = new Stance[] { Stance.CEASE_FIRE, Stance.PEACE }; break;
            case CEASE_FIRE: next = new Stance[] { Stance.PEACE, Stance.WAR }; break;
            case ALLIANCE: next = new Stance[] { Stance.PEACE, Stance.WAR }; break;
            case PEACE: default: next = new Stance[] { Stance.ALLIANCE, Stance.WAR }; break;
            }
            for (Stance s : next) {
                stances.add(s);
                stanceNames.add(Messages.message(s.getNameKey()));
            }
            this.myColonies = sorted(me.getColonyList());
            this.theirColonies = sorted(other.getColonyList());
            this.myGold = Math.max(0, me.getGold());
        }

        /** True if nothing is in the offer yet. */
        public boolean isEmpty() {
            return stance == null && giveGold <= 0 && askGold <= 0
                && giveColony == null && askColony == null;
        }

        /** The offer in words, one item a line. */
        public String describe() {
            List<String> out = new ArrayList<>();
            if (stance != null) out.add(Messages.message(stance.getNameKey()));
            if (giveGold > 0) out.add("You give " + giveGold + " gold");
            if (askGold > 0) out.add("They give " + askGold + " gold");
            if (giveColony != null) out.add("You give " + giveColony.getName());
            if (askColony != null) out.add("They give " + askColony.getName());
            return String.join("\n", out);
        }
    }

    private static List<Colony> sorted(List<Colony> list) {
        List<Colony> out = new ArrayList<>(list);
        out.sort(Comparator.comparing(Colony::getName));
        return out;
    }

    private DiplomacyInfo() {} // Static only

    /** The other European nations, with where you could talk to each. */
    public static List<Nation> nations(Player me) {
        List<Nation> out = new ArrayList<>();
        List<Player> others = me.getGame().getLiveEuropeanPlayers(me)
            .filter(p -> !p.isREF()).collect(Collectors.toList());
        for (Player p : others) {
            FreeColGameObject our = null, other = null;
            String where = null;
            if (me.hasContacted(p)) {
                // A unit of ours next to one of their colonies is best
                // (the usual way), then any unit of ours next to any of
                // theirs, then a colony of ours next to one of theirs.
                for (int pass = 0; pass < 2 && our == null; pass++) {
                    for (Unit u : me.getUnitSet()) {
                        if (u.isDisposed() || !u.hasTile() || u.isOnCarrier()) continue;
                        FreeColGameObject t = theirsNear(u.getTile(), p, pass == 0);
                        if (t != null) {
                            our = u;
                            other = t;
                            break;
                        }
                    }
                }
                for (Colony c : me.getColonyList()) {
                    if (our != null) break;
                    FreeColGameObject t = theirsNear(c.getTile(), p, false);
                    if (t != null) {
                        our = c;
                        other = t;
                    }
                }
                if (our != null) where = describe(our, other);
            }
            out.add(new Nation(p, me, our, other, where));
        }
        return out;
    }

    /** One of their colonies (or units, unless colonyOnly) next to a tile. */
    private static FreeColGameObject theirsNear(Tile tile, Player them, boolean colonyOnly) {
        for (Tile t : tile.getSurroundingTiles(1)) {
            Colony c = t.getColony();
            if (c != null && c.getOwner() == them) return c;
        }
        if (colonyOnly) return null;
        for (Tile t : tile.getSurroundingTiles(1)) {
            for (Unit u : t.getUnitList()) {
                if (u.getOwner() == them) return u;
            }
        }
        return null;
    }

    private static String describe(FreeColGameObject our, FreeColGameObject other) {
        String a = (our instanceof Colony) ? ((Colony)our).getName()
            : "your " + Messages.getName(((Unit)our).getType()).toLowerCase(java.util.Locale.UK);
        String b = (other instanceof Colony) ? ((Colony)other).getName()
            : "their " + Messages.getName(((Unit)other).getType()).toLowerCase(java.util.Locale.UK);
        return a + " next to " + b;
    }

    /** What could go into an offer to a nation. */
    public static Offer offer(Player me, Player other) {
        return new Offer(me, other);
    }

    /**
     * The agreement to send for an offer.
     *
     * @return The agreement, or null if the offer is empty.
     */
    public static DiplomaticTrade agreement(Game game, Player me, Offer o) {
        if (o.isEmpty()) return null;
        DiplomaticTrade dt = new DiplomaticTrade(game, TradeContext.DIPLOMATIC,
                                                 me, o.other, null, 0);
        // As the desktop does: the stance is "given" by the other side.
        if (o.stance != null) dt.add(new StanceTradeItem(game, o.other, me, o.stance));
        if (o.giveGold > 0) {
            dt.add(new GoldTradeItem(game, me, o.other, Math.min(o.giveGold, o.myGold)));
        }
        if (o.askGold > 0) dt.add(new GoldTradeItem(game, o.other, me, o.askGold));
        if (o.giveColony != null) dt.add(new ColonyTradeItem(game, me, o.other, o.giveColony));
        if (o.askColony != null) dt.add(new ColonyTradeItem(game, o.other, me, o.askColony));
        return dt;
    }
}
