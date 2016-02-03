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
 * ClassicGameManager
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-12
 * Updated : $Date: 2004/06/06 12:56:33 $
 *	     $Revision: 1.2 $
 */

package se.sics.tac.server.classic;
import java.util.logging.Level;
import java.util.logging.Logger;

import se.sics.tac.server.BidHandler;
import se.sics.tac.server.Game;
import se.sics.tac.server.GameHandler;
import se.sics.tac.server.GameManager;
import se.sics.tac.server.GameParamHandler;
import se.sics.tac.server.InfoManager;
import se.sics.tac.server.Market;
import se.sics.tac.server.QuoteHandler;
import se.sics.tac.server.SystemHandler;
import se.sics.tac.server.TACServer;
import se.sics.tac.server.TransactionHandler;
import se.sics.tac.server.User;

public class ClassicGameManager extends GameManager {

  private final static Logger log =
    Logger.getLogger(ClassicGameManager.class.getName());

  public final static String TAC_CLASSIC = "tacClassic";
  private final static String TAC_CLASSIC_NAME = "TAC Classic";

  private static final boolean ENABLE_SHORT_GAMES = false;

  protected void registerAt(InfoManager infoManager) {
    infoManager.addGameManager(TAC_CLASSIC, TAC_CLASSIC_NAME, this);
    // Add some shorter games too
//     addShortGame(infoManager, 9);
//     addShortGame(infoManager, 6);
  }

  private void addShortGame(InfoManager infoManager, int minutes) {
    infoManager.addGameManager(TAC_CLASSIC + '-'
			       + (ClassicMarket.DEFAULT_GAME_LENGTH / 60000
				  - minutes),
			       TAC_CLASSIC_NAME + " (" + minutes + " min)",
			       this);
  }

  protected void registerAt(TACServer tacServer) {
    GameHandler gameHandler = new GameHandler(TAC_CLASSIC);
    GameParamHandler gameParamHandler = new GameParamHandler();
    BidHandler bidHandler = new BidHandler();
    QuoteHandler quoteHandler = new QuoteHandler();
    TransactionHandler transHandler = new TransactionHandler();
    SystemHandler systemHandler = new SystemHandler();
    bidHandler.registerAt(tacServer);
    transHandler.registerAt(tacServer);
    gameHandler.registerAt(tacServer);
    gameParamHandler.registerAt(tacServer);
    quoteHandler.registerAt(tacServer);
    systemHandler.registerAt(tacServer);
  }

  public Game createGame(String gameType) {
    if (gameType.startsWith(TAC_CLASSIC)) {
      int len = gameType.length();
      int clen = TAC_CLASSIC.length();
      if (len == clen) {
	// Default TAC Classic game
	return new Game(gameType, ClassicMarket.DEFAULT_GAME_LENGTH, 8);

      } else if (ENABLE_SHORT_GAMES
		 && (len == clen + 2) && (gameType.charAt(clen) == '-')) {
	int digit = gameType.charAt(clen + 1) - '0';
	if (digit > 0 && digit < 10) {
	  return new Game(gameType, ClassicMarket.DEFAULT_GAME_LENGTH
			  - digit * 60 * 1000, 8);
	}
      }
    }
    return null;
  }

  public int getGameLength(String gameType) {
    if (ENABLE_SHORT_GAMES && gameType.startsWith(TAC_CLASSIC)) {
      int len = gameType.length();
      int clen = TAC_CLASSIC.length();
      if (len == clen) {
	// default tac classic game
	return ClassicMarket.DEFAULT_GAME_LENGTH;

      } else if ((len == clen + 2) && (gameType.charAt(clen) == '-')) {
	int digit = gameType.charAt(clen + 1) - '0';
	if (digit > 0 && digit < 10) {
	  return ClassicMarket.DEFAULT_GAME_LENGTH - digit * 60 * 1000;
	}
      }
    }
    return ClassicMarket.DEFAULT_GAME_LENGTH;
  }

  public Market createMarket(Game game) {
    InfoManager infoManager = getInfoManager();
    Market market = new ClassicMarket(infoManager, game);

    // Fill the game with dummy users if not already full
    fillGameWithDummies(game);

    // Must add builtin agents for all dummies if they should do something
    for (int i = 0, n = game.getNumberOfParticipants(); i < n; i++) {
      User user = game.getParticipant(i);
      if (user.isDummyUser()) {
	market.addBuiltinAgent(new DummyAgent(market, user));
      }
    }
    return market;
  }

} // ClassicGameManager
