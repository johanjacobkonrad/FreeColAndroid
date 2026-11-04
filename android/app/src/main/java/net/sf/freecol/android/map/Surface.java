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
 * Something a map renderer can draw on: an Android canvas, or a
 * Java2D image for the desktop preview.  Coordinates are in screen
 * pixels.
 */
public interface Surface {

    /**
     * Draw an image scaled into a rectangle.
     *
     * @param img The {@code Art.Img} to draw.
     * @param x The left edge.
     * @param y The top edge.
     * @param w The width.
     * @param h The height.
     * @param faded Draw it darker (fog of war).
     */
    void drawImage(Art.Img img, float x, float y, float w, float h,
                   boolean faded);

    /** Fill a rectangle with an ARGB colour. */
    void fillRect(float x, float y, float w, float h, int argb);

    /**
     * Draw a line of text.
     *
     * @param text The text.
     * @param cx The horizontal centre of the text.
     * @param baseline The baseline of the text.
     * @param size The text size in pixels.
     * @param argb The colour.
     */
    void drawText(String text, float cx, float baseline, float size, int argb);
}
