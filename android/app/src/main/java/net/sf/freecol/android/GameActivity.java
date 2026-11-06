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
    /** Intent extra: load this saved game (a path). */
    static final String EXTRA_LOAD = "load";
    /** How many save slots the menu offers, besides the autosave. */
    private static final int SLOTS = 3;
    /** Intent extras for a new game (see NewGameOptions). */
    static final String EXTRA_NATION = "nation", EXTRA_DIFFICULTY = "difficulty",
        EXTRA_MAP_SIZE = "mapSize", EXTRA_LAND = "land";

    private GameSession session;
    private AndroidArt art;
    private IsometricRenderer renderer;
    private MapView mapView;
    private MinimapView minimap;
    private Sidebar sidebar;
    private NewsRibbon news;
    private ColonyPanel colonyPanel;
    private EuropePanel europePanel;
    private ColonyListPanel colonyList;
    /** Reports and the Colopedia. */
    private InfoPanel infoPanel;
    private TradeRoutePanel routePanel;
    /** A colony opened from the colony list goes back to it on close. */
    private boolean backToList = false;
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
        // The news floats over the top left of the map.
        FrameLayout mapFrame = new FrameLayout(this);
        mapFrame.addView(mapView);
        news = new NewsRibbon(this);
        final FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP | Gravity.START);
        nlp.setMargins(pad, pad, pad, 0);
        mapFrame.addView(news, nlp);
        news.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            nlp.setMargins(pad + bars.left, pad + bars.top, pad, 0);
            v.setLayoutParams(nlp);
            return insets;
        });
        content.addView(mapFrame, new LinearLayout.LayoutParams(
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
                @Override public void menu() { showMenu(); }
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
        colonyList = new ColonyListPanel(this, session, renderer, art,
            this::closeColonyList, colony -> {
                colonyList.setVisibility(android.view.View.GONE);
                backToList = true;
                mapView.centreOn(colony.getTile());
                openColony(colony);
            });
        colonyList.setVisibility(android.view.View.GONE);
        colonyList.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                WindowInsets.Type.systemBars()
                | WindowInsets.Type.displayCutout());
            v.setPadding(pad + bars.left, pad / 2 + bars.top,
                         pad + bars.right, pad / 2 + bars.bottom);
            return insets;
        });
        // Under the colony screen, so a colony opened from the list
        // shows on top of it.
        root.addView(colonyList, root.indexOfChild(colonyPanel), new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        infoPanel = new InfoPanel(this, art, this::closeInfo);
        infoPanel.setVisibility(android.view.View.GONE);
        infoPanel.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                WindowInsets.Type.systemBars()
                | WindowInsets.Type.displayCutout());
            v.setPadding(pad + bars.left, pad / 2 + bars.top,
                         pad + bars.right, pad / 2 + bars.bottom);
            return insets;
        });
        root.addView(infoPanel, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        routePanel = new TradeRoutePanel(this, session, art, this::closeRoutes);
        routePanel.setVisibility(android.view.View.GONE);
        routePanel.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                WindowInsets.Type.systemBars()
                | WindowInsets.Type.displayCutout());
            v.setPadding(pad + bars.left, pad / 2 + bars.top,
                         pad + bars.right, pad / 2 + bars.bottom);
            return insets;
        });
        root.addView(routePanel, new FrameLayout.LayoutParams(
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
                String load = getIntent().getStringExtra(EXTRA_LOAD);
                if (load != null && new File(load).isFile()) {
                    session.load(new File(load));
                } else if (getIntent().getBooleanExtra(EXTRA_CONTINUE, false)
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
        if (backToList) {
            backToList = false;
            openColonyList();
        }
        refresh();
    }

    private void openColonyList() {
        colonyList.setVisibility(android.view.View.VISIBLE);
        colonyList.show();
        colonyList.requestApplyInsets();
    }

    private void closeColonyList() {
        colonyList.setVisibility(android.view.View.GONE);
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
        } else if (routePanel.getVisibility() == android.view.View.VISIBLE) {
            closeRoutes();
        } else if (infoPanel.getVisibility() == android.view.View.VISIBLE) {
            if (!infoPanel.goBack()) closeInfo();
        } else if (colonyList.getVisibility() == android.view.View.VISIBLE) {
            closeColonyList();
        } else {
            super.onBackPressed();
        }
    }

    // The menu

    /** The sidebar menu: resume, colonies, save, load, quit. */
    private void showMenu() {
        if (!session.isInGame()) return;
        final android.app.AlertDialog[] dialog = new android.app.AlertDialog[1];
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * dp);
        box.setPadding(pad, pad / 4, pad, pad / 2);
        Object[][] items = {
            { "Resume game", Theme.Icon.NEXT, (Runnable)() -> {} },
            { "Colonies", Theme.Icon.COLONY, (Runnable)this::openColonyList },
            { "Reports", Theme.Icon.ORDERS, (Runnable)this::openReports },
            { "Colopedia", Theme.Icon.BOOK, (Runnable)this::openColopedia },
            { "Diplomacy", Theme.Icon.JOIN, (Runnable)this::showDiplomacy },
            { "Trade routes", Theme.Icon.SHIP, (Runnable)this::openRoutes },
            { "Save game", Theme.Icon.SAVE, (Runnable)this::showSave },
            { "Load game", Theme.Icon.LOAD, (Runnable)this::showLoad },
            { "Quit to start screen", Theme.Icon.BACK, (Runnable)this::confirmQuit },
        };
        // Two to a row, so it fits a phone held sideways.
        LinearLayout row = null;
        for (int i = 0; i < items.length; i++) {
            final Object[] it = items[i];
            final Runnable action = (Runnable)it[2];
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                box.addView(row);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            lp.setMargins((i % 2 == 0) ? 0 : (int)(3 * dp), 0,
                          (i % 2 == 0) ? (int)(3 * dp) : 0, (int)(6 * dp));
            row.addView(menuItem((String)it[0], (Theme.Icon)it[1], v -> {
                        dialog[0].dismiss();
                        action.run();
                    }), lp);
        }
        if (items.length % 2 == 1) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            row.addView(new android.view.View(this), lp);
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(box);
        dialog[0] = new android.app.AlertDialog.Builder(this)
            .setTitle("Menu").setView(sv).show();
    }

    private TextView menuItem(String label, Theme.Icon icon,
                              android.view.View.OnClickListener l) {
        TextView b = Theme.text(this, label, 15, Theme.PAPER_INK, true);
        b.setTypeface(Theme.SERIF_BOLD);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setBackground(Theme.cell(this, 0xFFF4EACF));
        int p = (int)(10 * dp);
        b.setPadding(p, p * 3 / 4, p, p * 3 / 4);
        b.setMinHeight((int)(44 * dp));
        if (icon != null) {
            android.graphics.drawable.Drawable d
                = new Theme.IconDrawable(icon, 0xFF7A5A2C, (int)(20 * dp));
            d.setBounds(0, 0, (int)(20 * dp), (int)(20 * dp));
            b.setCompoundDrawables(d, null, null, null);
            b.setCompoundDrawablePadding(p);
        }
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout.LayoutParams menuParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, (int)(6 * dp));
        return lp;
    }

    // Trade routes

    private void openRoutes() {
        routePanel.setVisibility(android.view.View.VISIBLE);
        routePanel.show();
        routePanel.requestApplyInsets();
    }

    private void closeRoutes() {
        routePanel.setVisibility(android.view.View.GONE);
        refresh();
    }

    /** Put a wagon train or ship on one of the trade routes, or off. */
    private void chooseRoute(final Unit unit) {
        final List<net.sf.freecol.common.model.TradeRoute> routes;
        final net.sf.freecol.common.model.TradeRoute current;
        synchronized (session.lock()) {
            routes = new ArrayList<>(session.getMyPlayer().getTradeRoutes());
            current = unit.getTradeRoute();
        }
        if (routes.isEmpty()) {
            new android.app.AlertDialog.Builder(this)
                .setTitle("Trade route")
                .setMessage("You have no trade routes yet. Make one from the menu "
                            + "(\u2630 \u2192 Trade routes).")
                .setPositiveButton("Make one", (d, w) -> openRoutes())
                .setNegativeButton("Cancel", null)
                .show();
            return;
        }
        List<String> names = new ArrayList<>();
        for (net.sf.freecol.common.model.TradeRoute tr : routes) {
            names.add(tr.getName() + (tr == current ? " (now)" : ""));
        }
        if (current != null) names.add("Leave the route");
        new android.app.AlertDialog.Builder(this)
            .setTitle("Trade route for " + unit.getDescription())
            .setItems(names.toArray(new String[0]), (d, w) ->
                session.assignTradeRoute(unit, (w < routes.size()) ? routes.get(w) : null))
            .setNegativeButton("Cancel", null)
            .show();
    }

    // Diplomacy

    /** The other European nations: tap one to make an offer. */
    private void showDiplomacy() {
        final android.app.AlertDialog[] dialog = new android.app.AlertDialog[1];
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * dp);
        box.setPadding(pad, pad / 4, pad, pad / 2);
        List<DiplomacyInfo.Nation> nations;
        synchronized (session.lock()) {
            nations = DiplomacyInfo.nations(session.getMyPlayer());
        }
        TextView intro = Theme.serif(this, "Offers are carried by a unit or colony of "
            + "yours next to one of theirs, such as a scout at their colony.",
            13, Theme.PAPER_DIM, false);
        intro.setPadding(0, 0, 0, pad / 2);
        box.addView(intro);
        for (final DiplomacyInfo.Nation n : nations) {
            String line = n.name + (n.met ? " \u00b7 " + n.stance : "") + "\n"
                + (!n.met ? "Not met yet"
                   : (n.where != null) ? "Talk through " + n.where
                   : "Nobody of yours is next to theirs");
            final boolean can = n.met && n.our != null;
            TextView item = menuItem(line, Theme.Icon.JOIN, v -> {
                    if (!can) return;
                    dialog[0].dismiss();
                    session.proposeDiplomacy(n.player, null);
                });
            if (!can) item.setAlpha(0.55f);
            box.addView(item, menuParams());
        }
        if (nations.isEmpty()) {
            box.addView(Theme.serif(this, "There are no other European nations.",
                                    15, Theme.PAPER_INK, false));
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(box);
        dialog[0] = new android.app.AlertDialog.Builder(this)
            .setTitle("Diplomacy").setView(sv)
            .setNegativeButton("Close", null).show();
    }

    @Override
    public void onNegotiate(final DiplomacyInfo.Offer offer, final String text,
                            final java.util.function.Consumer<Boolean> answer) {
        runOnUiThread(() -> {
            final java.util.concurrent.atomic.AtomicBoolean done
                = new java.util.concurrent.atomic.AtomicBoolean(false);
            final Runnable cancel = () -> {
                if (done.compareAndSet(false, true)) answer.accept(false);
            };
            openQuestions.add(cancel);
            final int pad = (int)(16 * dp);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(pad, pad / 4, pad, pad / 2);
            TextView to = Theme.serif(this, text, 14, Theme.PAPER_INK, false);
            box.addView(to);

            // Relations.
            List<String> stances = new ArrayList<>();
            stances.add("No change");
            stances.addAll(offer.stanceNames);
            negotiateHeading(box, "Relations");
            box.addView(chips(stances, 0, i ->
                offer.stance = (i == 0) ? null : offer.stances.get(i - 1)));

            // Gold, both ways.
            negotiateHeading(box, "You give gold (you have " + offer.myGold + ")");
            box.addView(stepper(0, offer.myGold, 100, v -> offer.giveGold = v));
            negotiateHeading(box, "You ask for gold");
            box.addView(stepper(0, 10000, 100, v -> offer.askGold = v));

            // Colonies, both ways.
            if (!offer.myColonies.isEmpty()) {
                negotiateHeading(box, "You give a colony");
                List<String> names = new ArrayList<>();
                names.add("None");
                for (net.sf.freecol.common.model.Colony c : offer.myColonies) names.add(c.getName());
                box.addView(chips(names, 0, i ->
                    offer.giveColony = (i == 0) ? null : offer.myColonies.get(i - 1)));
            }
            if (!offer.theirColonies.isEmpty()) {
                negotiateHeading(box, "You ask for a colony");
                List<String> names = new ArrayList<>();
                names.add("None");
                for (net.sf.freecol.common.model.Colony c : offer.theirColonies) names.add(c.getName());
                box.addView(chips(names, 0, i ->
                    offer.askColony = (i == 0) ? null : offer.theirColonies.get(i - 1)));
            }
            android.widget.ScrollView sv = new android.widget.ScrollView(this);
            sv.addView(box);
            final android.app.AlertDialog d = new android.app.AlertDialog.Builder(this)
                .setTitle("Offer to the " + offer.name)
                .setView(sv)
                .setPositiveButton("Send offer", null)
                .setNegativeButton("Cancel", (x, w) -> {
                        openQuestions.remove(cancel);
                        cancel.run();
                    })
                .setOnCancelListener(x -> {
                        openQuestions.remove(cancel);
                        cancel.run();
                    })
                .create();
            d.setOnShowListener(x -> d.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                        if (offer.isEmpty()) {
                            to.setText(text + "\nChoose something to offer or ask for first.");
                            return;
                        }
                        openQuestions.remove(cancel);
                        d.dismiss();
                        if (done.compareAndSet(false, true)) answer.accept(true);
                    }));
            d.show();
        });
    }

    private void negotiateHeading(LinearLayout box, String s) {
        TextView h = Theme.heading(this, s, 0xFF7A5A2C);
        h.setPadding(0, (int)(10 * dp), 0, (int)(4 * dp));
        box.addView(h);
    }

    /** A row of choices, one picked (gold). */
    private android.view.View chips(List<String> names, int picked,
                                    java.util.function.IntConsumer onPick) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final List<TextView> all = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            final int which = i;
            TextView t = Theme.text(this, names.get(i), 13, Theme.PAPER_INK, true);
            t.setGravity(Gravity.CENTER);
            int p = (int)(10 * dp);
            t.setPadding(p, p / 2, p, p / 2);
            t.setMinHeight((int)(38 * dp));
            t.setOnClickListener(v -> {
                    for (int j = 0; j < all.size(); j++) {
                        all.get(j).setBackground((j == which) ? Theme.gold(this)
                                                 : Theme.cell(this, 0xFFF4EACF));
                    }
                    onPick.accept(which);
                });
            t.setBackground((i == picked) ? Theme.gold(this) : Theme.cell(this, 0xFFF4EACF));
            all.add(t);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, (int)(6 * dp), 0);
            row.addView(t, lp);
        }
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(row);
        return hs;
    }

    /** An amount with - and + buttons. */
    private android.view.View stepper(final int min, final int max, final int step,
                                      final java.util.function.IntConsumer onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final int[] value = { min };
        final TextView amount = Theme.serif(this, min + " gold", 15, Theme.PAPER_INK, true);
        amount.setGravity(Gravity.CENTER);
        amount.setMinWidth((int)(110 * dp));
        String[] labels = { "\u2212" + (step * 10), "\u2212" + step, null, "+" + step, "+" + (step * 10) };
        int[] deltas = { -step * 10, -step, 0, step, step * 10 };
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] == null) {
                row.addView(amount);
                continue;
            }
            final int delta = deltas[i];
            TextView b = Theme.text(this, labels[i], 13, Theme.PAPER_INK, true);
            b.setGravity(Gravity.CENTER);
            b.setBackground(Theme.cell(this, 0xFFF4EACF));
            b.setMinWidth((int)(52 * dp));
            b.setMinHeight((int)(38 * dp));
            b.setOnClickListener(v -> {
                    value[0] = Math.max(min, Math.min(max, value[0] + delta));
                    amount.setText(value[0] + " gold");
                    onChange.accept(value[0]);
                });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, (int)(5 * dp), 0);
            row.addView(b, lp);
        }
        return row;
    }

    // Reports and the Colopedia

    private net.sf.freecol.common.model.Specification spec() {
        synchronized (session.lock()) {
            return session.getGame().getSpecification();
        }
    }

    private void openInfo(InfoPanel.Source src) {
        infoPanel.setVisibility(android.view.View.VISIBLE);
        infoPanel.show(src, 0);
        infoPanel.requestApplyInsets();
    }

    private void closeInfo() {
        infoPanel.setVisibility(android.view.View.GONE);
        refresh();
    }

    private void openReports() {
        final ReportInfo.Kind[] kinds = ReportInfo.Kind.values();
        final String[] names = new String[kinds.length];
        for (int i = 0; i < kinds.length; i++) names[i] = kinds[i].label;
        openInfo(new InfoPanel.Source() {
                @Override public String title() { return "Reports"; }
                @Override public String[] tabs() { return names; }
                @Override public InfoPage tab(int i) {
                    synchronized (session.lock()) {
                        return ReportInfo.page(kinds[i], session.getMyPlayer(), renderer);
                    }
                }
                @Override public InfoPage link(String id) {
                    return ColopediaInfo.page(spec(), id);
                }
            });
        // The other nations' figures come from the server: show them
        // when they arrive.
        session.refreshNationSummaries(() -> runOnUiThread(infoPanel::refresh));
    }

    private void openColopedia() {
        final ColopediaInfo.Kind[] kinds = ColopediaInfo.Kind.values();
        final String[] names = new String[kinds.length];
        for (int i = 0; i < kinds.length; i++) names[i] = kinds[i].label;
        openInfo(new InfoPanel.Source() {
                @Override public String title() { return "Colopedia"; }
                @Override public String[] tabs() { return names; }
                @Override public InfoPage tab(int i) {
                    return ColopediaInfo.list(spec(), kinds[i]);
                }
                @Override public InfoPage link(String id) {
                    return ColopediaInfo.page(spec(), id);
                }
            });
    }

    /** A save slot's file. */
    private File slotFile(int n) {
        return new File(saveFile(this).getParentFile(), "slot" + n + ".fsg");
    }

    /** How a save looks in the lists: its year and nation, and when. */
    private String describe(File f) {
        String about = GameSession.describeSave(f);
        if (about == null) return null;
        return about + "\n" + android.text.format.DateFormat.getMediumDateFormat(this)
            .format(new java.util.Date(f.lastModified())) + ", "
            + android.text.format.DateFormat.getTimeFormat(this)
            .format(new java.util.Date(f.lastModified()));
    }

    private void showSave() {
        if (!session.isMyTurn()) {
            news.add("You can save during your turn.");
            return;
        }
        final android.app.AlertDialog[] dialog = new android.app.AlertDialog[1];
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * dp);
        box.setPadding(pad, pad / 4, pad, pad / 2);
        for (int n = 1; n <= SLOTS; n++) {
            final File f = slotFile(n);
            final String about = describe(f);
            final String name = "Slot " + n;
            box.addView(menuItem(name + ": " + (about == null ? "empty" : about),
                                 Theme.Icon.SAVE, v -> {
                        dialog[0].dismiss();
                        if (about == null) {
                            saveInto(f, name);
                        } else {
                            new android.app.AlertDialog.Builder(this)
                                .setMessage("Replace the game in " + name + "?")
                                .setPositiveButton("Replace", (d, w) -> saveInto(f, name))
                                .setNegativeButton("Cancel", null)
                                .show();
                        }
                    }), menuParams());
        }
        dialog[0] = new android.app.AlertDialog.Builder(this)
            .setTitle("Save game").setView(box)
            .setNegativeButton("Cancel", null).show();
    }

    private void saveInto(File f, String name) {
        session.saveTo(f, err -> runOnUiThread(() ->
            news.add(err == null ? "Saved in " + name + "." : "Not saved: " + err)));
    }

    private void showLoad() {
        final android.app.AlertDialog[] dialog = new android.app.AlertDialog[1];
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * dp);
        box.setPadding(pad, pad / 4, pad, pad / 2);
        List<File> files = new ArrayList<>();
        List<String> names = new ArrayList<>();
        files.add(saveFile(this));
        names.add("Autosave");
        for (int n = 1; n <= SLOTS; n++) {
            files.add(slotFile(n));
            names.add("Slot " + n);
        }
        boolean any = false;
        for (int i = 0; i < files.size(); i++) {
            final File f = files.get(i);
            final String about = describe(f);
            if (about == null) continue;
            any = true;
            box.addView(menuItem(names.get(i) + ": " + about, Theme.Icon.LOAD, v -> {
                        dialog[0].dismiss();
                        new android.app.AlertDialog.Builder(this)
                            .setMessage("Leave this game and load " + about.split("\n")[0]
                                        + "? Anything since your last save is lost.")
                            .setPositiveButton("Load", (d, w) -> loadGame(f))
                            .setNegativeButton("Cancel", null)
                            .show();
                    }), menuParams());
        }
        if (!any) {
            box.addView(Theme.serif(this, "There are no saved games yet.", 15,
                                    Theme.PAPER_INK, false));
        }
        dialog[0] = new android.app.AlertDialog.Builder(this)
            .setTitle("Load game").setView(box)
            .setNegativeButton("Cancel", null).show();
    }

    /** Start the game screen afresh with a saved game. */
    private void loadGame(File f) {
        // Keep a copy: the autosave is overwritten as this screen closes.
        File src = f;
        if (f.equals(saveFile(this))) {
            File copy = new File(f.getParentFile(), "loading.fsg");
            try {
                java.nio.file.Files.copy(f.toPath(), copy.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                src = copy;
            } catch (java.io.IOException e) {
                news.add("Could not load: " + e.getMessage());
                return;
            }
        }
        startActivity(new android.content.Intent(this, GameActivity.class)
                      .putExtra(EXTRA_LOAD, src.getPath()));
        finish();
    }

    private void confirmQuit() {
        new android.app.AlertDialog.Builder(this)
            .setMessage("Quit to the start screen? The game is saved, and "
                        + "Continue there carries on from it.")
            .setPositiveButton("Quit", (d, w) -> {
                    if (session.isMyTurn()) {
                        session.saveTo(saveFile(this), err -> runOnUiThread(this::finish));
                    } else {
                        finish();
                    }
                })
            .setNegativeButton("Cancel", null)
            .show();
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
            news.add(text);
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
            if (unit.canCarryGoods()) {
                labels.add(unit.getTradeRoute() == null ? "Trade route..."
                           : "Trade route: " + unit.getTradeRoute().getName());
                actions.add(() -> chooseRoute(unit));
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
            if (unit.getDestination() != null || unit.getTradeRoute() != null
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
