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
 * GameManager
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-09
 * Updated : $Date: 2004/05/04 15:48:18 $
 *	     $Revision: 1.1 $
 * Purpose :
 *
 */

package se.sics.tac.server;

public abstract class GameManager {

  private InfoManager infoManager;

  final void init(InfoManager infoManager) {
    if (infoManager == null) {
      throw new NullPointerException();
    }
    this.infoManager = infoManager;
    init();
    registerAt(infoManager);
  }

  /*********************************************************************
   * Utility methods
   *********************************************************************/

  protected InfoManager getInfoManager() {
    return infoManager;
  }

  /**
   * Fills any empty participant places with dummy users. Does nothing
   * if the game is already full.
   *
   * @param game the game to fill with dummy users.
   */
  protected void fillGameWithDummies(Game game) {
    // Fill the game with dummy agents
    User user = findHighestDummy(game);
    // This is usually done at game startup and all participants will be
    // sent to the InfoServer after startup so the explicit
    // notification is not be needed in that case but should be handled nicer. FIX THIS!!!
    if (!game.isFull()) {
      do {
	user = infoManager.getNextDummyUser(user);
      } while (game.addParticipant(user) && !game.isFull());
    }
  }

  protected boolean addDummyUser(Game game) {
    // Fill the game with dummy agents
    User user = findHighestDummy(game);
    // This is usually done at game startup and all participants will be
    // sent to the InfoServer after startup so the explicit
    // notification is not be needed in that case but should be handled nicer. FIX THIS!!!
    user = infoManager.getNextDummyUser(user);
    return game.addParticipant(user);
  }

  private User findHighestDummy(Game game) {
    User dummy = null;
    for (int i = 0, n = game.getNumberOfParticipants(); i < n; i++) {
      User user = game.getParticipant(i);
      if (user.isDummyUser()
	  && (dummy == null || user.getID() < dummy.getID())) {
	dummy = user;
      }
    }
    return dummy;
  }


  /*********************************************************************
   * Methods for GameManager sub classes
   *********************************************************************/

  /**
   * Initializes this game manager.
   */
  protected void init() {
  }

  /**
   * Registers all supported game types at the specified information manager.
   *
   * @param infoManager the information manager to which the game types
   *	should be registered
   */
  protected abstract void registerAt(InfoManager infoManager);

  /**
   * Register any message handlers at the specified TAC server
   *
   * @param server the TAC server where to register the message handlers
   */
  protected void registerAt(TACServer server) {
  }

  public abstract Game createGame(String gameType);

  public abstract int getGameLength(String gameType);

  public abstract Market createMarket(Game game);

} // GameManager
