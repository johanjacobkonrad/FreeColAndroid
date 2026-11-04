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

import net.sf.freecol.android.map.IsometricRenderer;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.BuildableType;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTile;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * Everything the colony screen shows, read from the game in one go
 * (while holding the session lock) so the screen can lay it out
 * without touching the game model.  No Android code, so it is
 * checked on a desktop JVM too.
 */
public final class ColonyInfo {

    /** An amount of some goods, with its icon. */
    public static final class Amount {
        public final GoodsType type;
        public final String iconKey;
        public final int amount;

        Amount(GoodsType type, int amount) {
            this.type = type;
            this.iconKey = "image.icon." + type.getId();
            this.amount = amount;
        }
    }

    /** A unit, with its picture and a short caption. */
    public static final class UnitItem {
        public final Unit unit;
        public final String imageKey;
        public final String name;
        public final String caption;

        UnitItem(Unit unit, String imageKey, String name, String caption) {
            this.unit = unit;
            this.imageKey = imageKey;
            this.name = name;
            this.caption = caption;
        }
    }

    /** One of the colony's tiles. */
    public static final class LandItem {
        public final ColonyTile colonyTile;
        public final Tile tile;
        public final boolean centre;
        public final UnitItem worker;
        public final List<Amount> production = new ArrayList<>();

        LandItem(ColonyTile ct, UnitItem worker) {
            this.colonyTile = ct;
            this.tile = ct.getWorkTile();
            this.centre = ct.isColonyCenterTile();
            this.worker = worker;
        }
    }

    /** A building and who works in it. */
    public static final class BuildingItem {
        public final Building building;
        public final String name;
        public final String imageKey;
        public final int capacity;
        public final List<UnitItem> workers = new ArrayList<>();
        public final List<Amount> production = new ArrayList<>();

        BuildingItem(Building b, String name, String imageKey) {
            this.building = b;
            this.name = name;
            this.imageKey = imageKey;
            this.capacity = b.getUnitCapacity();
        }
    }

    /** A ship or wagon train in the colony, with its load. */
    public static final class CarrierItem {
        public final UnitItem carrier;
        public final List<Amount> cargo = new ArrayList<>();
        public final List<UnitItem> passengers = new ArrayList<>();
        public final int spaceLeft;

        CarrierItem(UnitItem carrier, int spaceLeft) {
            this.carrier = carrier;
            this.spaceLeft = spaceLeft;
        }
    }

    /** Goods in the warehouse. */
    public static final class Stock {
        public final GoodsType type;
        public final String iconKey;
        public final String name;
        public final int count;
        public final int net;
        public final boolean overflow;

        Stock(GoodsType type, String name, int count, int net, boolean overflow) {
            this.type = type;
            this.iconKey = "image.icon." + type.getId();
            this.name = name;
            this.count = count;
            this.net = net;
            this.overflow = overflow;
        }
    }

    /** Something to pick from a list, with its label. */
    public static final class Choice<T> {
        public final T value;
        public final String label;

        Choice(T value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    public final Colony colony;
    public final String title;
    public final String stats;
    public final String building;
    public final List<LandItem> land = new ArrayList<>();
    public final List<BuildingItem> buildings = new ArrayList<>();
    public final List<UnitItem> outside = new ArrayList<>();
    public final List<CarrierItem> carriers = new ArrayList<>();
    public final List<Stock> warehouse = new ArrayList<>();


    /**
     * Read a colony.  Call while holding the session lock.
     *
     * @param colony The {@code Colony} to read.
     * @param me The player looking at it.
     * @param renderer For the units' image keys.
     */
    public ColonyInfo(Colony colony, Player me, IsometricRenderer renderer) {
        this.colony = colony;
        final Specification spec = colony.getSpecification();
        this.title = colony.getName();
        StringBuilder sb = new StringBuilder();
        sb.append(colony.getUnitCount()).append(" colonists   Rebels ")
            .append(colony.getSonsOfLiberty()).append('%');
        for (String id : new String[] { "model.goods.hammers",
                                         "model.goods.bells",
                                         "model.goods.crosses" }) {
            GoodsType gt = spec.getGoodsType(id);
            if (gt == null) continue;
            sb.append("   ").append(Messages.getName(gt)).append(' ')
                .append(signed(colony.getNetProductionOf(gt)));
        }
        this.stats = sb.toString();

        BuildableType current = colony.getCurrentlyBuilding();
        this.building = (current == null) ? "Build: nothing"
            : "Build: " + Messages.getName(current) + turns(colony, current);

        for (ColonyTile ct : colony.getColonyTiles()) {
            Unit w = ct.isEmpty() ? null : ct.getUnitList().get(0);
            LandItem li = new LandItem(ct, (w == null) ? null
                                       : unitItem(w, me, renderer));
            for (AbstractGoods ag : ct.getProduction()) {
                if (ag.getAmount() > 0) li.production.add(new Amount(ag.getType(), ag.getAmount()));
            }
            land.add(li);
        }

        for (Building b : colony.getBuildings()) {
            String key = "image.buildingicon." + b.getType().getId();
            BuildingItem bi = new BuildingItem(b,
                Messages.getName(b.getType()), key);
            for (Unit u : b.getUnitList()) bi.workers.add(unitItem(u, me, renderer));
            for (AbstractGoods ag : b.getProduction()) {
                if (ag.getAmount() > 0) bi.production.add(new Amount(ag.getType(), ag.getAmount()));
            }
            buildings.add(bi);
        }

        Tile tile = colony.getTile();
        for (Unit u : tile.getUnitList()) {
            if (u.getOwner() != me) continue;
            if (u.isCarrier()) {
                CarrierItem ci = new CarrierItem(unitItem(u, me, renderer),
                                                 u.getSpaceLeft());
                for (Goods g : u.getCompactGoodsList()) {
                    ci.cargo.add(new Amount(g.getType(), g.getAmount()));
                }
                for (Unit p : u.getUnitList()) ci.passengers.add(unitItem(p, me, renderer));
                carriers.add(ci);
            } else {
                outside.add(unitItem(u, me, renderer));
            }
        }

        int capacity = colony.getWarehouseCapacity();
        for (GoodsType gt : spec.getStorableGoodsTypeList()) {
            int count = colony.getGoodsCount(gt);
            int net = colony.getNetProductionOf(gt);
            boolean over = !gt.isFoodType() && count > capacity;
            warehouse.add(new Stock(gt, Messages.getName(gt), count, net, over));
        }
    }

    /**
     * What a worker could produce on a colony tile.
     *
     * @param ct The {@code ColonyTile}.
     * @param unit The worker.
     * @return The choices, best first.
     */
    public static List<Choice<GoodsType>> workChoices(ColonyTile ct, Unit unit) {
        List<Choice<GoodsType>> result = new ArrayList<>();
        for (GoodsType gt : ct.getSpecification().getGoodsTypeList()) {
            if (!gt.isFarmed()) continue;
            int n = ct.getPotentialProduction(gt, unit.getType());
            if (n > 0) result.add(new Choice<>(gt, Messages.getName(gt) + "  " + n));
        }
        result.sort((a, b) -> Integer.compare(potential(ct, b.value, unit),
                                              potential(ct, a.value, unit)));
        return result;
    }

    private static int potential(ColonyTile ct, GoodsType gt, Unit unit) {
        return ct.getPotentialProduction(gt, unit.getType());
    }

    /**
     * What a colony could build next, with how long each would take.
     *
     * @param session The {@code GameSession}.
     * @param colony The {@code Colony}.
     * @return The choices.
     */
    public static List<Choice<BuildableType>> buildChoices(GameSession session,
                                                           Colony colony) {
        List<Choice<BuildableType>> result = new ArrayList<>();
        for (BuildableType bt : session.getBuildable(colony)) {
            result.add(new Choice<>(bt, Messages.getName(bt) + turns(colony, bt)));
        }
        return result;
    }

    private static UnitItem unitItem(Unit u, Player me,
                                     IsometricRenderer renderer) {
        String caption = (u.isCarrier() || !u.isInColony())
            ? u.getMovesAsString()
            : (u.getWorkType() == null) ? "" : Messages.getName(u.getWorkType());
        return new UnitItem(u, renderer.unitKey(u), u.getDescription(), caption);
    }

    private static String turns(Colony colony, BuildableType bt) {
        int n = colony.getTurnsToComplete(bt);
        return (n < 0) ? " (needs more production)"
            : (n == 1) ? " (1 turn)" : " (" + n + " turns)";
    }

    private static String signed(int n) {
        return (n > 0) ? "+" + n : Integer.toString(n);
    }
}
