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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;


/**
 * FreeCol's artwork, looked up by the same resource keys as the
 * desktop game (see data/default/resources.properties), for example
 * "image.tile.model.tile.plains.center".
 *
 * Like the desktop game, an image "name0.png" may have variations
 * "name1.png", "name2.png"... in the same directory, and a tile picks
 * one from its position so the map does not look tiled.
 *
 * Loading the files is left to the platform (Android, or Java2D for
 * the desktop preview).
 */
public abstract class Art {

    /** A loaded image and its size in pixels. */
    public static final class Img {
        public final Object handle;
        public final int width, height;

        public Img(Object handle, int width, int height) {
            this.handle = handle;
            this.width = width;
            this.height = height;
        }
    }

    private static final Img MISSING = new Img(null, 0, 0);

    /** Resource key to file path, relative to the art root. */
    private final Map<String, String> paths = new HashMap<>();
    /** File path to the paths of its variations (itself first). */
    private final Map<String, List<String>> variations = new HashMap<>();
    /** Loaded images by path. */
    private final Map<String, Img> images = new HashMap<>();


    /**
     * Read the resource key table.  Call once before use.
     *
     * @exception IOException if the table can not be read.
     */
    protected void loadKeys() throws IOException {
        Properties p = new Properties();
        try (InputStream in = open("resources.properties")) {
            p.load(in);
        }
        for (String key : p.stringPropertyNames()) {
            if (key.startsWith("image.")) paths.put(key, p.getProperty(key));
        }
    }


    // Platform hooks

    /** Open a file under the art root. */
    protected abstract InputStream open(String path) throws IOException;

    /** List the file names in a directory under the art root. */
    protected abstract String[] list(String dir) throws IOException;

    /** Decode an image, or return null on failure. */
    protected abstract Img decode(InputStream in) throws IOException;


    // Lookups

    /**
     * Is there an image for a key?
     *
     * @param key The resource key.
     * @return True if the key is known.
     */
    public boolean has(String key) {
        return resolve(key) != null;
    }

    /**
     * Get the image for a key.
     *
     * @param key The resource key.
     * @param seed Picks a variation, if there are any.
     * @return The {@code Img}, or null if there is none.
     */
    public synchronized Img get(String key, int seed) {
        String path = resolve(key);
        if (path == null) return null;
        List<String> vars = getVariations(path);
        if (vars.size() > 1) {
            path = vars.get(Math.floorMod(seed, vars.size()));
        }
        Img img = images.get(path);
        if (img == null) {
            try (InputStream in = open(path)) {
                img = decode(in);
            } catch (IOException ioe) {
                img = null;
            }
            if (img == null) img = MISSING;
            images.put(path, img);
        }
        return (img == MISSING) ? null : img;
    }

    /** The image for a key, without variations. */
    public Img get(String key) {
        return get(key, 0);
    }

    /** Follow "resource:" aliases to a file path. */
    private String resolve(String key) {
        String value = paths.get(key);
        for (int i = 0; value != null && value.startsWith("resource:")
                 && i < 5; i++) {
            value = paths.get(value.substring("resource:".length()));
        }
        return value;
    }

    /** Find "name0.png", "name1.png"... next to a file. */
    private List<String> getVariations(String path) {
        List<String> vars = variations.get(path);
        if (vars != null) return vars;
        vars = new ArrayList<>();
        vars.add(path);
        int slash = path.lastIndexOf('/');
        String dir = (slash < 0) ? "" : path.substring(0, slash);
        String file = path.substring(slash + 1);
        int dot = file.lastIndexOf('.');
        String stem = file.substring(0, dot), ext = file.substring(dot);
        String prefix = stem.replaceAll("[0-9]*$", "");
        if (!prefix.equals(stem)) {
            Pattern p = Pattern.compile(Pattern.quote(prefix) + "[0-9][0-9]?"
                                        + Pattern.quote(ext));
            try {
                String[] names = list(dir);
                if (names != null) {
                    List<String> found = new ArrayList<>(Arrays.asList(names));
                    Collections.sort(found);
                    for (String n : found) {
                        if (!n.equals(file) && p.matcher(n).matches()) {
                            vars.add(dir.isEmpty() ? n : dir + "/" + n);
                        }
                    }
                }
            } catch (IOException ioe) {} // No variations then
        }
        variations.put(path, vars);
        return vars;
    }
}
