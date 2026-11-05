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
import java.util.Locale;

import net.sf.freecol.FreeCol;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.io.FreeColRules;
import net.sf.freecol.common.model.Specification;


/**
 * One-time set up of the shared engine: data directory, strings and
 * rules.  Uses only engine classes, so it works on a desktop JVM too.
 */
public final class Engine {

    private static boolean initialised = false;

    private Engine() {} // Static only


    /**
     * Point the engine at its data and load the strings and rules.
     *
     * @param dataDir The FreeCol "data" directory (rules and strings).
     * @exception IllegalStateException if the data is unusable.
     */
    public static synchronized void init(File dataDir) {
        if (initialised) return;
        // StAX implementation, in case service lookup fails (Android).
        setIfMissing("javax.xml.stream.XMLInputFactory",
                     "com.ctc.wstx.stax.WstxInputFactory");
        setIfMissing("javax.xml.stream.XMLOutputFactory",
                     "com.ctc.wstx.stax.WstxOutputFactory");
        setIfMissing("javax.xml.stream.XMLEventFactory",
                     "com.ctc.wstx.stax.WstxEventFactory");
        String err = FreeColDirectories.setDataDirectory(dataDir.getPath());
        if (err != null) {
            throw new IllegalStateException("bad data directory: " + err);
        }
        Messages.loadMessageBundle(Locale.UK);
        FreeColRules.loadRules();
        initialised = true;
    }

    /**
     * Load the classic rules at medium difficulty.
     *
     * @return A new {@code Specification}.
     * @exception IllegalStateException if the rules do not load.
     */
    public static Specification loadSpecification() {
        return loadSpecification("model.difficulty.medium");
    }

    /**
     * Load the classic rules at a given difficulty.
     *
     * @param difficulty A difficulty id such as "model.difficulty.hard".
     * @return A new {@code Specification}.
     * @exception IllegalStateException if the rules do not load.
     */
    public static Specification loadSpecification(String difficulty) {
        Specification spec = FreeCol.loadSpecification(
            FreeColRules.getFreeColRulesFile("freecol"), null, difficulty);
        if (spec == null) throw new IllegalStateException("rules failed to load");
        return spec;
    }

    private static void setIfMissing(String key, String value) {
        // Only Android lacks a built-in StAX; the JDK has its own.
        if (System.getProperty(key) != null) return;
        try {
            Class.forName(value);
            System.setProperty(key, value);
        } catch (ClassNotFoundException cnfe) {} // Use the platform's
    }
}
