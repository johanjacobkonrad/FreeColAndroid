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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.sf.freecol.android.map.IsometricRenderer;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.FoundingFather;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.HistoryEvent;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.NationSummary;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Stance;
import net.sf.freecol.common.model.Tension;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.UnitType;


/**
 * The reports in the sidebar menu, each as an {@link InfoPage}.  Read
 * while holding the session lock.  Other nations' figures come from
 * the server's nation summaries (GameSession.refreshNationSummaries).
 */
public final class ReportInfo {

    /** The reports, in menu order. */
    public enum Kind {
        CONGRESS("Continental Congress"), FOREIGN("Foreign affairs"),
        NATIVES("Native affairs"), MILITARY("Military"), NAVAL("Naval"),
        TRADE("Trade"), HISTORY("History");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    private ReportInfo() {} // Static only

    public static InfoPage page(Kind kind, Player me, IsometricRenderer renderer) {
        switch (kind) {
        case CONGRESS: return congress(me);
        case FOREIGN: return foreign(me);
        case NATIVES: return natives(me, renderer);
        case MILITARY: return units(me, renderer, false);
        case NAVAL: return units(me, renderer, true);
        case TRADE: return trade(me);
        case HISTORY: default: return history(me);
        }
    }

    /** The picture for a founding father: the one for his kind. */
    static String fatherImage(FoundingFather f) {
        return "image.flavor.model.foundingFather."
            + f.getType().toString().toLowerCase(java.util.Locale.UK);
    }

    private static InfoPage congress(Player me) {
        InfoPage page = new InfoPage(Kind.CONGRESS.label);
        page.summary = "Rebels " + me.getSoL() + "% · "
            + me.getFoundingFathers().size() + " founding fathers";
        InfoPage.Section now = page.section("Now recruiting");
        FoundingFather current = me.getCurrentFather();
        if (current == null) {
            now.add(null, "Nobody yet", "You are asked to choose a founding "
                    + "father as your colonies start producing liberty bells.", null);
        } else {
            int total = me.getTotalFoundingFatherCost();
            int left = me.getRemainingFoundingFatherCost();
            now.add(fatherImage(current), Messages.getName(current),
                    Messages.message(current.getTypeKey()) + "\n"
                    + Messages.getDescription(current),
                    Math.max(0, total - left) + " of " + total + " bells");
        }
        List<FoundingFather> fathers = new ArrayList<>(me.getFoundingFathers());
        fathers.sort(Comparator.comparing(f -> Messages.getName(f)));
        InfoPage.Section in = page.section("In the Congress (" + fathers.size() + ")");
        for (FoundingFather f : fathers) {
            in.add(fatherImage(f), Messages.getName(f),
                   Messages.message(f.getTypeKey()) + "\n" + Messages.getDescription(f),
                   null).link = f.getId();
        }
        if (fathers.isEmpty()) in.add(null, "None yet", null, null);
        return page;
    }

    private static String stance(Player me, Player other) {
        Stance s = me.getStance(other);
        return (s == null) ? "" : Messages.message(s.getNameKey());
    }

    private static InfoPage foreign(Player me) {
        InfoPage page = new InfoPage(Kind.FOREIGN.label);
        Game game = me.getGame();
        List<Player> others = game.getLiveEuropeanPlayers(me)
            .filter(p -> !p.isREF()).collect(Collectors.toList());
        InfoPage.Section sec = page.section("The European powers");
        for (Player p : others) {
            String name = Messages.message(p.getNationLabel());
            String crest = "image.miscicon." + p.getNationId();
            if (!me.hasContacted(p)) {
                sec.add(crest, name, "You have not met them yet.", null);
                continue;
            }
            NationSummary ns = me.getNationSummary(p);
            StringBuilder sb = new StringBuilder(stance(me, p));
            if (ns != null) {
                sb.append("\n").append(ns.getNumberOfSettlements()).append(" colonies · ")
                    .append(ns.getNumberOfUnits()).append(" units · rebels ")
                    .append(ns.getSoL()).append("%")
                    .append("\nArmy ").append(ns.getMilitaryStrength())
                    .append(" · navy ").append(ns.getNavalStrength())
                    .append(" · ").append(ns.getFoundingFathers())
                    .append(" founding fathers · tax ").append(ns.getTax()).append("%");
            }
            sec.add(crest, name, sb.toString(),
                    (ns == null || ns.getGold() < 0) ? null : ns.getGold() + " gold");
        }
        if (others.isEmpty()) sec.add(null, "No other European powers", null, null);
        NationSummary mine = me.getNationSummary(me);
        page.summary = "You: " + me.getColonyList().size() + " colonies, "
            + me.getUnitCount() + " units, score " + me.getScore()
            + ((mine == null) ? "" : ", army " + mine.getMilitaryStrength()
               + ", navy " + mine.getNavalStrength());
        return page;
    }

    private static InfoPage natives(Player me, IsometricRenderer renderer) {
        InfoPage page = new InfoPage(Kind.NATIVES.label);
        List<Player> natives = me.getGame().getLiveNativePlayers(me)
            .filter(p -> me.hasContacted(p)).collect(Collectors.toList());
        page.summary = natives.isEmpty() ? "" : "Scouts and visits tell you more.";
        if (natives.isEmpty()) {
            page.section("Native nations").add(null, "None met yet",
                "Explore the land to meet the native nations.", null);
        }
        for (Player p : natives) {
            Tension t = p.getTension(me);
            String mood = (t == null) ? "" : ", " + Messages.message(t.getNameKey())
                .toLowerCase(java.util.Locale.UK);
            List<IndianSettlement> known = new ArrayList<>(p.getIndianSettlementList());
            known.sort(Comparator.comparing(IndianSettlement::getName));
            InfoPage.Section sec = page.section(Messages.message(p.getNationLabel())
                + " · " + stance(me, p) + mood + " · "
                + known.size() + (known.size() == 1 ? " settlement" : " settlements"));
            for (IndianSettlement is : known) {
                boolean visited = is.hasVisited(me);
                StringBuilder sb = new StringBuilder();
                sb.append(Messages.message(is.getLearnableSkillLabel(visited)));
                Unit m = is.getMissionary();
                if (m != null) {
                    sb.append(" · ").append(m.getOwner() == me ? "your mission"
                        : Messages.message(m.getOwner().getNationLabel()) + " mission");
                }
                String alarm = is.getAlarmLevelKey(me);
                if (alarm != null) sb.append("\n").append(Messages.message(alarm));
                if (visited) {
                    List<String> wants = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        GoodsType gt = is.getWantedGoods(i);
                        if (gt != null) wants.add(Messages.getName(gt));
                    }
                    if (!wants.isEmpty()) {
                        sb.append("\nWants ").append(String.join(", ", wants));
                    }
                }
                sec.add(renderer.settlementKey(is), is.getName()
                        + (is.isCapital() ? " (capital)" : ""), sb.toString(), null);
            }
            if (known.isEmpty()) sec.add(null, "No settlements found yet", null, null);
        }
        return page;
    }

    private static String where(Unit u, Player me) {
        Location loc = u.getLocation();
        if (u.isOnCarrier()) return "aboard " + u.getCarrier().getName();
        if (u.isAtSea()) return "at sea";
        return (loc == null) ? "" : Messages.message(loc.getLocationLabelFor(me));
    }

    private static InfoPage units(Player me, IsometricRenderer renderer, boolean naval) {
        InfoPage page = new InfoPage(naval ? Kind.NAVAL.label : Kind.MILITARY.label);
        List<Unit> units = me.getUnits()
            .filter(u -> naval ? u.isNaval() : (!u.isNaval() && u.isOffensiveUnit()))
            .collect(Collectors.toList());
        page.summary = units.isEmpty()
            ? (naval ? "You have no ships." : "You have no soldiers or dragoons.")
            : units.size() + (naval ? " ships" : " armed units");
        if (naval) {
            InfoPage.Section sec = page.section("Your ships");
            units.sort(Comparator.comparing(Unit::getName,
                       Comparator.nullsLast(Comparator.naturalOrder())));
            for (Unit u : units) {
                List<String> load = new ArrayList<>();
                for (Goods g : u.getCompactGoodsList()) {
                    load.add(g.getAmount() + " " + Messages.getName(g.getType()));
                }
                for (Unit p : u.getUnitList()) load.add(p.getDescription());
                String name = (u.getName() == null) ? u.getDescription() : u.getName();
                sec.add(renderer.unitKey(u), name,
                        Messages.getName(u.getType()) + " · " + where(u, me)
                        + "\n" + (load.isEmpty() ? "Empty" : String.join(", ", load)),
                        u.getSpaceLeft() + " space left");
            }
            return page;
        }
        // Armed units, grouped by kind, with where they are.
        Map<String, List<Unit>> byKind = new LinkedHashMap<>();
        units.sort(Comparator.comparing(Unit::getDescription));
        for (Unit u : units) {
            byKind.computeIfAbsent(u.getDescription(), k -> new ArrayList<>()).add(u);
        }
        InfoPage.Section sec = page.section("Your forces");
        for (Map.Entry<String, List<Unit>> e : byKind.entrySet()) {
            Unit first = e.getValue().get(0);
            Map<String, Integer> places = new LinkedHashMap<>();
            for (Unit u : e.getValue()) places.merge(where(u, me), 1, Integer::sum);
            List<String> at = new ArrayList<>();
            for (Map.Entry<String, Integer> pe : places.entrySet()) {
                at.add((pe.getValue() > 1 ? pe.getValue() + " " : "") + pe.getKey());
            }
            sec.add(renderer.unitKey(first), e.getKey(), String.join(", ", at),
                    Integer.toString(e.getValue().size()));
        }
        // What could be armed: colonists outside colonies count too.
        UnitType vet = me.getSpecification().getUnitType("model.unit.veteranSoldier");
        if (vet != null && units.isEmpty()) {
            sec.add(null, "No forces", "Arm colonists with muskets (Orders, Equip) "
                    + "or buy veteran soldiers in Europe.", null);
        }
        return page;
    }

    private static InfoPage trade(Player me) {
        InfoPage page = new InfoPage(Kind.TRADE.label);
        Market market = me.getMarket();
        page.summary = "Tax " + me.getTax() + "% · prices per unit in Europe";
        InfoPage.Section sec = page.section("Goods");
        sec.compact = true;
        if (market == null) {
            sec.add(null, "No market yet", null, null);
            return page;
        }
        int total = 0;
        for (GoodsType gt : me.getSpecification().getStorableGoodsTypeList()) {
            if (!gt.isStorable()) continue;
            int sales = market.getSales(gt), income = market.getIncomeAfterTaxes(gt);
            total += income;
            String text = "sell " + market.getPaidForSale(gt) + " · buy "
                + market.getCostToBuy(gt);
            if (sales != 0) text += "\nsold " + sales + ", earned " + income;
            sec.add("image.icon." + gt.getId(), Messages.getName(gt), text, null);
        }
        page.summary += " · earned " + total + " gold so far";
        return page;
    }

    private static InfoPage history(Player me) {
        InfoPage page = new InfoPage(Kind.HISTORY.label);
        List<HistoryEvent> events = new ArrayList<>(me.getHistory());
        java.util.Collections.reverse(events);
        page.summary = "Score " + me.getScore();
        InfoPage.Section sec = page.section("Newest first");
        for (HistoryEvent e : events) {
            sec.add(null, Messages.message(e.getTurn().getLabel()),
                    Messages.message(e), null);
        }
        if (events.isEmpty()) sec.add(null, "Nothing yet", null, null);
        return page;
    }
}
