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

import java.util.logging.Logger;

/**
 * Class used to deal specifically with the operating system
 */
public class OSUtils {

    private static final Logger logger = Logger.getLogger(OSUtils.class.getName());

    private static final String SEPARATOR
        = System.getProperty("file.separator");


    /**
     * What OS are we using?
     *
     * @return The operating system name.
     */
    public static String getOperatingSystem() {
        return System.getProperty("os.name");
    }

    /**
     * Does the OS look like Mac OS X?
     *
     * @return True if Mac OS X appears to be present.
     */
    public static boolean onMacOSX() {
        return "Mac OS X".equals(getOperatingSystem());
    }

    /**
     * Does the OS look like some sort of unix?
     *
     * @return True we hope.
     */
    public static boolean onUnix() {
        return "/".equals(SEPARATOR);
    }

    /**
     * Does the OS look like some sort of Windows?
     *
     * @return True if Windows appears to be present.
     */
    public static boolean onWindows() {
        return getOperatingSystem().startsWith("Windows");
    }
}
