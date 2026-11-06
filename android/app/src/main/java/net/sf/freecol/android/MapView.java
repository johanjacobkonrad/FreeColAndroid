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
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import net.sf.freecol.android.map.IsometricRenderer;
import net.sf.freecol.android.map.MapRenderer;
import net.sf.freecol.android.map.Viewport;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * The map: drag to scroll, pinch to zoom, tap a unit to select it
 * and tap somewhere else to send it there.  Tap your colony to open
 * it.  A long press sends the selected unit to a tile even if your
 * own units or colony are there (to board a ship, or enter the
 * colony).
 */
public final class MapView extends View {

    private final GameSession session;
    private final MapRenderer renderer;
    private final Viewport viewport = new Viewport();
    private final ScaleGestureDetector scaler;
    private final GestureDetector gestures;
    private final CanvasSurface surface = new CanvasSurface();

    /** Opens a colony screen. */
    interface ColonyOpener {
        void open(Colony colony);
    }

    private ColonyOpener colonyOpener = null;

    // Animations: units sliding between tiles, and attacks.

    private static final class Anim {
        final Unit unit;
        final Tile from, to;
        final net.sf.freecol.android.map.Art.Animation frames; // Attacks
        final boolean attack;
        final int millis;
        long start = -1;

        Anim(Unit unit, Tile from, Tile to, boolean attack,
             net.sf.freecol.android.map.Art.Animation frames, int millis) {
            this.unit = unit;
            this.from = from;
            this.to = to;
            this.attack = attack;
            this.frames = frames;
            this.millis = millis;
        }
    }

    private final java.util.ArrayDeque<Anim> anims = new java.util.ArrayDeque<>();
    private Anim current = null;
    /** How long a step takes (0: no animations). */
    private volatile int slideMillis = 500;

    void setSlideMillis(int ms) {
        slideMillis = ms;
    }

    private IsometricRenderer iso() {
        return (renderer instanceof IsometricRenderer) ? (IsometricRenderer)renderer : null;
    }

    /** Show a unit going from one tile to the next.  Any thread. */
    void animateMove(Unit unit, Tile from, Tile to) {
        enqueue(new Anim(unit, from, to, false, null, slideMillis));
    }

    /** Show a unit attacking the next tile.  Any thread. */
    void animateAttack(Unit unit, Tile from, Tile to,
                       net.sf.freecol.android.map.Art.Animation frames) {
        // FreeCol's frames last 350 ms in all, which is a blur on a
        // phone: play them at about the speed of a step (twice as slow
        // at Normal).
        float slow = Math.max(1f, slideMillis / 250f);
        int ms = Math.round(((frames != null) ? Math.max(200, frames.total()) : 320) * slow);
        enqueue(new Anim(unit, from, to, true, frames, ms));
    }

    private void enqueue(Anim a) {
        if (slideMillis <= 0 || iso() == null || !isShown()) return;
        synchronized (anims) {
            if (anims.size() > 8) return; // Too much at once: just jump
            anims.add(a);
        }
        iso().setHidden(a.unit, true);
        postInvalidateOnAnimation();
    }

    /** Wait until every animation queued so far has been shown. */
    void awaitAnimations() {
        long end = System.currentTimeMillis() + 3000;
        synchronized (anims) {
            while ((current != null || !anims.isEmpty())
                   && System.currentTimeMillis() < end && isShown()) {
                try {
                    anims.wait(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** Draw the current animation (on the UI thread, in onDraw). */
    private void drawAnimation() {
        IsometricRenderer r = iso();
        if (r == null) return;
        Anim a;
        synchronized (anims) {
            if (current == null) current = anims.poll();
            a = current;
        }
        if (a == null) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (a.start < 0) a.start = now;
        float t = Math.min(1f, (now - a.start) / (float)a.millis);
        float fx = r.centreX(a.from), fy = r.centreY(a.from);
        float tx = r.centreX(a.to), ty = r.centreY(a.to);
        boolean west = tx < fx;
        if (!a.attack) {
            // Ease in and out.
            float e = t * t * (3 - 2 * t);
            r.drawUnitAt(surface, a.unit, null, fx + (tx - fx) * e, fy + (ty - fy) * e,
                         viewport, session.getMyPlayer(), false);
        } else if (a.frames != null) {
            int at = (int)(t * a.frames.total()), i = 0;
            for (int acc = 0; i < a.frames.frames.size() - 1; i++) {
                acc += a.frames.millis.get(i);
                if (at < acc) break;
            }
            // A little towards the enemy, facing them.
            r.drawUnitAt(surface, a.unit, a.frames.frames.get(i),
                         fx + (tx - fx) * 0.25f, fy + (ty - fy) * 0.25f,
                         viewport, session.getMyPlayer(), west);
        } else {
            // No frames for this unit: a lunge towards the enemy and back.
            float e = (t < 0.5f) ? t * 2 : (1 - t) * 2;
            r.drawUnitAt(surface, a.unit, null, fx + (tx - fx) * 0.4f * e,
                         fy + (ty - fy) * 0.4f * e, viewport, session.getMyPlayer(), false);
        }
        if (t >= 1f) {
            synchronized (anims) {
                current = null;
                // Show it on its tile again unless another animation of
                // it is waiting.
                boolean again = false;
                for (Anim o : anims) if (o.unit == a.unit) again = true;
                if (!again) r.setHidden(a.unit, false);
                anims.notifyAll();
            }
        }
        postInvalidateOnAnimation();
    }
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

    void setColonyOpener(ColonyOpener opener) {
        this.colonyOpener = opener;
    }

    /** Told after each redraw, so the minimap can follow. */
    private Runnable onRedraw = null;

    void setOnRedraw(Runnable r) {
        this.onRedraw = r;
    }

    Viewport getViewport() {
        return viewport;
    }

    /** Centre the map on a world position (from the minimap). */
    void jumpTo(float wx, float wy) {
        viewport.centreOn(wx, wy);
        invalidate();
    }

    /** Centre the map on a tile. */
    void centreOn(Tile t) {
        synchronized (session.lock()) {
            viewport.centreOn(renderer.centreX(t), renderer.centreY(t));
        }
        invalidate();
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
        Colony colony = null;
        synchronized (session.lock()) {
            Map map = (session.getGame() == null) ? null : session.getGame().getMap();
            if (map == null || !session.isInGame()) return;
            tile = renderer.tileAt(map, viewport, sx, sy);
            if (tile != null && tile.getColony() != null
                && tile.getColony().getOwner() == session.getMyPlayer()) {
                colony = tile.getColony();
            }
        }
        if (tile == null) return;
        if (!force && colony != null && colonyOpener != null) {
            colonyOpener.open(colony);
            return;
        }
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
            drawAnimation();
            surface.canvas = null;
        }
        if (onRedraw != null) onRedraw.run();
    }
}
