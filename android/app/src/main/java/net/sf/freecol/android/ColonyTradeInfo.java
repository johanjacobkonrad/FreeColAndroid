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
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.DiplomaticTrade;
import net.sf.freecol.common.model.DiplomaticTrade.TradeContext;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsContainer;
import net.sf.freecol.common.model.GoodsTradeItem;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.GoldTradeItem;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Unit;


/**
 * Trading with another European nation's colony (with Jan de Witt in
 * Congress), from a ship or wagon train next to it: sell some of its
 * cargo, buy some of the colony's goods, or both, for gold one way or
 * the other.  The colony's owner says yes, no, or makes a counter
 * offer.  No Android code, so the session check can use it.
 */
public final class ColonyTradeInfo {

    /** What can go into an offer, and what does. */
    public static final class Offer {
        public final Unit unit;
        public final Colony colony;
        public final Player other;
        /** The other nation ("French") and the colony's name. */
        public final String nation, place;
        /** The carrier's goods, a load (up to 100) of each. */
        public final List<Goods> mine = new ArrayList<>();
        /**
         * Goods to ask the colony for: any the carrier could take, since
         * a foreign colony's warehouse is hidden (if they do not have
         * enough, the trade fails).
         */
        public final List<GoodsType> theirs = new ArrayList<>();
        public final int myGold;

        // The choices.  Nothing chosen means no such item.
        public Goods sell = null;
        public GoodsType buy = null;
        public int sellAmount = 0, buyAmount = 0;
        /** Gold: more than 0 they pay you, less than 0 you pay them. */
        public int price = 0;

        Offer(Unit unit, Colony colony) {
            this.unit = unit;
            this.colony = colony;
            this.other = colony.getOwner();
            this.nation = Messages.message(other.getNationLabel());
            this.place = colony.getName();
            this.myGold = Math.max(0, unit.getOwner().getGold());
            for (Goods g : unit.getCompactGoodsList()) {
                if (g.getType().isStorable() && g.getAmount() > 0) {
                    mine.add(new Goods(unit.getGame(), unit, g.getType(),
                        Math.min(g.getAmount(), GoodsContainer.CARGO_SIZE)));
                }
            }
            for (GoodsType gt : unit.getSpecification().getStorableGoodsTypeList()) {
                Goods take = new Goods(unit.getGame(), null, gt, GoodsContainer.CARGO_SIZE);
                if (unit.couldCarry(take)) theirs.add(gt);
            }
        }

        public boolean isEmpty() {
            return (sell == null || sellAmount <= 0) && (buy == null || buyAmount <= 0);
        }

        /**
         * Roughly what the other side thinks the goods are worth to
         * them, in gold: what they would get selling yours in Europe,
         * less what buying theirs back would cost.  The price they
         * will take (more than 0 for them to pay you).  Their market
         * is hidden, so it is reckoned at yours, which is much the same.
         */
        public int fairPrice() {
            Player me = unit.getOwner();
            Market m = (other.getMarket() != null) ? other.getMarket() : me.getMarket();
            if (m == null) return 0;
            int tax = (other.getMarket() != null) ? other.getTax() : me.getTax();
            int v = 0;
            if (sell != null && sellAmount > 0) {
                v += (int)Math.round(m.getSalePrice(sell.getType(), sellAmount)
                                     * (1.0 - tax / 100.0));
            }
            if (buy != null && buyAmount > 0) {
                v -= m.getBidPrice(buy, buyAmount);
            }
            return v;
        }

        /** The offer in words, one item a line. */
        public String describe() {
            List<String> out = new ArrayList<>();
            if (sell != null && sellAmount > 0) out.add("You sell " + amount(sell.getType(), sellAmount));
            if (buy != null && buyAmount > 0) out.add("You buy " + amount(buy, buyAmount));
            if (price > 0) out.add("They pay you " + price + " gold");
            if (price < 0) out.add("You pay them " + (-price) + " gold");
            return String.join("\n", out);
        }
    }

    static String amount(GoodsType type, int n) {
        return n + " " + Messages.getName(type).toLowerCase(java.util.Locale.UK);
    }

    private ColonyTradeInfo() {} // Static only

    /** What could go into an offer from a carrier to a colony. */
    public static Offer offer(Unit unit, Colony colony) {
        return new Offer(unit, colony);
    }

    /**
     * The agreement to send for an offer.
     *
     * @return The agreement, or null if the offer is empty.
     */
    public static DiplomaticTrade agreement(Game game, Player me, Offer o) {
        if (o.isEmpty()) return null;
        DiplomaticTrade dt = new DiplomaticTrade(game, TradeContext.TRADE,
                                                 me, o.other, null, 0);
        if (o.sell != null && o.sellAmount > 0) {
            dt.add(new GoodsTradeItem(game, me, o.other,
                new Goods(game, o.unit, o.sell.getType(), o.sellAmount)));
        }
        if (o.buy != null && o.buyAmount > 0) {
            // Our copy of their colony has no warehouse, so the goods
            // are placed there afterwards (the server finds its own).
            Goods g = new Goods(game, null, o.buy, o.buyAmount);
            g.setLocation(o.colony);
            dt.add(new GoodsTradeItem(game, o.other, me, g));
        }
        if (o.price > 0) dt.add(new GoldTradeItem(game, o.other, me, o.price));
        if (o.price < 0) {
            dt.add(new GoldTradeItem(game, me, o.other, Math.min(-o.price, o.myGold)));
        }
        return dt;
    }
}
