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

import java.util.List;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import net.sf.freecol.android.map.Art;
import net.sf.freecol.android.map.IsometricRenderer;
import net.sf.freecol.common.model.BuildableType;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.Unit;


/**
 * The colony screen, shown over the map.
 *
 * Moving people works like the map: tap a colonist (on the land, in a
 * building, outside the colony or aboard a ship) and then tap where
 * they should go.  Tap a worked tile with nobody selected to change
 * what it produces.  Tap a ship to select it, then tap goods in the
 * warehouse to load them (up to 100), or its cargo to unload.
 */
final class ColonyPanel extends LinearLayout implements GameSession.Listener {

    private static final int BG = 0xFF2B2118, CELL = 0xFF3E3125,
        CELL_SELECTED = 0xFFE8D9B0, SHIP_CELL = 0xFF1E3A55;

    private final GameSession session;
    private final IsometricRenderer renderer;
    private final Art art;
    private final Runnable onClose;
    private final float dp;

    private final TextView title, stats, hint;
    private final Button buildButton;
    private final ColonyLandView land;
    private final LinearLayout buildings, side, goods;

    private Colony colony = null;
    private ColonyInfo info = null;
    private Unit selectedUnit = null, selectedCarrier = null;
    private boolean refreshPending = false;


    ColonyPanel(Context context, GameSession session, IsometricRenderer renderer,
                Art art, Runnable onClose) {
        super(context);
        this.session = session;
        this.renderer = renderer;
        this.art = art;
        this.onClose = onClose;
        this.dp = context.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setBackgroundColor(BG);
        setClickable(true); // Do not let taps through to the map

        // Header: name and figures, what it builds, close.
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(VERTICAL);
        title = text(18, Color.WHITE, true);
        stats = text(12, 0xFFDDCCAA, false);
        titles.addView(title);
        titles.addView(stats);
        header.addView(titles, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        buildButton = new Button(context);
        buildButton.setAllCaps(false);
        buildButton.setOnClickListener(v -> chooseBuild());
        header.addView(buildButton);
        Button close = new Button(context);
        close.setText("Close");
        close.setAllCaps(false);
        close.setOnClickListener(v -> onClose.run());
        header.addView(close);
        addView(header);

        hint = text(12, 0xFFFFE08A, false);
        addView(hint);

        // Body: land | buildings | outside and ships.
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(HORIZONTAL);
        land = new ColonyLandView(context, session, renderer, art);
        land.setOnLandTap(this::tapLand);
        body.addView(land, new LayoutParams(0, LayoutParams.MATCH_PARENT, 0.34f));

        buildings = new LinearLayout(context);
        buildings.setOrientation(VERTICAL);
        ScrollView bScroll = new ScrollView(context);
        bScroll.addView(buildings);
        body.addView(bScroll, new LayoutParams(0, LayoutParams.MATCH_PARENT, 0.42f));

        side = new LinearLayout(context);
        side.setOrientation(VERTICAL);
        ScrollView sScroll = new ScrollView(context);
        sScroll.addView(side);
        body.addView(sScroll, new LayoutParams(0, LayoutParams.MATCH_PARENT, 0.24f));
        addView(body, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        // Warehouse.
        goods = new LinearLayout(context);
        goods.setOrientation(HORIZONTAL);
        HorizontalScrollView gScroll = new HorizontalScrollView(context);
        gScroll.addView(goods);
        addView(gScroll);
    }

    /** Show a colony. */
    void show(Colony colony) {
        this.colony = colony;
        this.selectedUnit = null;
        this.selectedCarrier = null;
        rebuild();
    }

    Colony getColony() {
        return colony;
    }


    // Implement GameSession.Listener (forwarded by the activity)

    @Override
    public void onChanged() {
        // Many changes arrive at once; redraw once after them.
        if (refreshPending) return;
        refreshPending = true;
        postDelayed(() -> {
            refreshPending = false;
            if (getVisibility() == VISIBLE && colony != null) rebuild();
        }, 60);
    }

    @Override
    public void onStatus(String text) {
        post(() -> hint.setText(text));
    }


    // Building the screen

    private void rebuild() {
        synchronized (session.lock()) {
            if (colony == null || colony.isDisposed()
                || colony.getOwner() != session.getMyPlayer()) {
                onClose.run();
                return;
            }
            info = new ColonyInfo(colony, session.getMyPlayer(), renderer);
            if (selectedUnit != null && (selectedUnit.isDisposed()
                    || selectedUnit.getOwner() != session.getMyPlayer())) {
                selectedUnit = null;
            }
        }
        title.setText(info.title);
        stats.setText(info.stats);
        buildButton.setText(info.building);
        buildButton.setEnabled(session.isMyTurn());
        if (!session.isMyTurn()) {
            hint.setText("Wait for your turn to give orders.");
        } else if (selectedUnit != null) {
            hint.setText("Now tap a tile, a building or Outside.");
        } else if (selectedCarrier != null) {
            hint.setText("Tap warehouse goods to load, cargo to unload, a passenger to select.");
        } else {
            hint.setText("Tap a colonist, then where they should work. "
                + "Tap a worked tile to change its crop.");
        }
        land.setInfo(info, selectedUnit);
        buildBuildings();
        buildSide();
        buildGoods();
    }

    private void buildBuildings() {
        buildings.removeAllViews();
        LinearLayout row = null;
        int col = 0;
        for (ColonyInfo.BuildingItem b : info.buildings) {
            if (col % 2 == 0) {
                row = new LinearLayout(getContext());
                row.setOrientation(HORIZONTAL);
                buildings.addView(row);
            }
            col++;
            LinearLayout cell = new LinearLayout(getContext());
            cell.setOrientation(VERTICAL);
            cell.setBackgroundColor(CELL);
            int pad = px(4);
            cell.setPadding(pad, pad, pad, pad);

            LinearLayout top = new LinearLayout(getContext());
            top.setOrientation(HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            ImageView pic = image(b.imageKey, px(44), px(32));
            top.addView(pic);
            LinearLayout names = new LinearLayout(getContext());
            names.setOrientation(VERTICAL);
            names.setPadding(px(4), 0, 0, 0);
            names.addView(text(12, Color.WHITE, true, b.name));
            names.addView(amounts(b.production));
            top.addView(names);
            cell.addView(top);

            LinearLayout workers = new LinearLayout(getContext());
            workers.setOrientation(HORIZONTAL);
            for (ColonyInfo.UnitItem u : b.workers) {
                workers.addView(unitIcon(u, px(34)));
            }
            for (int i = b.workers.size(); i < b.capacity; i++) {
                View slot = new View(getContext());
                slot.setBackgroundColor(0x33FFFFFF);
                LayoutParams lp = new LayoutParams(px(24), px(34));
                lp.setMargins(0, 0, px(3), 0);
                workers.addView(slot, lp);
            }
            cell.addView(workers);
            cell.setOnClickListener(v -> tapBuilding(b));
            LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(px(2), px(2), px(2), px(2));
            row.addView(cell, lp);
        }
        if (row != null && col % 2 == 1) {
            row.addView(new View(getContext()), new LayoutParams(0, 1, 1f));
        }
    }

    private void buildSide() {
        side.removeAllViews();
        TextView outsideTitle = text(13, Color.WHITE, true, "Outside the colony");
        outsideTitle.setPadding(px(4), px(6), px(4), px(2));
        side.addView(outsideTitle);
        LinearLayout outside = flow();
        outside.setBackgroundColor(CELL);
        outside.setMinimumHeight(px(44));
        for (ColonyInfo.UnitItem u : info.outside) outside.addView(unitIcon(u, px(38)));
        outside.setOnClickListener(v -> tapOutside());
        outsideTitle.setOnClickListener(v -> tapOutside());
        side.addView(outside);

        for (ColonyInfo.CarrierItem c : info.carriers) {
            LinearLayout box = new LinearLayout(getContext());
            box.setOrientation(VERTICAL);
            box.setBackgroundColor((c.carrier.unit == selectedCarrier)
                                   ? 0xFF3C6E99 : SHIP_CELL);
            box.setPadding(px(4), px(4), px(4), px(4));
            LinearLayout head = new LinearLayout(getContext());
            head.setOrientation(HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(image(c.carrier.imageKey, px(40), px(40)));
            head.addView(text(12, Color.WHITE, true, c.carrier.name));
            head.setOnClickListener(v -> tapCarrier(c.carrier.unit));
            box.addView(head);
            LinearLayout load = flow();
            for (ColonyInfo.Amount a : c.cargo) {
                View g = goodsCell(a.iconKey, Integer.toString(a.amount), null, false);
                g.setOnClickListener(v -> session.unloadGoods(c.carrier.unit,
                                                             a.type, a.amount));
                load.addView(g);
            }
            for (ColonyInfo.UnitItem p : c.passengers) load.addView(unitIcon(p, px(34)));
            box.addView(load);
            if (c.spaceLeft > 0) {
                box.addView(text(11, 0xFFBBCCDD, false, c.spaceLeft + " space left"));
            }
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT,
                                               LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, px(6), 0, 0);
            side.addView(box, lp);
        }
    }

    private void buildGoods() {
        goods.removeAllViews();
        for (ColonyInfo.Stock st : info.warehouse) {
            String net = (st.net > 0) ? "+" + st.net
                : (st.net < 0) ? Integer.toString(st.net) : "";
            View g = goodsCell(st.iconKey, Integer.toString(st.count), net, st.overflow);
            g.setOnClickListener(v -> tapGoods(st));
            goods.addView(g);
        }
    }


    // Taps

    private void tapUnit(Unit u) {
        if (!session.isMyTurn()) return;
        boolean carrier;
        synchronized (session.lock()) {
            carrier = u.isCarrier();
        }
        if (carrier) {
            tapCarrier(u);
            return;
        }
        selectedUnit = (selectedUnit == u) ? null : u;
        selectedCarrier = null;
        rebuild();
    }

    private void tapCarrier(Unit u) {
        selectedCarrier = (selectedCarrier == u) ? null : u;
        selectedUnit = null;
        rebuild();
    }

    private void tapLand(ColonyInfo.LandItem li) {
        if (!session.isMyTurn()) return;
        if (selectedUnit != null) {
            if (li.centre) {
                hint.setText("The colony centre works itself.");
                return;
            }
            session.work(selectedUnit, li.colonyTile);
            selectedUnit = null;
            return;
        }
        // Nobody selected: offer to change the crop, or pick up the
        // worker to move them.
        if (li.worker != null && !li.centre) chooseCrop(li);
    }

    private void tapBuilding(ColonyInfo.BuildingItem b) {
        if (!session.isMyTurn()) return;
        if (selectedUnit == null) {
            hint.setText("Tap a colonist first, then the building.");
            return;
        }
        session.work(selectedUnit, b.building);
        selectedUnit = null;
    }

    private void tapOutside() {
        if (!session.isMyTurn() || selectedUnit == null) return;
        boolean inside;
        synchronized (session.lock()) {
            inside = selectedUnit.isInColony();
        }
        if (inside) {
            session.putOutsideColony(selectedUnit);
        } else {
            session.disembark(selectedUnit);
        }
        selectedUnit = null;
    }

    private void tapGoods(ColonyInfo.Stock st) {
        if (!session.isMyTurn()) return;
        if (selectedCarrier == null) {
            hint.setText(st.name + ": " + st.count + " in store"
                + ((st.net != 0) ? ", " + ((st.net > 0) ? "+" : "") + st.net
                   + " a turn" : "")
                + ". Select a ship to load goods.");
            return;
        }
        int amount = Math.min(100, st.count);
        if (amount > 0) session.loadGoods(selectedCarrier, st.type, amount);
    }

    private void chooseCrop(ColonyInfo.LandItem li) {
        final Unit worker = li.worker.unit;
        final List<ColonyInfo.Choice<GoodsType>> choices;
        synchronized (session.lock()) {
            choices = ColonyInfo.workChoices(li.colonyTile, worker);
        }
        if (choices.isEmpty()) return;
        String[] labels = new String[choices.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = choices.get(i).label;
        new AlertDialog.Builder(getContext())
            .setTitle(li.worker.name + ": produce")
            .setItems(labels, (d, which) ->
                session.changeWorkType(worker, choices.get(which).value))
            .setNeutralButton("Select colonist", (d, w) -> {
                    selectedUnit = worker;
                    rebuild();
                })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void chooseBuild() {
        if (!session.isMyTurn() || colony == null) return;
        final List<ColonyInfo.Choice<BuildableType>> choices;
        synchronized (session.lock()) {
            choices = ColonyInfo.buildChoices(session, colony);
        }
        if (choices.isEmpty()) {
            hint.setText("Nothing can be built here yet.");
            return;
        }
        String[] labels = new String[choices.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = choices.get(i).label;
        new AlertDialog.Builder(getContext())
            .setTitle("Build next")
            .setItems(labels, (d, which) ->
                session.setBuild(colony, choices.get(which).value))
            .setNegativeButton("Cancel", null)
            .show();
    }


    // Small view helpers

    private View unitIcon(ColonyInfo.UnitItem u, int height) {
        LinearLayout cell = new LinearLayout(getContext());
        cell.setOrientation(VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean sel = (u.unit == selectedUnit) || (u.unit == selectedCarrier);
        cell.setBackgroundColor(sel ? CELL_SELECTED : 0x00000000);
        cell.setPadding(px(2), px(1), px(2), px(1));
        cell.addView(image(u.imageKey, (int)(height * 0.7f), height));
        if (!u.caption.isEmpty()) {
            TextView t = text(9, sel ? Color.BLACK : 0xFFDDCCAA, false, u.caption);
            t.setMaxLines(1);
            cell.addView(t);
        }
        cell.setOnClickListener(v -> tapUnit(u.unit));
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT,
                                           LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, px(3), 0);
        cell.setLayoutParams(lp);
        return cell;
    }

    private View goodsCell(String iconKey, String count, String net,
                           boolean warn) {
        LinearLayout cell = new LinearLayout(getContext());
        cell.setOrientation(VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(px(4), px(2), px(4), px(2));
        cell.addView(image(iconKey, px(26), px(26)));
        cell.addView(text(12, warn ? 0xFFFF6B5B : Color.WHITE, true, count));
        if (net != null) {
            int colour = net.startsWith("-") ? 0xFFFF8A7A : 0xFF9BE08A;
            cell.addView(text(10, colour, false, net));
        }
        return cell;
    }

    private View amounts(List<ColonyInfo.Amount> list) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (ColonyInfo.Amount a : list) {
            row.addView(image(a.iconKey, px(16), px(16)));
            TextView t = text(11, 0xFFDDCCAA, false, a.amount + "  ");
            row.addView(t);
        }
        return row;
    }

    private LinearLayout flow() {
        LinearLayout l = new LinearLayout(getContext());
        l.setOrientation(HORIZONTAL);
        l.setPadding(px(2), px(2), px(2), px(2));
        return l;
    }

    private ImageView image(String key, int w, int h) {
        ImageView iv = new ImageView(getContext());
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Art.Img img = art.get(key);
        if (img != null) iv.setImageBitmap((Bitmap)img.handle);
        iv.setLayoutParams(new LayoutParams(w, h));
        return iv;
    }

    private TextView text(int sp, int colour, boolean bold) {
        return text(sp, colour, bold, "");
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
