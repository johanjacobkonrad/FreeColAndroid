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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.sf.freecol.android.map.IsometricRenderer;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * A single-player game against the AI: the map on the left, and the
 * {@link Sidebar} with the minimap, the selected unit, the orders and
 * the news on the right.
 */
public final class GameActivity extends Activity implements GameSession.Listener {

    /** Intent extra: carry on with the saved game. */
    static final String EXTRA_CONTINUE = "continue";
    /** Intent extras for a new game (see NewGameOptions). */
    static final String EXTRA_NATION = "nation", EXTRA_DIFFICULTY = "difficulty",
        EXTRA_MAP_SIZE = "mapSize", EXTRA_LAND = "land";

    private GameSession session;
    private AndroidArt art;
    private IsometricRenderer renderer;
    private MapView mapView;
    private MinimapView minimap;
    private Sidebar sidebar;
    private ColonyPanel colonyPanel;
    private EuropePanel europePanel;
    private float dp;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        session = new GameSession(this);
        try {
            art = new AndroidArt(getAssets());
        } catch (Exception e) {
            throw new RuntimeException("Game art is missing", e);
        }
        renderer = new IsometricRenderer(art);
        mapView = new MapView(this, session, renderer);
        minimap = new MinimapView(this, session, art);
        minimap.setViewport(mapView.getViewport());
        minimap.setJumper(mapView::jumpTo);
        mapView.setOnRedraw(minimap::viewChanged);

        dp = getResources().getDisplayMetrics().density;
        final int pad = (int)(8 * dp);

        // The map on the left, the sidebar on the right.
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(mapView, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        sidebar = new Sidebar(this, session, art, renderer, minimap,
                              new Sidebar.Controls() {
                @Override public void next() { session.nextUnit(); }
                @Override public void waitUnit() { session.waitUnit(); }
                @Override public void orders() { showOrders(); }
                @Override public void context(String mode) {
                    if (Sidebar.SAIL.equals(mode)) {
                        session.sailToEurope(session.getSelectedUnit());
                    } else if (Sidebar.JOIN.equals(mode)) {
                        session.joinColony();
                    } else {
                        session.buildColony();
                    }
                }
                @Override public void europe() { openEurope(); }
                @Override public void endTurn() { session.endTurn(); }
            });
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int wide = Math.max(dm.widthPixels, dm.heightPixels);
        int sideWidth = Math.min((int)(300 * dp), Math.max((int)(240 * dp), (int)(0.22f * wide)));
        // A brass edge between the map and the sidebar.
        android.view.View edge = new android.view.View(this);
        edge.setBackgroundColor(Theme.EDGE);
        content.addView(edge, new LinearLayout.LayoutParams(
            Math.max(1, (int)(2 * dp)), LinearLayout.LayoutParams.MATCH_PARENT));
        content.addView(sidebar, new LinearLayout.LayoutParams(
            sideWidth, LinearLayout.LayoutParams.MATCH_PARENT));
        // The map runs under the system bars; the sidebar keeps clear.
        final int gap = (int)(5 * dp);
        sidebar.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(gap, gap + bars.top, gap + bars.right, gap + bars.bottom);
            return insets;
        });
        root.addView(content, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        colonyPanel = new ColonyPanel(this, session, renderer, art,
                                      this::closeColony);
        colonyPanel.setVisibility(android.view.View.GONE);
        // The colony screen also keeps clear of the system bars.
        colonyPanel.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                WindowInsets.Type.systemBars()
                | WindowInsets.Type.displayCutout());
            v.setPadding(pad + bars.left, pad / 2 + bars.top,
                         pad + bars.right, pad / 2 + bars.bottom);
            return insets;
        });
        root.addView(colonyPanel, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        europePanel = new EuropePanel(this, session, renderer, art,
                                      this::closeEurope);
        europePanel.setVisibility(android.view.View.GONE);
        europePanel.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                WindowInsets.Type.systemBars()
                | WindowInsets.Type.displayCutout());
            v.setPadding(pad + bars.left, pad / 2 + bars.top,
                         pad + bars.right, pad / 2 + bars.bottom);
            return insets;
        });
        root.addView(europePanel, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        mapView.setColonyOpener(this::openColony);
        setContentView(root);
        refresh();

        new Thread(() -> {
            try {
                onStatus("Unpacking game data...");
                File data = new File(getFilesDir(), "data");
                Assets.copy(getAssets(), "data", data);
                Engine.init(data);
                File save = saveFile(this);
                session.setSaveFile(save);
                if (getIntent().getBooleanExtra(EXTRA_CONTINUE, false)
                    && save.isFile()) {
                    session.load(save);
                } else {
                    NewGameOptions opts = new NewGameOptions();
                    android.content.Intent in = getIntent();
                    opts.nationId = in.getStringExtra(EXTRA_NATION);
                    if (in.getStringExtra(EXTRA_DIFFICULTY) != null) {
                        opts.difficulty = in.getStringExtra(EXTRA_DIFFICULTY);
                    }
                    if (in.getStringExtra(EXTRA_MAP_SIZE) != null) {
                        opts.mapSize = in.getStringExtra(EXTRA_MAP_SIZE);
                    }
                    if (in.getStringExtra(EXTRA_LAND) != null) {
                        opts.land = in.getStringExtra(EXTRA_LAND);
                    }
                    net.sf.freecol.common.model.Specification spec
                        = Engine.loadSpecification(opts.difficulty);
                    opts.applyTo(spec);
                    session.start(spec, opts.nationId);
                }
            } catch (Throwable t) {
                onStatus("Could not start: " + t);
            }
        }, "GameStart").start();
    }

    private void openColony(net.sf.freecol.common.model.Colony colony) {
        // A ship (or wagon) next to the colony sails in as it opens.
        Unit u = session.getSelectedUnit();
        if (u != null && session.isMyTurn()) {
            boolean adjacent;
            synchronized (session.lock()) {
                Tile t = u.getTile();
                adjacent = u.isCarrier() && t != null && t != colony.getTile()
                    && t.getDirection(colony.getTile()) != null;
            }
            if (adjacent) session.bringIntoPort(u, colony);
        }
        colonyPanel.setVisibility(android.view.View.VISIBLE);
        colonyPanel.show(colony);
        colonyPanel.requestApplyInsets();
    }

    private void closeColony() {
        colonyPanel.setVisibility(android.view.View.GONE);
        refresh();
    }

    private void openEurope() {
        europePanel.setVisibility(android.view.View.VISIBLE);
        europePanel.show();
        europePanel.requestApplyInsets();
    }

    private void closeEurope() {
        europePanel.setVisibility(android.view.View.GONE);
        refresh();
    }

    @Override
    public void onBackPressed() {
        if (europePanel.getVisibility() == android.view.View.VISIBLE) {
            closeEurope();
        } else if (colonyPanel.getVisibility() == android.view.View.VISIBLE) {
            closeColony();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * The saved game.  It is saved at the start of each of your turns
     * and when the app goes into the background.
     *
     * @param context The app {@code Context}.
     * @return The save file.
     */
    static File saveFile(android.content.Context context) {
        return new File(new File(context.getFilesDir(), "saves"), "autosave.fsg");
    }

    @Override
    protected void onPause() {
        // Android may stop the app at any time once it is in the
        // background, so save now (only does so during our turn).
        session.saveNow();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // Unblock the game if a question is still open.
        for (Runnable r : new ArrayList<>(openQuestions)) r.run();
        session.stop();
        super.onDestroy();
    }


    // Implement GameSession.Listener

    /** Ways to cancel questions still showing (see onDestroy). */
    private final List<Runnable> openQuestions
        = java.util.Collections.synchronizedList(new ArrayList<>());

    @Override
    public void onChoose(String title, String text, List<String> options,
                         java.util.function.IntConsumer answer) {
        runOnUiThread(() -> {
            final java.util.concurrent.atomic.AtomicBoolean done
                = new java.util.concurrent.atomic.AtomicBoolean(false);
            final android.app.AlertDialog[] dialog = new android.app.AlertDialog[1];
            final Runnable cancel = () -> {
                if (done.compareAndSet(false, true)) answer.accept(-1);
            };
            openQuestions.add(cancel);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            int pad = (int)(16 * dp);
            box.setPadding(pad, pad / 2, pad, pad / 2);
            TextView msg = Theme.serif(this, text, 15, Theme.PAPER_INK, false);
            msg.setPadding(0, 0, 0, pad / 2);
            box.addView(msg);
            for (int i = 0; i < options.size(); i++) {
                final int which = i;
                // The first answer is the usual one: give it the gold.
                TextView b = Theme.text(this, options.get(i), 14, Theme.PAPER_INK, i == 0);
                b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                b.setBackground(i == 0 ? Theme.gold(this) : Theme.cell(this, 0xFFF4EACF));
                b.setPadding(pad * 3 / 4, pad / 2, pad * 3 / 4, pad / 2);
                b.setMinHeight((int)(44 * dp));
                b.setOnClickListener(v -> {
                        if (done.compareAndSet(false, true)) {
                            openQuestions.remove(cancel);
                            if (dialog[0] != null) dialog[0].dismiss();
                            answer.accept(which);
                        }
                    });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, pad / 2);
                box.addView(b, lp);
            }
            android.widget.ScrollView scroll = new android.widget.ScrollView(this);
            scroll.addView(box);
            dialog[0] = new android.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scroll)
                .setOnCancelListener(d -> {
                        openQuestions.remove(cancel);
                        cancel.run();
                    })
                .show();
        });
    }

    @Override
    public void onAskName(String title, String text, String suggestion,
                          java.util.function.Consumer<String> answer) {
        runOnUiThread(() -> {
            final java.util.concurrent.atomic.AtomicBoolean done
                = new java.util.concurrent.atomic.AtomicBoolean(false);
            final Runnable keep = () -> {
                if (done.compareAndSet(false, true)) answer.accept(null);
            };
            openQuestions.add(keep);
            // The question and the name field share the dialog's inner
            // margin and its serif, dark ink on parchment.
            int side = (int)(24 * dp), pad = (int)(8 * dp);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(side, pad / 2, side, 0);
            TextView msg = Theme.serif(this, text, 15, Theme.PAPER_INK, false);
            msg.setPadding(0, 0, 0, pad * 3 / 2);
            box.addView(msg);
            final android.widget.EditText edit = new android.widget.EditText(this);
            edit.setSingleLine(true);
            edit.setText(suggestion);
            edit.setSelectAllOnFocus(true);
            edit.setTypeface(Theme.SERIF_BOLD);
            edit.setTextSize(17);
            edit.setTextColor(Theme.PAPER_INK);
            edit.setHighlightColor(0x66B8913F);
            edit.setBackground(Theme.cell(this, 0xFFF6EED8));
            edit.setPadding(pad * 3 / 2, pad, pad * 3 / 2, pad);
            box.addView(edit, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
            new android.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("OK", (d, w) -> {
                        openQuestions.remove(keep);
                        if (done.compareAndSet(false, true)) {
                            answer.accept(edit.getText().toString());
                        }
                    })
                .setOnCancelListener(d -> {
                        openQuestions.remove(keep);
                        keep.run();
                    })
                .show();
        });
    }

    @Override
    public void onChanged() {
        runOnUiThread(this::refresh);
        runOnUiThread(minimap::gameChanged);
        colonyPanel.onChanged();
        europePanel.onChanged();
    }

    @Override
    public void onStatus(String text) {
        runOnUiThread(() -> {
            sidebar.addNews(text);
            if (colonyPanel.getVisibility() == android.view.View.VISIBLE) {
                colonyPanel.onStatus(text);
            }
            if (europePanel.getVisibility() == android.view.View.VISIBLE) {
                europePanel.onStatus(text);
            }
        });
    }


    private void refresh() {
        sidebar.refresh();
        mapView.showSelected();
        mapView.invalidate();
    }

    /** The selected unit's orders, as a menu. */
    private void showOrders() {
        final Unit unit = session.getSelectedUnit();
        if (unit == null || !session.isMyTurn()) return;
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        synchronized (session.lock()) {
            if (unit.hasTile() && !unit.isOnCarrier()) {
                boolean naval = unit.isNaval();
                labels.add(naval ? "Sentry" : "Fortify");
                actions.add(() -> session.fortifyUnit());
            }
            for (ColonyInfo.Choice<net.sf.freecol.common.model.TileImprovementType> c
                     : session.improvementChoices(unit)) {
                labels.add(c.label);
                actions.add(() -> session.improve(unit, c.value));
            }
            for (GameSession.EquipChoice c : session.equipChoices(unit)) {
                labels.add("Equip: " + c.label);
                actions.add(() -> session.equip(unit, c));
            }
            if (unit.getDestination() != null
                || unit.getState() != Unit.UnitState.ACTIVE) {
                labels.add("Clear orders");
                actions.add(() -> session.clearOrders(unit));
            }
            labels.add("Disband unit");
            final String name = unit.getDescription();
            actions.add(() -> new android.app.AlertDialog.Builder(this)
                .setMessage("Disband " + name + " for good?")
                .setPositiveButton("Disband", (d, w) -> session.disband(unit))
                .setNegativeButton("Cancel", null)
                .show());
        }
        new android.app.AlertDialog.Builder(this)
            .setTitle(unit.getDescription())
            .setItems(labels.toArray(new String[0]),
                      (d, which) -> actions.get(which).run())
            .setNegativeButton("Cancel", null)
            .show();
    }
}
