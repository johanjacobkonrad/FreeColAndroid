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
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;


/**
 * The app's look, shared by every screen: dark wood panels with a
 * brass edge, parchment cards, gold for what matters, serif headings
 * (the start screen's style).
 */
public final class Theme {

    public static final int WOOD = 0xFF2A1D12, WOOD_DARK = 0xFF1D140C,
        WOOD_TOP = 0xFF3E2A18, WOOD_BOTTOM = 0xFF2A1C10, EDGE = 0xFF8A6A3C,
        GOLD = 0xFFE3C27A, GOLD_DARK = 0xFFB8913F, GOLD_LIGHT = 0xFFF6DFA5,
        INK = 0xFFF3E6C8, DIM = 0xFFC9B38C, PAPER = 0xFFE9DCBC,
        PAPER_INK = 0xFF3A2A18, PAPER_DIM = 0xFF7D6647, HIGHLIGHT = 0x29E3C27A,
        SELECTED = 0xFF5A4424, GOOD = 0xFF5C8A3A, BAD = 0xFFB0442F;

    public static final Typeface SERIF = Typeface.create(Typeface.SERIF, Typeface.NORMAL);
    public static final Typeface SERIF_BOLD = Typeface.create(Typeface.SERIF, Typeface.BOLD);

    private Theme() {} // Static only


    // Backgrounds

    /** A wooden plank with a brass edge. */
    public static GradientDrawable plank(Context c) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { WOOD_TOP, WOOD_BOTTOM });
        g.setCornerRadius(6 * dp(c));
        g.setStroke(px(c, 1), EDGE);
        return g;
    }

    /** A parchment card. */
    public static GradientDrawable paper(Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(PAPER);
        g.setCornerRadius(6 * dp(c));
        g.setStroke(px(c, 1), EDGE);
        return g;
    }

    /** The gold button (End turn and other main actions). */
    public static GradientDrawable gold(Context c) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { GOLD, GOLD_DARK });
        g.setCornerRadius(5 * dp(c));
        g.setStroke(px(c, 1), GOLD_LIGHT);
        return g;
    }

    /** A flat fill with a brass edge, for cells inside panels. */
    public static GradientDrawable cell(Context c, int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(4 * dp(c));
        g.setStroke(px(c, 1), 0x8A8A6A3C);
        return g;
    }

    /** The wood behind whole screens and the sidebar. */
    public static GradientDrawable wood() {
        return new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[] { WOOD_DARK, WOOD, WOOD });
    }


    // Text and buttons

    public static TextView text(Context c, String s, float sp, int colour, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(colour);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static TextView serif(Context c, String s, float sp, int colour, boolean bold) {
        TextView t = text(c, s, sp, colour, false);
        t.setTypeface(bold ? SERIF_BOLD : SERIF);
        return t;
    }

    /** A small spaced-out heading, like "NEWS". */
    public static TextView heading(Context c, String s, int colour) {
        TextView t = text(c, s.toUpperCase(java.util.Locale.UK), 10, colour, true);
        t.setLetterSpacing(0.14f);
        return t;
    }

    /** Button styles. */
    public enum Kind { PLANK, GOLD, FLAT }

    /**
     * A button: a label, optionally with a line icon above it (or
     * beside it when wide).
     */
    public static TextView button(Context c, String label, Icon icon, Kind kind,
                                  boolean iconAbove) {
        TextView b = text(c, label, (kind == Kind.GOLD) ? 16 : 12,
                          (kind == Kind.GOLD) ? PAPER_INK : INK, kind != Kind.FLAT);
        if (kind == Kind.GOLD) b.setTypeface(SERIF_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setMaxLines(2);
        int p = px(c, 6);
        b.setPadding(p, p, p, p);
        if (kind == Kind.PLANK) b.setBackground(plank(c));
        else if (kind == Kind.GOLD) b.setBackground(gold(c));
        if (icon != null) {
            Drawable d = new IconDrawable(icon, (kind == Kind.GOLD) ? PAPER_INK : GOLD, px(c, 20));
            d.setBounds(0, 0, px(c, 20), px(c, 20));
            if (iconAbove) b.setCompoundDrawables(null, d, null, null);
            else b.setCompoundDrawables(d, null, null, null);
            b.setCompoundDrawablePadding(px(c, iconAbove ? 2 : 8));
        }
        return b;
    }

    /** Show a button as usable or not (dimmed). */
    public static void enable(TextView b, boolean on) {
        b.setEnabled(on);
        b.setAlpha(on ? 1f : 0.38f);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
    }

    public static float dp(Context c) {
        return c.getResources().getDisplayMetrics().density;
    }

    public static int px(Context c, float dps) {
        return Math.max(1, Math.round(dps * dp(c)));
    }


    // Line icons

    /** The little gold line icons on buttons. */
    public enum Icon { NEXT, WAIT, ORDERS, SHIP, HOUSE, COLONY, JOIN, BACK, CLOSE, BUILD, EQUIP, SAIL, MENU, SAVE, LOAD }

    /** Draws an {@link Icon} as strokes in a 24x24 box. */
    static final class IconDrawable extends Drawable {
        private final Icon icon;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int size;

        IconDrawable(Icon icon, int colour, int size) {
            this.icon = icon;
            this.size = size;
            paint.setColor(colour);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }

        @Override
        public void draw(Canvas canvas) {
            float s = getBounds().width() / 24f;
            paint.setStrokeWidth(1.9f * s);
            canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.scale(s, s);
            path.reset();
            switch (icon) {
            case NEXT: line(5, 12, 18, 12); line(13, 6, 19, 12); line(19, 12, 13, 18); break;
            case BACK: line(19, 12, 6, 12); line(11, 6, 5, 12); line(5, 12, 11, 18); break;
            case WAIT:
                canvas.drawCircle(12, 12, 8, paint);
                line(12, 7, 12, 12); line(12, 12, 15, 14); break;
            case ORDERS: line(4, 7, 20, 7); line(4, 12, 20, 12); line(4, 17, 14, 17); break;
            case MENU: line(4, 6, 20, 6); line(4, 12, 20, 12); line(4, 18, 20, 18); break;
            case SAVE:
                line(12, 3, 12, 14); line(7, 9, 12, 14); line(12, 14, 17, 9);
                line(4, 15, 4, 20); line(4, 20, 20, 20); line(20, 20, 20, 15); break;
            case LOAD:
                line(12, 14, 12, 3); line(7, 8, 12, 3); line(12, 3, 17, 8);
                line(4, 15, 4, 20); line(4, 20, 20, 20); line(20, 20, 20, 15); break;
            case CLOSE: line(6, 6, 18, 18); line(18, 6, 6, 18); break;
            case SHIP: case SAIL:
                line(3, 17, 21, 17); line(21, 17, 18, 20); line(18, 20, 6, 20); line(6, 20, 3, 17);
                line(12, 3, 12, 15); line(12, 4, 18, 13); line(18, 13, 12, 13); break;
            case HOUSE: case JOIN:
                line(3, 11, 12, 4); line(12, 4, 21, 11); line(5, 10, 5, 20); line(5, 20, 19, 20);
                line(19, 20, 19, 10); line(10, 20, 10, 14); line(10, 14, 14, 14); line(14, 14, 14, 20); break;
            case COLONY: case BUILD:
                line(3, 20, 21, 20); line(5, 20, 5, 12); line(5, 12, 9, 8); line(9, 8, 13, 12);
                line(13, 12, 13, 20); line(15, 20, 15, 14); line(15, 14, 19, 14); line(19, 14, 19, 20);
                line(9, 3, 9, 8); break;
            case EQUIP: line(5, 19, 15, 9); line(13, 5, 19, 11); line(15, 9, 17, 7); break;
            default: break;
            }
            canvas.drawPath(path, paint);
            canvas.restore();
        }

        private void line(float x1, float y1, float x2, float y2) {
            path.moveTo(x1, y1);
            path.lineTo(x2, y2);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
