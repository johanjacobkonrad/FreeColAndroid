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

import java.util.function.Consumer;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import net.sf.freecol.android.map.Art;
import net.sf.freecol.android.map.IsometricRenderer;
import net.sf.freecol.common.model.Colony;


/**
 * All your colonies, one card each (from the sidebar menu).  Tap a
 * colony to open it.
 */
final class ColonyListPanel extends LinearLayout {

    private final GameSession session;
    private final IsometricRenderer renderer;
    private final Art art;
    private final Runnable onClose;
    private final Consumer<Colony> onOpen;
    private final TextView title;
    private final LinearLayout list;


    ColonyListPanel(Context c, GameSession session, IsometricRenderer renderer,
                    Art art, Runnable onClose, Consumer<Colony> onOpen) {
        super(c);
        this.session = session;
        this.renderer = renderer;
        this.art = art;
        this.onClose = onClose;
        this.onOpen = onOpen;
        setOrientation(VERTICAL);
        setBackground(Theme.wood());
        setClickable(true);

        LinearLayout header = new LinearLayout(c);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        title = Theme.serif(c, "Colonies", 19, Theme.GOLD, true);
        header.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView close = Theme.button(c, "Close", Theme.Icon.CLOSE, Theme.Kind.PLANK, false);
        close.setOnClickListener(v -> onClose.run());
        header.addView(close);
        LayoutParams hlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        hlp.setMargins(0, 0, 0, Theme.px(c, 6));
        addView(header, hlp);

        list = new LinearLayout(c);
        list.setOrientation(VERTICAL);
        ScrollView scroll = new ScrollView(c);
        scroll.addView(list);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /** Show the list afresh. */
    void show() {
        final Context c = getContext();
        ColonyListInfo info;
        synchronized (session.lock()) {
            if (session.getMyPlayer() == null) return;
            info = new ColonyListInfo(session.getMyPlayer(), renderer);
        }
        title.setText(info.rows.isEmpty() ? "Colonies"
            : "Colonies · " + info.rows.size() + " with "
            + info.colonists + " colonists");
        list.removeAllViews();
        if (info.rows.isEmpty()) {
            list.addView(Theme.serif(c, "You have no colonies yet. Land a colonist "
                + "and use Build colony.", 14, Theme.DIM, false));
            return;
        }
        // Two cards a row: there is room across a phone held sideways.
        LinearLayout row = null;
        int i = 0;
        for (final ColonyListInfo.Row r : info.rows) {
            if (i++ % 2 == 0) {
                row = new LinearLayout(c);
                row.setOrientation(HORIZONTAL);
                list.addView(row);
            }
            row.addView(card(r), cardParams());
        }
        if (i % 2 == 1) row.addView(new android.view.View(c), cardParams());
    }

    private LayoutParams cardParams() {
        LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        int m = Theme.px(getContext(), 3);
        lp.setMargins(m, m, m, m);
        return lp;
    }

    private LinearLayout card(final ColonyListInfo.Row r) {
        final Context c = getContext();
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Theme.paper(c));
        int p = Theme.px(c, 7);
        card.setPadding(p, p, p, p);
        ImageView pic = new ImageView(c);
        pic.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Art.Img img = art.get(r.imageKey);
        if (img != null) pic.setImageBitmap((Bitmap)img.handle);
        card.addView(pic, new LayoutParams(Theme.px(c, 60), Theme.px(c, 46)));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(VERTICAL);
        words.setPadding(Theme.px(c, 8), 0, 0, 0);
        words.addView(Theme.serif(c, r.name, 14, Theme.PAPER_INK, true));
        words.addView(Theme.text(c, r.size + (r.size == 1 ? " colonist" : " colonists")
            + " · rebels " + r.rebels + "%", 11, Theme.PAPER_DIM, false));
        words.addView(Theme.text(c, "food " + signed(r.food) + " · hammers "
            + signed(r.hammers) + " · bells " + signed(r.bells),
            11, Theme.PAPER_DIM, false));
        words.addView(Theme.text(c, r.building, 11, Theme.PAPER_INK, false));
        if (!r.warnings.isEmpty()) {
            words.addView(Theme.text(c, String.join(" · ", r.warnings),
                                     11, Theme.BAD, true));
        }
        card.addView(words, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        card.setOnClickListener(v -> onOpen.accept(r.colony));
        return card;
    }

    private static String signed(int n) {
        return (n > 0) ? "+" + n : Integer.toString(n);
    }
}
