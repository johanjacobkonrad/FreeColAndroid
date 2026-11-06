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

import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Direction;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Resource;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TileType;
import net.sf.freecol.common.model.Unit;


/**
 * FreeCol's isometric map view, drawn with its own artwork.
 *
 * Tiles are 128x64 diamonds in staggered rows: odd rows are shifted
 * half a tile to the right and each row is half a tile lower than
 * the last, as in the desktop game.
 *
 * Different neighbouring land terrains fade into each other through
 * the desktop's masks; rivers use its per-tile river pictures (and
 * forests with a gap for them); roads curve through a tile they pass
 * straight through.  Water is not animated, and river mouths into the
 * sea are not drawn.
 */
public final class IsometricRenderer implements MapRenderer {

    public static final int TILE_W = 128, TILE_H = 64;
    private static final int HALF_W = TILE_W / 2, HALF_H = TILE_H / 2;
    /** How far units are drawn above the tile centre (desktop value). */
    private static final int UNIT_OFFSET = 20;
    /** Where the unit's owner/state chip goes (desktop value). */
    private static final int CHIP_X = 25;

    private final Art art;


    public IsometricRenderer(Art art) {
        this.art = art;
    }


    // Geometry

    private static float left(int x, int y) {
        return x * TILE_W + (y & 1) * HALF_W;
    }

    private static float top(int y) {
        return y * HALF_H;
    }

    @Override
    public float worldWidth(Map map) {
        return map.getWidth() * TILE_W + HALF_W;
    }

    @Override
    public float worldHeight(Map map) {
        return (map.getHeight() + 1) * HALF_H;
    }

    @Override
    public float centreX(Tile tile) {
        return left(tile.getX(), tile.getY()) + HALF_W;
    }

    @Override
    public float centreY(Tile tile) {
        return top(tile.getY()) + HALF_H;
    }

    @Override
    public Tile tileAt(Map map, Viewport v, float sx, float sy) {
        float wx = v.toWorldX(sx), wy = v.toWorldY(sy);
        int row0 = (int)Math.floor(wy / HALF_H);
        Tile best = null;
        float bestScore = Float.MAX_VALUE;
        for (int r = row0 - 2; r <= row0 + 1; r++) {
            if (r < 0 || r >= map.getHeight()) continue;
            int c = (int)Math.floor((wx - (r & 1) * HALF_W) / TILE_W);
            if (c < 0 || c >= map.getWidth()) continue;
            float cx = left(c, r) + HALF_W, cy = top(r) + HALF_H;
            float score = Math.abs(wx - cx) / HALF_W
                + Math.abs(wy - cy) / HALF_H;
            if (score < bestScore) {
                bestScore = score;
                best = map.getTile(c, r);
            }
        }
        return (bestScore <= 1.2f) ? best : null;
    }


    // Drawing

    @Override
    public void draw(Surface s, Map map, Player me, Unit selected, Viewport v,
                     java.util.function.Predicate<Tile> only, boolean units) {
        // Tall pictures (forests, hills, units) reach up into the rows
        // above, so draw a few extra rows below the screen's edge.
        int row0 = Math.max(0, (int)Math.floor(v.toWorldY(0) / HALF_H) - 1);
        int row1 = Math.min(map.getHeight() - 1,
            (int)Math.ceil(v.toWorldY(v.height) / HALF_H) + 3);
        int col0 = Math.max(0, (int)Math.floor(v.toWorldX(0) / TILE_W) - 1);
        int col1 = Math.min(map.getWidth() - 1,
            (int)Math.ceil(v.toWorldX(v.width) / TILE_W) + 1);

        // Pass 1: the ground, so nothing tall is covered by later rows.
        for (int y = row0; y <= row1; y++) {
            for (int x = col0; x <= col1; x++) {
                Tile t = map.getTile(x, y);
                if (t != null && (only == null || only.test(t))) {
                    drawGround(s, t, me, v);
                }
            }
        }
        // Pass 2: everything standing on the ground, back to front.
        for (int y = row0; y <= row1; y++) {
            for (int x = col0; x <= col1; x++) {
                Tile t = map.getTile(x, y);
                if (t != null && t.isExplored()
                    && (only == null || only.test(t))) {
                    drawItems(s, t, me, selected, v, units);
                }
            }
        }
        // The selected unit's destination.
        // Pass 3: settlement names on top of everything, so forests
        // and hills in front of a colony do not hide them.
        if (units) {
            for (int y = row0; y <= row1; y++) {
                for (int x = col0; x <= col1; x++) {
                    Tile t = map.getTile(x, y);
                    if (t != null && t.isExplored() && t.getSettlement() != null
                        && (only == null || only.test(t))) {
                        drawLabel(s, t.getSettlement(), t, v);
                    }
                }
            }
        }
        if (units && selected != null
            && selected.getDestination() instanceof Tile) {
            Tile d = (Tile)selected.getDestination();
            image(s, art.get("image.tile.tileSelect"), d, 0, 0, v, false);
        }
    }

    private void drawGround(Surface s, Tile t, Player me, Viewport v) {
        final int x = t.getX(), y = t.getY();
        final int seed = seed(x, y);
        TileType type = t.getType();
        if (!t.isExplored() || type == null) {
            image(s, art.get("image.tile.model.tile.unexplored.center", seed),
                  t, 0, 0, v, false);
            return;
        }
        boolean fog = !me.canSee(t);
        // Water is animated on the desktop; its variations are frames,
        // so use the first one everywhere.
        image(s, art.get("image.tile." + type.getId() + ".center",
                         type.isWater() ? 0 : seed), t, 0, 0, v, fog);
        if (t.isLand()) drawTransitions(s, t, v, fog);
        int style = t.getStyle();
        if (!t.isLand() && style > 0) {
            int edge = style >> 4, corner = style & 15;
            if (edge > 0) {
                image(s, art.get("image.tile.model.tile.beach.edge" + edge, seed),
                      t, 0, 0, v, fog);
            }
            if (corner > 0) {
                image(s, art.get("image.tile.model.tile.beach.corner" + corner, seed),
                      t, 0, 0, v, fog);
            }
        }
        for (Direction d : Direction.values()) {
            Tile n = t.getNeighbourOrNull(d);
            if (n != null && !n.isExplored()) {
                image(s, art.get("image.tile.model.tile.unexplored.border."
                                 + d.toString(), seed), t, 0, 0, v, false);
            }
        }
    }

    /** The four sides a tile blends towards its neighbours on. */
    private static final Direction[] BLEND = {
        Direction.NE, Direction.SW, Direction.NW, Direction.SE };

    /**
     * Soft edges: fade a different neighbouring land terrain into
     * this tile through the desktop's masks.  Water edges keep their
     * beaches.
     */
    private void drawTransitions(Surface s, Tile t, Viewport v, boolean fog) {
        for (Direction d : BLEND) {
            Tile n = t.getNeighbourOrNull(d);
            if (n == null || !n.isExplored() || !n.isLand()
                || n.getType() == null || n.getType() == t.getType()) continue;
            String terrain = "image.tile." + n.getType().getId() + ".center";
            String mask = "image.mask." + d.toString().toLowerCase(java.util.Locale.ROOT);
            // The same mask on both sides of an edge; never the last,
            // almost straight one (as the desktop does).
            Tile seedTile = (d == Direction.NW || d == Direction.NE) ? t : n;
            int count = art.variationCount(mask);
            int pick = (count > 1)
                ? Math.floorMod(seed(seedTile.getX(), seedTile.getY()), count - 1) : 0;
            Art.Img img = art.masked(terrain, mask, pick);
            if (img != null) image(s, img, t, 0, 0, v, fog);
        }
    }

    private void drawItems(Surface s, Tile t, Player me, Unit selected,
                           Viewport v, boolean units) {
        final boolean fog = !me.canSee(t);
        final int seed = seed(t.getX(), t.getY());
        final TileType type = t.getType();
        if (type == null) return;

        // Ploughed land and other completed improvements with a
        // picture of their own (roads and rivers are drawn elsewhere).
        for (net.sf.freecol.common.model.TileImprovement ti : t.getCompleteTileImprovements()) {
            if (ti.isRoad() || ti.isRiver()) continue;
            Art.Img img = art.get("image.tile." + ti.getType().getId());
            if (img != null) image(s, img, t, 0, 0, v, fog);
        }
        // Rivers: one picture per combination of sides it flows
        // through (minor and major).
        net.sf.freecol.common.model.TileImprovementStyle rs = t.getRiverStyle();
        if (t.isLand() && t.hasRiver() && rs != null) {
            Art.Img river = art.get("image.tile.model.improvement.river.s" + rs.getString(), 0);
            if (river != null) image(s, river, t, 0, 0, v, fog);
        }
        if (t.hasRoad() && t.getRoad().isComplete()) drawRoad(s, t, v);

        Resource res = t.getResource();
        if (res != null) {
            centred(s, art.get("image.tileitem." + res.getType().getId()), t, v, fog);
        }
        if (t.hasLostCityRumour()) {
            centred(s, art.get("image.tileitem.lostCityRumour"), t, v, fog);
        }
        Art.Img overlay = art.get("image.tileoverlay." + type.getId(), seed);
        if (overlay != null) {
            image(s, overlay, t, 0, TILE_H - overlay.height, v, fog);
        }
        if (t.isForested()) {
            Art.Img forest = null;
            if (t.hasRiver() && rs != null) {
                // A forest with a gap for the river.
                String mask = rs.getMask();
                forest = art.get("image.tileforest." + type.getId() + ".s"
                    + ("0000".equals(mask) ? "0100" : mask), 0);
            }
            if (forest == null) forest = art.get("image.tileforest." + type.getId(), seed);
            if (forest != null) {
                image(s, forest, t, (TILE_W - forest.width) / 2f,
                      TILE_H - forest.height, v, fog);
            }
        }

        Settlement settlement = t.getSettlement();
        if (settlement != null) drawSettlement(s, settlement, t, v, fog, units);

        // Units are only shown where the player can see right now.
        if (fog || !units) return;
        Unit shown = null;
        if (selected != null && selected.getTile() == t) {
            shown = (selected.isOnCarrier()) ? selected.getCarrier() : selected;
        } else if (settlement == null) {
            shown = t.getFirstUnit();
        }
        if (shown != null && hidden.contains(shown)) {
            // Being animated: show the next unit here, if any.
            Unit other = null;
            for (Unit u : t.getUnitList()) {
                if (!hidden.contains(u) && !u.isOnCarrier()) {
                    other = u;
                    break;
                }
            }
            shown = (settlement == null) ? other : null;
        }
        if (shown == null) return;
        if (selected != null && selected.getTile() == t) {
            centred(s, art.get("image.tile.unitSelect"), t, v, false);
        }
        drawUnit(s, shown, t, v, me);
        int count = t.getUnitCount();
        for (int i = 1; i < count && i < 8; i++) {
            float lx = left(t.getX(), t.getY()) + CHIP_X;
            float ly = top(t.getY()) + 18 + 2 * i;
            s.fillRect(v.toScreenX(lx), v.toScreenY(ly),
                       12 * v.scale, Math.max(1f, v.scale), 0xFFFFFFFF);
        }
    }

    /** Road lines from the tile centre towards neighbouring roads. */
    private void drawRoad(Surface s, Tile t, Viewport v) {
        final int colour = 0xFF804000;
        final float w = Math.max(1.5f, 3.5f * v.scale);
        float cx = v.toScreenX(centreX(t)), cy = v.toScreenY(centreY(t));
        // Where the road leaves this tile: halfway to each neighbour
        // with a road.
        java.util.List<float[]> ends = new java.util.ArrayList<>();
        for (Direction d : Direction.values()) {
            Tile n = t.getNeighbourOrNull(d);
            if (n == null || !n.hasRoad() || !n.getRoad().isComplete()) continue;
            float nx = v.toScreenX(centreX(n)), ny = v.toScreenY(centreY(n));
            ends.add(new float[] { (cx + nx) / 2, (cy + ny) / 2 });
        }
        boolean any = !ends.isEmpty();
        if (ends.size() == 2) {
            // Passing through: curve gently through the middle.
            s.drawCurve(ends.get(0)[0], ends.get(0)[1], cx, cy,
                        ends.get(1)[0], ends.get(1)[1], w, colour);
        } else {
            for (float[] e : ends) s.drawLine(cx, cy, e[0], e[1], w, colour);
        }
        if (!any && t.getSettlement() == null) {
            float r = 10 * v.scale;
            s.drawLine(cx - r, cy - r / 2, cx + r, cy + r / 2, w, colour);
            s.drawLine(cx - r, cy + r / 2, cx + r, cy - r / 2, w, colour);
        }
    }

    private void drawSettlement(Surface s, Settlement settlement, Tile t,
                                Viewport v, boolean fog, boolean label) {
        Art.Img img = art.get(settlementKey(settlement));
        if (img == null) return;
        float y = TILE_H - img.height;
        if (y > 0) y /= 2;
        image(s, img, t, (TILE_W - img.width) / 2f, y, v, fog);

    }

    /** A settlement's name (and colony size) on a dark band. */
    private void drawLabel(Surface s, Settlement settlement, Tile t, Viewport v) {
        float size = Math.max(11f, 14 * v.scale);
        if (14 * v.scale < 6) return; // Zoomed too far out to read
        String name = settlement.getName();
        if (settlement instanceof Colony) {
            name = ((Colony)settlement).getApparentUnitCount() + "  " + name;
        }
        float cx = v.toScreenX(centreX(t));
        float by = v.toScreenY(top(t.getY()) + TILE_H) + size * 0.2f;
        // Rough text width; the band only needs to be about right.
        float w = name.length() * size * 0.56f + size;
        Integer col = settlement.getOwner().getNationColor();
        int edge = (col == null) ? 0xFF808080 : (0xFF000000 | col);
        s.fillRect(cx - w / 2, by - size * 1.05f, w, size * 1.35f, 0xB0000000);
        s.fillRect(cx - w / 2, by + size * 0.3f - Math.max(1.5f, size * 0.12f),
                   w, Math.max(1.5f, size * 0.12f), edge);
        s.drawText(name, cx, by, size, 0xFFFFFFFF);
    }

    /** Units being animated: drawn by the animation, not on their tile. */
    private final java.util.Set<Unit> hidden = java.util.Collections.newSetFromMap(
        new java.util.concurrent.ConcurrentHashMap<>());

    /** Hide (or show again) a unit that an animation is drawing. */
    public void setHidden(Unit unit, boolean hide) {
        if (unit == null) return;
        if (hide) hidden.add(unit); else hidden.remove(unit);
    }

    /**
     * Draw a unit's picture (and its owner chip) centred on a point of
     * the world rather than a tile: a unit moving between tiles.
     *
     * @param img The picture (a frame of an animation), or null for the
     *     unit's own.
     * @param flip Mirror it (facing west).
     */
    public void drawUnitAt(Surface s, Unit unit, Art.Img img, float wcx, float wcy,
                           Viewport v, Player me, boolean flip) {
        if (img == null) img = art.get(unitKey(unit));
        if (img == null) return;
        float wx = wcx - img.width / 2f, wy = wcy - img.height / 2f - UNIT_OFFSET;
        if (flip) {
            s.drawImageFlipped(img, v.toScreenX(wx), v.toScreenY(wy),
                               img.width * v.scale, img.height * v.scale);
        } else {
            s.drawImage(img, v.toScreenX(wx), v.toScreenY(wy),
                        img.width * v.scale, img.height * v.scale, false);
        }
        Integer col = unit.getOwner().getNationColor();
        int argb = (col == null) ? 0xFF808080 : (0xFF000000 | col);
        float cx = wcx - HALF_W + CHIP_X, cy = wcy - HALF_H;
        s.fillRect(v.toScreenX(cx) - v.scale, v.toScreenY(cy) - v.scale,
                   14 * v.scale, 18 * v.scale, 0xFF000000);
        s.fillRect(v.toScreenX(cx), v.toScreenY(cy), 12 * v.scale, 16 * v.scale, argb);
    }

    private void drawUnit(Surface s, Unit unit, Tile t, Viewport v, Player me) {
        Art.Img img = art.get(unitKey(unit));
        if (img != null) {
            boolean faded = unit.getState() == Unit.UnitState.SENTRY;
            image(s, img, t, (TILE_W - img.width) / 2f,
                  (TILE_H - img.height) / 2f - UNIT_OFFSET, v, faded);
        }
        // Owner colour chip with the unit's state letter.
        Integer col = unit.getOwner().getNationColor();
        int argb = (col == null) ? 0xFF808080 : (0xFF000000 | col);
        float cx = left(t.getX(), t.getY()) + CHIP_X, cy = top(t.getY());
        s.fillRect(v.toScreenX(cx) - v.scale, v.toScreenY(cy) - v.scale,
                   14 * v.scale, 18 * v.scale, 0xFF000000);
        s.fillRect(v.toScreenX(cx), v.toScreenY(cy),
                   12 * v.scale, 16 * v.scale, argb);
        if (12 * v.scale >= 6) {
            String label = Messages.message(unit.getOccupationLabel(me, false));
            s.drawText(label, v.toScreenX(cx + 6), v.toScreenY(cy + 13),
                       12 * v.scale, isDark(argb) ? 0xFFFFFFFF : 0xFF000000);
        }
    }


    // Image keys, as in the desktop ImageLibrary

    /**
     * The art key for a unit's picture (also used by the unit list).
     *
     * @param unit The {@code Unit}.
     * @return Its image key.
     */
    public String unitKey(Unit unit) {
        String roleId = unit.getRole().getId();
        String roleQual = (Role.isDefaultRoleId(roleId)) ? ""
            : "." + Role.getRoleIdSuffix(roleId);
        String base = "image.unit." + unit.getType().getId() + roleQual;
        String key = base;
        if (unit.hasNativeEthnicity()
            && !unit.getType().hasAbility(
                net.sf.freecol.common.model.Ability.BORN_IN_INDIAN_SETTLEMENT)
            && art.has(base + ".native")) {
            key = base + ".native";
        }
        String extra = key + "." + unit.getOwner().getNationResourceKey();
        return (art.has(extra)) ? extra : key;
    }

    public String settlementKey(Settlement settlement) {
        String key = "image.tileitem." + settlement.getType().getId();
        if (settlement instanceof Colony) {
            Colony colony = (Colony)settlement;
            if (colony.isUndead()) {
                key += ".undead";
            } else {
                int count = colony.getApparentUnitCount();
                key += (count <= 3) ? ".small" : (count <= 7) ? ".medium"
                    : ".large";
                String stockade = colony.getStockadeKey();
                if (stockade != null) key += "." + stockade;
            }
            String extra = key + "." + settlement.getOwner().getNationResourceKey();
            if (art.has(extra)) key = extra;
        } else if (settlement instanceof IndianSettlement) {
            if (((IndianSettlement)settlement).hasMissionary()) key += ".mission";
        }
        return key;
    }


    // Helpers

    /** Draw an image at an offset from a tile's top left corner. */
    private void image(Surface s, Art.Img img, Tile t, float dx, float dy,
                       Viewport v, boolean faded) {
        if (img == null) return;
        float wx = left(t.getX(), t.getY()) + dx, wy = top(t.getY()) + dy;
        // Overlap by a fraction of a pixel to hide seams when zoomed.
        s.drawImage(img, v.toScreenX(wx), v.toScreenY(wy),
                    img.width * v.scale + 0.5f, img.height * v.scale + 0.5f,
                    faded);
    }

    /** Draw an image centred on a tile. */
    private void centred(Surface s, Art.Img img, Tile t, Viewport v,
                         boolean faded) {
        if (img == null) return;
        image(s, img, t, (TILE_W - img.width) / 2f, (TILE_H - img.height) / 2f,
              v, faded);
    }

    /** A stable pseudo-random number for a tile's art variation. */
    private static int seed(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        h ^= (h >>> 13);
        return h & 0x7fffffff;
    }

    private static boolean isDark(int argb) {
        int r = (argb >> 16) & 0xff, g = (argb >> 8) & 0xff, b = argb & 0xff;
        return (r * 299 + g * 587 + b * 114) / 1000 < 128;
    }
}
