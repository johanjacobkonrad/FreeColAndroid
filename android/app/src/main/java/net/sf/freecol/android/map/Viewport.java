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
package net.sf.freecol.android.map;


/**
 * Which part of the map is on screen, and how big.
 *
 * "World" coordinates are pixels of the whole map at scale 1, as laid
 * out by a {@link MapRenderer}; screen = (world - origin) * scale.
 */
public final class Viewport {

    public static final float MIN_SCALE = 0.25f, MAX_SCALE = 3f;

    /** World position of the screen's top left corner. */
    public float originX = 0, originY = 0;
    /** Screen pixels per world pixel. */
    public float scale = 1f;
    /** Screen size in pixels. */
    public int width = 1, height = 1;
    /** Size of the whole world in world pixels. */
    public float worldWidth = 1, worldHeight = 1;


    public float toScreenX(float wx) { return (wx - originX) * scale; }
    public float toScreenY(float wy) { return (wy - originY) * scale; }
    public float toWorldX(float sx) { return sx / scale + originX; }
    public float toWorldY(float sy) { return sy / scale + originY; }

    /** Scroll by a distance in screen pixels. */
    public void panBy(float dx, float dy) {
        originX += dx / scale;
        originY += dy / scale;
        clamp();
    }

    /** Zoom by a factor, keeping the world point under (sx, sy) still. */
    public void zoomBy(float factor, float sx, float sy) {
        float wx = toWorldX(sx), wy = toWorldY(sy);
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * factor));
        originX = wx - sx / scale;
        originY = wy - sy / scale;
        clamp();
    }

    /** Put a world point in the middle of the screen. */
    public void centreOn(float wx, float wy) {
        originX = wx - width / (2 * scale);
        originY = wy - height / (2 * scale);
        clamp();
    }

    /** Is a world point on screen, with a margin in screen pixels? */
    public boolean isVisible(float wx, float wy, float margin) {
        float sx = toScreenX(wx), sy = toScreenY(wy);
        return sx >= margin && sy >= margin
            && sx <= width - margin && sy <= height - margin;
    }

    /** Keep the map on screen: no scrolling far past its edges. */
    public void clamp() {
        float viewW = width / scale, viewH = height / scale;
        float slackX = viewW / 2, slackY = viewH / 2;
        originX = clampRange(originX, -slackX, worldWidth - viewW + slackX);
        originY = clampRange(originY, -slackY, worldHeight - viewH + slackY);
    }

    private static float clampRange(float v, float lo, float hi) {
        if (hi < lo) return (lo + hi) / 2;
        return Math.max(lo, Math.min(hi, v));
    }
}
