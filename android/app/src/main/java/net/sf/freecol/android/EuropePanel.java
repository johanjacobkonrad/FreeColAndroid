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

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import net.sf.freecol.android.map.Art;
import net.sf.freecol.android.map.IsometricRenderer;
import net.sf.freecol.common.model.Unit;


/**
 * The Europe screen, shown over the map.
 *
 * Tap a ship in port to select it; then tap market goods to buy 100
 * (or what you can afford), its cargo to sell, or a colonist on the
 * docks to put them aboard.  Recruit emigrants and train units from
 * the lists.  Select a colonist on the docks and tap Equip to buy them
 * muskets, horses or tools.  "Sail to the New World" sends the
 * selected ship off, to one of your ports or back where it came from.
 */
final class EuropePanel extends LinearLayout implements GameSession.Listener {

    private static final int CELL = 0xFF3A2816,
        SHIP = 0xFF24343C, SHIP_SELECTED = 0xFF35556A;

    private final GameSession session;
    private final IsometricRenderer renderer;
    private final Art art;
    private final Runnable onClose;
    private final float dp;

    private final TextView title, hint;
    private final TextView sailButton, equipButton;
    private final LinearLayout port, people, training, market;

    private EuropeInfo info = null;
    private Unit selectedShip = null, selectedUnit = null;
    private boolean refreshPending = false;


    EuropePanel(Context context, GameSession session, IsometricRenderer renderer,
                Art art, Runnable onClose) {
        super(context);
        this.session = session;
        this.renderer = renderer;
        this.art = art;
        this.onClose = onClose;
        this.dp = context.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setBackground(Theme.wood());
        setClickable(true);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        title = Theme.serif(context, "", 19, Theme.GOLD, true);
        header.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        sailButton = Theme.button(context, "Sail to the New World", Theme.Icon.SAIL,
                                  Theme.Kind.GOLD, false);
        sailButton.setTextSize(13);
        sailButton.setOnClickListener(v -> chooseDestination());
        equipButton = Theme.button(context, "Equip", Theme.Icon.EQUIP, Theme.Kind.PLANK, false);
        equipButton.setOnClickListener(v -> chooseEquip());
        header.addView(equipButton, headerButton());
        header.addView(sailButton, headerButton());
        TextView close = Theme.button(context, "Close", Theme.Icon.CLOSE, Theme.Kind.PLANK, false);
        close.setOnClickListener(v -> onClose.run());
        header.addView(close, headerButton());
        addView(header);

        hint = text(12, Theme.GOLD, false, "");
        hint.setPadding(0, px(3), 0, px(3));
        addView(hint);

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(HORIZONTAL);
        port = column();
        body.addView(scroll(port), new LayoutParams(0, LayoutParams.MATCH_PARENT, 0.38f));
        people = column();
        body.addView(scroll(people), new LayoutParams(0, LayoutParams.MATCH_PARENT, 0.32f));
        training = column();
        body.addView(scroll(training), new LayoutParams(0, LayoutParams.MATCH_PARENT, 0.30f));
        addView(body, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        market = new LinearLayout(context);
        market.setOrientation(HORIZONTAL);
        HorizontalScrollView ms = new HorizontalScrollView(context);
        ms.setBackground(Theme.plank(context));
        ms.addView(market);
        LayoutParams mlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        mlp.setMargins(0, px(4), 0, 0);
        addView(ms, mlp);
    }

    private LayoutParams headerButton() {
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.setMargins(px(5), 0, 0, 0);
        return lp;
    }

    /** Show Europe afresh. */
    void show() {
        selectedShip = null;
        selectedUnit = null;
        rebuild();
    }


    // Implement GameSession.Listener (forwarded by the activity)

    @Override
    public void onChanged() {
        if (refreshPending) return;
        refreshPending = true;
        postDelayed(() -> {
            refreshPending = false;
            if (getVisibility() == VISIBLE) rebuild();
        }, 60);
    }

    @Override
    public void onStatus(String text) {
        post(() -> hint.setText(text));
    }


    // Building the screen

    private void rebuild() {
        synchronized (session.lock()) {
            if (session.getMyPlayer() == null
                || session.getMyPlayer().getEurope() == null) {
                onClose.run();
                return;
            }
            info = new EuropeInfo(session.getMyPlayer(), renderer);
        }
        // Forget selections that have left port.
        boolean shipHere = false, unitHere = false;
        for (EuropeInfo.Ship s : info.inPort) {
            if (s.ship.unit == selectedShip) shipHere = true;
            for (ColonyInfo.UnitItem p : s.passengers) {
                if (p.unit == selectedUnit) unitHere = true;
            }
        }
        for (ColonyInfo.UnitItem u : info.docks) {
            if (u.unit == selectedUnit) unitHere = true;
        }
        if (!shipHere) selectedShip = null;
        if (!unitHere) selectedUnit = null;

        title.setText(info.title + "    Gold " + info.gold + "    Tax " + info.tax + "%");
        boolean myTurn = session.isMyTurn();
        sailButton.setVisibility((myTurn && selectedShip != null) ? VISIBLE : GONE);
        boolean canEquip = false;
        if (selectedUnit != null) {
            synchronized (session.lock()) {
                canEquip = !session.equipChoices(selectedUnit).isEmpty();
            }
        }
        equipButton.setVisibility((myTurn && canEquip) ? VISIBLE : GONE);
        if (!myTurn) {
            hint.setText("Wait for your turn to trade.");
        } else if (selectedUnit != null) {
            hint.setText(describe(selectedUnit)
                + ": tap a ship to put them aboard, or Equip them.");
        } else if (selectedShip != null) {
            hint.setText(describe(selectedShip) + ": tap goods to buy, cargo to sell,"
                + " a colonist on the docks to board. Or sail.");
        } else if (info.inPort.isEmpty()) {
            hint.setText("No ships in port. Send one with Sail to Europe on the map.");
        } else {
            hint.setText("Tap a ship to load it.");
        }
        buildPort();
        buildPeople();
        buildTraining();
        buildMarket();
    }

    private void buildPort() {
        port.removeAllViews();
        port.addView(heading("In port"));
        for (EuropeInfo.Ship s : info.inPort) {
            LinearLayout box = new LinearLayout(getContext());
            box.setOrientation(VERTICAL);
            boolean on = s.ship.unit == selectedShip;
            android.graphics.drawable.GradientDrawable bg
                = Theme.cell(getContext(), on ? SHIP_SELECTED : SHIP);
            if (on) bg.setStroke(px(2), Theme.GOLD);
            box.setBackground(bg);
            box.setPadding(px(4), px(4), px(4), px(4));
            LinearLayout head = new LinearLayout(getContext());
            head.setOrientation(HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(image(s.ship.imageKey, px(44), px(44)));
            head.addView(text(12, Theme.INK, true, s.ship.name
                              + "\n" + s.spaceLeft + " space left"));
            box.addView(head);
            LinearLayout load = row();
            for (ColonyInfo.Amount a : s.cargo) {
                View g = goodsCell(a.iconKey, Integer.toString(a.amount), null, false);
                g.setOnClickListener(v -> sellFrom(s.ship.unit, a));
                load.addView(g);
            }
            for (ColonyInfo.UnitItem p : s.passengers) load.addView(unitIcon(p, px(34)));
            box.addView(load);
            box.setOnClickListener(v -> tapShip(s.ship.unit));
            head.setOnClickListener(v -> tapShip(s.ship.unit));
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT,
                                               LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, px(6));
            port.addView(box, lp);
        }
        if (!info.atSea.isEmpty()) {
            port.addView(heading("At sea"));
            for (ColonyInfo.UnitItem u : info.atSea) {
                LinearLayout line = row();
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.addView(image(u.imageKey, px(32), px(32)));
                line.addView(text(11, Theme.DIM, false, u.name + ", " + u.caption));
                port.addView(line);
            }
        }
    }

    private void buildPeople() {
        people.removeAllViews();
        people.addView(heading("On the docks"));
        LinearLayout docks = row();
        docks.setBackground(Theme.cell(getContext(), CELL));
        docks.setMinimumHeight(px(44));
        for (ColonyInfo.UnitItem u : info.docks) docks.addView(unitIcon(u, px(40)));
        people.addView(docks);

        people.addView(heading("Recruit (" + info.recruitPrice + " gold each)"));
        for (EuropeInfo.Offer o : info.recruits) {
            people.addView(offer(o, () -> confirm("Recruit a " + o.name + " for "
                + o.price + " gold?", () -> session.recruit(o.index))));
        }
    }

    private void buildTraining() {
        training.removeAllViews();
        training.addView(heading("Train or buy"));
        for (EuropeInfo.Offer o : info.training) {
            training.addView(offer(o, () -> confirm("Buy a " + o.name + " for "
                + o.price + " gold?", () -> session.train(o.type))));
        }
    }

    private void buildMarket() {
        market.removeAllViews();
        for (EuropeInfo.Price p : info.market) {
            View g = goodsCell(p.iconKey, p.sell + "/" + p.buy,
                               p.boycott ? "boycott" : null, p.boycott);
            g.setOnClickListener(v -> tapMarket(p));
            market.addView(g);
        }
    }


    /** A unit's type (and role), as the selection's name in the hint. */
    private String describe(Unit u) {
        synchronized (session.lock()) {
            String own = u.getName();
            String d = u.getDescription();
            return (own != null && !own.isEmpty() && !d.contains(own)) ? own + ", " + d : d;
        }
    }


    // Taps

    private void tapShip(Unit ship) {
        if (!session.isMyTurn()) return;
        if (selectedUnit != null) {
            session.board(selectedUnit, ship);
            selectedUnit = null;
            selectedShip = ship;
            return;
        }
        selectedShip = (selectedShip == ship) ? null : ship;
        rebuild();
    }

    private void tapUnit(Unit u) {
        if (!session.isMyTurn()) return;
        selectedUnit = (selectedUnit == u) ? null : u;
        rebuild();
    }

    private void sellFrom(Unit ship, ColonyInfo.Amount a) {
        if (!session.isMyTurn()) return;
        session.sell(ship, a.type, a.amount);
    }

    private void tapMarket(EuropeInfo.Price p) {
        if (!session.isMyTurn()) return;
        if (selectedShip == null) {
            hint.setText(p.name + ": they pay " + p.sell + ", you pay " + p.buy
                + " a unit" + (p.boycott ? " (under boycott)" : "")
                + ". Select a ship to buy.");
            return;
        }
        session.buy(selectedShip, p.type, 100);
    }

    /** Sail the selected ship: to one of our coastal colonies, or back. */
    private void chooseDestination() {
        final Unit ship = selectedShip;
        if (ship == null || !session.isMyTurn()) return;
        final java.util.List<net.sf.freecol.common.model.Colony> ports
            = new java.util.ArrayList<>();
        synchronized (session.lock()) {
            for (net.sf.freecol.common.model.Colony c
                     : session.getMyPlayer().getColonyList()) {
                if (c.isConnectedPort()) ports.add(c);
            }
        }
        if (ports.isEmpty()) {
            session.sailToAmerica(ship);
            selectedShip = null;
            return;
        }
        String[] labels = new String[ports.size() + 1];
        labels[0] = "Where it came from";
        for (int i = 0; i < ports.size(); i++) labels[i + 1] = ports.get(i).getName();
        new AlertDialog.Builder(getContext())
            .setTitle("Sail to")
            .setItems(labels, (d, which) -> {
                    session.sailToAmerica(ship, (which == 0) ? null : ports.get(which - 1));
                    selectedShip = null;
                })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /** Equip the selected colonist on the docks, at market prices. */
    private void chooseEquip() {
        final Unit unit = selectedUnit;
        if (unit == null || !session.isMyTurn()) return;
        final java.util.List<GameSession.EquipChoice> choices;
        synchronized (session.lock()) {
            choices = session.equipChoices(unit);
        }
        if (choices.isEmpty()) return;
        String[] labels = new String[choices.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = choices.get(i).label;
        new AlertDialog.Builder(getContext())
            .setTitle("Equip " + unit.getDescription())
            .setItems(labels, (d, which) -> session.equip(unit, choices.get(which)))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void confirm(String question, Runnable yes) {
        if (!session.isMyTurn()) return;
        new AlertDialog.Builder(getContext())
            .setMessage(question)
            .setPositiveButton("Yes", (d, w) -> yes.run())
            .setNegativeButton("No", null)
            .show();
    }


    // View helpers

    private View offer(EuropeInfo.Offer o, Runnable onTap) {
        LinearLayout line = row();
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setBackground(Theme.cell(getContext(), CELL));
        line.addView(image(o.imageKey, px(30), px(40)));
        boolean afford = info.gold >= o.price;
        line.addView(text(12, afford ? Theme.INK : 0xFF8C7A60, false,
                          o.name + "\n" + o.price + " gold"));
        line.setOnClickListener(v -> onTap.run());
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT,
                                           LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, px(3));
        line.setLayoutParams(lp);
        return line;
    }

    private View unitIcon(ColonyInfo.UnitItem u, int height) {
        LinearLayout cell = new LinearLayout(getContext());
        cell.setOrientation(VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        if (u.unit == selectedUnit) cell.setBackground(Theme.gold(getContext()));
        cell.setPadding(px(2), px(1), px(2), px(1));
        cell.addView(image(u.imageKey, (int)(height * 0.7f), height));
        cell.setOnClickListener(v -> tapUnit(u.unit));
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT,
                                           LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, px(3), 0);
        cell.setLayoutParams(lp);
        return cell;
    }

    private View goodsCell(String iconKey, String top, String bottom, boolean warn) {
        LinearLayout cell = new LinearLayout(getContext());
        cell.setOrientation(VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(px(4), px(2), px(4), px(2));
        cell.addView(image(iconKey, px(26), px(26)));
        cell.addView(text(12, warn ? 0xFFFF6B5B : Theme.INK, true, top));
        if (bottom != null) cell.addView(text(10, 0xFFFF8A7A, false, bottom));
        return cell;
    }

    private TextView heading(String s) {
        TextView t = Theme.heading(getContext(), s, Theme.GOLD);
        t.setPadding(px(4), px(6), px(4), px(3));
        return t;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(getContext());
        l.setOrientation(VERTICAL);
        l.setPadding(px(3), 0, px(3), 0);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(getContext());
        l.setOrientation(HORIZONTAL);
        l.setPadding(px(2), px(2), px(2), px(2));
        return l;
    }

    private ScrollView scroll(View v) {
        ScrollView s = new ScrollView(getContext());
        s.addView(v);
        return s;
    }

    private ImageView image(String key, int w, int h) {
        ImageView iv = new ImageView(getContext());
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Art.Img img = art.get(key);
        if (img != null) iv.setImageBitmap((Bitmap)img.handle);
        iv.setLayoutParams(new LayoutParams(w, h));
        return iv;
    }

    private TextView text(int sp, int colour, boolean bold, String s) {
        TextView t = new TextView(getContext());
        t.setTextSize(sp);
        t.setTextColor(colour);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setText(s);
        return t;
    }

    private int px(int dps) {
        return (int)(dps * dp);
    }
}
