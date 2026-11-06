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
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import net.sf.freecol.android.map.Art;


/**
 * The start screen: FreeCol's landing engraving on the left; on the
 * right, Continue (with the saved nation's coat of arms), New game,
 * and the Phase 1 engine test tucked away as a small link.
 *
 * New game replaces the right-hand side with the game setup: nation,
 * difficulty, map size and land, then Start.
 */
public final class MainActivity extends Activity {

    private static final int TURNS = 50;

    private static final int BG = 0xFF17120C, PANEL = 0xFF241B12,
        CARD = 0xFF33281B, CARD_ON = 0xFFE3C27A, INK = 0xFFF3E6C8,
        INK_DIM = 0xFFB9A37C, ACCENT = 0xFFC8963E;

    private AndroidArt art;
    private float dp;
    private FrameLayout right;
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

        // Keep the menus clear of system bars and cutouts; the picture
        // can go under them.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            right.setPadding(0, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
        });
        setContentView(root);
        showMenu();
    }

    @Override
    protected void onResume() {
        super.onResume();
        showMenu(); // The saved game may have changed
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

        TextView newGame = button("New game", true);
        newGame.setOnClickListener(v -> showSetup());
        menu.addView(newGame, wide(0, 0, 0, px(8)));
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


    // Test games (hold the title)

    private void showTestGames() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Test games")
            .setItems(new String[] {
                    "Start near independence: three colonies, 70% rebels, an army, "
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
        LinearLayout wait = column();
        wait.setGravity(Gravity.CENTER);
        wait.addView(text("Loading the rules...", 15, INK_DIM, false));
        setRight(wait);
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
        LinearLayout page = column();
        page.setPadding(px(24), px(16), px(24), px(8));
        page.addView(text("New game", 22, INK, true));

        // Nation cards.
        page.addView(heading("Nation"));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final List<View> cards = new ArrayList<>();
        final TextView advantage = text("", 12, INK_DIM, false);
        for (NewGameOptions.Choice c : nations) {
            LinearLayout card = column();
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(px(8), px(8), px(8), px(6));
            Bitmap flag = bitmap("image.miscicon." + c.id);
            if (flag != null) {
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(flag);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                card.addView(iv, new LinearLayout.LayoutParams(px(40), px(44)));
            }
            card.addView(text(c.label, 13, INK, true));
            card.setTag(c);
            card.setOnClickListener(v -> {
                    options.nationId = c.id;
                    markCards(cards, c.id);
                    advantage.setText(c.detail);
                });
            cards.add(card);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(84),
                LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, px(8), 0);
            row.addView(card, lp);
        }
        HorizontalScrollView rs = new HorizontalScrollView(this);
        rs.setHorizontalScrollBarEnabled(false);
        rs.addView(row);
        page.addView(rs);
        markCards(cards, options.nationId);
        for (NewGameOptions.Choice c : nations) {
            if (c.id.equals(options.nationId)) advantage.setText(c.detail);
        }
        advantage.setPadding(0, px(4), 0, 0);
        page.addView(advantage);

        // Difficulty, map size, land.
        page.addView(heading("Difficulty"));
        List<String[]> diff = new ArrayList<>();
        for (NewGameOptions.Choice c : difficulties) diff.add(new String[] { c.id, c.label });
        page.addView(segments(diff, options.difficulty, id -> options.difficulty = id));

        page.addView(heading("Map size"));
        List<String[]> sizes = new ArrayList<>();
        for (Object[] m : NewGameOptions.MAP_SIZES) {
            sizes.add(new String[] { (String)m[0], m[1] + "  " + m[2] + "×" + m[3] });
        }
        page.addView(segments(sizes, options.mapSize, id -> options.mapSize = id));
        TextView sizeNote = text("Bigger maps take the computer players longer each turn.",
                                 11, INK_DIM, false);
        page.addView(sizeNote);

        page.addView(heading("Land"));
        List<String[]> land = new ArrayList<>();
        for (Object[] l : NewGameOptions.LAND) land.add(new String[] { (String)l[0], (String)l[1] });
        page.addView(segments(land, options.land, id -> options.land = id));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(page);

        // Back / Start, always visible.
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(px(24), px(6), px(24), px(12));
        TextView back = button("Back", false);
        back.setOnClickListener(v -> showMenu());
        bar.addView(back, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        View gap = new View(this);
        bar.addView(gap, new LinearLayout.LayoutParams(px(12), 1));
        TextView start = button("Start", true);
        start.setOnClickListener(v -> startActivity(
            new Intent(this, GameActivity.class)
                .putExtra(GameActivity.EXTRA_NATION, options.nationId)
                .putExtra(GameActivity.EXTRA_DIFFICULTY, options.difficulty)
                .putExtra(GameActivity.EXTRA_MAP_SIZE, options.mapSize)
                .putExtra(GameActivity.EXTRA_LAND, options.land)));
        bar.addView(start, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 2f));

        LinearLayout box = column();
        box.addView(scroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        box.addView(bar);
        setRight(box);
    }

    private void markCards(List<View> cards, String selected) {
        for (View v : cards) {
            NewGameOptions.Choice c = (NewGameOptions.Choice)v.getTag();
            boolean on = c.id.equals(selected);
            v.setBackground(rounded(on ? 0xFF4A3920 : CARD, on ? CARD_ON : CARD));
        }
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
