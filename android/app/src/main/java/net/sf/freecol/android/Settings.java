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
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;


/**
 * The player's settings, kept on the phone: music and sound effect
 * volume, and how long the map shows a unit after it moves.  Also the
 * view that changes them, used by the start screen and the game menu.
 */
final class Settings {

    private static final String FILE = "settings", MUSIC = "music", EFFECTS = "effects",
        SPEED = "speed";

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

    /** How long the map shows a unit after it moves, in milliseconds. */
    static int movePause(Context c) {
        String s = speed(c);
        for (Object[] sp : SPEEDS) if (sp[0].equals(s)) return (Integer)sp[2];
        return 900;
    }

    /**
     * The settings, to change in place.  Changes are kept at once, and
     * the volume changes are heard at once.
     *
     * @param c The context.
     * @param onSpeed Told the new move pause (or null).
     */
    static LinearLayout view(final Context c, final java.util.function.IntConsumer onSpeed) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(slider(c, "Music", music(c), v -> {
                prefs(c).edit().putInt(MUSIC, v).apply();
                Audio.get(c).musicVolumeChanged();
            }));
        box.addView(slider(c, "Sound effects", effects(c), v -> {
                prefs(c).edit().putInt(EFFECTS, v).apply();
            }));
        TextView h = Theme.heading(c, "Move speed", Theme.GOLD);
        h.setPadding(0, Theme.px(c, 14), 0, Theme.px(c, 2));
        box.addView(h);
        box.addView(Theme.text(c, "How long the map shows a unit after it moves, "
            + "before the next one is selected.", 12, Theme.DIM, false));
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Theme.px(c, 6), 0, 0);
        final List<TextView> all = new ArrayList<>();
        for (Object[] sp : SPEEDS) {
            final String id = (String)sp[0];
            TextView t = Theme.text(c, (String)sp[1], 14, Theme.INK, true);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(Theme.px(c, 44));
            t.setTag(id);
            t.setOnClickListener(v -> {
                    prefs(c).edit().putString(SPEED, id).apply();
                    for (TextView o : all) mark(c, o, o.getTag().equals(id));
                    if (onSpeed != null) onSpeed.accept(movePause(c));
                });
            all.add(t);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(0, 0, Theme.px(c, 6), 0);
            row.addView(t, lp);
            mark(c, t, id.equals(speed(c)));
        }
        box.addView(row);
        // The music's licence (CC-BY 4.0) asks for its authors' names.
        TextView credits = Theme.text(c, "Music by Alexander Zhelanov and Stian Grenborgen, "
            + "under CC-BY 4.0.", 11, Theme.DIM, false);
        credits.setPadding(0, Theme.px(c, 14), 0, 0);
        box.addView(credits);
        return box;
    }

    private static void mark(Context c, TextView t, boolean on) {
        if (on) {
            t.setBackground(Theme.gold(c));
            t.setTextColor(Theme.PAPER_INK);
        } else {
            t.setBackground(Theme.plank(c));
            t.setTextColor(Theme.INK);
        }
    }

    private static LinearLayout slider(Context c, String label, int value,
                                       java.util.function.IntConsumer onChange) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Theme.px(c, 10), 0, 0);
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView h = Theme.heading(c, label, Theme.GOLD);
        top.addView(h, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final TextView shown = Theme.text(c, describe(value), 13, Theme.INK, true);
        top.addView(shown);
        box.addView(top);
        SeekBar bar = new SeekBar(c);
        bar.setMax(100);
        bar.setProgress(value);
        bar.setProgressTintList(ColorStateList.valueOf(Theme.GOLD));
        bar.setThumbTintList(ColorStateList.valueOf(Theme.GOLD_LIGHT));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(Theme.EDGE));
        bar.setMinimumHeight(Theme.px(c, 36));
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
