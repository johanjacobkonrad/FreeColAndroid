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
package net.sf.freecol.common.util;


/**
 * A mutable integer rectangle, with the same semantics as the parts of
 * {@code java.awt.Rectangle} the game engine uses.
 *
 * The engine uses this instead of the AWT class so that it can run
 * where AWT is unavailable (such as Android).
 */
public final class IntRectangle {

    /** The top left corner. */
    public int x, y;

    /** The size. */
    public int width, height;


    /**
     * Create an empty rectangle at the origin.
     */
    public IntRectangle() {
        this(0, 0, 0, 0);
    }

    /**
     * Create a rectangle.
     *
     * @param x The left edge.
     * @param y The top edge.
     * @param width The width.
     * @param height The height.
     */
    public IntRectangle(int x, int y, int width, int height) {
        setBounds(x, y, width, height);
    }

    /**
     * Set the position and size of this rectangle.
     *
     * @param x The left edge.
     * @param y The top edge.
     * @param width The width.
     * @param height The height.
     */
    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Copy the position and size of another rectangle.
     *
     * @param r The rectangle to copy.
     */
    public void setBounds(IntRectangle r) {
        setBounds(r.x, r.y, r.width, r.height);
    }

    /**
     * Is this rectangle empty?
     *
     * @return True if the width or height is not positive.
     */
    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }

    /**
     * Does this rectangle contain a point?
     *
     * As with AWT, the right and bottom edges are exclusive, and an
     * empty rectangle contains nothing.
     *
     * @param px The point x coordinate.
     * @param py The point y coordinate.
     * @return True if the point is inside.
     */
    public boolean contains(int px, int py) {
        int w = this.width;
        int h = this.height;
        if ((w | h) < 0) return false;
        if (px < this.x || py < this.y) return false;
        w += this.x;
        h += this.y;
        // Overflow-safe, as in java.awt.Rectangle
        return (w < this.x || w > px) && (h < this.y || h > py);
    }

    /**
     * Grow this rectangle to include a point.
     *
     * @param px The point x coordinate.
     * @param py The point y coordinate.
     */
    public void add(int px, int py) {
        final long x1 = Math.min(this.x, px);
        final long x2 = Math.max((long)this.x + this.width, px);
        final long y1 = Math.min(this.y, py);
        final long y2 = Math.max((long)this.y + this.height, py);
        setBounds((int)x1, (int)y1, clip(x2 - x1), clip(y2 - y1));
    }

    private static int clip(long v) {
        return (int)Math.min(v, Integer.MAX_VALUE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof IntRectangle)) return false;
        IntRectangle r = (IntRectangle)o;
        return x == r.x && y == r.y && width == r.width && height == r.height;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return ((x * 31 + y) * 31 + width) * 31 + height;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "IntRectangle[x=" + x + ",y=" + y
            + ",width=" + width + ",height=" + height + "]";
    }
}
