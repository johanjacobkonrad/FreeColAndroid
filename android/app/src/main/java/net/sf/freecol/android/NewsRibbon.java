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

import java.util.ArrayDeque;
import java.util.Deque;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;


/**
 * The news as a see-through parchment ribbon over the top left of the
 * map: the latest message, and a "+3" to tap for the ones before it.
 */
final class NewsRibbon extends LinearLayout {

    private static final int KEPT = 40, SHOWN_OPEN = 12;

    private final Deque<String> news = new ArrayDeque<>();
    private final TextView latest, more;
    private final LinearLayout older;
    private final ScrollView olderScroll;
    /** Messages that came in since the list was last opened. */
    private int unseen = 0;
    private boolean open = false;


    NewsRibbon(Context c) {
        super(c);
        setOrientation(VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xB8E9DCBC); // Parchment, see-through
        bg.setCornerRadius(6 * Theme.dp(c));
        bg.setStroke(Theme.px(c, 1), 0xB08A6A3C);
        setBackground(bg);
        setPadding(Theme.px(c, 10), Theme.px(c, 5), Theme.px(c, 8), Theme.px(c, 5));

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        latest = Theme.serif(c, "", 12.5f, Theme.PAPER_INK, true);
        latest.setMaxLines(3);
        row.addView(latest, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        more = Theme.text(c, "", 11, 0xFF6B4E24, true);
        more.setPadding(Theme.px(c, 10), 0, 0, 0);
        row.addView(more);
        addView(row);

        older = new LinearLayout(c);
        older.setOrientation(VERTICAL);
        final int maxOpen = Theme.px(c, 190);
        olderScroll = new ScrollView(c) {
                @Override
                protected void onMeasure(int w, int h) {
                    super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxOpen, MeasureSpec.AT_MOST));
                }
            };
        olderScroll.setVerticalScrollBarEnabled(false);
        olderScroll.addView(older);
        olderScroll.setVisibility(GONE);
        LayoutParams olp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        olp.setMargins(0, Theme.px(c, 4), 0, 0);
        addView(olderScroll, olp);

        setOnClickListener(v -> setOpen(!open));
        setVisibility(GONE);
    }

    @Override
    protected void onMeasure(int w, int h) {
        // No wider than a comfortable line, and never most of the map.
        int max = Math.min(Theme.px(getContext(), 430),
                           (int)(MeasureSpec.getSize(w) * 0.7f));
        super.onMeasure(MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST), h);
    }

    /** Add a message at the top. */
    void add(String line) {
        if (line == null || line.isEmpty()) return;
        news.addFirst(line);
        while (news.size() > KEPT) news.removeLast();
        if (!open) unseen++;
        update();
    }

    private void setOpen(boolean on) {
        open = on && news.size() > 1;
        if (open) unseen = 0;
        update();
    }

    private void update() {
        setVisibility(news.isEmpty() ? GONE : VISIBLE);
        if (news.isEmpty()) return;
        latest.setText(news.peekFirst());
        int others = news.size() - 1;
        int extra = Math.min(Math.max(unseen - 1, 0), others);
        more.setText(others == 0 ? ""
                     : open ? "▴"
                     : (extra > 0 ? "+" + extra + " ▾" : "▾"));
        older.removeAllViews();
        olderScroll.setVisibility(open ? VISIBLE : GONE);
        if (!open) return;
        int i = 0;
        for (String s : news) {
            if (i++ == 0) continue;
            if (i > SHOWN_OPEN + 1) break;
            TextView t = Theme.serif(getContext(), s, 11.5f,
                                     (i <= 3) ? 0xFF4A3820 : Theme.PAPER_DIM, false);
            t.setPadding(0, 0, 0, Theme.px(getContext(), 3));
            older.addView(t);
        }
        olderScroll.scrollTo(0, 0);
    }
}
