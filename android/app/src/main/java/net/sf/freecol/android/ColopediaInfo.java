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
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.common.model.FoundingFather;
import net.sf.freecol.common.model.FreeColObject;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.UnitType;


/**
 * The Colopedia: the game's encyclopaedia of units, buildings, goods,
 * terrain and founding fathers, from the rules and their texts.  Each
 * category is a list; each item a page with its picture, figures and
 * description.
 */
public final class ColopediaInfo {

    /** The categories, in menu order. */
    public enum Kind {
        UNITS("Units"), BUILDINGS("Buildings"), GOODS("Goods"),
        TERRAIN("Terrain"), FATHERS("Founding fathers");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    private ColopediaInfo() {} // Static only

    /** The picture for anything in the Colopedia. */
    public static String imageKey(FreeColObject o) {
        if (o instanceof UnitType) return "image.unit." + o.getId();
        if (o instanceof BuildingType) return "image.buildingicon." + o.getId();
        if (o instanceof GoodsType) return "image.icon." + o.getId();
        if (o instanceof TileType) return "image.tile." + o.getId() + ".center";
        if (o instanceof FoundingFather) return ReportInfo.fatherImage((FoundingFather)o);
        return null;
    }

    /** The items in a category. */
    public static List<? extends FreeColObject> items(Specification spec, Kind kind) {
        List<FreeColObject> out = new ArrayList<>();
        switch (kind) {
        case UNITS:
            for (UnitType ut : spec.getUnitTypeList()) {
                // Leave out the King's army and the natives' own units.
                if (ut.hasAbility("model.ability.refUnit")
                    || ut.hasAbility("model.ability.native")) continue;
                out.add(ut);
            }
            break;
        case BUILDINGS:
            out.addAll(spec.getBuildingTypeList());
            break;
        case GOODS:
            out.addAll(spec.getGoodsTypeList());
            break;
        case TERRAIN:
            out.addAll(spec.getTileTypeList());
            break;
        case FATHERS:
            out.addAll(spec.getFoundingFathers());
            break;
        }
        return out;
    }

    /** A list of a category, as a page of entries linking to items. */
    public static InfoPage list(Specification spec, Kind kind) {
        InfoPage page = new InfoPage(kind.label);
        InfoPage.Section sec = page.section(null);
        sec.compact = true;
        for (FreeColObject o : items(spec, kind)) {
            String sub = "";
            if (o instanceof FoundingFather) {
                sub = Messages.message(((FoundingFather)o).getTypeKey());
            } else if (o instanceof UnitType) {
                UnitType ut = (UnitType)o;
                sub = (ut.getPrice() > 0) ? ut.getPrice() + " gold in Europe" : "";
            }
            sec.add(imageKey(o), name(o), sub, null).link = o.getId();
        }
        return page;
    }

    private static String name(FreeColObject o) {
        return Messages.getName(o.getId());
    }

    private static String description(FreeColObject o) {
        String key = o.getId() + ".description";
        String text = Messages.containsKey(key) ? Messages.message(key) : "";
        if (o instanceof FoundingFather && Messages.containsKey(o.getId() + ".text")) {
            text += "\n\n" + Messages.message(o.getId() + ".text");
        }
        // A few texts have HTML (links between pages): keep the words.
        return text.replaceAll("<[^>]+>", "").trim();
    }

    private static String goods(List<AbstractGoods> list) {
        List<String> out = new ArrayList<>();
        for (AbstractGoods ag : list) {
            out.add(ag.getAmount() + " " + Messages.getName(ag.getType()));
        }
        return out.isEmpty() ? "nothing" : String.join(", ", out);
    }

    /**
     * One item's page.
     *
     * @param spec The rules.
     * @param id The item's id.
     * @return Its page, or null if there is no such item.
     */
    public static InfoPage page(Specification spec, String id) {
        FreeColObject o = (id == null) ? null : spec.getType(id);
        if (o == null) return null;
        InfoPage page = new InfoPage(name(o));
        page.imageKey = imageKey(o);
        page.body = description(o);
        InfoPage.Section facts = page.section("Facts");
        facts.compact = true;
        if (o instanceof UnitType) {
            UnitType ut = (UnitType)o;
            facts.add(null, "Offence", null, fmt(ut.getOffence()));
            facts.add(null, "Defence", null, fmt(ut.getDefence()));
            facts.add(null, "Moves", null, Integer.toString(ut.getMovement() / 3));
            facts.add(null, "Sight", null, Integer.toString(ut.getLineOfSight()));
            if (ut.getSpace() > 0) {
                facts.add(null, "Cargo holds", null, Integer.toString(ut.getSpace()));
            }
            if (ut.getPrice() > 0) {
                facts.add(null, "Price in Europe", null, ut.getPrice() + " gold");
            }
            if (ut.getSkill() > 0) facts.add(null, "Skill", null, Integer.toString(ut.getSkill()));
            GoodsType ex = ut.getExpertProduction();
            if (ex != null) {
                facts.add(imageKey(ex), "Expert at", null, Messages.getName(ex)).link = ex.getId();
            }
            List<AbstractGoods> needs = ut.getRequiredGoodsList();
            if (!needs.isEmpty()) facts.add(null, "Costs to build", null, goods(needs));
        } else if (o instanceof BuildingType) {
            BuildingType bt = (BuildingType)o;
            facts.add(null, "Colonists needed", null,
                      Integer.toString(bt.getRequiredPopulation()));
            facts.add(null, "Costs", null, goods(bt.getRequiredGoodsList()));
            if (bt.getWorkPlaces() > 0) {
                facts.add(null, "Workers", null, Integer.toString(bt.getWorkPlaces()));
            }
            BuildingType from = bt.getUpgradesFrom();
            if (from != null) {
                facts.add(imageKey(from), "Upgrades", null, name(from)).link = from.getId();
            }
            BuildingType to = bt.getUpgradesTo();
            if (to != null) {
                facts.add(imageKey(to), "Upgrades to", null, name(to)).link = to.getId();
            }
        } else if (o instanceof GoodsType) {
            GoodsType gt = (GoodsType)o;
            GoodsType in = gt.getInputType(), out = gt.getOutputType();
            if (in != null) facts.add(imageKey(in), "Made from", null, name(in)).link = in.getId();
            if (out != null) facts.add(imageKey(out), "Made into", null, name(out)).link = out.getId();
            facts.add(null, "Stored in the warehouse", null, gt.isStorable() ? "yes" : "no");
            if (gt.isFoodType()) facts.add(null, "Food", null, "yes");
        } else if (o instanceof TileType) {
            TileType tt = (TileType)o;
            facts.add(null, "Move cost", null, Integer.toString(tt.getBasicMoveCost() / 3));
            InfoPage.Section prod = page.section("Produces (by a free colonist)");
            prod.compact = true;
            UnitType colonist = spec.getDefaultUnitType();
            for (GoodsType gt : spec.getGoodsTypeList()) {
                int n = tt.getPotentialProduction(gt, colonist);
                if (n > 0) {
                    prod.add(imageKey(gt), name(gt), null, Integer.toString(n)).link = gt.getId();
                }
            }
            if (prod.entries.isEmpty()) page.sections.remove(prod);
        } else if (o instanceof FoundingFather) {
            FoundingFather f = (FoundingFather)o;
            facts.add(null, "Kind", null, Messages.message(f.getTypeKey()));
        }
        if (facts.entries.isEmpty()) page.sections.remove(facts);
        return page;
    }

    private static String fmt(double d) {
        return (d == Math.rint(d)) ? Integer.toString((int)d) : Double.toString(d);
    }
}
