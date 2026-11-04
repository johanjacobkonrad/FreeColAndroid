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

import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.MouseInfo;
import java.awt.Toolkit;
import java.util.Arrays;


/**
 * Screen and display helpers for the desktop client.
 */
public final class GraphicsUtils {

    private GraphicsUtils() {} // Static only

    /**
     * Get a good screen device for starting FreeCol.
     *
     * @return A screen device, or null if none available
     *     (as in headless mode).
     */
    public static GraphicsDevice getGoodGraphicsDevice() {
        try {
            return MouseInfo.getPointerInfo().getDevice();
        } catch (HeadlessException he) {}

        try {
            final GraphicsEnvironment lge
                = GraphicsEnvironment.getLocalGraphicsEnvironment();
            return lge.getDefaultScreenDevice();
        } catch (HeadlessException he) {}
        return null;
    }
    
    /**
     * Tries to determine the DPI of the given {@code GraphicsDevice}.
     * 
     * @param gd The {@code GraphicsDevice} to determine the DPI for.
     * @return The calculated DPI.
     */
    public static int determineDpi(GraphicsDevice gd) {
        /*
         * getScreenResolution returns the DPI at maximum resolution -- but we might
         * currently be running with a lower resolution.
         */
        final int candidateDpi = Toolkit.getDefaultToolkit().getScreenResolution();

        final DisplayMode[] displayModes = gd.getDisplayModes();
        if (displayModes.length <= 1) {
            /*
             * There is a bug when running under KDE X11 with multiple screens
             * where getDisplayModes() return an empty array.
             */
            return candidateDpi;
        }

        final int highestScreenSize = Arrays.stream(displayModes).map(dm -> dm.getHeight()).max(Integer::compare).get();
        final int currentScreenSize = gd.getDisplayMode().getHeight();

        final int actualDpi = (candidateDpi * currentScreenSize) / highestScreenSize;
        return actualDpi;
    }
}
