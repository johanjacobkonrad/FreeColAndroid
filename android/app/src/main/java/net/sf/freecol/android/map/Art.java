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
        // The base data (terrain masks and such) first; the default
        // art overrides it, as in the desktop game.
        String base = baseRoot();
        if (base != null) {
            try (InputStream in = open(base + "resources.properties")) {
                load(in, base);
            } catch (IOException ioe) {} // No base data bundled
        }
        try (InputStream in = open("resources.properties")) {
            load(in, "");
        }
    }

    private void load(InputStream in, String prefix) throws IOException {
        Properties p = new Properties();
        p.load(in);
        for (String key : p.stringPropertyNames()) {
            String v = p.getProperty(key);
            if (key.startsWith("image.") || key.startsWith("animation.")) {
                paths.put(key, v.startsWith("resource:") ? v : prefix + v);
            } else if (key.startsWith("color.") && v.startsWith("urn:color:0x")) {
                try {
                    colours.put(key, 0xFF000000
                        | Integer.parseInt(v.substring("urn:color:0x".length()), 16));
                } catch (NumberFormatException nfe) {} // Skip it
            }
        }
    }

    /**
     * Where the base data lives, relative to the art root, or null.
     *
     * @return A directory prefix such as "base/", or null.
     */
    protected String baseRoot() {
        return "base/";
    }

    /**
     * A colour from the resources, such as the minimap's
     * "color.economic.MiniMap.model.tile.plains".
     *
     * @param key The resource key.
     * @param fallback The colour to use if there is none.
     * @return An ARGB colour.
     */
    public int colour(String key, int fallback) {
        Integer c = colours.get(key);
        return (c == null) ? fallback : c;
    }

    /** Colours by resource key. */
    private final Map<String, Integer> colours = new HashMap<>();

    /** Masked images, by "imageKey|maskKey|variation". */
    private final Map<String, Img> maskedCache = new HashMap<>();

    /**
     * An image with its alpha taken from a mask (the mask's opaque
     * parts keep the image, its clear parts make it clear), as the
     * desktop does for soft terrain edges.
     *
     * @param imageKey The image's resource key (first variation).
     * @param maskKey The mask's resource key.
     * @param maskSeed Picks the mask's variation.
     * @return The masked image, or null if either is missing.
     */
    public synchronized Img masked(String imageKey, String maskKey, int maskSeed) {
        Img image = get(imageKey, 0);
        Img mask = get(maskKey, maskSeed);
        if (image == null || mask == null) return null;
        String k = imageKey + "|" + maskKey + "|" + System.identityHashCode(mask);
        Img m = maskedCache.get(k);
        if (m == null) {
            m = applyMask(image, mask);
            maskedCache.put(k, m);
        }
        return m;
    }

    /**
     * How many variations an image has.
     *
     * @param key The resource key.
     * @return The number of variations (at least 1), or 0 if missing.
     */
    public synchronized int variationCount(String key) {
        String path = resolve(key);
        return (path == null) ? 0 : getVariations(path).size();
    }


    // Platform hooks

    /** Open a file under the art root. */
    protected abstract InputStream open(String path) throws IOException;

    /** List the file names in a directory under the art root. */
    protected abstract String[] list(String dir) throws IOException;

    /** Decode an image, or return null on failure. */
    protected abstract Img decode(InputStream in) throws IOException;

    /**
     * Make a copy of an image with the mask's alpha (Porter-Duff
     * "destination in").
     */
    protected abstract Img applyMask(Img image, Img mask);


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

    /** An animation: its frames and how long each shows. */
    public static final class Animation {
        public final java.util.List<Img> frames = new java.util.ArrayList<>();
        public final java.util.List<Integer> millis = new java.util.ArrayList<>();

        public int total() {
            int t = 0;
            for (int m : millis) t += m;
            return t;
        }
    }

    private final java.util.Map<String, Animation> animations = new java.util.HashMap<>();

    /**
     * An animation by its key ("animation.unit.<type>.<role>.attack.e"):
     * FreeCol's ".sza" files, zips of frames listed with their times
     * in "animation.txt" ("soldier-attack-1.png (50ms)").
     *
     * @param key The resource key.
     * @return The animation, or null if there is none.
     */
    public synchronized Animation animation(String key) {
        String path = resolve(key);
        if (path == null) return null;
        if (animations.containsKey(path)) return animations.get(path);
        Animation a = null;
        java.util.Map<String, byte[]> files = new java.util.HashMap<>();
        try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(open(path))) {
            for (java.util.zip.ZipEntry e; (e = zin.getNextEntry()) != null; ) {
                java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                byte[] b = new byte[16384];
                for (int n; (n = zin.read(b)) > 0; ) buf.write(b, 0, n);
                files.put(e.getName(), buf.toByteArray());
            }
            byte[] list = files.get("animation.txt");
            if (list != null) {
                a = new Animation();
                for (String line : new String(list, java.nio.charset.StandardCharsets.UTF_8)
                         .split("\\r?\\n")) {
                    java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("\\s*(\\S+)\\s*\\((\\d+)ms\\)").matcher(line);
                    if (!m.find() || !files.containsKey(m.group(1))) continue;
                    Img img = decode(new java.io.ByteArrayInputStream(files.get(m.group(1))));
                    if (img == null) continue;
                    a.frames.add(img);
                    a.millis.add(Integer.parseInt(m.group(2)));
                }
                if (a.frames.isEmpty()) a = null;
            }
        } catch (IOException ioe) {
            a = null;
        }
        animations.put(path, a);
        return a;
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
        {
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
