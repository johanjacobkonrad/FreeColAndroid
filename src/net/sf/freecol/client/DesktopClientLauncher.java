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

package net.sf.freecol.client;

import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import net.sf.freecol.FreeCol;
import net.sf.freecol.client.gui.SplashScreen;
import net.sf.freecol.common.FreeColException;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.util.Utils;


/**
 * Starts the desktop (Swing) client on behalf of {@link FreeCol}.
 *
 * {@code FreeCol} loads this class by name, so that the launcher and
 * the shared engine do not depend on the desktop client.
 */
public final class DesktopClientLauncher implements FreeCol.DesktopClient {

    private static final Logger logger
        = Logger.getLogger(DesktopClientLauncher.class.getName());


    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String,String> getSpecialOptions() throws FreeColException {
        final Map<String,String> options = ClientOptions.getSpecialOptions();
        final Map<String,String> result = new HashMap<>();
        copy(options, ClientOptions.LANGUAGE, result, LANGUAGE);
        copy(options, ClientOptions.USE_PIXMAPS, result, USE_PIXMAPS);
        copy(options, ClientOptions.USE_OPENGL, result, USE_OPENGL);
        copy(options, ClientOptions.USE_XRENDER, result, USE_XRENDER);
        return result;
    }

    private static void copy(Map<String,String> from, String fromKey,
                             Map<String,String> to, String toKey) {
        String value = from.get(fromKey);
        if (value != null) to.put(toKey, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(final InputStream splashStream, final String fontName,
                      final int[] windowSize, final boolean sound,
                      final boolean introVideo, final boolean debugStart,
                      final boolean fastStart) {
        final Dimension size = (windowSize == null) ? null
            : new Dimension(windowSize[0], windowSize[1]);
        SwingUtilities.invokeLater(() -> {
            /*
             * Please do NOT move the splash screen into SwingGUI again. Make a separate
             * interface if this needs to be pluggable.
             *
             * The Swing-classes need to be constructed in the EDT -- and the splash screen
             * is never displayed correctly if SwingGUI is loaded correctly in the EDT.
             *
             * In addition, the splash screen should not be displayed at the same time
             * as a fullscreen frame.
             */
            final SplashScreen splashScreen = createSplashScreen(splashStream);
            SwingUtilities.invokeLater(() -> {
                Specification spec = null;
                File savegame = FreeColDirectories.getSavegameFile();
                if (debugStart) {
                    spec = FreeCol.getRulesSpecification();
                } else if (fastStart) {
                    if (savegame == null) {
                        // continue last saved game if possible,
                        // otherwise start a new one
                        savegame = FreeColDirectories.getLastSaveGameFile();
                        if (savegame == null) {
                            spec = FreeCol.getRulesSpecification();
                        }
                    }
                    // savegame was specified on command line
                }
                new FreeColClient(splashScreen, fontName, size,
                                  (String)null, sound, introVideo,
                                  savegame, spec);
            });
        });
    }

    private static SplashScreen createSplashScreen(InputStream splashStream) {
        SplashScreen splashScreen = null;
        if (splashStream != null) {
            try {
                final GraphicsDevice defaultScreenDevice = Utils.getGoodGraphicsDevice();
                splashScreen = new SplashScreen(defaultScreenDevice, splashStream);
                splashScreen.setVisible(true);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Splash screen failure", e);
            }
        }
        return splashScreen;
    }

    /**
     * Wrapper for the test suite to start a test client.
     *
     * @param spec The {@code Specification} to use in the new client.
     * @return The new {@code FreeColClient}.
     */
    public static FreeColClient startTestClient(Specification spec) {
        FreeCol.setHeadless(true);
        return new FreeColClient(null, (String)null, (Dimension)null,
                                 (String)null, false, false, (File)null, spec);
    }
}
