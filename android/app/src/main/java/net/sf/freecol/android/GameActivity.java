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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.sf.freecol.android.map.Art;
import net.sf.freecol.android.map.IsometricRenderer;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * A single-player game against the AI: the map, a line of news, a row
 * of the units on the selected unit's tile (tap one to pick it), and
 * buttons for the commonest orders.
 */
public final class GameActivity extends Activity implements GameSession.Listener {

    private static final int NEWS_LINES = 4;

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
    private ColonyPanel colonyPanel;
    private EuropePanel europePanel;
    private Button europeButton;
    private LinearLayout unitRow;
    private List<Unit> unitRowUnits = new ArrayList<>();
    private Unit unitRowSelected = null;
    private float dp;
    private TextView info, news;
    private Button nextButton, waitButton, fortifyButton, colonyButton, endButton;
    /** The colony button joins a colony next to the unit, not founds one. */
    private boolean joinMode = false;
    /** The colony button sends a ship to Europe. */
    private boolean sailMode = false;
    private final Deque<String> newsLines = new ArrayDeque<>();


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

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(mapView);
        minimap = new MinimapView(this, session, art);
        minimap.setViewport(mapView.getViewport());
        minimap.setJumper(mapView::jumpTo);
        mapView.setOnRedraw(minimap::viewChanged);

        dp = getResources().getDisplayMetrics().density;
        final int pad = (int)(8 * dp);

        LinearLayout hud = new LinearLayout(this);
        hud.setOrientation(LinearLayout.VERTICAL);

        info = label(15, dp);
        news = label(13, dp);
        news.setMaxLines(NEWS_LINES);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.addView(info);
        top.addView(news);
        top.setPadding(0, 0, (int)(175 * dp), 0); // Clear of the minimap
        hud.addView(top, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        // Space between the news and the buttons lets taps reach the map.
        hud.addView(new android.view.View(this) {
                @Override
                public boolean onTouchEvent(android.view.MotionEvent e) {
                    return false;
                }
            }, new LinearLayout.LayoutParams(1, 0, 1f));

        // The units on the selected tile, ship passengers included.
        unitRow = new LinearLayout(this);
        unitRow.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView rowScroll = new HorizontalScrollView(this);
        rowScroll.setHorizontalScrollBarEnabled(false);
        rowScroll.addView(unitRow);
        hud.addView(rowScroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.END);
        nextButton = button(bar, "Next unit", v -> session.nextUnit());
        waitButton = button(bar, "Wait", v -> session.waitUnit());
        fortifyButton = button(bar, "Orders", v -> showOrders());
        colonyButton = button(bar, "Build colony", v -> {
                if (sailMode) {
                    session.sailToEurope(session.getSelectedUnit());
                } else if (joinMode) {
                    session.joinColony();
                } else {
                    session.buildColony();
                }
            });
        europeButton = button(bar, "Europe", v -> openEurope());
        endButton = button(bar, "End turn", v -> session.endTurn());
        hud.addView(bar, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        // The map goes edge to edge; keep the controls clear of the
        // status bar, navigation bar and camera cutout.
        hud.setPadding(pad, pad, pad, pad);
        hud.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                WindowInsets.Type.systemBars()
                | WindowInsets.Type.displayCutout());
            v.setPadding(pad + bars.left, pad + bars.top,
                         pad + bars.right, pad + bars.bottom);
            return WindowInsets.CONSUMED;
        });
        root.addView(hud, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        // The minimap, top right, clear of the system bars.
        final FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(
            (int)(160 * dp), (int)(100 * dp), Gravity.TOP | Gravity.END);
        mlp.setMargins(0, pad, pad, 0);
        root.addView(minimap, mlp);
        minimap.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            mlp.setMargins(0, pad + bars.top, pad + bars.right, 0);
            v.setLayoutParams(mlp);
            return insets;
        });
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
            box.setPadding(pad, pad / 2, pad, 0);
            TextView msg = new TextView(this);
            msg.setText(text);
            msg.setTextSize(15);
            msg.setPadding(0, 0, 0, pad / 2);
            box.addView(msg);
            for (int i = 0; i < options.size(); i++) {
                final int which = i;
                Button b = new Button(this);
                b.setAllCaps(false);
                b.setText(options.get(i));
                b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                b.setOnClickListener(v -> {
                        if (done.compareAndSet(false, true)) {
                            openQuestions.remove(cancel);
                            if (dialog[0] != null) dialog[0].dismiss();
                            answer.accept(which);
                        }
                    });
                box.addView(b, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
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
            final android.widget.EditText edit = new android.widget.EditText(this);
            edit.setSingleLine(true);
            edit.setText(suggestion);
            edit.setSelectAllOnFocus(true);
            new android.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(text)
                .setView(edit)
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
            newsLines.addLast(text);
            while (newsLines.size() > NEWS_LINES) newsLines.removeFirst();
            news.setText(String.join("\n", newsLines));
            if (colonyPanel.getVisibility() == android.view.View.VISIBLE) {
                colonyPanel.onStatus(text);
            }
            if (europePanel.getVisibility() == android.view.View.VISIBLE) {
                europePanel.onStatus(text);
            }
        });
    }


    private void refresh() {
        boolean myTurn = session.isMyTurn();
        Unit unit = session.getSelectedUnit();
        String text = "";
        boolean canColony = false, canFortify = false, naval = false;
        boolean canJoin = false, canSail = false;
        List<Unit> here = new ArrayList<>();
        synchronized (session.lock()) {
            Player me = session.getMyPlayer();
            if (session.isInGame() && me != null) {
                text = net.sf.freecol.common.i18n.Messages.message(
                    session.getGame().getTurn().getLabel())
                    + "   Gold: " + me.getGold();
                if (unit != null) {
                    String doing = session.describeOrders(unit);
                    text += "   " + unit.getDescription()
                        + " (moves " + unit.getMovesAsString()
                        + (doing.isEmpty() ? "" : ", " + doing) + ")";
                    canColony = session.canBuildColonyHere(unit);
                    canJoin = session.joinableColony(unit) != null;
                    canSail = session.canSailToEurope(unit);
                    canFortify = unit.hasTile() && !unit.isOnCarrier();
                    naval = unit.isNaval();
                    Tile t = unit.getTile();
                    if (t != null) here = session.getMyUnitsAt(t);
                }
            }
        }
        info.setText(text);
        nextButton.setEnabled(myTurn);
        waitButton.setEnabled(myTurn && unit != null);
        fortifyButton.setEnabled(myTurn && unit != null);
        updateUnitRow(here, unit);
        joinMode = canJoin;
        sailMode = canSail;
        colonyButton.setText(canSail ? "Sail to Europe"
                             : canJoin ? "Join colony" : "Build colony");
        colonyButton.setEnabled(myTurn && (canColony || canJoin || canSail));
        europeButton.setEnabled(session.isInGame());
        endButton.setEnabled(myTurn);
        mapView.showSelected();
        mapView.invalidate();
    }

    /** Show the units on the selected tile, if there is more than one. */
    private void updateUnitRow(List<Unit> units, Unit sel) {
        if (units.size() < 2) units = new ArrayList<>();
        if (units.equals(unitRowUnits) && sel == unitRowSelected) {
            // Same units: just refresh the moves left.
            for (int i = 0; i < unitRow.getChildCount(); i++) {
                TextView moves = (TextView)((LinearLayout)unitRow.getChildAt(i))
                    .getChildAt(1);
                synchronized (session.lock()) {
                    moves.setText(units.get(i).getMovesAsString());
                }
            }
            return;
        }
        unitRowUnits = units;
        unitRowSelected = sel;
        unitRow.removeAllViews();
        final int size = (int)(52 * dp), pad = (int)(3 * dp);
        for (final Unit u : units) {
            Bitmap bm = null;
            String moves;
            boolean aboard;
            synchronized (session.lock()) {
                Art.Img img = art.get(renderer.unitKey(u));
                if (img != null) bm = (Bitmap)img.handle;
                moves = u.getMovesAsString();
                aboard = u.isOnCarrier();
            }
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(pad, pad, pad, pad);
            // Selected: light; passengers: blue-ish; others: dark.
            cell.setBackgroundColor((u == sel) ? 0xCCFFFFFF
                : aboard ? 0xAA1E4A7A : 0xAA000000);
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (bm != null) iv.setImageBitmap(bm);
            cell.addView(iv, new LinearLayout.LayoutParams(size, size));
            TextView t = new TextView(this);
            t.setTextSize(11);
            t.setTextColor((u == sel) ? Color.BLACK : Color.WHITE);
            t.setText(moves);
            t.setGravity(Gravity.CENTER);
            cell.addView(t);
            cell.setOnClickListener(v -> session.select(u));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, pad * 2, 0);
            unitRow.addView(cell, lp);
        }
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

    private TextView label(int sp, float dp) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setShadowLayer(3 * dp, 0, 0, Color.BLACK);
        return t;
    }

    private Button button(LinearLayout bar, String text,
                          android.view.View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        b.setAlpha(0.9f);
        bar.addView(b);
        return b;
    }
}
