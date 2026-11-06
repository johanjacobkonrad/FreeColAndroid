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

import android.content.Context;
import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import net.sf.freecol.android.map.Art;


/**
 * The end of a game: how it ended, the final score and what earned it
 * on the left; the high scores, with this game's place, on the right.
 * Also used by the start screen for the high scores alone.
 */
final class EndGamePanel extends LinearLayout {

    private final Art art;

    /**
     * @param art The game art, for the victory picture (may be null).
     * @param info How the game ended, or null for the high scores alone.
     * @param scores The high scores (info's, or loaded for the start screen).
     * @param keepPlaying Continue playing (shown if not null).
     * @param leave Back to the start screen.
     */
    EndGamePanel(Context c, Art art, EndGameInfo info, List<EndGameInfo.Row> scores,
                 Runnable keepPlaying, Runnable leave) {
        super(c);
        this.art = art;
        setOrientation(HORIZONTAL);
        setBackground(Theme.wood());
        setClickable(true);
        int pad = Theme.px(c, 10);
        setPadding(pad, pad, pad, pad);

        if (info != null) {
            LinearLayout left = new LinearLayout(c);
            left.setOrientation(VERTICAL);
            Bitmap pic = (info.kind == EndGameInfo.Kind.WON) ? image("image.flavor.Victory") : null;
            if (pic != null) {
                ImageView iv = new ImageView(c);
                iv.setImageBitmap(pic);
                iv.setAdjustViewBounds(true);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                left.addView(iv, new LayoutParams(LayoutParams.MATCH_PARENT, Theme.px(c, 110)));
            }
            TextView title = Theme.serif(c, info.title, 30, Theme.GOLD, true);
            left.addView(title);
            left.addView(Theme.serif(c, info.nation + " · " + info.year, 15, Theme.INK, false));
            if (!info.text.isEmpty()) {
                TextView t = Theme.serif(c, info.text, 13, Theme.DIM, false);
                t.setPadding(0, Theme.px(c, 4), 0, 0);
                left.addView(t);
            }

            LinearLayout card = new LinearLayout(c);
            card.setOrientation(VERTICAL);
            card.setBackground(Theme.paper(c));
            card.setPadding(pad, pad, pad, pad);
            card.addView(Theme.heading(c, "Final score", Theme.EDGE));
            card.addView(Theme.serif(c, Integer.toString(info.score), 34, Theme.PAPER_INK, true));
            card.addView(Theme.text(c, (info.rank > 0)
                ? "Number " + info.rank + " in the high scores!"
                : "Not in the top ten this time.", 13, Theme.PAPER_INK, true));
            card.addView(Theme.text(c, info.colonies + " colonies · " + info.units
                + " units · " + info.fathers + " founding fathers · rebels "
                + info.rebels + "%", 12, Theme.PAPER_DIM, false));
            if (!info.scored.isEmpty()) {
                TextView h = Theme.heading(c, "What scored", Theme.EDGE);
                h.setPadding(0, Theme.px(c, 8), 0, Theme.px(c, 2));
                card.addView(h);
                for (String[] e : info.scored) {
                    LinearLayout row = new LinearLayout(c);
                    row.setOrientation(HORIZONTAL);
                    TextView what = Theme.text(c, e[0] + "  " + e[1], 11, Theme.PAPER_DIM, false);
                    row.addView(what, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
                    row.addView(Theme.text(c, e[2], 11, Theme.PAPER_INK, true));
                    card.addView(row);
                }
            }
            LayoutParams clp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            clp.setMargins(0, Theme.px(c, 8), 0, Theme.px(c, 8));
            left.addView(card, clp);

            LinearLayout buttons = new LinearLayout(c);
            buttons.setOrientation(HORIZONTAL);
            if (keepPlaying != null) {
                TextView keep = Theme.button(c, "Continue playing", Theme.Icon.NEXT,
                                             Theme.Kind.GOLD, false);
                keep.setTextSize(14);
                keep.setOnClickListener(v -> keepPlaying.run());
                LayoutParams klp = new LayoutParams(0, Theme.px(c, 46), 1f);
                klp.setMargins(0, 0, Theme.px(c, 6), 0);
                buttons.addView(keep, klp);
            }
            TextView go = Theme.button(c, "Start screen", Theme.Icon.BACK,
                (keepPlaying == null) ? Theme.Kind.GOLD : Theme.Kind.PLANK, false);
            go.setTextSize(14);
            go.setOnClickListener(v -> leave.run());
            buttons.addView(go, new LayoutParams(0, Theme.px(c, 46), 1f));
            left.addView(buttons);

            ScrollView ls = new ScrollView(c);
            ls.addView(left);
            LayoutParams llp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
            llp.setMargins(0, 0, pad, 0);
            addView(ls, llp);
        }

        // The high scores.
        LinearLayout right = new LinearLayout(c);
        right.setOrientation(VERTICAL);
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Theme.serif(c, "High scores", 20, Theme.GOLD, true),
                     new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        if (info == null) {
            TextView close = Theme.button(c, "Back", Theme.Icon.BACK, Theme.Kind.PLANK, false);
            close.setOnClickListener(v -> leave.run());
            head.addView(close);
        }
        right.addView(head);
        if (scores.isEmpty()) {
            TextView none = Theme.serif(c, "No high scores yet. Finish a game, win the war of "
                + "independence or retire, to get on the list.", 14, Theme.DIM, false);
            none.setPadding(0, pad, 0, 0);
            right.addView(none);
        }
        for (EndGameInfo.Row r : scores) right.addView(scoreRow(r), rowParams());
        ScrollView rs = new ScrollView(c);
        rs.addView(right);
        addView(rs, new LayoutParams(0, LayoutParams.MATCH_PARENT, (info == null) ? 1f : 1.1f));
    }

    private LayoutParams rowParams() {
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Theme.px(getContext(), 5), 0, 0);
        return lp;
    }

    private View scoreRow(EndGameInfo.Row r) {
        final Context c = getContext();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(r.mine ? Theme.gold(c) : Theme.paper(c));
        int p = Theme.px(c, 7);
        row.setPadding(p, p, p, p);
        TextView rank = Theme.serif(c, "#" + r.rank, 16, Theme.PAPER_INK, true);
        rank.setMinWidth(Theme.px(c, 36));
        row.addView(rank);
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(VERTICAL);
        words.addView(Theme.serif(c, r.who, 13, Theme.PAPER_INK, true));
        words.addView(Theme.text(c, r.when + (r.detail.isEmpty() ? "" : " · " + r.detail),
                                 11, r.mine ? Theme.PAPER_INK : Theme.PAPER_DIM, false));
        row.addView(words, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Theme.serif(c, Integer.toString(r.score), 18, Theme.PAPER_INK, true));
        return row;
    }

    private Bitmap image(String key) {
        if (art == null) return null;
        Art.Img img = art.get(key);
        return (img == null) ? null : (Bitmap)img.handle;
    }
}
