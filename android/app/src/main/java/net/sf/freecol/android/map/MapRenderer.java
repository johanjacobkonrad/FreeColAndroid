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

import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * Draws the map and maps screen positions back to tiles.
 *
 * The first renderer is FreeCol's isometric view; a classic top-down
 * view of square tiles can be added later as a second implementation
 * without changing the rest of the app.
 *
 * Callers must hold the game session's lock while calling these.
 */
public interface MapRenderer {

    /** Width of the whole map in world pixels. */
    float worldWidth(Map map);

    /** Height of the whole map in world pixels. */
    float worldHeight(Map map);

    /** World x of a tile's centre. */
    float centreX(Tile tile);

    /** World y of a tile's centre. */
    float centreY(Tile tile);

    /**
     * Find the tile at a screen position.
     *
     * @return The {@code Tile}, or null if off the map.
     */
    Tile tileAt(Map map, Viewport v, float sx, float sy);

    /**
     * Draw the visible part of the map.
     *
     * @param s The {@code Surface} to draw on.
     * @param map The {@code Map} to draw.
     * @param me The player whose view this is.
     * @param selected The selected unit, if any.
     * @param v The {@code Viewport}.
     */
    default void draw(Surface s, Map map, Player me, Unit selected, Viewport v) {
        draw(s, map, me, selected, v, null, true);
    }

    /**
     * Draw part of the map, for close-ups such as a colony's land.
     *
     * @param s The {@code Surface} to draw on.
     * @param map The {@code Map} to draw.
     * @param me The player whose view this is.
     * @param selected The selected unit, if any.
     * @param v The {@code Viewport}.
     * @param only If not null, draw only the tiles it accepts.
     * @param units Draw units on the map.
     */
    void draw(Surface s, Map map, Player me, Unit selected, Viewport v,
              java.util.function.Predicate<Tile> only, boolean units);
}
