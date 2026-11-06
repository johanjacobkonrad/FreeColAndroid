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

import net.sf.freecol.android.map.IsometricRenderer;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.BuildableType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;


/**
 * The colony list (from the sidebar menu): one line per colony with
 * its size, rebels, food and production, what it is building and
 * anything that needs attention.  Read while holding the session lock;
 * plain Java, so the desktop check can use it.
 */
public final class ColonyListInfo {

    /** One colony. */
    public static final class Row {
        public final Colony colony;
        public final String name, imageKey, building;
        public final int size, rebels, food, hammers, bells;
        /** Problems, such as starving or goods that will spoil. */
        public final List<String> warnings = new ArrayList<>();

        Row(Colony colony, String imageKey, String building, int size,
            int rebels, int food, int hammers, int bells) {
            this.colony = colony;
            this.name = colony.getName();
            this.imageKey = imageKey;
            this.building = building;
            this.size = size;
            this.rebels = rebels;
            this.food = food;
            this.hammers = hammers;
            this.bells = bells;
        }
    }

    public final List<Row> rows = new ArrayList<>();
    public final int colonists;


    public ColonyListInfo(Player me, IsometricRenderer renderer) {
        int total = 0;
        List<Colony> colonies = new ArrayList<>(me.getColonyList());
        colonies.sort(Comparator.comparing(Colony::getName));
        for (Colony c : colonies) {
            Specification spec = c.getSpecification();
            int food = 0;
            for (GoodsType gt : spec.getFoodGoodsTypeList()) {
                food += c.getNetProductionOf(gt);
            }
            BuildableType current = c.getCurrentlyBuilding();
            String building = (current == null) ? "Building nothing"
                : ColonyInfo.buildLabel(c, current);
            Row r = new Row(c, renderer.settlementKey(c), building,
                            c.getUnitCount(), c.getSonsOfLiberty(), food,
                            net(c, spec, "model.goods.hammers"),
                            net(c, spec, "model.goods.bells"));
            if (food < 0) {
                int stored = 0;
                for (GoodsType gt : spec.getFoodGoodsTypeList()) {
                    stored += c.getGoodsCount(gt);
                }
                int turns = stored / -food;
                r.warnings.add(turns <= 1 ? "Starving!"
                               : "Food runs out in " + turns + " turns");
            }
            if (current == null) r.warnings.add("Nothing to build");
            int capacity = c.getWarehouseCapacity();
            for (GoodsType gt : spec.getStorableGoodsTypeList()) {
                if (gt.isFoodType()) continue;
                int count = c.getGoodsCount(gt);
                int next = count + c.getNetProductionOf(gt);
                if (next > capacity && c.getNetProductionOf(gt) > 0) {
                    r.warnings.add(Messages.getName(gt) + " will spoil");
                }
            }
            total += r.size;
            rows.add(r);
        }
        this.colonists = total;
    }

    private static int net(Colony c, Specification spec, String id) {
        GoodsType gt = spec.getGoodsType(id);
        return (gt == null) ? 0 : c.getNetProductionOf(gt);
    }
}
