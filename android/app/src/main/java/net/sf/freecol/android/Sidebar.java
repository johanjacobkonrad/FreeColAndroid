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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.sf.freecol.android.map.Art;
import net.sf.freecol.android.map.IsometricRenderer;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * The game screen's right-hand panel, top to bottom: the minimap with
 * the year, gold and tax under it; the selected unit's card (picture,
 * name, moves, cargo, and the other units on its tile); the commands,
 * with one wide button for whatever fits the unit; Europe and End
 * turn.
 */
final class Sidebar extends LinearLayout {

    /** What the buttons do. */
    interface Controls {
        void next();
        void waitUnit();
        void orders();
        /** The wide context button: build or join a colony, or sail. */
        void context(String mode);
        void europe();
        void endTurn();
        /** The menu: colonies, saving, quitting. */
        void menu();
    }

    static final String BUILD = "build", JOIN = "join", SAIL = "sail";

    private final GameSession session;
    private final Art art;
    private final IsometricRenderer renderer;
    private final Controls controls;

    private final ImageView crest;
    private final TextView year, gold, tax;
    private final LinearLayout card, cargo, stack, pips;
    private final ImageView unitPic;
    private final TextView unitName, unitSub, noUnit;
    private final TextView nextB, waitB, ordersB, europeB, endB, contextB;
    private String contextMode = null;
    private String crestFor = null;


    Sidebar(Context c, GameSession session, Art art, IsometricRenderer renderer,
            MinimapView minimap, Controls controls) {
        super(c);
        this.session = session;
        this.art = art;
        this.renderer = renderer;
        this.controls = controls;
        setOrientation(VERTICAL);
        setBackground(Theme.wood());
        int gap = Theme.px(c, 5);
        setPadding(gap, gap, gap, gap);

        // Minimap and status in one frame.
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(VERTICAL);
        top.setBackground(Theme.plank(c));
        int p = Theme.px(c, 4);
        top.setPadding(p, p, p, p);
        FrameLayout mm = new FrameLayout(c);
        GradientDrawable mmEdge = new GradientDrawable();
        mmEdge.setColor(0xFF000000);
        mm.setBackground(mmEdge);
        mm.addView(minimap, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        top.addView(mm, new LayoutParams(LayoutParams.MATCH_PARENT, Theme.px(c, 80)));
        LinearLayout status = new LinearLayout(c);
        status.setOrientation(HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(0, p, 0, 0);
        crest = new ImageView(c);
        crest.setScaleType(ImageView.ScaleType.FIT_CENTER);
        status.addView(crest, new LayoutParams(Theme.px(c, 20), Theme.px(c, 22)));
        year = Theme.serif(c, "", 15, Theme.GOLD, true);
        year.setPadding(Theme.px(c, 5), 0, 0, 0);
        status.addView(year, Theme.weight(1));
        gold = Theme.text(c, "", 12, Theme.INK, true);
        status.addView(gold);
        tax = Theme.text(c, "", 11, Theme.DIM, false);
        tax.setPadding(Theme.px(c, 6), 0, 0, 0);
        status.addView(tax);
        ImageView menu = new ImageView(c);
        menu.setImageDrawable(new Theme.IconDrawable(Theme.Icon.MENU, Theme.GOLD, Theme.px(c, 20)));
        menu.setScaleType(ImageView.ScaleType.CENTER);
        menu.setContentDescription("Menu");
        menu.setOnClickListener(v -> controls.menu());
        LayoutParams mlp = new LayoutParams(Theme.px(c, 32), Theme.px(c, 28));
        mlp.setMargins(Theme.px(c, 4), 0, 0, 0);
        status.addView(menu, mlp);
        top.addView(status);
        addView(top, full(gap));

        // The selected unit.
        card = new LinearLayout(c);
        card.setOrientation(VERTICAL);
        card.setBackground(Theme.paper(c));
        card.setPadding(Theme.px(c, 6), Theme.px(c, 5), Theme.px(c, 6), Theme.px(c, 5));
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        unitPic = new ImageView(c);
        unitPic.setScaleType(ImageView.ScaleType.FIT_CENTER);
        GradientDrawable disc = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[] { 0xFFFFF6DC, 0xFFD8C69F });
        disc.setShape(GradientDrawable.OVAL);
        disc.setStroke(Theme.px(c, 1), 0xFFB49A6A);
        unitPic.setBackground(disc);
        int ip = Theme.px(c, 3);
        unitPic.setPadding(ip, ip, ip, ip);
        row.addView(unitPic, new LayoutParams(Theme.px(c, 40), Theme.px(c, 40)));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(VERTICAL);
        words.setPadding(Theme.px(c, 6), 0, 0, 0);
        unitName = Theme.serif(c, "", 12, Theme.PAPER_INK, true);
        unitName.setMaxLines(2);
        unitName.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(unitName);
        unitSub = Theme.text(c, "", 10, Theme.PAPER_DIM, false);
        unitSub.setMaxLines(2);
        words.addView(unitSub);
        pips = new LinearLayout(c);
        pips.setOrientation(HORIZONTAL);
        pips.setPadding(0, Theme.px(c, 2), 0, 0);
        words.addView(pips);
        row.addView(words, Theme.weight(1));
        card.addView(row);
        cargo = new LinearLayout(c);
        cargo.setOrientation(HORIZONTAL);
        cargo.setGravity(Gravity.CENTER_VERTICAL);
        cargo.setPadding(0, Theme.px(c, 3), 0, 0);
        card.addView(cargo);
        stack = new LinearLayout(c);
        stack.setOrientation(HORIZONTAL);
        HorizontalScrollView ss = new HorizontalScrollView(c);
        ss.setHorizontalScrollBarEnabled(false);
        ss.addView(stack);
        card.addView(ss);
        noUnit = Theme.serif(c, "No unit selected.", 11, Theme.PAPER_DIM, false);
        card.addView(noUnit);
        addView(card, full(gap));

        // Commands.
        LinearLayout cmds = new LinearLayout(c);
        cmds.setOrientation(VERTICAL);
        cmds.setBackground(Theme.plank(c));
        LinearLayout r1 = new LinearLayout(c);
        r1.setOrientation(HORIZONTAL);
        nextB = Theme.button(c, "Next", Theme.Icon.NEXT, Theme.Kind.FLAT, true);
        waitB = Theme.button(c, "Wait", Theme.Icon.WAIT, Theme.Kind.FLAT, true);
        ordersB = Theme.button(c, "Orders", Theme.Icon.ORDERS, Theme.Kind.FLAT, true);
        for (TextView b : new TextView[] { nextB, waitB, ordersB }) {
            b.setTextSize(10.5f);
            r1.addView(b, Theme.weight(1));
        }
        nextB.setOnClickListener(v -> controls.next());
        waitB.setOnClickListener(v -> controls.waitUnit());
        ordersB.setOnClickListener(v -> controls.orders());
        cmds.addView(r1);
        contextB = Theme.button(c, "", Theme.Icon.SAIL, Theme.Kind.FLAT, false);
        contextB.setTextSize(12.5f);
        contextB.setTextColor(0xFFFFF3D6);
        GradientDrawable lit = new GradientDrawable();
        lit.setColor(Theme.HIGHLIGHT);
        lit.setCornerRadius(4 * Theme.dp(c));
        contextB.setBackground(lit);
        contextB.setOnClickListener(v -> {
                if (contextMode != null) controls.context(contextMode);
            });
        LayoutParams clp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        clp.setMargins(Theme.px(c, 3), 0, Theme.px(c, 3), Theme.px(c, 3));
        cmds.addView(contextB, clp);
        addView(cmds, full(gap));

        // Room to breathe; the news is on the map (NewsRibbon).
        addView(new View(c), new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        // Europe and End turn.
        LinearLayout bottom = new LinearLayout(c);
        bottom.setOrientation(HORIZONTAL);
        europeB = Theme.button(c, "Europe", Theme.Icon.HOUSE, Theme.Kind.PLANK, false);
        europeB.setCompoundDrawablePadding(Theme.px(c, 4));
        europeB.setOnClickListener(v -> controls.europe());
        LayoutParams elp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
        elp.setMargins(0, 0, gap, 0);
        bottom.addView(europeB, elp);
        endB = Theme.button(c, "End turn", null, Theme.Kind.GOLD, false);
        endB.setOnClickListener(v -> controls.endTurn());
        bottom.addView(endB, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1.6f));
        addView(bottom, new LayoutParams(LayoutParams.MATCH_PARENT, Theme.px(c, 42)));
    }

    private static LayoutParams full(int bottom) {
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, bottom);
        return lp;
    }


    // Refresh from the game

    void refresh() {
        final Context c = getContext();
        boolean myTurn = session.isMyTurn();
        Unit unit = session.getSelectedUnit();
        String yearText = "", goldText = "", taxText = "", nation = null;
        String name = "", sub = "";
        int movesWhole = 0, movesMax = 0;
        List<Object[]> load = new ArrayList<>();
        List<Unit> here = new ArrayList<>();
        Bitmap pic = null;
        String mode = null;
        synchronized (session.lock()) {
            Player me = session.getMyPlayer();
            if (session.isInGame() && me != null) {
                yearText = Messages.message(session.getGame().getTurn().getLabel());
                goldText = me.getGold() + " gold";
                taxText = "tax " + me.getTax() + "%";
                nation = me.getNationId();
                if (unit != null) {
                    String own = unit.getName();
                    String type = Messages.getName(unit.getType());
                    name = (own != null && !own.isEmpty()) ? own : unit.getDescription();
                    String doing = session.describeOrders(unit);
                    sub = ((own != null && !own.isEmpty()) ? type + " · " : "")
                        + "moves " + unit.getMovesAsString()
                        + (doing.isEmpty() ? "" : "\n" + doing);
                    movesWhole = (unit.getMovesLeft() + 2) / 3;
                    movesMax = Math.max(movesWhole, unit.getInitialMovesLeft() / 3);
                    Art.Img img = art.get(renderer.unitKey(unit));
                    if (img != null) pic = (Bitmap)img.handle;
                    for (Goods g : unit.getCompactGoodsList()) {
                        Art.Img gi = art.get("image.icon." + g.getType().getId());
                        load.add(new Object[] { (gi == null) ? null : gi.handle,
                                                Integer.toString(g.getAmount()) });
                    }
                    for (Unit p : unit.getUnitList()) {
                        Art.Img ui = art.get(renderer.unitKey(p));
                        load.add(new Object[] { (ui == null) ? null : ui.handle, "" });
                    }
                    Tile t = unit.getTile();
                    if (t != null) here = session.getMyUnitsAt(t);
                    mode = session.canSailToEurope(unit) ? SAIL
                        : session.joinableColony(unit) != null ? JOIN
                        : session.canBuildColonyHere(unit) ? BUILD : null;
                }
            }
        }
        year.setText(yearText);
        gold.setText(goldText);
        tax.setText(taxText);
        if (nation != null && !nation.equals(crestFor)) {
            crestFor = nation;
            Art.Img ci = art.get("image.miscicon." + nation);
            if (ci != null) crest.setImageBitmap((Bitmap)ci.handle);
        }

        boolean has = unit != null;
        noUnit.setVisibility(has ? GONE : VISIBLE);
        for (View v : new View[] { unitPic, unitName, unitSub, pips }) {
            v.setVisibility(has ? VISIBLE : GONE);
        }
        if (has) {
            unitName.setText(name);
            unitSub.setText(sub);
            if (pic != null) unitPic.setImageBitmap(pic);
            pips.removeAllViews();
            for (int i = 0; i < Math.min(movesMax, 12); i++) {
                View pip = new View(c);
                GradientDrawable g = new GradientDrawable();
                g.setColor(i < movesWhole ? Theme.GOOD : 0x40000000);
                g.setCornerRadius(2 * Theme.dp(c));
                pip.setBackground(g);
                LayoutParams lp = new LayoutParams(Theme.px(c, 9), Theme.px(c, 5));
                lp.setMargins(0, 0, Theme.px(c, 2), 0);
                pips.addView(pip, lp);
            }
        }
        cargo.removeAllViews();
        cargo.setVisibility(load.isEmpty() ? GONE : VISIBLE);
        for (Object[] l : load) {
            if (l[0] != null) {
                ImageView iv = new ImageView(c);
                iv.setImageBitmap((Bitmap)l[0]);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                cargo.addView(iv, new LayoutParams(Theme.px(c, 14), Theme.px(c, 16)));
            }
            if (!((String)l[1]).isEmpty()) {
                TextView t = Theme.text(c, (String)l[1], 10, Theme.PAPER_INK, false);
                t.setPadding(Theme.px(c, 1), 0, Theme.px(c, 5), 0);
                cargo.addView(t);
            }
        }
        // Other units on the same tile: tap to select.
        stack.removeAllViews();
        stack.setVisibility(here.size() > 1 ? VISIBLE : GONE);
        if (here.size() > 1) {
            for (final Unit u : here) {
                Bitmap b = null;
                synchronized (session.lock()) {
                    Art.Img img = art.get(renderer.unitKey(u));
                    if (img != null) b = (Bitmap)img.handle;
                }
                ImageView iv = new ImageView(c);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                if (b != null) iv.setImageBitmap(b);
                int pp = Theme.px(c, 1);
                iv.setPadding(pp, pp, pp, pp);
                if (u == unit) {
                    GradientDrawable g = new GradientDrawable();
                    g.setColor(0x33000000);
                    g.setStroke(Theme.px(c, 1), Theme.EDGE);
                    g.setCornerRadius(3 * Theme.dp(c));
                    iv.setBackground(g);
                }
                iv.setOnClickListener(v -> session.select(u));
                LayoutParams lp = new LayoutParams(Theme.px(c, 26), Theme.px(c, 30));
                lp.setMargins(0, Theme.px(c, 3), Theme.px(c, 2), 0);
                stack.addView(iv, lp);
            }
        }

        contextMode = myTurn ? mode : null;
        contextB.setVisibility(contextMode == null ? GONE : VISIBLE);
        if (contextMode != null) {
            Theme.Icon icon = SAIL.equals(contextMode) ? Theme.Icon.SAIL
                : JOIN.equals(contextMode) ? Theme.Icon.JOIN : Theme.Icon.BUILD;
            android.graphics.drawable.Drawable d
                = new Theme.IconDrawable(icon, Theme.GOLD, Theme.px(c, 18));
            d.setBounds(0, 0, Theme.px(c, 18), Theme.px(c, 18));
            contextB.setCompoundDrawables(d, null, null, null);
            contextB.setText(SAIL.equals(contextMode) ? "Sail to Europe"
                             : JOIN.equals(contextMode) ? "Join colony" : "Build colony");
        }
        Theme.enable(nextB, myTurn);
        Theme.enable(waitB, myTurn && has);
        Theme.enable(ordersB, myTurn && has);
        Theme.enable(europeB, session.isInGame());
        Theme.enable(endB, myTurn);
    }
}
