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

package net.sf.freecol.client.control;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.FreeColException;
import net.sf.freecol.common.debug.FreeColDebugger;
import net.sf.freecol.common.model.*;
import net.sf.freecol.common.model.Game.LogoutReason;
import net.sf.freecol.common.model.Monarch.MonarchAction;
import net.sf.freecol.common.model.NativeTrade.NativeTradeAction;
import net.sf.freecol.common.model.NationOptions.NationState;
import net.sf.freecol.common.networking.Connection;
import net.sf.freecol.common.networking.Message;
import net.sf.freecol.common.networking.AddPlayerMessage;
import net.sf.freecol.common.networking.AnimateAttackMessage;
import net.sf.freecol.common.networking.AnimateMoveMessage;
import net.sf.freecol.common.networking.ChatMessage;
import net.sf.freecol.common.networking.ChooseFoundingFatherMessage;
import net.sf.freecol.common.networking.CloseMessage;
import net.sf.freecol.common.networking.DiplomacyMessage;
import net.sf.freecol.common.networking.DisconnectMessage;
import net.sf.freecol.common.networking.ErrorMessage;
import net.sf.freecol.common.networking.FeatureChangeMessage;
import net.sf.freecol.common.networking.FirstContactMessage;
import net.sf.freecol.common.networking.FountainOfYouthMessage;
import net.sf.freecol.common.networking.GameEndedMessage;
import net.sf.freecol.common.networking.GameStateMessage;
import net.sf.freecol.common.networking.HighScoresMessage;
import net.sf.freecol.common.networking.InciteMessage;
import net.sf.freecol.common.networking.IndianDemandMessage;
import net.sf.freecol.common.networking.LoginMessage;
import net.sf.freecol.common.networking.LogoutMessage;
import net.sf.freecol.common.networking.LootCargoMessage;
import net.sf.freecol.common.networking.MonarchActionMessage;
import net.sf.freecol.common.networking.MultipleMessage;
import net.sf.freecol.common.networking.NationSummaryMessage;
import net.sf.freecol.common.networking.NativeTradeMessage;
import net.sf.freecol.common.networking.NewLandNameMessage;
import net.sf.freecol.common.networking.NewRegionNameMessage;
import net.sf.freecol.common.networking.NewTradeRouteMessage;
import net.sf.freecol.common.networking.NewTurnMessage;
import net.sf.freecol.common.networking.PartialMessage;
import net.sf.freecol.common.networking.ReadyMessage;
import net.sf.freecol.common.networking.ReconnectMessage;
import net.sf.freecol.common.networking.RemoveMessage;
import net.sf.freecol.common.networking.ScoutSpeakToChiefMessage;
import net.sf.freecol.common.networking.SetAIMessage;
import net.sf.freecol.common.networking.SetAvailableMessage;
import net.sf.freecol.common.networking.SetColorMessage;
import net.sf.freecol.common.networking.SetCurrentPlayerMessage;
import net.sf.freecol.common.networking.SetDeadMessage;
import net.sf.freecol.common.networking.SetNationMessage;
import net.sf.freecol.common.networking.SetNationTypeMessage;
import net.sf.freecol.common.networking.SetStanceMessage;
import net.sf.freecol.common.networking.SpySettlementMessage;
import net.sf.freecol.common.networking.StartGameMessage;
import net.sf.freecol.common.networking.TrivialMessage;
import net.sf.freecol.common.networking.UpdateGameOptionsMessage;
import net.sf.freecol.common.networking.UpdateMapGeneratorOptionsMessage;
import net.sf.freecol.common.networking.UpdateMessage;
import net.sf.freecol.common.networking.VacantPlayersMessage;
import net.sf.freecol.common.networking.WrapperMessage;
import net.sf.freecol.common.option.OptionGroup;
import net.sf.freecol.server.FreeColServer.ServerState;


/**
 * Client-side handling of messages received from the server.
 *
 * This used to live in a {@code clientHandler} method on each message
 * class.  It was moved here so that the shared game engine
 * ({@code common} and {@code server}) does not depend on the desktop
 * client, which lets the engine run on platforms without Swing/AWT.
 */
public final class ClientMessageHandler {

    private static final Logger logger
        = Logger.getLogger(ClientMessageHandler.class.getName());

    @FunctionalInterface
    private interface Handler<T extends Message> {
        void handle(FreeColClient freeColClient, T message)
            throws FreeColException;
    }

    private static final Map<Class<?>, Handler<?>> handlers = new HashMap<>();

    private static <T extends Message> void register(Class<T> type,
                                                     Handler<T> handler) {
        handlers.put(type, handler);
    }

    static {
        register(AddPlayerMessage.class, ClientMessageHandler::addPlayer);
        register(AnimateAttackMessage.class, ClientMessageHandler::animateAttack);
        register(AnimateMoveMessage.class, ClientMessageHandler::animateMove);
        register(ChatMessage.class, ClientMessageHandler::chat);
        register(ChooseFoundingFatherMessage.class, ClientMessageHandler::chooseFoundingFather);
        register(CloseMessage.class, ClientMessageHandler::close);
        register(DiplomacyMessage.class, ClientMessageHandler::diplomacy);
        register(DisconnectMessage.class, ClientMessageHandler::disconnect);
        register(ErrorMessage.class, ClientMessageHandler::error);
        register(FeatureChangeMessage.class, ClientMessageHandler::featureChange);
        register(FirstContactMessage.class, ClientMessageHandler::firstContact);
        register(FountainOfYouthMessage.class, ClientMessageHandler::fountainOfYouth);
        register(GameEndedMessage.class, ClientMessageHandler::gameEnded);
        register(GameStateMessage.class, ClientMessageHandler::gameState);
        register(HighScoresMessage.class, ClientMessageHandler::highScores);
        register(InciteMessage.class, ClientMessageHandler::incite);
        register(IndianDemandMessage.class, ClientMessageHandler::indianDemand);
        register(LoginMessage.class, ClientMessageHandler::login);
        register(LogoutMessage.class, ClientMessageHandler::logout);
        register(LootCargoMessage.class, ClientMessageHandler::lootCargo);
        register(MonarchActionMessage.class, ClientMessageHandler::monarchAction);
        register(MultipleMessage.class, ClientMessageHandler::multiple);
        register(NationSummaryMessage.class, ClientMessageHandler::nationSummary);
        register(NativeTradeMessage.class, ClientMessageHandler::nativeTrade);
        register(NewLandNameMessage.class, ClientMessageHandler::newLandName);
        register(NewRegionNameMessage.class, ClientMessageHandler::newRegionName);
        register(NewTradeRouteMessage.class, ClientMessageHandler::newTradeRoute);
        register(NewTurnMessage.class, ClientMessageHandler::newTurn);
        register(PartialMessage.class, ClientMessageHandler::partial);
        register(ReadyMessage.class, ClientMessageHandler::ready);
        register(ReconnectMessage.class, ClientMessageHandler::reconnect);
        register(RemoveMessage.class, ClientMessageHandler::remove);
        register(ScoutSpeakToChiefMessage.class, ClientMessageHandler::scoutSpeakToChief);
        register(SetAIMessage.class, ClientMessageHandler::setAI);
        register(SetAvailableMessage.class, ClientMessageHandler::setAvailable);
        register(SetColorMessage.class, ClientMessageHandler::setColor);
        register(SetCurrentPlayerMessage.class, ClientMessageHandler::setCurrentPlayer);
        register(SetDeadMessage.class, ClientMessageHandler::setDead);
        register(SetNationMessage.class, ClientMessageHandler::setNation);
        register(SetNationTypeMessage.class, ClientMessageHandler::setNationType);
        register(SetStanceMessage.class, ClientMessageHandler::setStance);
        register(SpySettlementMessage.class, ClientMessageHandler::spySettlement);
        register(StartGameMessage.class, ClientMessageHandler::startGame);
        register(TrivialMessage.class, ClientMessageHandler::trivial);
        register(UpdateGameOptionsMessage.class, ClientMessageHandler::updateGameOptions);
        register(UpdateMapGeneratorOptionsMessage.class, ClientMessageHandler::updateMapGeneratorOptions);
        register(UpdateMessage.class, ClientMessageHandler::update);
        register(VacantPlayersMessage.class, ClientMessageHandler::vacantPlayers);
        register(WrapperMessage.class, ClientMessageHandler::wrapper);
    }

    private ClientMessageHandler() {} // Static only


    /**
     * Handle a message on the client side.
     *
     * The most specific registered handler for the message class (or
     * one of its superclasses) is used.
     *
     * @param freeColClient The {@code FreeColClient} handling the message.
     * @param message The {@code Message} to handle.
     * @exception FreeColException if there is a problem handling it.
     */
    @SuppressWarnings("unchecked")
    public static void handle(FreeColClient freeColClient, Message message)
        throws FreeColException {
        for (Class<?> c = message.getClass(); c != null && c != Object.class;
             c = c.getSuperclass()) {
            Handler<Message> h = (Handler<Message>)handlers.get(c);
            if (h != null) {
                h.handle(freeColClient, message);
                return;
            }
        }
        throw new FreeColException(message.getType() + " clientHandler NYI");
    }


    // Convenience methods formerly on Message

    private static InGameController igc(FreeColClient freeColClient) {
        return freeColClient.getInGameController();
    }

    private static PreGameController pgc(FreeColClient freeColClient) {
        return freeColClient.getPreGameController();
    }

    private static void invokeLater(FreeColClient freeColClient,
                                    Runnable runnable) {
        freeColClient.getGUI().invokeNowOrLater(runnable);
    }

    /**
     * Generic actions common to many messages: play a sound and/or
     * flush model messages if the server asked for it.
     */
    private static void clientGeneric(FreeColClient freeColClient,
                                      Message message) {
        final FreeColClient fcc = freeColClient;
        if (fcc.currentPlayerIsMyPlayer()) {
            final String soundId = message.peekStringAttribute("sound");
            final boolean flush
                = message.peekBooleanAttribute("flush", Boolean.FALSE);
            if (soundId != null || flush) {
                invokeLater(freeColClient, () -> {
                        if (soundId != null) igc(fcc).sound(soundId);
                        if (flush) igc(fcc).nextModelMessage();
                    });
            }
        }
    }


    // Per-message handlers

    private static void addPlayer(FreeColClient freeColClient,
        AddPlayerMessage msg) throws FreeColException {
        List<Player> players = msg.getPlayers();
        
        if (freeColClient.isInGame()) {
            igc(freeColClient).addPlayerHandler(players);
        } else {
            pgc(freeColClient).addPlayerHandler(players);
        }
    }

    private static void animateAttack(FreeColClient freeColClient,
        AnimateAttackMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final Player player = freeColClient.getMyPlayer();
        final Tile attackerTile = msg.getAttackerTile(game);
        final Tile defenderTile = msg.getDefenderTile(game);
        final boolean result = msg.getResult();
        final Unit attacker = msg.getAttacker(game);
        final Unit defender = msg.getDefender(game);

        if (attacker == null) {
            logger.warning("Attack animation missing attacker unit.");
            return;
        }
        if (defender == null) {
            logger.warning("Attack animation missing defender unit.");
            return;
        }
        if (attackerTile == null) {
            logger.warning("Attack animation for: " + player.getId()
                + " omitted attacker tile.");
        }
        if (defenderTile == null) {
            logger.warning("Attack animation for: " + player.getId()
                + " omitted defender tile.");
        }

        // This only performs animation, if required.  It does not
        // actually perform an attack.
        igc(freeColClient)
            .animateAttackHandler(attacker, defender,
                                  attackerTile, defenderTile, result);
        clientGeneric(freeColClient, msg);
    }

    private static void animateMove(FreeColClient freeColClient,
        AnimateMoveMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final Player player = freeColClient.getMyPlayer();
        final Tile oldTile = msg.getOldTile(game);
        final Tile newTile = msg.getNewTile(game);
        final Unit unit = msg.getUnit(game);

        if (unit == null) return;
        if (oldTile == null) {
            logger.warning("Animation for: " + player.getId()
                + " missing old Tile.");
            return;
        }
        if (newTile == null) {
            logger.warning("Animation for: " + player.getId()
                + " missing new Tile.");
            return;
        }

        // This only performs animation, if required.  It does not
        // actually change unit positions, which happens in an "update".
        igc(freeColClient).animateMoveHandler(unit, oldTile, newTile);
        clientGeneric(freeColClient, msg);
    }

    private static void chat(FreeColClient freeColClient,
        ChatMessage msg) throws FreeColException {
        final Color color = msg.getColor();
        final String sender = msg.getSender();
        final String text = msg.getMessage();
        final boolean isPrivate = msg.isPrivate();
        if (sender == null || text == null) return;

        if (freeColClient.isInGame()) {
            igc(freeColClient).chatHandler(sender, text, color, isPrivate);
        } else {
            pgc(freeColClient).chatHandler(sender, text, color, isPrivate);
        }
    }

    private static void chooseFoundingFather(FreeColClient freeColClient,
        ChooseFoundingFatherMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final List<FoundingFather> fathers = msg.getFathers(game);
        
        igc(freeColClient).chooseFoundingFatherHandler(fathers);
        clientGeneric(freeColClient, msg);
    }

    private static void close(FreeColClient freeColClient,
        CloseMessage msg) throws FreeColException {
        // This is a trivial handler to allow the server to signal to
        // the client that an offer that caused a popup (for example,
        // a native demand or diplomacy proposal) has not been
        // answered quickly enough and that the offering player has
        // assumed this player has refused-by-inaction, and therefore,
        // the popup needs to be closed.
        igc(freeColClient).closeHandler(msg.getPanel());
    }

    private static void diplomacy(FreeColClient freeColClient,
        DiplomacyMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final DiplomaticTrade agreement = msg.getAgreement();
        final FreeColGameObject our = msg.getOurFCGO(game);
        final FreeColGameObject other = msg.getOtherFCGO(game);
        final Unit extraUnit = msg.getExtraUnit();
        
        if (our == null) {
            logger.warning("Our FCGO omitted from diplomacy msg.");
            return;
        }
        if (other == null) {
            logger.warning("Other FCGO omitted from diplomacy msg.");
            return;
        }
        if (extraUnit != null) extraUnit.intern();

        igc(freeColClient).diplomacyHandler(our, other, agreement);
        clientGeneric(freeColClient, msg);
    }

    private static void disconnect(FreeColClient freeColClient,
        DisconnectMessage msg) throws FreeColException {
        ; // Do nothing on the client side
    }

    private static void error(FreeColClient freeColClient,
        ErrorMessage msg) throws FreeColException {
        final StringTemplate template = msg.getTemplate();
        final String message = msg.getMessage();
        
        if (freeColClient.isInGame()) {
            igc(freeColClient).errorHandler(template, message);
            clientGeneric(freeColClient, msg);
        } else {
            pgc(freeColClient).errorHandler(template, message);
        }
    }

    private static void featureChange(FreeColClient freeColClient,
        FeatureChangeMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final FreeColGameObject parent = msg.getParent(game);
        final List<FreeColObject> children = msg.getChildren();
        final boolean add = msg.getAdd();

        if (parent == null) {
            logger.warning("featureChange with null parent.");
            return;
        }
        if (children.isEmpty()) {
            logger.warning("featureChange with no children.");
            return;
        }

        igc(freeColClient).featureChangeHandler(parent, children, add);
        clientGeneric(freeColClient, msg);
    }

    private static void firstContact(FreeColClient freeColClient,
        FirstContactMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final Player player = msg.getPlayer(game);
        final Player other = msg.getOtherPlayer(game);
        final Tile tile = msg.getTile(game);
        final int n = msg.getSettlementCount();

        if (player == null || player != freeColClient.getMyPlayer()) {
            logger.warning("firstContact with bad player: " + player);
            return;
        }
        if (other == null || other == player || !other.isIndian()) {
            logger.warning("firstContact with bad other player: " + other);
            return;
        }
        if (tile != null && tile.getOwner() != other) {
            logger.warning("firstContact with bad tile: " + tile);
            return;
        }

        igc(freeColClient).firstContactHandler(player, other, tile, n);
        clientGeneric(freeColClient, msg);
    }

    private static void fountainOfYouth(FreeColClient freeColClient,
        FountainOfYouthMessage msg) throws FreeColException {
        final int n = msg.getMigrants();

        if (n <= 0) {
            logger.warning("Invalid migrants attribute: " + n);
            return;
        }

        igc(freeColClient).fountainOfYouthHandler(n);
        clientGeneric(freeColClient, msg);
    }

    private static void gameEnded(FreeColClient freeColClient,
        GameEndedMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final Player winner = msg.getWinner(game);
        final String highScore = msg.getScore();

        if (winner == null) {
            logger.warning("Invalid player for gameEnded");
            return;
        }
        FreeColDebugger.finishDebugRun(freeColClient, true);
        if (winner != freeColClient.getMyPlayer()) return;
        
        igc(freeColClient).gameEndedHandler(highScore);
    }

    private static void gameState(FreeColClient freeColClient,
        GameStateMessage msg) throws FreeColException {
        final ServerState state = msg.getState();
        if (state != null) freeColClient.setServerState(state);
    }

    private static void highScores(FreeColClient freeColClient,
        HighScoresMessage msg) throws FreeColException {
        final String key = msg.getKey();
        final List<HighScore> scores = msg.getScores();

        igc(freeColClient).highScoresHandler(key, scores);
        clientGeneric(freeColClient, msg);
    }

    private static void incite(FreeColClient freeColClient,
        InciteMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final Player player = freeColClient.getMyPlayer();
        final Unit unit = msg.getUnit(player);
        final IndianSettlement is = msg.getSettlement(unit);
        final Player enemy = msg.getEnemy(game);
        final int gold = msg.getGold();
        
        igc(freeColClient).inciteHandler(unit, is, enemy, gold);
        clientGeneric(freeColClient, msg);
    }

    private static void indianDemand(FreeColClient freeColClient,
        IndianDemandMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final Player player = freeColClient.getMyPlayer();
        final Unit unit = msg.getUnit(game);
        final Colony colony = msg.getColony(game);
        final GoodsType goodsType = msg.getType(game);
        final int amount = msg.getAmount();
        
        if (unit == null) {
            logger.warning("IndianDemand with null unit.");
            return;
        }
        if (colony == null) {
            logger.warning("IndianDemand with null colony");
            return;
        } else if (!player.owns(colony)) {
            throw new RuntimeException("Demand to anothers colony: " + colony);
        }

        igc(freeColClient).indianDemandHandler(unit, colony, goodsType, amount);
        clientGeneric(freeColClient, msg);
    }

    private static void login(FreeColClient freeColClient,
        LoginMessage msg) throws FreeColException {
        Game game = msg.getGame();
        freeColClient.getConnectController()
            .login(msg.getState(), game, msg.getUserName(),
                   msg.getSinglePlayer(), msg.getCurrentPlayer());
    }

    private static void logout(FreeColClient freeColClient,
        LogoutMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final Player player = msg.getPlayer(game);
        final LogoutReason reason = msg.getReason();

        if (player == null) return;

        if (freeColClient.isInGame()) {
            igc(freeColClient).logoutHandler(player, reason);

        } else {
            pgc(freeColClient).logoutHandler(player, reason);
        }
    }

    private static void lootCargo(FreeColClient freeColClient,
        LootCargoMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final Unit unit = msg.getWinner(game);
        final String loserId = msg.getLoserId();
        final List<Goods> goods = msg.getGoods();

        if (unit == null) return;

        igc(freeColClient).lootCargoHandler(unit, goods, loserId);
        clientGeneric(freeColClient, msg);
    }

    private static void monarchAction(FreeColClient freeColClient,
        MonarchActionMessage msg) throws FreeColException {
        final MonarchAction action = msg.getAction();
        final StringTemplate template = msg.getTemplate();
        final String key = msg.getMonarchKey();
        
        igc(freeColClient).monarchActionHandler(action, template, key);
        clientGeneric(freeColClient, msg);
    }

    private static void multiple(FreeColClient freeColClient,
        MultipleMessage msg) throws FreeColException {
        final Connection conn = freeColClient.askServer().getConnection();
        if (conn == null) return;

        if (!msg.getMessages().isEmpty()) {
            for (Message m : msg.getMessages()) {
                Message ret = conn.handle(m);
                assert ret == null;
            }
        }                 
        clientGeneric(freeColClient, msg);
    }

    private static void nationSummary(FreeColClient freeColClient,
        NationSummaryMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final Player other = msg.getPlayer(game);
        final NationSummary ns = msg.getNationSummary();

        igc(freeColClient).nationSummaryHandler(other, ns);
        clientGeneric(freeColClient, msg);
    }

    private static void nativeTrade(FreeColClient freeColClient,
        NativeTradeMessage msg) throws FreeColException {
        final NativeTradeAction action = msg.getAction();
        final NativeTrade nt = msg.getNativeTrade();

        igc(freeColClient).nativeTradeHandler(action, nt);
        clientGeneric(freeColClient, msg);
    }

    private static void newLandName(FreeColClient freeColClient,
        NewLandNameMessage msg) throws FreeColException {
        final Unit unit = msg.getUnit(freeColClient.getMyPlayer());
        final String defaultName = msg.getNewLandName();

        if (defaultName == null || !unit.hasTile()) return;

        igc(freeColClient).newLandNameHandler(unit, defaultName);
        clientGeneric(freeColClient, msg);
    }

    private static void newRegionName(FreeColClient freeColClient,
        NewRegionNameMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final Tile tile = msg.getTile(game);
        final Unit unit = msg.getUnit(freeColClient.getMyPlayer());
        final Region region = msg.getRegion(game);
        final String name = msg.getNewRegionName();

        if (name == null || region == null) return;

        igc(freeColClient).newRegionNameHandler(region, tile, unit, name);
        clientGeneric(freeColClient, msg);
    }

    private static void newTradeRoute(FreeColClient freeColClient,
        NewTradeRouteMessage msg) throws FreeColException {
        final TradeRoute tr = msg.getTradeRoute();
        if (tr == null) return;
        tr.intern();

        igc(freeColClient).newTradeRouteHandler(tr);
        clientGeneric(freeColClient, msg);
    }

    private static void newTurn(FreeColClient freeColClient,
        NewTurnMessage msg) throws FreeColException {
        final int turn = msg.getTurnNumber();

        if (turn < 0) {
            logger.warning("Invalid turn for newTurn: "
                + Integer.toString(turn) );
            return;
        }

        igc(freeColClient).newTurnHandler(turn);
        clientGeneric(freeColClient, msg);
    }

    private static void partial(FreeColClient freeColClient,
        PartialMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final String id = msg.peekStringAttribute(PartialMessage.ID_TAG);

        if (id == null) {
            logger.warning("Partial update is missing identifier attribute");
            return;
        }
        FreeColGameObject fcgo = game.getFreeColGameObject(id);
        if (fcgo == null) {
            logger.warning("Partial update of missing object: " + id);
            return;
        }
                
        if (freeColClient.isInGame()) {
            igc(freeColClient).partialHandler(fcgo, msg.peekStringAttributeMap());
        } else {
            logger.warning("Partial update when not in game.");
        }
        clientGeneric(freeColClient, msg);
    }

    private static void ready(FreeColClient freeColClient,
        ReadyMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final Player player = msg.getPlayer(game);
        final boolean ready = msg.getValue();

        if (player == null) return;

        pgc(freeColClient).readyHandler(player, ready);
    }

    private static void reconnect(FreeColClient freeColClient,
        ReconnectMessage msg) throws FreeColException {
        igc(freeColClient).reconnectHandler();
    }

    private static void remove(FreeColClient freeColClient,
        RemoveMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final FreeColGameObject divert = msg.getDivertObject(game);
        final List<FreeColGameObject> objects = msg.getRemovals(game);

        if (objects.isEmpty()) return;

        igc(freeColClient).removeHandler(objects, divert);
        clientGeneric(freeColClient, msg);
    }

    private static void scoutSpeakToChief(FreeColClient freeColClient,
        ScoutSpeakToChiefMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final Unit unit = msg.getUnit(game);
        final IndianSettlement is = msg.getSettlement(game);
        final String result = msg.getResult();

        igc(freeColClient).scoutSpeakToChiefHandler(unit, is, result);
        clientGeneric(freeColClient, msg);
    }

    private static void setAI(FreeColClient freeColClient,
        SetAIMessage msg) throws FreeColException {
        final Player player = msg.getPlayer(freeColClient.getGame());
        final boolean ai = msg.getAI();

        if (player == null) return;

        igc(freeColClient).setAIHandler(player, ai);
    }

    private static void setAvailable(FreeColClient freeColClient,
        SetAvailableMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final Specification spec = game.getSpecification();
        final Nation nation = msg.getNation(spec);
        final NationState nationState = msg.getNationState();

        pgc(freeColClient).setAvailableHandler(nation, nationState);
    }

    private static void setColor(FreeColClient freeColClient,
        SetColorMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final Specification spec = game.getSpecification();
        final Nation nation = msg.getNation(spec);
        final Color color = msg.getColor();
      
        if (nation == null) {
            logger.warning("Invalid nation: " + msg.toString());
            return;
        }
        if (color == null) {
            logger.warning("Invalid color: " + msg.toString());
            return;
        }

        pgc(freeColClient).setColorHandler(nation, color);
    }

    private static void setCurrentPlayer(FreeColClient freeColClient,
        SetCurrentPlayerMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final Player player = msg.getPlayer(game);

        if (player == null) {
            throw new FreeColException("Invalid player: "
                + msg.peekStringAttribute("player"));
        }

        igc(freeColClient).setCurrentPlayerHandler(player);
        clientGeneric(freeColClient, msg);
    }

    private static void setDead(FreeColClient freeColClient,
        SetDeadMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final Player player = msg.getPlayer(game);

        if (player == null) {
            logger.warning("Invalid player for setDead");
            return;
        }

        igc(freeColClient).setDeadHandler(player);
        clientGeneric(freeColClient, msg);
    }

    private static void setNation(FreeColClient freeColClient,
        SetNationMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final Player player = msg.getPlayer(game);
        final Nation nation = msg.getValue(game.getSpecification());

        if (player != null && nation != null) {
            player.setNation(nation);
            
            if (!freeColClient.isInGame()) {
                pgc(freeColClient).addPlayerHandler(List.of(player));
            }
        }
    }

    private static void setNationType(FreeColClient freeColClient,
        SetNationTypeMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final NationType nationType = msg.getValue(game.getSpecification());

        pgc(freeColClient).setNationTypeHandler(nationType);
    }

    private static void setStance(FreeColClient freeColClient,
        SetStanceMessage msg) throws FreeColException {
        final Game game = freeColClient.getGame();
        final Stance stance = msg.getStance();
        final Player p1 = msg.getFirstPlayer(game);
        final Player p2 = msg.getSecondPlayer(game);

        if (p1 == null) {
            logger.warning("Invalid player1 for setStance");
            return;
        }
        if (p2 == null) {
            logger.warning("Invalid player2 for setStance");
            return;
        }

        igc(freeColClient).setStanceHandler(stance, p1, p2);
        clientGeneric(freeColClient, msg);
    }

    private static void spySettlement(FreeColClient freeColClient,
        SpySettlementMessage msg) throws FreeColException {
        final Tile spyTile = msg.getSpyTile();

        igc(freeColClient).spySettlementHandler(spyTile);
        clientGeneric(freeColClient, msg);
    }

    private static void startGame(FreeColClient freeColClient,
        StartGameMessage msg) throws FreeColException {
        pgc(freeColClient).startGameHandler();
    }

    private static void trivial(FreeColClient freeColClient,
        TrivialMessage msg) throws FreeColException {
        throw new FreeColException(msg.getType() + " clientHandler NYI");
    }

    private static void updateGameOptions(FreeColClient freeColClient,
        UpdateGameOptionsMessage msg) throws FreeColException {
        final OptionGroup gameOptions = msg.getGameOptions();

        if (freeColClient.isInGame()) {
            ; // Ignored
        } else {
            pgc(freeColClient).updateGameOptionsHandler(gameOptions);
        }
        clientGeneric(freeColClient, msg);
    }

    private static void updateMapGeneratorOptions(FreeColClient freeColClient,
        UpdateMapGeneratorOptionsMessage msg) throws FreeColException {
        final OptionGroup mapOptions = msg.getMapGeneratorOptions();

        if (freeColClient.isInGame()) {
            ; // Ignore
        } else {
            pgc(freeColClient).updateMapGeneratorOptionsHandler(mapOptions);
        }
    }

    private static void update(FreeColClient freeColClient,
        UpdateMessage msg) throws FreeColException {
        if (freeColClient.isInGame()) {
            igc(freeColClient).updateHandler(msg.getChildren());
            clientGeneric(freeColClient, msg);
        } else {
            pgc(freeColClient).updateHandler(msg.getChildren());
        }
    }

    private static void vacantPlayers(FreeColClient freeColClient,
        VacantPlayersMessage msg) throws FreeColException {
        final List<String> vacant = msg.getVacantPlayers();

        freeColClient.setVacantPlayerNames(vacant);
    }

    private static void wrapper(FreeColClient freeColClient,
        WrapperMessage msg) throws FreeColException {
        if (msg.getMessage() != null) {
            handle(freeColClient, msg.getMessage());
        }
    }
}
