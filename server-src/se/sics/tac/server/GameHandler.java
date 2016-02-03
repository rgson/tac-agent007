/**
 * SICS TAC Server
 * http://www.sics.se/tac/	  tac-dev@sics.se
 *
 * Copyright (c) 2001-2003 SICS AB. All rights reserved.
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
 * GameHandler
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-06
 * Updated : $Date: 2004/06/06 14:43:11 $
 *	     $Revision: 1.4 $
 */

package se.sics.tac.server;

public class GameHandler implements MessageHandler {

  private final static int CHECK = -1;
  private final static int DEFAULT = 0;
  private final static int JOIN = 1;

  protected final String defaultGameType;

  public GameHandler(String gameType) {
    if (gameType == null) throw new NullPointerException();
    this.defaultGameType = gameType;
  }

  public void registerAt(TACServer server) {
    server.addMessageHandler("nextGame", this, false);
    server.addMessageHandler("checkGame", this, false);
    server.addMessageHandler("joinGame", this, false);
  }

  public void handleMessage(InfoManager infoManager, TACMessage message) {
    String type = message.getType();
    if ("nextGame".equals(type)) {
      handleNextGame(infoManager, message, DEFAULT);

    } else if ("checkGame".equals(type)) {
      handleNextGame(infoManager, message, CHECK);

    } else if ("joinGame".equals(type)) {
      handleNextGame(infoManager, message, JOIN);
    }
  }

  private void handleNextGame(InfoManager infoManager, TACMessage message,
			      int behaviour) {
    // Perhaps allow comma-separated list of allowed game types
    // in priority order (new games created with first specified game type). FIX THIS!!!!!!!!
    String gameType = getString(message, "type", null);
    try {
      Game game = behaviour == CHECK
	? infoManager.checkGame(message.getUser(), gameType)
	: infoManager.getNextGame(message.getUser(),
				  (gameType == null
				   ? defaultGameType
				   : gameType),
				  behaviour == JOIN);
      int gameID;
      long startTime;
      if (game != null) {
	gameID = game.getGameID();
	startTime = game.getStartTime();
      } else {
	Market market = infoManager.getMarket();
	gameID = -1;
	startTime = infoManager.getServerTime() + 60000;
	if (market != null) {
	  long time = market.getGame().getEndTime() + 60000;
	  if (time > startTime) {
	    startTime = time;
	  }
	}
      }
      message.reply("<gameID>" + gameID + "</gameID>"
		    + "<startTime>" + (startTime / 1000) + "</startTime>"
		    + "<gameType>0</gameType>",
		    TACException.NO_ERROR);
    } catch (NoSuchManagerException e) {
      message.replyError(TACException.GAME_TYPE_NOT_SUPPORTED);
    }
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

} // GameHandler
