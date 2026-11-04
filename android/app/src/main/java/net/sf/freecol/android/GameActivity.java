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

    private GameSession session;
    private AndroidArt art;
    private IsometricRenderer renderer;
    private MapView mapView;
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
        fortifyButton = button(bar, "Fortify", v -> session.fortifyUnit());
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
                    session.start(Engine.loadSpecification());
                }
            } catch (Throwable t) {
                onStatus("Could not start: " + t);
            }
        }, "GameStart").start();
    }

    private void openColony(net.sf.freecol.common.model.Colony colony) {
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
        session.stop();
        super.onDestroy();
    }


    // Implement GameSession.Listener

    @Override
    public void onChanged() {
        runOnUiThread(this::refresh);
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
                    text += "   " + unit.getDescription()
                        + " (moves " + unit.getMovesAsString() + ")";
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
        fortifyButton.setEnabled(myTurn && canFortify);
        fortifyButton.setText(naval ? "Sentry" : "Fortify");
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
