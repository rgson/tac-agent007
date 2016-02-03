/**
 * SICS TAC Classic Server
 * http://www.sics.se/tac/    tac-dev@sics.se
 *
 * Copyright (c) 2001-2004 SICS AB. All rights reserved.
 *
 * SICS grants you the right to use, modify, and redistribute this
 * software for noncommercial purposes, on the conditions that you:
 * (1) retain the original headers, including the copyright notice and
 * this text, (2) clearly document the difference between any derived
 * software and the original, and (3) acknowledge your use of this
 * software in pertaining publications and reports.  SICS provides
 * this software "as is", without any warranty of any kind.  IN NO
 * EVENT SHALL SICS BE LIABLE FOR ANY DIRECT, SPECIAL OR INDIRECT,
 * PUNITIVE, INCIDENTAL OR CONSEQUENTIAL LOSSES OR DAMAGES ARISING OUT
 * OF THE USE OF THE SOFTWARE.
 *
 * -----------------------------------------------------------------
 *
 * GameParamHandler
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : Sun Jun 06 14:25:41 2004
 * Updated : $Date: 2004/09/14 11:31:15 $
 *           $Revision: 1.3 $
 */
package se.sics.tac.server;
import java.util.logging.Logger;

/**
 */
public class GameParamHandler implements MessageHandler {

  private static final Logger log =
    Logger.getLogger(GameParamHandler.class.getName());

  private final static int AUCTIONS = 0;
  private final static int PARAMETERS = 1;
  private final static int CONSTANTS = 2;

  public GameParamHandler() {
  }

  public void registerAt(TACServer server) {
    server.addMessageHandler("getGameAuctionIDs", this, true);
    server.addMessageHandler("getGameParams", this, true);
    server.addMessageHandler("getGameConsts", this, true);
  }

  public void handleMessage(InfoManager infoManager, TACMessage message) {
    String type = message.getType();

    if ("getGameAuctionIDs".equals(type)) {
      handleGameParams(infoManager, message, AUCTIONS);

    } else if ("getGameParams".equals(type)) {
      handleGameParams(infoManager, message, PARAMETERS);

    } else if ("getGameConsts".equals(type)) {
      handleGameParams(infoManager, message, CONSTANTS);

    }
  }

  private void handleGameParams(InfoManager infoManager, TACMessage message,
				int type) {
    Market market = infoManager.getMarket();
    int gameID = getInt(message, "gameID", -1);
    Game game;
    User user;

    if (gameID <= 0) {
      message.replyMissingField("gameID");

    } else if (market == null) {
      // Should check for game existance and game complete. FIX THIS!!!
      message.replyError(TACException.GAME_FUTURE);

    } else if ((game = market.getGame()).getGameID() != gameID) {
      // Should also check for game existance. FIX THIS!!!!
      message.replyError(game.getGameID() < gameID
			 ? TACException.GAME_FUTURE
			 : TACException.GAME_COMPLETE);

    } else if (!game.isParticipant(user = message.getUser())) {
      message.replyError(TACException.NOT_MEMBER_OF_GAME);

    } else if (type == PARAMETERS) {
      message.reply(market.generateGameParams(user),
		    TACException.NO_ERROR);

    } else if (type == AUCTIONS) {
      message.reply(market.generateGameAuctionIDs(user),
		    TACException.NO_ERROR);

    } else if (type == CONSTANTS) {
      // Game constants
      message.reply(market.generateGameConstants(user),
		    TACException.NO_ERROR);
    }
  }

  private int getInt(TACMessage message, String param, int defaultValue) {
    while (message.nextTag()) {
      if (message.isTag(param)) {
	return message.getValueAsInt(defaultValue);
      }
    }
    return defaultValue;
  }

  private String getString(TACMessage message, String param,
			   String defaultValue) {
    while (message.nextTag()) {
      if (message.isTag(param)) {
	return message.getValue(defaultValue);
      }
    }
    return defaultValue;
  }

} // GameParamHandler
