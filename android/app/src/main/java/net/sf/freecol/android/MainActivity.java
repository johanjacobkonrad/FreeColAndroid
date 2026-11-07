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
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import net.sf.freecol.android.map.Art;


/**
 * The start screen: FreeCol's landing engraving on the left; on the
 * right, Continue (with the saved nation's coat of arms), New game,
 * High scores and Settings, and the Phase 1 engine test tucked away as
 * a small link.
 *
 * New game covers the whole screen: the nations as cards, and a bar
 * with difficulty, map size, land and rivals (tap to choose) and Start.
 */
public final class MainActivity extends Activity {

    private static final int TURNS = 50;

    private static final int BG = 0xFF17120C, PANEL = 0xFF241B12,
        CARD = 0xFF33281B, CARD_ON = 0xFFE3C27A, INK = 0xFFF3E6C8,
        INK_DIM = 0xFFB9A37C, ACCENT = 0xFFC8963E;

    private AndroidArt art;
    private float dp;
    private FrameLayout right;
    /** Over everything: the new game page (it needs the whole width). */
    private FrameLayout full;
    private final NewGameOptions options = new NewGameOptions();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        dp = getResources().getDisplayMetrics().density;
        try {
            art = new AndroidArt(getAssets());
        } catch (Exception e) {
            art = null; // The screen still works, without pictures
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(BG);

        // Left: the engraving, with the title over it.
        FrameLayout picture = new FrameLayout(this);
        ImageView engraving = new ImageView(this);
        engraving.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap landing = bitmap("image.flavor.event.firstLanding");
        if (landing != null) engraving.setImageBitmap(landing);
        picture.addView(engraving, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        // Darken towards the bottom so the title stands out.
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { 0x00000000, 0x33000000, 0xDD17120C }));
        picture.addView(shade, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("FreeCol", 44, INK, true);
        title.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        title.setShadowLayer(6 * dp, 0, 2 * dp, Color.BLACK);
        titles.addView(title);
        // Hidden: hold the title for test games.
        title.setOnLongClickListener(v -> {
                showTestGames();
                return true;
            });
        TextView sub = text("The colonization of the New World", 15, INK_DIM, false);
        sub.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
        sub.setShadowLayer(4 * dp, 0, 1 * dp, Color.BLACK);
        titles.addView(sub);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.START);
        tl.setMargins(px(28), 0, 0, px(24));
        picture.addView(titles, tl);
        root.addView(picture, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.MATCH_PARENT, 0.5f));

        // Right: menus.
        right = new FrameLayout(this);
        right.setBackgroundColor(PANEL);
        root.addView(right, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.MATCH_PARENT, 0.5f));

        full = new FrameLayout(this);
        full.setBackground(Theme.wood());
        full.setClickable(true);
        full.setVisibility(View.GONE);
        FrameLayout frame = new FrameLayout(this);
        frame.addView(root, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        frame.addView(full, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Keep the menus clear of system bars and cutouts; the picture
        // can go under them.
        frame.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            right.setPadding(0, bars.top, bars.right, bars.bottom);
            full.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
        });
        setContentView(frame);
        showMenu();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (full.getVisibility() != View.VISIBLE) showMenu(); // The saved game may have changed
        Audio.get(this).startMusic();
    }

    @Override
    protected void onPause() {
        Audio.get(this).stopMusic();
        super.onPause();
    }


    // The main menu

    private void showMenu() {
        LinearLayout menu = column();
        menu.setGravity(Gravity.CENTER_VERTICAL);
        menu.setPadding(px(32), px(24), px(32), px(16));

        File save = GameActivity.saveFile(this);
        String about = GameSession.describeSave(save);
        if (about != null) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(rounded(CARD, ACCENT));
            card.setPadding(px(14), px(12), px(14), px(12));
            String nation = GameSession.saveNation(save);
            Bitmap flag = (nation == null) ? null
                : bitmap("image.miscicon." + nation);
            if (flag != null) {
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(flag);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                card.addView(iv, new LinearLayout.LayoutParams(px(48), px(52)));
            }
            LinearLayout words = column();
            words.setPadding(px(14), 0, 0, 0);
            words.addView(text("Continue", 20, INK, true));
            words.addView(text(about, 14, INK_DIM, false));
            card.addView(words);
            card.setOnClickListener(v -> startActivity(
                new Intent(this, GameActivity.class)
                    .putExtra(GameActivity.EXTRA_CONTINUE, true)));
            menu.addView(card, wide(0, 0, 0, px(14)));
        }

        TextView newGame = Theme.button(this, "New game", null, Theme.Kind.GOLD, false);
        newGame.setTextSize(22);
        newGame.setOnClickListener(v -> showSetup());
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, px(64));
        nlp.setMargins(0, 0, 0, px(10));
        menu.addView(newGame, nlp);
        LinearLayout trio = new LinearLayout(this);
        trio.setOrientation(LinearLayout.HORIZONTAL);
        Object[][] small = {
            { "Load game", Theme.Icon.LOAD, (Runnable)this::showLoad },
            { "High scores", Theme.Icon.TROPHY, (Runnable)this::showHighScores },
            { "Settings", Theme.Icon.GEAR, (Runnable)this::showSettings } };
        for (int i = 0; i < small.length; i++) {
            final Runnable r = (Runnable)small[i][2];
            TextView b = Theme.button(this, (String)small[i][0], (Theme.Icon)small[i][1],
                                      Theme.Kind.PLANK, true);
            b.setTextSize(12);
            b.setOnClickListener(v -> r.run());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, px(62), 1f);
            lp.setMargins(i == 0 ? 0 : px(5), 0, i == small.length - 1 ? 0 : px(5), 0);
            trio.addView(b, lp);
        }
        menu.addView(trio, wide(0, 0, 0, px(8)));
        if (about != null) {
            menu.addView(text("A new game replaces your saved game once it starts.",
                              12, INK_DIM, false));
        }

        LinearLayout box = column();
        box.addView(menu, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView test = text("Engine test", 12, INK_DIM, false);
        test.setPadding(px(32), px(8), px(32), px(12));
        test.setOnClickListener(v -> showEngineTest());
        box.addView(test);
        setRight(box);
    }


    // Load game

    /** The saved games: your game's autosave and the save slots. */
    private void showLoad() {
        File auto = GameActivity.saveFile(this);
        List<File> files = new ArrayList<>();
        List<String> names = new ArrayList<>();
        files.add(auto);
        names.add("Autosave");
        for (int n = 1; n <= 3; n++) {
            files.add(new File(auto.getParentFile(), "slot" + n + ".fsg"));
            names.add("Slot " + n);
        }
        final List<File> found = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            File f = files.get(i);
            String about = GameSession.describeSave(f);
            if (about == null) continue;
            found.add(f);
            labels.add(names.get(i) + ": " + about + "\n"
                + android.text.format.DateFormat.getMediumDateFormat(this)
                    .format(new java.util.Date(f.lastModified())) + ", "
                + android.text.format.DateFormat.getTimeFormat(this)
                    .format(new java.util.Date(f.lastModified())));
        }
        if (found.isEmpty()) {
            new android.app.AlertDialog.Builder(this)
                .setTitle("Load game")
                .setMessage("There are no saved games yet. Games save themselves "
                            + "each turn; in a game, Menu \u2192 Save keeps a copy in a slot.")
                .setPositiveButton("OK", null).show();
            return;
        }
        new android.app.AlertDialog.Builder(this)
            .setTitle("Load game")
            .setItems(labels.toArray(new String[0]), (d, w) -> {
                    File f = found.get(w);
                    Intent in = new Intent(this, GameActivity.class);
                    if (f.equals(auto)) {
                        in.putExtra(GameActivity.EXTRA_CONTINUE, true);
                    } else {
                        in.putExtra(GameActivity.EXTRA_LOAD, f.getPath());
                    }
                    startActivity(in);
                })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // Settings

    private void showSettings() {
        LinearLayout page = column();
        page.setPadding(px(32), px(24), px(32), px(16));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Theme.serif(this, "Settings", 22, Theme.GOLD, true),
                     new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView back = Theme.button(this, "Back", Theme.Icon.BACK, Theme.Kind.PLANK, false);
        back.setOnClickListener(v -> showMenu());
        head.addView(back);
        page.addView(head);
        LinearLayout body = Settings.view(this, null, false);
        body.setPadding(0, px(16), 0, 0);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(body);
        page.addView(sv);
        setRight(page);
    }

    // High scores

    private void showHighScores() {
        // No "Loading" page: the menu stays until the list is ready.
        new Thread(() -> {
            List<EndGameInfo.Row> rows;
            try {
                File data = new File(getFilesDir(), "data");
                Assets.copy(getAssets(), "data", data);
                Engine.init(data);
                rows = EndGameInfo.highScores();
            } catch (Throwable t) {
                rows = new java.util.ArrayList<>();
            }
            final List<EndGameInfo.Row> r = rows;
            runOnUiThread(() -> setRight(new EndGamePanel(this, null, null, r, null,
                                                          this::showMenu)));
        }, "HighScores").start();
    }

    // Test games (hold the title)

    private void showTestGames() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Test games")
            .setItems(new String[] {
                    "Start near independence: five colonies, 70% rebels, an army, "
                    + "and a small royal army to fight" },
                (d, w) -> startTest("scenarios/independence.fsg"))
            .setNegativeButton("Cancel", null)
            .show();
    }

    /** Copy a test game out of the app and play it (saved apart from yours). */
    private void startTest(String asset) {
        java.io.File dir = new java.io.File(getFilesDir(), "saves");
        dir.mkdirs();
        java.io.File f = new java.io.File(dir, "test-start.fsg");
        try (java.io.InputStream in = getAssets().open(asset);
             java.io.OutputStream out = new java.io.FileOutputStream(f)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (java.io.IOException e) {
            new android.app.AlertDialog.Builder(this)
                .setMessage("The test game is missing: " + e.getMessage())
                .setPositiveButton("OK", null).show();
            return;
        }
        startActivity(new Intent(this, GameActivity.class)
                      .putExtra(GameActivity.EXTRA_LOAD, f.getPath())
                      .putExtra(GameActivity.EXTRA_TEST, true));
    }

    // New game setup

    private void showSetup() {
        // No "Loading" page: the menu stays until the rules are read.
        new Thread(() -> {
            List<NewGameOptions.Choice> nations;
            try {
                File data = new File(getFilesDir(), "data");
                Assets.copy(getAssets(), "data", data);
                Engine.init(data);
                nations = NewGameOptions.nations(Engine.loadSpecification(options.difficulty));
            } catch (Throwable t) {
                runOnUiThread(() -> {
                        LinearLayout err = column();
                        err.setGravity(Gravity.CENTER);
                        err.addView(text("Could not load the rules: " + t, 14, INK, false));
                        setRight(err);
                    });
                return;
            }
            final List<NewGameOptions.Choice> n = nations;
            final List<NewGameOptions.Choice> d = NewGameOptions.difficulties();
            runOnUiThread(() -> buildSetup(n, d));
        }, "Rules").start();
    }

    private void buildSetup(List<NewGameOptions.Choice> nations,
                            List<NewGameOptions.Choice> difficulties) {
        if (options.nationId == null && !nations.isEmpty()) {
            options.nationId = nations.get(0).id;
        }
        showMenu(); // Behind the page, for Back
        LinearLayout page = column();
        page.setPadding(px(20), px(14), px(20), px(12));

        // Back, and the title.
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = new ImageView(this);
        back.setImageDrawable(new Theme.IconDrawable(Theme.Icon.BACK, Theme.GOLD, px(22)));
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setBackground(Theme.plank(this));
        back.setContentDescription("Back");
        back.setOnClickListener(v -> closeSetup());
        head.addView(back, new LinearLayout.LayoutParams(px(44), px(44)));
        TextView title = Theme.serif(this, "Choose your nation", 22, Theme.GOLD, true);
        title.setPadding(px(14), 0, 0, 0);
        head.addView(title);
        page.addView(head, wide(0, 0, 0, px(10)));

        // The nations, as cards.
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        final List<View> all = new ArrayList<>();
        for (int i = 0; i < nations.size(); i++) {
            final NewGameOptions.Choice c = nations.get(i);
            View card = nationCard(c, nations.size() > 4);
            card.setOnClickListener(v -> {
                    options.nationId = c.id;
                    for (View o : all) markNation(o, ((NewGameOptions.Choice)o.getTag()).id.equals(c.id));
                });
            all.add(card);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            int gap = (nations.size() > 4) ? px(3) : px(5);
            lp.setMargins(i == 0 ? 0 : gap, 0, i == nations.size() - 1 ? 0 : gap, 0);
            cards.addView(card, lp);
        }
        for (View o : all) markNation(o, ((NewGameOptions.Choice)o.getTag()).id.equals(options.nationId));
        page.addView(cards, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // The world: difficulty, map size and land, then Start.
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(Theme.plank(this));
        int bp = px(6);
        bar.setPadding(bp, bp, bp, bp);
        List<String[]> diff = new ArrayList<>();
        for (NewGameOptions.Choice c : difficulties) diff.add(new String[] { c.id, c.label });
        bar.addView(picker("Difficulty", diff, () -> options.difficulty,
                           id -> options.difficulty = id, null), pickerParams());
        List<String[]> sizes = new ArrayList<>();
        for (Object[] m : NewGameOptions.MAP_SIZES) {
            sizes.add(new String[] { (String)m[0], m[1] + "  (" + m[2] + " \u00d7 " + m[3] + ")" });
        }
        bar.addView(picker("Map size", sizes, () -> options.mapSize,
                           id -> options.mapSize = id,
                           "bigger maps mean longer waits each turn"),
                    pickerParams());
        List<String[]> land = new ArrayList<>();
        for (Object[] l : NewGameOptions.LAND) land.add(new String[] { (String)l[0], (String)l[1] });
        bar.addView(picker("Land", land, () -> options.land, id -> options.land = id, null),
                    pickerParams());
        List<String[]> rivals = new ArrayList<>();
        for (int r = NewGameOptions.MIN_RIVALS; r <= NewGameOptions.MAX_RIVALS; r++) {
            rivals.add(new String[] { Integer.toString(r), r + ((r == 1) ? " nation" : " nations") });
        }
        bar.addView(picker("Rivals", rivals, () -> Integer.toString(options.rivals),
                           id -> options.rivals = Integer.parseInt(id),
                           "more rivals mean longer waits each turn"),
                    pickerParams());
        TextView start = Theme.button(this, "Start", null, Theme.Kind.GOLD, false);
        start.setTextSize(17);
        start.setOnClickListener(v -> startActivity(
            new Intent(this, GameActivity.class)
                .putExtra(GameActivity.EXTRA_NATION, options.nationId)
                .putExtra(GameActivity.EXTRA_DIFFICULTY, options.difficulty)
                .putExtra(GameActivity.EXTRA_MAP_SIZE, options.mapSize)
                .putExtra(GameActivity.EXTRA_LAND, options.land)
                .putExtra(GameActivity.EXTRA_RIVALS, options.rivals)));
        bar.addView(start, new LinearLayout.LayoutParams(0, px(50), 1.3f));
        page.addView(bar, wide(0, px(12), 0, 0));

        full.removeAllViews();
        full.addView(page, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        full.setVisibility(View.VISIBLE);
    }

    private void closeSetup() {
        full.setVisibility(View.GONE);
        full.removeAllViews();
        showMenu();
    }

    @Override
    public void onBackPressed() {
        if (full.getVisibility() == View.VISIBLE) {
            closeSetup();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * A nation's card: coat of arms, name, advantage, starting units.
     * Compact (smaller arms and type) when six share the row.
     */
    private View nationCard(NewGameOptions.Choice c, boolean compact) {
        LinearLayout card = column();
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        if (compact) {
            card.setPadding(px(6), px(8), px(6), px(6));
        } else {
            card.setPadding(px(10), px(12), px(10), px(10));
        }
        card.setTag(c);
        Bitmap flag = bitmap("image.miscicon." + c.id);
        if (flag != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(flag);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(iv, compact ? new LinearLayout.LayoutParams(px(40), px(45))
                                     : new LinearLayout.LayoutParams(px(50), px(56)));
        }
        TextView name = Theme.serif(this, c.label, compact ? 15 : 17, INK, true);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, px(4), 0, 0);
        card.addView(name, wide(0, 0, 0, 0));
        TextView kind = Theme.heading(this, c.kind, Theme.GOLD);
        kind.setGravity(Gravity.CENTER);
        kind.setPadding(0, px(2), 0, px(4));
        card.addView(kind, wide(0, 0, 0, 0));
        TextView about = text(c.about + (c.units.isEmpty() ? "" : "\n" + c.units),
                              compact ? 11 : 12, Theme.DIM, false);
        about.setGravity(Gravity.CENTER);
        about.setLineSpacing(0, 1.1f);
        card.addView(about, wide(0, 0, 0, 0));
        return card;
    }

    private void markNation(View v, boolean on) {
        LinearLayout card = (LinearLayout)v;
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(8 * dp);
        if (on) {
            g.setColor(Theme.PAPER);
            g.setStroke(px(2), Theme.GOLD);
        } else {
            g.setColor(Theme.WOOD_TOP);
            g.setStroke(Math.max(1, (int)dp), Theme.EDGE);
        }
        card.setBackground(g);
        // Name, advantage, text (the last three): dark on the chosen
        // card's parchment.
        int n = card.getChildCount();
        if (n >= 3) {
            ((TextView)card.getChildAt(n - 3)).setTextColor(on ? Theme.PAPER_INK : INK);
            ((TextView)card.getChildAt(n - 2)).setTextColor(on ? 0xFF7A5A2C : Theme.GOLD);
            ((TextView)card.getChildAt(n - 1)).setTextColor(on ? 0xFF4A3620 : Theme.DIM);
        }
    }

    private LinearLayout.LayoutParams pickerParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, px(50), 1f);
        lp.setMargins(0, 0, px(6), 0);
        return lp;
    }

    /** One of the world settings: its name and value; tap to choose. */
    private View picker(final String label, final List<String[]> choices,
                        final java.util.function.Supplier<String> current,
                        final java.util.function.Consumer<String> onPick, final String note) {
        LinearLayout cell = column();
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setPadding(px(12), 0, px(8), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x1AE3C27A);
        g.setCornerRadius(5 * dp);
        cell.setBackground(g);
        TextView l = Theme.heading(this, label, Theme.DIM);
        cell.addView(l);
        final TextView value = text("", 14, INK, true);
        cell.addView(value);
        final Runnable show = () -> {
            for (String[] c : choices) {
                if (c[0].equals(current.get())) value.setText(c[1].split("  ")[0] + " \u25be");
            }
        };
        show.run();
        cell.setOnClickListener(v -> {
                String[] names = new String[choices.size()];
                int at = 0;
                for (int i = 0; i < names.length; i++) {
                    names[i] = choices.get(i)[1];
                    if (choices.get(i)[0].equals(current.get())) at = i;
                }
                android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this)
                    .setTitle((note == null) ? label : label + " \u00b7 " + note)
                    .setSingleChoiceItems(names, at, (d, w) -> {
                            onPick.accept(choices.get(w)[0]);
                            show.run();
                            d.dismiss();
                        })
                    .setNegativeButton("Cancel", null);
                b.show(); // (A message would hide the list: the note is in the title.)
            });
        return cell;
    }

    /** A row of mutually exclusive choices. */
    private View segments(List<String[]> choices, String selected,
                          java.util.function.Consumer<String> onPick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final List<TextView> views = new ArrayList<>();
        for (String[] c : choices) {
            TextView t = text(c[1], 13, INK, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(px(10), px(9), px(10), px(9));
            t.setTag(c[0]);
            t.setOnClickListener(v -> {
                    onPick.accept(c[0]);
                    for (TextView o : views) markSegment(o, o.getTag().equals(c[0]));
                });
            views.add(t);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(0, 0, px(6), 0);
            row.addView(t, lp);
            markSegment(t, c[0].equals(selected));
        }
        return row;
    }

    private void markSegment(TextView t, boolean on) {
        t.setBackground(rounded(on ? CARD_ON : CARD, on ? CARD_ON : CARD));
        t.setTextColor(on ? 0xFF241B12 : INK);
        t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }


    // The Phase 1 engine test

    private void showEngineTest() {
        LinearLayout page = column();
        page.setPadding(px(20), px(16), px(20), px(12));
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView back = button("Back", false);
        back.setOnClickListener(v -> showMenu());
        top.addView(back);
        View gap = new View(this);
        top.addView(gap, new LinearLayout.LayoutParams(px(10), 1));
        final TextView run = button("Run " + TURNS + " AI turns", true);
        top.addView(run);
        page.addView(top);
        final TextView output = text("The engine test lets the AI players play a "
            + "game on this phone, without the map.\n", 12, INK_DIM, false);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        final ScrollView scroll = new ScrollView(this);
        scroll.addView(output);
        page.addView(scroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        run.setOnClickListener(v -> {
                run.setEnabled(false);
                run.setAlpha(0.5f);
                new Thread(() -> {
                    java.util.function.Consumer<String> log = line -> runOnUiThread(() -> {
                            output.append(line + "\n");
                            scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
                        });
                    try {
                        File data = new File(getFilesDir(), "data");
                        log.accept("Unpacking game data...");
                        Assets.copy(getAssets(), "data", data);
                        AutoPlay.Result r = AutoPlay.run(data, TURNS,
                            20 * 60 * 1000L, log);
                        log.accept(r.toString());
                    } catch (Throwable t) {
                        log.accept("FAILED: " + t);
                    }
                    runOnUiThread(() -> {
                            run.setEnabled(true);
                            run.setAlpha(1f);
                        });
                }, "AutoPlay").start();
            });
        setRight(page);
    }


    // Helpers

    private void setRight(View v) {
        right.removeAllViews();
        right.addView(v, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private Bitmap bitmap(String key) {
        if (art == null) return null;
        Art.Img img = art.get(key);
        return (img == null) ? null : (Bitmap)img.handle;
    }

    private TextView heading(String s) {
        TextView t = text(s.toUpperCase(java.util.Locale.UK), 11, ACCENT, true);
        t.setLetterSpacing(0.12f);
        t.setPadding(0, px(14), 0, px(6));
        return t;
    }

    private TextView button(String label, boolean primary) {
        TextView b = text(label, 16, primary ? 0xFF241B12 : INK, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(18), px(13), px(18), px(13));
        b.setBackground(rounded(primary ? CARD_ON : CARD, primary ? CARD_ON : INK_DIM));
        return b;
    }

    private GradientDrawable rounded(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(8 * dp);
        g.setStroke(Math.max(1, (int)dp), stroke);
        return g;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout.LayoutParams wide(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    private TextView text(String s, int sp, int colour, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(colour);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int px(int dps) {
        return (int)(dps * dp);
    }
}
