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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import net.sf.freecol.android.map.Art;
import net.sf.freecol.android.map.IsometricRenderer;
import net.sf.freecol.android.map.Viewport;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * The whole map in miniature, in the desktop minimap's colours, with
 * settlements in their owner's colour, your units as white dots and a
 * frame showing what the main map shows.  Tap or drag to go there.
 *
 * Each tile is two pixels wide and one row high, with odd rows
 * shifted by one; rows are drawn at half height, which keeps the
 * isometric map's shape.
 */
final class MinimapView extends View {

    /** Moves the main map. */
    interface Jumper {
        void jumpTo(float worldX, float worldY);
    }

    private final GameSession session;
    private final Art art;
    private final Paint pixels = new Paint(); // No filtering: crisp tiles
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint back = new Paint();
    private Bitmap bitmap = null;
    private Viewport viewport = null;
    private Jumper jumper = null;
    private boolean dirty = true;


    MinimapView(Context context, GameSession session, Art art) {
        super(context);
        this.session = session;
        this.art = art;
        frame.setStyle(Paint.Style.STROKE);
        frame.setColor(0xFFFFFFFF);
        frame.setStrokeWidth(1.5f * context.getResources().getDisplayMetrics().density);
        back.setColor(0xCC000000);
    }

    void setViewport(Viewport v) {
        this.viewport = v;
    }

    void setJumper(Jumper j) {
        this.jumper = j;
    }

    /** The game changed: redraw the map picture (soon). */
    void gameChanged() {
        if (dirty) return;
        dirty = true;
        postDelayed(this::invalidate, 250);
    }

    /** The main map scrolled or zoomed: move the frame. */
    void viewChanged() {
        invalidate();
    }

    private void rebuild() {
        dirty = false;
        synchronized (session.lock()) {
            if (!session.isInGame() || session.getGame() == null
                || session.getGame().getMap() == null) return;
            final Map map = session.getGame().getMap();
            final Player me = session.getMyPlayer();
            final int w = map.getWidth() * 2 + 1, h = map.getHeight();
            if (bitmap == null || bitmap.getWidth() != w || bitmap.getHeight() != h) {
                bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            }
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < map.getWidth(); x++) {
                    Tile t = map.getTile(x, y);
                    int c = 0xFF101010;
                    if (t != null && t.isExplored() && t.getType() != null) {
                        // The desktop's land colours; its water ones are
                        // a bright violet, so deep blues instead.
                        c = (t.isLand())
                            ? art.colour("color.economic.MiniMap." + t.getType().getId(),
                                         0xFF4C7A2E)
                            : t.getType().isHighSeasConnected() ? 0xFF16304E
                            : 0xFF27507E;
                        Settlement st = t.getSettlement();
                        if (st != null) {
                            Integer nc = st.getOwner().getNationColor();
                            c = (nc == null) ? 0xFFFFFFFF : (0xFF000000 | nc);
                        } else if (me != null && me.canSee(t)) {
                            Unit u = t.getFirstUnit();
                            if (u != null && u.getOwner() == me) c = 0xFFFFFFFF;
                        } else if (me != null) {
                            c = darker(c); // Fog of war
                        }
                    }
                    int px0 = x * 2 + (y & 1);
                    px[y * w + px0] = c;
                    px[y * w + px0 + 1] = c;
                }
            }
            bitmap.setPixels(px, 0, w, 0, 0, w, h);
        }
    }

    private static int darker(int c) {
        int r = ((c >> 16) & 0xff) * 3 / 5, g = ((c >> 8) & 0xff) * 3 / 5,
            b = (c & 0xff) * 3 / 5;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Where the map picture sits in the view: fitted, centred. */
    private RectF placement() {
        if (bitmap == null) return null;
        // A tile is two pixels wide here but each map row is only a
        // quarter of a tile's width high, so squash the rows by half to
        // keep the main map's shape.
        float bw = bitmap.getWidth(), bh = bitmap.getHeight() * 0.5f;
        float s = Math.min(getWidth() / bw, getHeight() / bh);
        float dw = bw * s, dh = bh * s;
        float left = (getWidth() - dw) / 2, top = (getHeight() - dh) / 2;
        return new RectF(left, top, left + dw, top + dh);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (dirty || bitmap == null) rebuild();
        RectF r = placement();
        if (r == null) return;
        canvas.drawRect(r, back);
        canvas.drawBitmap(bitmap, null, r, pixels);
        if (viewport != null) {
            // Main map world pixels -> minimap: a tile is 128 wide
            // (2 px here) and each row 32 high (1 px here).
            float sx = r.width() / bitmap.getWidth(), sy = r.height() / bitmap.getHeight();
            float kx = 2f / IsometricRenderer.TILE_W, ky = 2f / IsometricRenderer.TILE_H;
            float x0 = viewport.toWorldX(0) * kx, y0 = viewport.toWorldY(0) * ky;
            float x1 = viewport.toWorldX(viewport.width) * kx;
            float y1 = viewport.toWorldY(viewport.height) * ky;
            canvas.save();
            canvas.clipRect(r);
            canvas.drawRect(r.left + x0 * sx, r.top + y0 * sy,
                            r.left + x1 * sx, r.top + y1 * sy, frame);
            canvas.restore();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a != MotionEvent.ACTION_DOWN && a != MotionEvent.ACTION_MOVE) {
            return a == MotionEvent.ACTION_UP;
        }
        RectF r = placement();
        if (r == null || jumper == null) return true;
        float fx = (e.getX() - r.left) / r.width(), fy = (e.getY() - r.top) / r.height();
        fx = Math.max(0, Math.min(1, fx));
        fy = Math.max(0, Math.min(1, fy));
        float wx = fx * bitmap.getWidth() * IsometricRenderer.TILE_W / 2f;
        float wy = fy * bitmap.getHeight() * IsometricRenderer.TILE_H / 2f;
        jumper.jumpTo(wx, wy);
        return true;
    }
}
