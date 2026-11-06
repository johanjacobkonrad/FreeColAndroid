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
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import net.sf.freecol.android.map.Art;


/**
 * The reports and the Colopedia: a column of tabs on the left, the
 * chosen page on the right.  Entries that link to another page (a
 * Colopedia item) open it; Back returns.
 */
final class InfoPanel extends LinearLayout {

    /** Where the pages come from. */
    interface Source {
        String title();
        String[] tabs();
        /** The page of a tab; called on the UI thread. */
        InfoPage tab(int index);
        /** The page an entry links to, or null. */
        InfoPage link(String id);
    }

    private final Art art;
    private final Runnable onClose;
    private final TextView title, back;
    private final LinearLayout tabs, content;
    private final ScrollView scroll;
    private Source source = null;
    private int current = 0;
    /** Linked pages opened, newest first (Back goes to the previous). */
    private final Deque<String> trail = new ArrayDeque<>();


    InfoPanel(Context c, Art art, Runnable onClose) {
        super(c);
        this.art = art;
        this.onClose = onClose;
        setOrientation(VERTICAL);
        setBackground(Theme.wood());
        setClickable(true);

        LinearLayout header = new LinearLayout(c);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        back = Theme.button(c, "Back", Theme.Icon.BACK, Theme.Kind.PLANK, false);
        back.setOnClickListener(v -> goBack());
        LayoutParams blp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        blp.setMargins(0, 0, Theme.px(c, 8), 0);
        header.addView(back, blp);
        title = Theme.serif(c, "", 19, Theme.GOLD, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView close = Theme.button(c, "Close", Theme.Icon.CLOSE, Theme.Kind.PLANK, false);
        close.setOnClickListener(v -> onClose.run());
        header.addView(close);
        LayoutParams hlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        hlp.setMargins(0, 0, 0, Theme.px(c, 6));
        addView(header, hlp);

        LinearLayout body = new LinearLayout(c);
        body.setOrientation(HORIZONTAL);
        tabs = new LinearLayout(c);
        tabs.setOrientation(VERTICAL);
        ScrollView tabScroll = new ScrollView(c);
        tabScroll.setVerticalScrollBarEnabled(false);
        tabScroll.addView(tabs);
        LayoutParams tlp = new LayoutParams(Theme.px(c, 150), LayoutParams.MATCH_PARENT);
        tlp.setMargins(0, 0, Theme.px(c, 8), 0);
        body.addView(tabScroll, tlp);
        content = new LinearLayout(c);
        content.setOrientation(VERTICAL);
        scroll = new ScrollView(c);
        scroll.addView(content);
        body.addView(scroll, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        addView(body, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /** Show pages from a source, starting at a tab. */
    void show(Source source, int tab) {
        this.source = source;
        trail.clear();
        buildTabs();
        select(tab);
    }

    /** Rebuild the page shown (when the game changes underneath). */
    void refresh() {
        if (source == null || getVisibility() != VISIBLE) return;
        if (trail.isEmpty()) render(source.tab(current));
    }

    /** Back: a linked page, or nothing (then the caller closes). */
    boolean goBack() {
        if (trail.isEmpty()) return false;
        trail.removeFirst();
        if (trail.isEmpty()) {
            render(source.tab(current));
        } else {
            render(source.link(trail.peekFirst()));
        }
        return true;
    }

    private void buildTabs() {
        final Context c = getContext();
        tabs.removeAllViews();
        String[] names = source.tabs();
        for (int i = 0; i < names.length; i++) {
            final int which = i;
            TextView t = Theme.text(c, names[i], 12.5f, Theme.INK, true);
            t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            int p = Theme.px(c, 9);
            t.setPadding(p, p * 2 / 3, p, p * 2 / 3);
            t.setMinHeight(Theme.px(c, 40));
            t.setOnClickListener(v -> select(which));
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, Theme.px(c, 4));
            tabs.addView(t, lp);
        }
    }

    private void select(int tab) {
        current = Math.max(0, Math.min(tab, tabs.getChildCount() - 1));
        trail.clear();
        final Context c = getContext();
        for (int i = 0; i < tabs.getChildCount(); i++) {
            TextView t = (TextView)tabs.getChildAt(i);
            boolean on = i == current;
            t.setBackground(on ? Theme.gold(c) : Theme.plank(c));
            t.setTextColor(on ? Theme.PAPER_INK : Theme.INK);
        }
        render(source.tab(current));
    }

    private void open(String id) {
        InfoPage page = source.link(id);
        if (page == null) return;
        trail.addFirst(id);
        render(page);
    }

    private void render(InfoPage page) {
        final Context c = getContext();
        back.setVisibility(trail.isEmpty() ? GONE : VISIBLE);
        content.removeAllViews();
        if (page == null) return;
        title.setText(source.title() + " · " + page.title);
        if (!page.summary.isEmpty()) {
            TextView s = Theme.text(c, page.summary, 12, Theme.DIM, false);
            s.setPadding(Theme.px(c, 2), 0, 0, Theme.px(c, 6));
            content.addView(s);
        }
        // A Colopedia page: its picture beside its description.
        if (page.imageKey != null || !page.body.isEmpty()) {
            LinearLayout top = new LinearLayout(c);
            top.setOrientation(HORIZONTAL);
            top.setBackground(Theme.paper(c));
            int p = Theme.px(c, 10);
            top.setPadding(p, p, p, p);
            Bitmap b = image(page.imageKey);
            if (b != null) {
                ImageView iv = new ImageView(c);
                iv.setImageBitmap(b);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setAdjustViewBounds(true);
                LayoutParams ilp = new LayoutParams(Theme.px(c, 110), Theme.px(c, 110));
                ilp.setMargins(0, 0, p, 0);
                top.addView(iv, ilp);
            }
            LinearLayout words = new LinearLayout(c);
            words.setOrientation(VERTICAL);
            words.addView(Theme.serif(c, page.title, 17, Theme.PAPER_INK, true));
            if (!page.body.isEmpty()) {
                TextView body = Theme.serif(c, page.body, 13, Theme.PAPER_INK, false);
                body.setLineSpacing(0, 1.15f);
                body.setPadding(0, Theme.px(c, 4), 0, 0);
                words.addView(body);
            }
            top.addView(words, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            LayoutParams tlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            tlp.setMargins(Theme.px(c, 3), 0, Theme.px(c, 3), Theme.px(c, 6));
            content.addView(top, tlp);
        }
        for (InfoPage.Section sec : page.sections) {
            if (sec.heading != null) {
                TextView h = Theme.heading(c, sec.heading, Theme.GOLD);
                h.setPadding(Theme.px(c, 3), Theme.px(c, 6), 0, Theme.px(c, 3));
                content.addView(h);
            }
            int cols = sec.compact ? 3 : 2;
            LinearLayout row = null;
            for (int i = 0; i < sec.entries.size(); i++) {
                if (i % cols == 0) {
                    row = new LinearLayout(c);
                    row.setOrientation(HORIZONTAL);
                    content.addView(row);
                }
                row.addView(entry(sec.entries.get(i), sec.compact), cell());
            }
            int left = (cols - sec.entries.size() % cols) % cols;
            for (int i = 0; i < left; i++) row.addView(new View(c), cell());
        }
        scroll.scrollTo(0, 0);
    }

    private LayoutParams cell() {
        LayoutParams lp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
        int m = Theme.px(getContext(), 3);
        lp.setMargins(m, m, m, m);
        return lp;
    }

    private View entry(final InfoPage.Entry e, boolean compact) {
        final Context c = getContext();
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Theme.paper(c));
        int p = Theme.px(c, compact ? 6 : 8);
        card.setPadding(p, p, p, p);
        Bitmap b = image(e.imageKey);
        if (b != null) {
            ImageView iv = new ImageView(c);
            iv.setImageBitmap(b);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int w = Theme.px(c, compact ? 34 : 48), h = Theme.px(c, compact ? 30 : 44);
            LayoutParams ilp = new LayoutParams(w, h);
            ilp.setMargins(0, 0, Theme.px(c, 7), 0);
            card.addView(iv, ilp);
        }
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(VERTICAL);
        words.addView(Theme.serif(c, e.title, compact ? 12.5f : 13.5f, Theme.PAPER_INK, true));
        if (!e.text.isEmpty()) {
            words.addView(Theme.text(c, e.text, 11, Theme.PAPER_DIM, false));
        }
        card.addView(words, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        if (!e.right.isEmpty()) {
            TextView r = Theme.text(c, e.right, 12, Theme.PAPER_INK, true);
            r.setGravity(Gravity.END);
            r.setPadding(Theme.px(c, 6), 0, 0, 0);
            card.addView(r);
        }
        if (e.link != null) {
            card.setOnClickListener(v -> open(e.link));
            // A gold edge marks what can be opened.
            GradientDrawable g = Theme.paper(c);
            g.setStroke(Theme.px(c, 1), Theme.GOLD_DARK);
            card.setBackground(g);
        }
        return card;
    }

    private Bitmap image(String key) {
        if (key == null) return null;
        Art.Img img = art.get(key);
        return (img == null) ? null : (Bitmap)img.handle;
    }
}
