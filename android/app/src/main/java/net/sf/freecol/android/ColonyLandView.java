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

import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

import net.sf.freecol.android.map.Art;
import net.sf.freecol.android.map.IsometricRenderer;
import net.sf.freecol.android.map.Viewport;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * The colony's own land: the colony tile and its eight neighbours,
 * with who works each one and what it produces.
 */
final class ColonyLandView extends View {

    /** Told which colony tile was tapped. */
    interface OnLandTap {
        void tapped(ColonyInfo.LandItem item);
    }

    private final GameSession session;
    private final IsometricRenderer renderer;
    private final Art art;
    private final CanvasSurface surface = new CanvasSurface();
    private final Viewport viewport = new Viewport();
    private ColonyInfo info = null;
    private Unit selected = null;
    private OnLandTap onTap = null;


    ColonyLandView(Context context, GameSession session,
                   IsometricRenderer renderer, Art art) {
        super(context);
        this.session = session;
        this.renderer = renderer;
        this.art = art;
    }

    void setInfo(ColonyInfo info, Unit selected) {
        this.info = info;
        this.selected = selected;
        invalidate();
    }

    void setOnLandTap(OnLandTap l) {
        this.onTap = l;
    }

    /** Fit the 3x3 diamond of tiles into the view. */
    private void layoutViewport(Tile centre) {
        viewport.width = getWidth();
        viewport.height = getHeight();
        // The neighbours reach 1.5 tiles left and right, and 1.5 tile
        // heights up and down, from the centre tile's middle.
        float needW = 3 * IsometricRenderer.TILE_W + 16;
        float needH = 3 * IsometricRenderer.TILE_H + 40;
        viewport.scale = Math.min(getWidth() / needW, getHeight() / needH);
        viewport.worldWidth = Float.MAX_VALUE;
        viewport.worldHeight = Float.MAX_VALUE;
        viewport.originX = renderer.centreX(centre) - getWidth() / (2 * viewport.scale);
        viewport.originY = renderer.centreY(centre) - getHeight() / (2 * viewport.scale);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (info == null) return;
        synchronized (session.lock()) {
            if (session.getGame() == null) return;
            Map map = session.getGame().getMap();
            Tile centre = info.colony.getTile();
            if (centre == null) return;
            layoutViewport(centre);
            final Set<Tile> mine = new HashSet<>();
            for (ColonyInfo.LandItem li : info.land) mine.add(li.tile);
            surface.canvas = canvas;
            renderer.draw(surface, map, session.getMyPlayer(), null, viewport,
                          mine::contains, false);
            float s = viewport.scale;
            for (ColonyInfo.LandItem li : info.land) {
                float cx = viewport.toScreenX(renderer.centreX(li.tile));
                float cy = viewport.toScreenY(renderer.centreY(li.tile));
                // The worker, a little left of centre.
                if (li.worker != null) {
                    if (li.worker.unit == selected) {
                        Art.Img sel = art.get("image.tile.unitSelect");
                        if (sel != null) {
                            surface.drawImage(sel, cx - sel.width * s / 2,
                                cy - sel.height * s / 2, sel.width * s,
                                sel.height * s, false);
                        }
                    }
                    Art.Img u = art.get(li.worker.imageKey);
                    if (u != null) {
                        float h = 52 * s, w = u.width * h / u.height;
                        surface.drawImage(u, cx - 30 * s - w / 2, cy - h + 14 * s,
                                          w, h, false);
                    }
                }
                // What it produces, stacked to the right.
                float px = (li.worker == null && !li.centre) ? cx - 12 * s : cx + 4 * s;
                float py = cy - 22 * s;
                for (ColonyInfo.Amount a : li.production) {
                    Art.Img icon = art.get(a.iconKey);
                    float sz = 22 * s;
                    if (icon != null) surface.drawImage(icon, px, py, sz, sz, false);
                    String n = Integer.toString(a.amount);
                    surface.drawText(n, px + sz + 9 * s + 1, py + 17 * s + 1,
                                     16 * s, 0xFF000000);
                    surface.drawText(n, px + sz + 9 * s, py + 17 * s,
                                     16 * s, 0xFFFFFFFF);
                    py += sz + 2 * s;
                }
            }
            surface.canvas = null;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (e.getAction() != MotionEvent.ACTION_UP || info == null) return false;
        Tile t;
        synchronized (session.lock()) {
            if (session.getGame() == null) return true;
            t = renderer.tileAt(session.getGame().getMap(), viewport,
                                e.getX(), e.getY());
        }
        if (t == null || onTap == null) return true;
        for (ColonyInfo.LandItem li : info.land) {
            if (li.tile == t) {
                onTap.tapped(li);
                break;
            }
        }
        return true;
    }
}
