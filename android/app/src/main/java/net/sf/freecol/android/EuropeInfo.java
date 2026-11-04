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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.sf.freecol.android.map.IsometricRenderer;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.AbstractUnit;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;


/**
 * Everything the Europe screen shows, read in one go (while holding
 * the session lock).  No Android code, so it is checked on a desktop
 * JVM too.
 */
public final class EuropeInfo {

    /** A ship in port, with its load. */
    public static final class Ship {
        public final ColonyInfo.UnitItem ship;
        public final List<ColonyInfo.Amount> cargo = new ArrayList<>();
        public final List<ColonyInfo.UnitItem> passengers = new ArrayList<>();
        public final int spaceLeft;

        Ship(ColonyInfo.UnitItem ship, int spaceLeft) {
            this.ship = ship;
            this.spaceLeft = spaceLeft;
        }
    }

    /** Something to buy: an emigrant, or a unit to train. */
    public static final class Offer {
        public final int index;
        public final UnitType type;
        public final String imageKey;
        public final String name;
        public final int price;

        Offer(int index, UnitType type, String imageKey, String name, int price) {
            this.index = index;
            this.type = type;
            this.imageKey = imageKey;
            this.name = name;
            this.price = price;
        }
    }

    /** A market line. */
    public static final class Price {
        public final GoodsType type;
        public final String iconKey;
        public final String name;
        public final int sell, buy;
        public final boolean boycott;

        Price(GoodsType type, String name, int sell, int buy, boolean boycott) {
            this.type = type;
            this.iconKey = "image.icon." + type.getId();
            this.name = name;
            this.sell = sell;
            this.buy = buy;
            this.boycott = boycott;
        }
    }

    public final String title;
    public final int gold;
    public final int tax;
    public final int recruitPrice;
    public final List<Ship> inPort = new ArrayList<>();
    public final List<ColonyInfo.UnitItem> docks = new ArrayList<>();
    /** Ships at sea: caption says where to and how many turns. */
    public final List<ColonyInfo.UnitItem> atSea = new ArrayList<>();
    public final List<Offer> recruits = new ArrayList<>();
    public final List<Offer> training = new ArrayList<>();
    public final List<Price> market = new ArrayList<>();


    /**
     * Read Europe.  Call while holding the session lock.
     *
     * @param me The player.
     * @param renderer For the units' image keys.
     */
    public EuropeInfo(Player me, IsometricRenderer renderer) {
        final Europe europe = me.getEurope();
        final Specification spec = me.getSpecification();
        this.title = Messages.getName(europe);
        this.gold = me.getGold();
        this.tax = me.getTax();
        this.recruitPrice = me.getEuropeanRecruitPrice();

        for (Unit u : europe.getUnitList()) {
            ColonyInfo.UnitItem item = item(u, renderer, u.getMovesAsString());
            if (u.isNaval()) {
                Ship s = new Ship(item, u.getSpaceLeft());
                for (Goods g : u.getCompactGoodsList()) {
                    s.cargo.add(new ColonyInfo.Amount(g.getType(), g.getAmount()));
                }
                for (Unit p : u.getUnitList()) {
                    s.passengers.add(item(p, renderer, ""));
                }
                inPort.add(s);
            } else {
                docks.add(item(u, renderer, ""));
            }
        }
        if (me.getHighSeas() != null) {
            for (Unit u : me.getHighSeas().getUnitList()) {
                String where = (u.getDestination() instanceof Europe)
                    ? "to Europe" : "to America";
                int turns = u.getWorkLeft();
                atSea.add(item(u, renderer, where + ", " + turns
                               + ((turns == 1) ? " turn" : " turns")));
            }
        }

        int i = 0;
        for (AbstractUnit au : europe.getExpandedRecruitables(false)) {
            UnitType ut = au.getType(spec);
            recruits.add(new Offer(i++, ut, unitTypeKey(ut, renderer),
                                   Messages.getName(ut), recruitPrice));
        }

        Set<UnitType> trainable = new LinkedHashSet<>();
        trainable.addAll(spec.getUnitTypesTrainedInEurope(me));
        trainable.addAll(spec.getUnitTypesPurchasedInEurope(me));
        for (UnitType ut : trainable) {
            int price = europe.getUnitPrice(ut);
            if (price <= 0) continue;
            training.add(new Offer(-1, ut, unitTypeKey(ut, renderer),
                                   Messages.getName(ut), price));
        }

        final Market m = me.getMarket();
        for (GoodsType gt : spec.getStorableGoodsTypeList()) {
            market.add(new Price(gt, Messages.getName(gt),
                                 m.getPaidForSale(gt), m.getCostToBuy(gt),
                                 !me.canTrade(gt)));
        }
    }

    private static ColonyInfo.UnitItem item(Unit u, IsometricRenderer renderer,
                                            String caption) {
        return new ColonyInfo.UnitItem(u, renderer.unitKey(u),
                                       u.getDescription(), caption);
    }

    /** Image key for a unit type with its default role. */
    private static String unitTypeKey(UnitType ut, IsometricRenderer renderer) {
        return "image.unit." + ut.getId();
    }
}
