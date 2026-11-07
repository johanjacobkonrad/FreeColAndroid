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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;


/**
 * The player's settings, kept on the phone: music and sound effect
 * volume, naming new regions, how long the map shows a unit after it
 * moves, and animations.  Also the
 * view that changes them, used by the start screen and the game menu.
 */
final class Settings {

    private static final String FILE = "settings", MUSIC = "music", EFFECTS = "effects",
        SPEED = "speed", ANIMATE = "animate", NAME_REGIONS = "nameRegions";

    /** Move speeds: id, name, pause in milliseconds. */
    static final Object[][] SPEEDS = {
        { "fast", "Fast", 300 }, { "normal", "Normal", 900 }, { "slow", "Slow", 1600 } };

    private Settings() {} // Static only

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Music volume, 0 (off) to 100. */
    static int music(Context c) {
        return prefs(c).getInt(MUSIC, 60);
    }

    /** Sound effects volume, 0 (off) to 100. */
    static int effects(Context c) {
        return prefs(c).getInt(EFFECTS, 80);
    }

    static String speed(Context c) {
        return prefs(c).getString(SPEED, "normal");
    }

    /** Units slide between tiles and attacks are shown. */
    static boolean animate(Context c) {
        return prefs(c).getBoolean(ANIMATE, true);
    }

    /** Ask the player to name the rivers, lakes and so on they find. */
    static boolean nameRegions(Context c) {
        return prefs(c).getBoolean(NAME_REGIONS, true);
    }

    /** How long a unit takes to slide one tile (0: no animations). */
    static int slideMillis(Context c) {
        if (!animate(c)) return 0;
        String s = speed(c);
        return "fast".equals(s) ? 300 : "slow".equals(s) ? 850 : 500;
    }

    /** How long the map shows a unit after it moves, in milliseconds. */
    static int movePause(Context c) {
        String s = speed(c);
        for (Object[] sp : SPEEDS) if (sp[0].equals(s)) return (Integer)sp[2];
        return 900;
    }

    /** Colours for wood (the start screen) or parchment (a dialog). */
    private static final class Look {
        final int heading, ink, dim, track, fill, thumb;
        final boolean paper;

        Look(boolean paper) {
            this.paper = paper;
            heading = paper ? 0xFF7A5A2C : Theme.GOLD;
            ink = paper ? Theme.PAPER_INK : Theme.INK;
            dim = paper ? Theme.PAPER_DIM : Theme.DIM;
            track = paper ? 0x557A5A2C : Theme.EDGE;
            fill = paper ? 0xFF7A5A2C : Theme.GOLD;
            thumb = paper ? 0xFF5A4020 : Theme.GOLD_LIGHT;
        }
    }

    /**
     * The settings, to change in place, in two columns: sound and
     * naming on the left, the map on the right.  Changes are kept at once, and the
     * volume changes are heard at once.
     *
     * @param c The context.
     * @param onSpeed Told the new move pause (or null).
     * @param paper True on parchment (a dialog), false on wood.
     */
    static LinearLayout view(final Context c, final java.util.function.IntConsumer onSpeed,
                             boolean paper) {
        final Look look = new Look(paper);
        LinearLayout cols = new LinearLayout(c);
        cols.setOrientation(LinearLayout.HORIZONTAL);

        // Sound.
        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(slider(c, look, "Music", music(c), v -> {
                prefs(c).edit().putInt(MUSIC, v).apply();
                Audio.get(c).musicVolumeChanged();
            }));
        // The music's licence (CC-BY 4.0) asks for its authors' names.
        left.addView(Theme.text(c, "Music by Alexander Zhelanov and Stian Grenborgen "
            + "(CC-BY 4.0)", 11, look.dim, false));
        View gap = new View(c);
        left.addView(gap, new LinearLayout.LayoutParams(1, Theme.px(c, 10)));
        left.addView(slider(c, look, "Sound effects", effects(c), v -> {
                prefs(c).edit().putInt(EFFECTS, v).apply();
            }));
        TextView nh = Theme.heading(c, "New places", look.heading);
        nh.setPadding(0, Theme.px(c, 10), 0, Theme.px(c, 2));
        left.addView(nh);
        TextView nw = Theme.text(c, "Name the rivers, lakes, mountains and seas you find, "
                                 + "or keep the names they come with.", 12, look.dim, false);
        nw.setPadding(0, 0, 0, Theme.px(c, 6));
        left.addView(nw);
        List<String[]> naming = new ArrayList<>();
        naming.add(new String[] { "ask", "Ask me" });
        naming.add(new String[] { "auto", "Automatic" });
        left.addView(choices(c, look, naming, nameRegions(c) ? "ask" : "auto", id -> {
                prefs(c).edit().putBoolean(NAME_REGIONS, "ask".equals(id)).apply();
            }));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        llp.setMargins(0, 0, Theme.px(c, 20), 0);
        cols.addView(left, llp);

        // The game.
        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        right.addView(Theme.heading(c, "Move speed", look.heading));
        TextView why = Theme.text(c, "How long the map shows a unit after it moves.",
                                  12, look.dim, false);
        why.setPadding(0, Theme.px(c, 2), 0, Theme.px(c, 6));
        right.addView(why);
        List<String[]> speeds = new ArrayList<>();
        for (Object[] sp : SPEEDS) speeds.add(new String[] { (String)sp[0], (String)sp[1] });
        right.addView(choices(c, look, speeds, speed(c), id -> {
                prefs(c).edit().putString(SPEED, id).apply();
                if (onSpeed != null) onSpeed.accept(movePause(c));
            }));
        TextView ah = Theme.heading(c, "Animations", look.heading);
        ah.setPadding(0, Theme.px(c, 12), 0, Theme.px(c, 2));
        right.addView(ah);
        TextView aw = Theme.text(c, "Units slide from tile to tile, and attacks are shown.",
                                 12, look.dim, false);
        aw.setPadding(0, 0, 0, Theme.px(c, 6));
        right.addView(aw);
        List<String[]> onOff = new ArrayList<>();
        onOff.add(new String[] { "on", "On" });
        onOff.add(new String[] { "off", "Off" });
        right.addView(choices(c, look, onOff, animate(c) ? "on" : "off", id -> {
                prefs(c).edit().putBoolean(ANIMATE, "on".equals(id)).apply();
                if (onSpeed != null) onSpeed.accept(movePause(c));
            }));
        cols.addView(right, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return cols;
    }

    /** A row of choices, one picked (gold). */
    private static LinearLayout choices(Context c, Look look, List<String[]> items,
                                        String picked, java.util.function.Consumer<String> onPick) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final List<TextView> all = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            final String id = items.get(i)[0];
            TextView t = Theme.text(c, items.get(i)[1], 14, look.ink, true);
            t.setGravity(Gravity.CENTER);
            t.setTag(id);
            t.setOnClickListener(v -> {
                    for (TextView o : all) mark(c, look, o, o.getTag().equals(id));
                    onPick.accept(id);
                });
            all.add(t);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Theme.px(c, 42), 1f);
            lp.setMargins(i == 0 ? 0 : Theme.px(c, 3), 0, i == items.size() - 1 ? 0 : Theme.px(c, 3), 0);
            row.addView(t, lp);
            mark(c, look, t, id.equals(picked));
        }
        return row;
    }

    private static void mark(Context c, Look look, TextView t, boolean on) {
        if (on) {
            t.setBackground(Theme.gold(c));
            t.setTextColor(Theme.PAPER_INK);
        } else {
            t.setBackground(look.paper ? Theme.cell(c, 0xFFF4EACF) : Theme.plank(c));
            t.setTextColor(look.ink);
        }
    }

    private static LinearLayout slider(Context c, Look look, String label, int value,
                                       java.util.function.IntConsumer onChange) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.addView(Theme.heading(c, label, look.heading),
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final TextView shown = Theme.text(c, describe(value), 13, look.ink, true);
        top.addView(shown);
        box.addView(top);
        SeekBar bar = new SeekBar(c);
        bar.setMax(100);
        bar.setProgress(value);
        bar.setProgressTintList(ColorStateList.valueOf(look.fill));
        bar.setThumbTintList(ColorStateList.valueOf(look.thumb));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(look.track));
        bar.setPadding(Theme.px(c, 10), 0, Theme.px(c, 10), 0);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int v, boolean user) {
                    shown.setText(describe(v));
                    if (user) onChange.accept(v);
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
        box.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                                                       Theme.px(c, 40)));
        return box;
    }

    private static String describe(int v) {
        return (v == 0) ? "Off" : v + "%";
    }
}
