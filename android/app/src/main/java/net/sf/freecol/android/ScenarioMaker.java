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
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.sf.freecol.common.io.FreeColSavegameFile;
import net.sf.freecol.common.model.AbstractUnit;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Force;
import net.sf.freecol.common.model.Monarch;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.server.FreeColServer;
import net.sf.freecol.server.model.ServerGame;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;


/**
 * Makes the test game "Start near independence" (a hidden option on
 * the start screen): the AI plays every nation for a while, then one
 * nation that has coastal colonies becomes the human player's, with
 * enough rebels to declare, an army to fight with, and a small royal
 * army so the war can be played to the end in a sitting.  A desktop
 * tool (android/make-scenario.sh); the result goes in the app's assets.
 */
public final class ScenarioMaker {

    /** The human player's user name, as GameSession logs in. */
    private static final String USER = "Player";

    private ScenarioMaker() {} // Static only

    public static void main(String[] args) throws Exception {
        File data = new File(args[0]);
        File out = new File(args[1]);
        int turns = (args.length > 2) ? Integer.parseInt(args[2]) : 60;

        // 1. Let the AI play every nation for a while.
        Engine.init(data);
        Specification spec = Engine.loadSpecification();
        FreeColServer server = new FreeColServer(false, true, spec,
            InetAddress.getLoopbackAddress(), -1, "scenario");
        server.startGame();
        ServerGame game = server.getGame();
        Player first = game.getCurrentPlayer();
        if (first == null) first = game.getFirstPlayer();
        game.setCurrentPlayer(first);
        net.sf.freecol.common.networking.ChangeSet cs
            = new net.sf.freecol.common.networking.ChangeSet();
        cs.add(net.sf.freecol.common.networking.ChangeSet.See.all(),
               new net.sf.freecol.common.networking.SetCurrentPlayerMessage(first));
        game.sendToAll(cs);
        int start = game.getTurn().getNumber();
        long t0 = System.currentTimeMillis();
        while (game.getTurn().getNumber() - start < turns) {
            Thread.sleep(250);
            if (System.currentTimeMillis() - t0 > 40 * 60_000L) {
                throw new RuntimeException("Timed out at " + game.getTurn());
            }
        }
        File raw = File.createTempFile("scenario", ".fsg");
        server.saveGame(raw, null, null);
        server.shutdown();
        System.out.println("AI game saved at " + game.getTurn());

        // 2. Load it (nothing runs until players log in) and set it up.
        server = new FreeColServer(new FreeColSavegameFile(raw), (Specification)null,
            InetAddress.getLoopbackAddress(), -1, "scenario");
        game = server.getGame();
        spec = game.getSpecification();
        List<ServerPlayer> candidates = new ArrayList<>();
        for (Player p : game.getLiveEuropeanPlayers().collect(
                 java.util.stream.Collectors.toList())) {
            if (p.isREF()) continue;
            boolean coastal = p.getColonyList().stream().anyMatch(Colony::isConnectedPort);
            if (coastal) candidates.add((ServerPlayer)p);
        }
        if (candidates.isEmpty()) throw new RuntimeException("No nation with a port");
        candidates.sort(Comparator.comparingInt((ServerPlayer p) -> p.getColonyList().size())
                        .reversed());
        ServerPlayer me = candidates.get(0);
        me.setAI(false);
        me.setName(USER);
        // The human player runs the game (starts it once loaded).
        java.lang.reflect.Field admin = Player.class.getDeclaredField("admin");
        admin.setAccessible(true);
        admin.setBoolean(me, true);
        if (me.getNewLandName() == null) me.setNewLandName("New Land");
        me.setGold(Math.max(me.getGold(), 3000));

        // Rebels: about 70% in every colony.
        for (Colony c : me.getColonyList()) {
            int want = Colony.LIBERTY_PER_REBEL * c.getUnitCount() * 7 / 10;
            if (c.getLiberty() < want) c.modifyLiberty(want - c.getLiberty());
        }

        // An army in the biggest port.
        Colony port = me.getColonyList().stream().filter(Colony::isConnectedPort)
            .max(Comparator.comparingInt(Colony::getUnitCount)).get();
        Tile tile = port.getTile();
        UnitType vet = spec.getUnitType("model.unit.veteranSoldier");
        UnitType art = spec.getUnitType("model.unit.artillery");
        for (int i = 0; i < 4; i++) {
            new ServerUnit(game, tile, me, vet, spec.getRole("model.role.soldier"));
        }
        for (int i = 0; i < 2; i++) {
            new ServerUnit(game, tile, me, vet, spec.getRole("model.role.dragoon"));
        }
        for (int i = 0; i < 2; i++) new ServerUnit(game, tile, me, art);
        me.invalidateCanSeeTiles();

        // A small royal army, so the war ends in a sitting.
        Monarch monarch = me.getMonarch();
        Force ref = monarch.getExpeditionaryForce();
        ref.clearLandUnits();
        ref.clearNavalUnits();
        ref.add(new AbstractUnit("model.unit.kingsRegular", "model.role.infantry", 4));
        ref.add(new AbstractUnit("model.unit.kingsRegular", "model.role.cavalry", 2));
        ref.add(new AbstractUnit("model.unit.artillery", "model.role.default", 1));
        ref.add(new AbstractUnit("model.unit.manOWar", "model.role.default", 2));

        // It starts on our turn.
        game.setCurrentPlayer(me);
        server.saveGame(out, null, null);
        server.shutdown();
        raw.delete();
        System.out.println("Scenario: " + me.getNation().getId() + " with "
            + me.getColonyList().size() + " colonies, rebels " + me.getSoL()
            + "%, army in " + port.getName() + ", " + game.getTurn()
            + " -> " + out + " (" + out.length() + " bytes)");
        System.out.println("Can declare: " + (me.checkDeclareIndependence() == null
            ? "yes" : net.sf.freecol.common.i18n.Messages.message(me.checkDeclareIndependence())));
        System.exit(0);
    }
}
