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

package net.sf.freecol.client.gui;

import java.awt.Color;


/**
 * Conversions between the game model's ARGB integer colors and AWT colors.
 *
 * The shared game engine stores colors as plain ARGB integers so that it
 * does not depend on AWT, which is unavailable on some platforms.
 */
public final class ColorUtils {

    private ColorUtils() {} // Static only

    /**
     * Convert a model ARGB color to an AWT color.
     *
     * @param argb The ARGB value, or null.
     * @return The corresponding {@code Color}, or null if argb is null.
     */
    public static Color toColor(Integer argb) {
        return (argb == null) ? null : new Color(argb, true);
    }

    /**
     * Convert an AWT color to a model ARGB value.
     *
     * @param color The {@code Color}, or null.
     * @return The ARGB value, or null if color is null.
     */
    public static Integer toARGB(Color color) {
        return (color == null) ? null : color.getRGB();
    }
}
