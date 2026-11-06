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

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import net.sf.freecol.android.map.Art;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.TradeRoute;
import net.sf.freecol.common.model.TradeRouteStop;
import net.sf.freecol.common.model.Unit;


/**
 * Make and change trade routes (from the sidebar menu): your routes
 * down the left; on the right the chosen one's name and stops, each
 * stop with the goods to leave it carrying (anything else is unloaded
 * there).
 */
final class TradeRoutePanel extends LinearLayout {

    private final GameSession session;
    private final Art art;
    private final Runnable onClose;
    private final LinearLayout routes, editor;

    private TradeRoute route = null;
    private String name = "";
    private final List<GameSession.StopDraft> stops = new ArrayList<>();
    private String error = null;
    /** Show "Saved" once after a save. */
    private boolean saved = false;


    TradeRoutePanel(Context c, GameSession session, Art art, Runnable onClose) {
        super(c);
        this.session = session;
        this.art = art;
        this.onClose = onClose;
        setOrientation(VERTICAL);
        setBackground(Theme.wood());
        setClickable(true);

        LinearLayout header = new LinearLayout(c);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Theme.serif(c, "Trade routes", 19, Theme.GOLD, true),
                       new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView close = Theme.button(c, "Close", Theme.Icon.CLOSE, Theme.Kind.PLANK, false);
        close.setOnClickListener(v -> onClose.run());
        header.addView(close);
        LayoutParams hlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        hlp.setMargins(0, 0, 0, Theme.px(c, 6));
        addView(header, hlp);

        LinearLayout body = new LinearLayout(c);
        body.setOrientation(HORIZONTAL);
        routes = new LinearLayout(c);
        routes.setOrientation(VERTICAL);
        ScrollView rs = new ScrollView(c);
        rs.addView(routes);
        LayoutParams rlp = new LayoutParams(Theme.px(c, 170), LayoutParams.MATCH_PARENT);
        rlp.setMargins(0, 0, Theme.px(c, 8), 0);
        body.addView(rs, rlp);
        editor = new LinearLayout(c);
        editor.setOrientation(VERTICAL);
        ScrollView es = new ScrollView(c);
        es.addView(editor);
        body.addView(es, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        addView(body, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /** Show the routes afresh. */
    void show() {
        route = null;
        List<TradeRoute> all = routes();
        if (!all.isEmpty()) edit(all.get(0));
        rebuild();
    }

    private List<TradeRoute> routes() {
        synchronized (session.lock()) {
            Player me = session.getMyPlayer();
            return (me == null) ? new ArrayList<>() : new ArrayList<>(me.getTradeRoutes());
        }
    }

    /** Start editing a route: copy its name and stops. */
    private void edit(TradeRoute tr) {
        route = tr;
        error = null;
        stops.clear();
        synchronized (session.lock()) {
            name = (tr.getName() == null) ? "" : tr.getName();
            for (TradeRouteStop st : tr.getStopList()) {
                GameSession.StopDraft d = new GameSession.StopDraft(st.getLocation());
                d.cargo.addAll(st.getCargo());
                stops.add(d);
            }
        }
    }

    private void rebuild() {
        buildList();
        buildEditor();
    }

    private void buildList() {
        final Context c = getContext();
        routes.removeAllViews();
        for (final TradeRoute tr : routes()) {
            String label;
            synchronized (session.lock()) {
                label = tr.getName() + "\n" + tr.getStopCount() + " stops · "
                    + tr.getAssignedUnits().size() + " units";
            }
            TextView t = Theme.text(c, label, 12, Theme.INK, true);
            boolean on = tr == route;
            t.setBackground(on ? Theme.gold(c) : Theme.plank(c));
            t.setTextColor(on ? Theme.PAPER_INK : Theme.INK);
            int p = Theme.px(c, 9);
            t.setPadding(p, p * 2 / 3, p, p * 2 / 3);
            t.setOnClickListener(v -> {
                    edit(tr);
                    rebuild();
                });
            routes.addView(t, gap());
        }
        TextView add = Theme.button(c, "New route", Theme.Icon.BUILD, Theme.Kind.PLANK, false);
        add.setOnClickListener(v -> {
                if (!session.isMyTurn()) {
                    error = "You can change trade routes during your turn.";
                    buildEditor();
                    return;
                }
                session.newTradeRoute(tr -> post(() -> {
                        if (tr == null) {
                            error = "Could not make a new route.";
                        } else {
                            edit(tr);
                        }
                        rebuild();
                    }));
            });
        routes.addView(add, gap());
    }

    private LayoutParams gap() {
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Theme.px(getContext(), 5));
        return lp;
    }

    private void buildEditor() {
        final Context c = getContext();
        editor.removeAllViews();
        if (route == null) {
            editor.addView(Theme.serif(c, "A trade route sends wagon trains or ships round "
                + "a list of stops by themselves. At each stop they unload whatever is not "
                + "on that stop's list, then load what is (colonies keep 50 of each, "
                + "which you can change by tapping goods in the colony). Make a route "
                + "here, then give a wagon train or ship Orders → Trade route.",
                14, Theme.DIM, false));
            return;
        }
        // Name.
        editor.addView(heading("Name"));
        final EditText nameField = new EditText(c);
        nameField.setSingleLine(true);
        nameField.setText(name);
        nameField.setTypeface(Theme.SERIF_BOLD);
        nameField.setTextColor(Theme.PAPER_INK);
        nameField.setTextSize(15);
        nameField.setBackground(Theme.paper(c));
        int p = Theme.px(c, 8);
        nameField.setPadding(p, p, p, p);
        nameField.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
                @Override public void afterTextChanged(android.text.Editable s) {
                    name = s.toString();
                }
            });
        editor.addView(nameField, gap());

        String units;
        synchronized (session.lock()) {
            List<String> names = new ArrayList<>();
            for (Unit u : route.getAssignedUnits()) {
                names.add((u.getName() != null) ? u.getName() : u.getDescription());
            }
            units = names.isEmpty() ? "No units on it yet: give a wagon train or ship "
                + "Orders → Trade route." : "On this route: " + String.join(", ", names);
        }
        editor.addView(Theme.text(c, units, 12, Theme.DIM, false), gap());

        // Stops.
        editor.addView(heading("Stops, in order"));
        for (int i = 0; i < stops.size(); i++) editor.addView(stopCard(i), gap());
        TextView addStop = Theme.button(c, "Add a stop", Theme.Icon.COLONY, Theme.Kind.PLANK, false);
        addStop.setOnClickListener(v -> pickStop());
        editor.addView(addStop, gap());

        if (error != null || saved) {
            TextView e = Theme.text(c, (error != null) ? error : "Saved.", 13,
                                    (error != null) ? 0xFFFF8A7A : 0xFF9BE08A, true);
            e.setPadding(0, Theme.px(c, 4), 0, Theme.px(c, 4));
            editor.addView(e);
            saved = false;
        }
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        TextView save = Theme.button(c, "Save route", null, Theme.Kind.GOLD, false);
        save.setOnClickListener(v -> save());
        row.addView(save, new LayoutParams(0, Theme.px(c, 44), 1.6f));
        TextView del = Theme.button(c, "Delete", Theme.Icon.CLOSE, Theme.Kind.PLANK, false);
        del.setOnClickListener(v -> new AlertDialog.Builder(c)
            .setMessage("Delete the route " + name + "? Its units stop following it.")
            .setPositiveButton("Delete", (d, w) -> {
                    session.deleteTradeRoute(route);
                    route = null;
                    postDelayed(this::show, 300);
                })
            .setNegativeButton("Cancel", null)
            .show());
        LayoutParams dlp = new LayoutParams(0, Theme.px(c, 44), 1f);
        dlp.setMargins(Theme.px(c, 6), 0, 0, 0);
        row.addView(del, dlp);
        editor.addView(row, gap());
    }

    private TextView heading(String s) {
        TextView h = Theme.heading(getContext(), s, Theme.GOLD);
        h.setPadding(Theme.px(getContext(), 2), Theme.px(getContext(), 6), 0,
                     Theme.px(getContext(), 3));
        return h;
    }

    private String placeName(Location loc) {
        if (loc instanceof Europe) return "Europe";
        if (loc instanceof Colony) return ((Colony)loc).getName();
        return String.valueOf(loc);
    }

    private View stopCard(final int index) {
        final Context c = getContext();
        final GameSession.StopDraft d = stops.get(index);
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(VERTICAL);
        card.setBackground(Theme.paper(c));
        int p = Theme.px(c, 7);
        card.setPadding(p, p, p, p);

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Theme.serif(c, (index + 1) + ". " + placeName(d.location), 14,
                                Theme.PAPER_INK, true),
                    new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        top.addView(small("↑", v -> move(index, -1), index > 0));
        top.addView(small("↓", v -> move(index, 1), index < stops.size() - 1));
        top.addView(small("✕", v -> {
                stops.remove(index);
                buildEditor();
            }, true));
        card.addView(top);

        TextView what = Theme.text(c, d.cargo.isEmpty()
            ? "Leave carrying nothing: unload everything here."
            : "Leave carrying (tap one to take it off):", 11, Theme.PAPER_DIM, false);
        what.setPadding(0, Theme.px(c, 3), 0, Theme.px(c, 3));
        card.addView(what);
        LinearLayout goods = new LinearLayout(c);
        goods.setOrientation(HORIZONTAL);
        goods.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < d.cargo.size(); i++) {
            final int which = i;
            View g = goodsChip(d.cargo.get(i), null);
            g.setOnClickListener(v -> {
                    d.cargo.remove(which);
                    buildEditor();
                });
            goods.addView(g);
        }
        TextView add = Theme.text(c, "+ Goods", 12, Theme.PAPER_INK, true);
        add.setGravity(Gravity.CENTER);
        add.setBackground(Theme.cell(c, 0xFFF4EACF));
        add.setPadding(Theme.px(c, 10), 0, Theme.px(c, 10), 0);
        add.setMinHeight(Theme.px(c, 36));
        add.setOnClickListener(v -> pickGoods(d));
        goods.addView(add, new LayoutParams(LayoutParams.WRAP_CONTENT, Theme.px(c, 36)));
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(goods);
        card.addView(hs);
        return card;
    }

    private TextView small(String label, View.OnClickListener l, boolean on) {
        final Context c = getContext();
        TextView b = Theme.text(c, label, 15, Theme.PAPER_INK, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Theme.cell(c, 0xFFF4EACF));
        b.setOnClickListener(l);
        Theme.enable(b, on);
        LayoutParams lp = new LayoutParams(Theme.px(c, 38), Theme.px(c, 34));
        lp.setMargins(Theme.px(c, 4), 0, 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void move(int index, int by) {
        int to = index + by;
        if (to < 0 || to >= stops.size()) return;
        stops.add(to, stops.remove(index));
        buildEditor();
    }

    /** A goods icon with its name (one hold: 100). */
    private View goodsChip(GoodsType gt, String caption) {
        final Context c = getContext();
        LinearLayout chip = new LinearLayout(c);
        chip.setOrientation(HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackground(Theme.cell(c, 0xFFF4EACF));
        int p = Theme.px(c, 5);
        chip.setPadding(p, 0, p + 2, 0);
        Art.Img img = art.get("image.icon." + gt.getId());
        if (img != null) {
            ImageView iv = new ImageView(c);
            iv.setImageBitmap((Bitmap)img.handle);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            chip.addView(iv, new LayoutParams(Theme.px(c, 22), Theme.px(c, 22)));
        }
        TextView t = Theme.text(c, (caption != null) ? caption : Messages.getName(gt),
                                11, Theme.PAPER_INK, true);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(Theme.px(c, 3), 0, 0, 0);
        chip.addView(t);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, Theme.px(c, 36));
        lp.setMargins(0, 0, Theme.px(c, 5), 0);
        chip.setLayoutParams(lp);
        return chip;
    }

    /** Add a stop: one of your colonies, or Europe. */
    private void pickStop() {
        final List<Location> places = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        synchronized (session.lock()) {
            Player me = session.getMyPlayer();
            List<Colony> colonies = new ArrayList<>(me.getColonyList());
            colonies.sort((a, b) -> a.getName().compareTo(b.getName()));
            for (Colony col : colonies) {
                places.add(col);
                names.add(col.getName() + (col.isConnectedPort() ? " (port)" : ""));
            }
            if (me.getEurope() != null) {
                places.add(me.getEurope());
                names.add("Europe (ships only)");
            }
        }
        if (places.isEmpty()) {
            error = "You need a colony first.";
            buildEditor();
            return;
        }
        new AlertDialog.Builder(getContext())
            .setTitle("Add a stop")
            .setItems(names.toArray(new String[0]), (d, w) -> {
                    stops.add(new GameSession.StopDraft(places.get(w)));
                    buildEditor();
                })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /** Add goods to a stop's list: each tap is one hold (100). */
    private void pickGoods(final GameSession.StopDraft d) {
        final Context c = getContext();
        final List<GoodsType> types;
        synchronized (session.lock()) {
            types = new ArrayList<>(session.getGame().getSpecification()
                                    .getStorableGoodsTypeList());
        }
        final AlertDialog[] dialog = new AlertDialog[1];
        LinearLayout grid = new LinearLayout(c);
        grid.setOrientation(VERTICAL);
        int pad = Theme.px(c, 14);
        grid.setPadding(pad, pad / 3, pad, pad / 3);
        LinearLayout row = null;
        for (int i = 0; i < types.size(); i++) {
            final GoodsType gt = types.get(i);
            if (i % 3 == 0) {
                row = new LinearLayout(c);
                row.setOrientation(HORIZONTAL);
                grid.addView(row);
            }
            View chip = goodsChip(gt, null);
            chip.setOnClickListener(v -> {
                    d.cargo.add(gt);
                    dialog[0].dismiss();
                    buildEditor();
                });
            LayoutParams lp = new LayoutParams(0, Theme.px(c, 38), 1f);
            lp.setMargins(0, 0, Theme.px(c, 5), Theme.px(c, 5));
            row.addView(chip, lp);
        }
        ScrollView sv = new ScrollView(c);
        sv.addView(grid);
        dialog[0] = new AlertDialog.Builder(c)
            .setTitle("Leave " + placeName(d.location) + " carrying")
            .setView(sv)
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void save() {
        if (!session.isMyTurn()) {
            error = "You can change trade routes during your turn.";
            buildEditor();
            return;
        }
        final String n = name.trim();
        if (n.isEmpty()) {
            error = "Give the route a name.";
            buildEditor();
            return;
        }
        session.updateTradeRoute(route, n, new ArrayList<>(stops), err -> post(() -> {
                error = (err == null) ? null : err;
                saved = err == null;
                rebuild();
            }));
    }
}
