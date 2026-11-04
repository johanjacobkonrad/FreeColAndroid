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
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import net.sf.freecol.android.map.Art;
import net.sf.freecol.android.map.MapRenderer;
import net.sf.freecol.android.map.Surface;
import net.sf.freecol.android.map.Viewport;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * The map: drag to scroll, pinch to zoom, tap a unit to select it
 * and tap somewhere else to send it there.  A long press sends the
 * selected unit to a tile even if your own units are there (to board
 * a ship, say).
 */
public final class MapView extends View {

    private final GameSession session;
    private final MapRenderer renderer;
    private final Viewport viewport = new Viewport();
    private final ScaleGestureDetector scaler;
    private final GestureDetector gestures;
    private final CanvasSurface surface = new CanvasSurface();

    private boolean centred = false;
    private Unit lastSelected = null;


    public MapView(Context context, GameSession session, MapRenderer renderer) {
        super(context);
        this.session = session;
        this.renderer = renderer;
        float density = getResources().getDisplayMetrics().density;
        viewport.scale = Math.max(0.6f, Math.min(2f, density / 2f));
        scaler = new ScaleGestureDetector(context,
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector d) {
                    viewport.zoomBy(d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                    invalidate();
                    return true;
                }
            });
        gestures = new GestureDetector(context,
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                        float dx, float dy) {
                    viewport.panBy(dx, dy);
                    invalidate();
                    return true;
                }

                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    tap(e.getX(), e.getY(), false);
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    tap(e.getX(), e.getY(), true);
                }
            });
    }

    /** Show the selected unit, if it is off screen. */
    public void showSelected() {
        Unit u = session.getSelectedUnit();
        if (u == null || u == lastSelected) return;
        lastSelected = u;
        synchronized (session.lock()) {
            Tile t = u.getTile();
            if (t == null) return;
            float x = renderer.centreX(t), y = renderer.centreY(t);
            if (!viewport.isVisible(x, y, 120)) viewport.centreOn(x, y);
        }
    }

    private void tap(float sx, float sy, boolean force) {
        Tile tile;
        synchronized (session.lock()) {
            Map map = (session.getGame() == null) ? null : session.getGame().getMap();
            if (map == null || !session.isInGame()) return;
            tile = renderer.tileAt(map, viewport, sx, sy);
        }
        if (tile == null) return;
        if (!force && session.selectOnTile(tile)) {
            lastSelected = session.getSelectedUnit();
            invalidate();
            return;
        }
        if (session.getSelectedUnit() != null) {
            performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
            session.moveSelectedTo(tile);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        boolean a = scaler.onTouchEvent(e);
        boolean b = gestures.onTouchEvent(e);
        return a || b || super.onTouchEvent(e);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        viewport.width = w;
        viewport.height = h;
        viewport.clamp();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xFF000000);
        synchronized (session.lock()) {
            if (!session.isInGame() || session.getGame() == null
                || session.getGame().getMap() == null
                || session.getMyPlayer() == null) return;
            Map map = session.getGame().getMap();
            viewport.worldWidth = renderer.worldWidth(map);
            viewport.worldHeight = renderer.worldHeight(map);
            if (!centred) {
                Unit u = session.getSelectedUnit();
                Tile t = (u != null && u.getTile() != null) ? u.getTile()
                    : session.getMyPlayer().getFallbackTile();
                if (t != null) {
                    viewport.centreOn(renderer.centreX(t), renderer.centreY(t));
                    centred = true;
                    lastSelected = u;
                }
            }
            surface.canvas = canvas;
            renderer.draw(surface, map, session.getMyPlayer(),
                          session.getSelectedUnit(), viewport);
            surface.canvas = null;
        }
    }


    /** Draws on an Android canvas. */
    private static final class CanvasSurface implements Surface {
        Canvas canvas;
        private final RectF dst = new RectF();
        private final Paint plain = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint faded = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint fill = new Paint();
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

        CanvasSurface() {
            ColorMatrix m = new ColorMatrix();
            m.setScale(0.62f, 0.62f, 0.62f, 1f);
            faded.setColorFilter(new ColorMatrixColorFilter(m));
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public void drawImage(Art.Img img, float x, float y, float w, float h,
                              boolean fade) {
            dst.set(x, y, x + w, y + h);
            canvas.drawBitmap((Bitmap)img.handle, null, dst, fade ? faded : plain);
        }

        @Override
        public void fillRect(float x, float y, float w, float h, int argb) {
            fill.setColor(argb);
            canvas.drawRect(x, y, x + w, y + h, fill);
        }

        @Override
        public void drawText(String s, float cx, float baseline, float size,
                             int argb) {
            text.setColor(argb);
            text.setTextSize(size);
            canvas.drawText(s, cx, baseline, text);
        }
    }
}
