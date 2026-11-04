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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.res.AssetManager;


/** Helpers for the app's bundled assets. */
public final class Assets {

    private Assets() {} // Static only

    /**
     * Copy an asset directory tree to a real directory, since the
     * engine reads its data from files.
     *
     * @param am The {@code AssetManager}.
     * @param path The asset path to copy.
     * @param dest Where to copy it to.
     * @exception IOException on failure.
     */
    public static void copy(AssetManager am, String path, File dest)
        throws IOException {
        String[] children = am.list(path);
        if (children == null || children.length == 0) {
            dest.getParentFile().mkdirs();
            try (InputStream in = am.open(path);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return;
        }
        dest.mkdirs();
        for (String child : children) {
            copy(am, path + "/" + child, new File(dest, child));
        }
    }
}
