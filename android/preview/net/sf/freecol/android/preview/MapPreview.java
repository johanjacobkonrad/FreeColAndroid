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
package net.sf.freecol.android.preview;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import net.sf.freecol.android.Engine;
import net.sf.freecol.android.GameSession;
import net.sf.freecol.android.map.Art;
import net.sf.freecol.android.map.IsometricRenderer;
import net.sf.freecol.android.map.MapRenderer;
import net.sf.freecol.android.map.Surface;
import net.sf.freecol.android.map.Viewport;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.Unit;


/**
 * Desktop preview of the Android map renderer: plays a few turns (the
 * ship sails west towards land) and saves what the phone would show
 * as PNG images.  Uses Java2D, so it is not part of the app.
 *
 * Usage: MapPreview DATA_DIR OUT_DIR [TURNS]
 */
public final class MapPreview {

    /** Art loaded from the data/default directory. */
    private static final class FileArt extends Art {
        private final File root;

        FileArt(File root) throws IOException {
            this.root = root;
            loadKeys();
        }

        @Override
        protected InputStream open(String path) throws IOException {
            return new FileInputStream(new File(root, path));
        }

        @Override
        protected String[] list(String dir) {
            return new File(root, dir).list();
        }

        @Override
        protected Img decode(InputStream in) throws IOException {
            BufferedImage bi = ImageIO.read(in);
            return (bi == null) ? null : new Img(bi, bi.getWidth(), bi.getHeight());
        }
    }

    /** A Java2D surface. */
    private static final class G2Surface implements Surface {
        private final Graphics2D g;

        G2Surface(Graphics2D g) {
            this.g = g;
        }

        @Override
        public void drawImage(Art.Img img, float x, float y, float w, float h,
                              boolean faded) {
            g.drawImage((BufferedImage)img.handle, Math.round(x), Math.round(y),
                        Math.round(x + w) - Math.round(x),
                        Math.round(y + h) - Math.round(y), null);
            if (faded) {
                // Darken just the image's pixels, like a colour filter.
                BufferedImage dark = new BufferedImage(img.width, img.height,
                    BufferedImage.TYPE_INT_ARGB);
                Graphics2D dg = dark.createGraphics();
                dg.drawImage((BufferedImage)img.handle, 0, 0, null);
                dg.setComposite(AlphaComposite.SrcIn);
                dg.setColor(new Color(0, 0, 0, 90));
                dg.fillRect(0, 0, img.width, img.height);
                dg.dispose();
                g.drawImage(dark, Math.round(x), Math.round(y),
                            Math.round(x + w) - Math.round(x),
                            Math.round(y + h) - Math.round(y), null);
            }
        }

        @Override
        public void fillRect(float x, float y, float w, float h, int argb) {
            g.setColor(new Color(argb, true));
            g.fillRect(Math.round(x), Math.round(y), Math.round(w), Math.round(h));
        }

        @Override
        public void drawLine(float x1, float y1, float x2, float y2,
                             float width, int argb) {
            g.setColor(new Color(argb, true));
            g.setStroke(new java.awt.BasicStroke(width,
                java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                               RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawLine(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y2));
        }

        @Override
        public void drawText(String text, float cx, float baseline, float size,
                             int argb) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size)));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(argb, true));
            g.drawString(text, cx - fm.stringWidth(text) / 2f, baseline);
        }
    }

    public static void main(String[] args) throws Exception {
        File data = new File(args[0]);
        File out = new File(args[1]);
        int turns = (args.length > 2) ? Integer.parseInt(args[2]) : 3;
        out.mkdirs();
        Engine.init(data);
        Art art = new FileArt(new File(data, "default"));
        MapRenderer renderer = new IsometricRenderer(art);

        GameSession s = new GameSession(new GameSession.Listener() {
                @Override public void onChanged() {}
                @Override public void onStatus(String text) {
                    System.out.println("  > " + text);
                }
            });
        s.start(Engine.loadSpecification());
        Unit ship = null;
        for (int t = 0; t < turns; t++) {
            while (!s.isMyTurn()) Thread.sleep(100);
            s.sync();
            Tile target;
            synchronized (s.lock()) {
                if (ship == null) ship = s.getSelectedUnit();
                Tile st = ship.getTile();
                target = s.getGame().getMap().getTile(Math.max(0, st.getX() - 30),
                                                       st.getY());
            }
            while (s.getSelectedUnit() != ship) {
                s.selectOnTile(ship.getTile());
                s.sync();
            }
            s.moveSelectedTo(target);
            s.sync();
            if (t < turns - 1) {
                s.endTurn();
                s.sync();
                while (s.isMyTurn()) Thread.sleep(100);
            }
        }
        while (s.getSelectedUnit() != ship) {
            s.selectOnTile(ship.getTile());
            s.sync();
        }

        // A phone screen in landscape, at two zoom levels.
        render(s, renderer, ship, 2400, 1080, 1.4f, new File(out, "map-near.png"));
        render(s, renderer, ship, 2400, 1080, 0.5f, new File(out, "map-far.png"));
        s.stop();
        System.out.println("Wrote previews to " + out);
        System.exit(0);
    }

    private static void render(GameSession s, MapRenderer r, Unit focus,
                               int w, int h, float scale, File file)
        throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                           RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        synchronized (s.lock()) {
            Map map = s.getGame().getMap();
            Viewport v = new Viewport();
            v.width = w;
            v.height = h;
            v.scale = scale;
            v.worldWidth = r.worldWidth(map);
            v.worldHeight = r.worldHeight(map);
            Tile t = focus.getTile();
            v.centreOn(r.centreX(t), r.centreY(t));
            r.draw(new G2Surface(g), map, s.getMyPlayer(),
                   s.getSelectedUnit(), v);
            // Check tapping works: the screen centre should be the focus.
            Tile picked = r.tileAt(map, v, v.toScreenX(r.centreX(t)),
                                   v.toScreenY(r.centreY(t)));
            System.out.println("Pick check at scale " + scale + ": "
                + (picked == t ? "ok" : "WRONG (" + picked + ")"));
        }
        g.dispose();
        ImageIO.write(img, "png", file);
    }
}
