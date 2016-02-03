/**
 * SICS TAC Server - InfoServer
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
 * TACStore
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 9 April, 2002
 * Updated : $Date: 2004/09/14 11:27:01 $
 *	     $Revision: 1.12 $
 */

package se.sics.tac.is;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.botbox.util.ArrayUtils;
import se.sics.tac.log.TACGameInfo;

public abstract class TACStore {

  private static final Logger log =
    Logger.getLogger(TACStore.class.getName());

  public static final String LAST_GAME_ID = "lastGameID";
  public static final String LAST_UNIQUE_GAME_ID = "lastUniqueGameID";
  public static final String LAST_PLAYED_GAME_ID = "lastPlayID";
  public static final String LAST_AUCTION_ID = "lastAuctionID";
  public static final String LAST_TRANSACTION_ID = "lastTransactionID";
  public static final String LAST_BID_ID = "lastBidID";
  public static final String LAST_FINISHED_COMPETITION =
    "lastFinishedCompetition";

  public static final String ADMIN_NAME = "admin";

  // Flags for the game results
  public static final int ZERO_GAME = 1 << 5;

  /** Change operations (used in <i>type</i>Updated() methods) */
  protected static final int AGENT_JOINED = 1;
  protected static final int REMOVED = 2;
  protected static final int ADDED = 3;
  protected static final int CHANGED = 4;
  protected static final int STARTED = 5;
  protected static final int STOPPED = 6;
  protected static final int LOCKED = 7;

  protected String[] intNames = null;
  protected int[] intValues = null;

  protected TACGame currentGame;
  protected TACGame[] comingGames;
  protected Competition currentCompetition;
  protected Competition[] comingCompetitions;

  private TACUser[] users;
  private Hashtable userTable = new Hashtable();
  private transient TACUser administrator;

  public int getInt(String name, int def) {
    int index = ArrayUtils.indexOf(intNames, name);
    return index >= 0 ? intValues[index] : def;
  }

  public void setInt(String name, int value) {
    int index = ArrayUtils.indexOf(intNames, name);
    if (index >= 0) {
      if (intValues[index] != value) {
	intValues[index] = value;
	attributeChanged(name, value, CHANGED);
      }
    } else {
      intNames = (String[]) ArrayUtils.add(String.class, intNames, name);
      intValues = ArrayUtils.add(intValues, value);
      attributeChanged(name, value, ADDED);
    }
  }

  public void setMaxInt(String name, int value) {
    int index = ArrayUtils.indexOf(intNames, name);
    if (index >= 0) {
      if (intValues[index] < value) {
	intValues[index] = value;
	attributeChanged(name, value, CHANGED);
      }
    } else {
      intNames = (String[]) ArrayUtils.add(String.class, intNames, name);
      intValues = ArrayUtils.add(intValues, value);
      attributeChanged(name, value, ADDED);
    }
  }

  public TACUser getAdministrator() {
    return administrator;
  }

  public TACUser createUser(String name, String password, String email)
    throws TACException
  {
    if (name == null || (name = name.trim()).length() < 2) {
      throw new TACException("name must be at least 2 characters");
    }
    if (name.charAt(0) == '0') {
      throw new TACException("name may not start with zero");
    }
    if (name.indexOf(',') >= 0) {
      throw new TACException("name must not contain ','");
    }
    if (password == null || (password = password.trim()).length() < 4) {
      throw new TACException("password must be at least 4 characters");
    }
    if (password.charAt(0) == '0') {
      throw new TACException("password may not start with zero");
    }

    // Check if the user 'name' already exists
    if (getUser(name) != null) {
      throw new TACException("user '" + name + "' already exists");
    }

    // Check if no other user can be used to construct 'name'
    // by adding a digit
    int length = name.length();
    char c = name.charAt(length - 1);
    if ((c >= '0') && (c <= '9')
	&& (getUser(name.substring(0, length - 1)) != null)) {
      throw new TACException("user '" + name + "' already exists");
    }

    // Check that 'name' can not be used to construct the name
    // of another user (by adding a digit).
    if (users != null) {
      for (int i = 0, n = users.length; i < n; i++) {
	String u = users[i].getName();
	if (u.startsWith(name)
	    && u.length() == (length + 1)
	    && ((c = u.charAt(length)) >= '0')
	    && (c <= '9')) {
	  throw new TACException("user '" + name + "' already exists");
	}
      }
    }

    TACUser user = addUserToMemory(users == null ? 0 : users.length * 11, -1,
				   name, password, email);
    userChanged(user, ADDED);
    return user;
  }

  /**
   * Adds a new user to the memory cache. All notification about the
   * user addition must be handled outside this method.
   */
  protected TACUser addUserToMemory(int id, int parent,
				    String name, String password,
				    String email) {
    TACUser user = new TACUser(id, parent, name, password, email);
    users = (TACUser[]) ArrayUtils.add(TACUser.class, users, user);
    userTable.put(name, user);
    if (administrator == null && ADMIN_NAME.equals(name)) {
      administrator = user;
    }
    return user;
  }

  /**
   * Changes the user in the memory cache. All notification about the
   * user change must be handled outside this method.
   */
  protected TACUser changeUserInMemory(TACUser user, int parent,
				       String name, String password,
				       String email) {
    user.setParentID(parent);
    if (name.equals(user.getName())) {
      // Name has not changed
      user.setUserInfo(name, password, email);
    } else {
      userTable.remove(user.getName());
      user.setUserInfo(name, password, email);
      userTable.put(name, user);
    }
    return user;
  }

  public TACUser getUser(int id) {
    if (id  < 0 || users == null) {
      return null;
    }

    // Optimization in case the users are in id order (the usual case)
    int index = id / 11;
    if ((index < users.length) && (users[index].getID() == id)) {
      return users[index];
    }

    index = TACUser.indexOf(users, id - (id % 11));
    return index >= 0 ? users[index] : null;
  }

  public TACUser getUser(String name) {
    return (TACUser) userTable.get(name);
  }

  public String getUserName(int id) {
    if (id < 0) {
      return "dummy" + id;
    }

    TACUser user = getUser(id);
    if (user != null) {
      int pos = id % 11;
      if (pos == 0) {
	return user.getName();
      }
      return user.getName() + (pos - 1);
    }
    return "" + '[' + id + ']';
  }

  public TACUser[] getUsers() {
    return users;
  }

  protected void setUsers(TACUser[] users) {
    userTable.clear();
    if (users != null) {
      for (int i = 0, n = users.length; i < n; i++) {
	userTable.put(users[i].getName(), users[i]);
      }
    }
    this.users = users;
    this.administrator = getUser(ADMIN_NAME);
  }

  public TACGame getCurrentGame() {
    return currentGame;
  }

  public Competition getCurrentCompetition() {
    return currentCompetition;
  }

  public Competition getCompetition(int competitionID) {
    Competition[] comp = comingCompetitions;
    if (comp != null) {
      int index = Competition.indexOf(comp, competitionID);
      if (index >= 0) {
	return comp[index];
      }
    }
    return null;
  }

  public Competition getCompetitionByID(int gameID) {
    Competition[] comp = comingCompetitions;
    if (comp != null) {
      for (int i = 0, n = comp.length; i < n; i++) {
	if (comp[i].isGameByID(gameID)) {
	  return comp[i];
	}
      }
    }
    return null;
  }

  public Competition getCompetitionByUniq(int gameID) {
    Competition[] comp = comingCompetitions;
    if (comp != null) {
      for (int i = 0, n = comp.length; i < n; i++) {
	if (comp[i].isGameByUniq(gameID)) {
	  return comp[i];
	}
      }
    }
    return null;
  }

  public Competition[] getComingCompetitions() {
    return comingCompetitions;
  }

  /**
   * Updates the user and returns the updated user if such was
   * found. Returns <code>null</code> if the user was not found or if
   * it did not need updating.
   */
  public abstract TACUser updateUser(int userID);

  public abstract TACUser updateUser(String userName);

  protected abstract void attributeChanged(String name, int value,
					   int operation);

  protected abstract void userChanged(TACUser user, int operation);

  protected abstract void gameChanged(TACGame game, int operation);

  protected abstract void competitionChanged(Competition competition,
					     int operation);

  // Just add a competition to the competitions...
  public void addCompetition(Competition competition) {
    if (comingCompetitions != null) {
      // Add the competitions in game order.
      int startGame = competition.getStartGame();
      int i = comingCompetitions.length - 1;
      while (i >= 0 && startGame < comingCompetitions[i].getStartGame()) i--;
      comingCompetitions = (Competition[])
	ArrayUtils.insert(comingCompetitions, i + 1, 1);
      comingCompetitions[i + 1] = competition;
    } else {
      comingCompetitions = new Competition[] { competition };
    }
    competitionChanged(competition, ADDED);
    log.fine(">>> Competition added " + competition.getDescription() +
	     " total = " + comingCompetitions.length);
  }

  public void removeCompetition(Competition competition) {
    int index = ArrayUtils.indexOf(comingCompetitions, competition);
    if (index >= 0) {
      comingCompetitions = (Competition[])
	ArrayUtils.remove(comingCompetitions, index);
      competitionChanged(competition, REMOVED);
      if (currentCompetition == competition) {
	currentCompetition = null;
	setInt("currentCompetition", -1);
      }
    }
  }

  public void gameStarted(int uid, int gameID, String gameType,
			  long startTime, int gameLength,
			  int participantsInGame) {
    // Check that no old coming games starting prior to this game still exists
    TACGame[] comingGames = getComingGames();
    if (comingGames != null) {
      for (int i = 0, n = comingGames.length; i < n; i++) {
	if ((comingGames[i].getStartTimeMillis() < startTime)
	    && (comingGames[i].getID() != uid)) {
	  log.severe("detected existing coming game "
		     + comingGames[i].getID() + " ("
		     + comingGames[i].getGameID()
		     + ") prior to current game " + uid + " (" + gameID + ')');
	  removeComingGame(comingGames[i]);
	} else {
	  // Since the coming games are in time order, no more games
	  // needs to be checked.
	  break;
	}
      }
    }
    this.currentGame = createGame(uid, gameID, gameType,
				  startTime, gameLength, participantsInGame);
    checkCompetition(currentGame);
  }

  public void gameStopped(int uid, int gameID) {
    checkEndCompetition(uid + 1);

    TACGame game = this.currentGame;
    if (game != null && game.getID() == uid) {
      this.currentGame = null;
      removeComingGame(game);

      setInt(LAST_PLAYED_GAME_ID, gameID);
//       gameStopped(game);
    }
  }

  private void checkEndCompetition(int uniqGameID) {
    if ((currentCompetition != null)
	&& !currentCompetition.isGameByUniq(uniqGameID)) {
      // Competitions has ended!!!
      Competition comp = currentCompetition;
      currentCompetition = null;
      setInt("currentCompetition", -1);
      competitionChanged(comp, STOPPED);
    }
  }

  protected abstract boolean hasGameResults(int gameID);

  protected abstract void gameStopped(TACGameInfo game);

  public void addGameResults(TACGameInfo game) {
    int gameID = game.getGameID();
    if (hasGameResults(gameID)) {
      return;
    }
    gameStopped(game);

    // Add score for the participants
    Competition comp = getCompetitionByID(gameID);
    if (comp != null) {
      float w = comp.getWeight(gameID);
      log.fine("Current Weight is: " + w);
      float lowestScore = 0f;
      for (int i = 0, n = game.getNumberOfAgents(); i < n; i++) {
	if (game.getAgentScore(i) < lowestScore) {
	  lowestScore = game.getAgentScore(i);
	}
      }
      boolean lowestScoreForZero =
	(comp.getFlags() & Competition.LOWEST_SCORE_FOR_ZERO) != 0;
      for (int i = 0, n = game.getNumberOfAgents(); i < n; i++) {
	if (!game.isBuiltinAgent(i)) {
	  TACUser user = comp.getParticipant(game.getAgentID(i));
	  if (user != null) {
	    float score = game.getAgentScore(i);
	    boolean isZeroGame = score == 0f;
	    float agentScore =
	      (isZeroGame && lowestScoreForZero) ? lowestScore : score;
	    user.addScore(gameID, agentScore, w, isZeroGame);
	    setScore(comp, gameID, user, agentScore, game.getAgentPenalty(i),
		     game.getAgentUtility(i), w,
		     isZeroGame ? ZERO_GAME : 0);
	  }
	}
      }

    } else {
      for (int i = 0, n = game.getNumberOfAgents(); i < n; i++) {
	if (!game.isBuiltinAgent(i)) {
	  TACUser user = getUser(game.getAgentID(i));
	  if (user != null) {
	    float score = game.getAgentScore(i);
	    boolean isZeroGame = score == 0f;
	    user.addScore(gameID, score, 1.0f, isZeroGame);
	    setScore(null, gameID, user, score,
		     game.getAgentPenalty(i), game.getAgentUtility(i), 1.0f,
		     isZeroGame ? ZERO_GAME : 0);
	  }
	}
      }
    }

  }

//   public void updateScore(int gameID, int agentID, float score,
// 			  int penalty, int util, int flags) {
//     // Update the users score!
//     Competition comp = getCompetitionByID(gameID);
//     TACUser user;
//     if ((comp != null) && ((user = comp.getParticipant(agentID)) != null)) {
//       float w = comp.getWeight(gameID);
//       log.fine("Current Weight is: " + w);
//       user.addScore(gameID, score, w);
//       setScore(comp, gameID, user, score, penalty, util, w, flags);
//     } else if (((user = getUser(agentID)) != null)
// 	       && agentID == user.getID()) {
//       user.addScore(gameID, score, 1.0f);
//       setScore(null, gameID, user, score, penalty, util, 1.0f, flags);
//     } else {
//       // Do nothing (no user)
//     }
//   }

  protected abstract void setScore(Competition competition,
				   int gameID, TACUser agent, float score,
				   int penalty, int util, float weight,
				   int flags);

  public TACGame createGame(int id, int gameID, String gameType,
			    long time, int gameLength,
			    int participantsInGame) {
    int index = TACGame.indexOfUniqID(comingGames, id);
    if (index >= 0) {
      gameLocked(comingGames[index], gameID);
      return comingGames[index];
    }
    TACGame game = new TACGame(id, gameID, gameType, time, gameLength,
			       participantsInGame);
    addComingGame(game);
    return game;
  }

//   public void gameLocked(int uid, int gid) {
//     int index = TACGame.indexOfUniqID(comingGames, uid);
//     if (index >= 0) {
//       gameLocked(comingGames[index], gid);
//     }
//   }

  public void gameLocked(TACGame game, int gid) {
    if (game.getGameID() != gid) {
      game.setGameID(gid);
      setMaxInt(LAST_GAME_ID, gid);
      gameChanged(game, LOCKED);
    }
  }

  public void competitionLocked(Competition competition) {
    competitionChanged(competition, CHANGED);
  }

  private void checkCompetition(TACGame game) {
    // if this is not in the current competition - check if a new should start!
    int uid = game.getID();
    checkEndCompetition(uid);
    if (currentCompetition == null) {
      int gameID = game.getGameID();
      log.fine(">>> Checking competition for game: " + uid
	       + " (" + gameID + ')');
      if (comingCompetitions != null) {
	Competition[] comps = comingCompetitions;
	for (int i = 0, n = comps.length; i < n; i++) {
	  Competition comp = comps[i];
	  if (comp.isGame(game)) {
	    currentCompetition = comp;
	    setInt("currentCompetition", comp.getID());
	    competitionChanged(comp, STARTED);

	    if (!comp.hasGameID() && gameID > 0) {
	      int compStartGame = comp.getStartGame();
	      if (compStartGame == uid) {
		comp.setStartGameID(gameID);
	      } else {
		comp.setStartGameID(gameID - (uid - compStartGame));
	      }
	      competitionChanged(comp, CHANGED);
	    }

	    break;
	  }
	  // The competitions are ordered in game order so we could break
	  // the loop here if game > comps[i].getEndGame(). FIX THIS!!!
	}
      }
    }
  }

  public void gameJoined(int uniqGameID, int userID) throws TACException {
    TACGame game = getComingGameByUniqID(uniqGameID);
    if (game == null) {
      throw new TACException("game with id " + uniqGameID + " not found");
    }
    if (game.joinGame(userID)) {
      gameChanged(game, AGENT_JOINED);
    }
  }

  public TACGame gameRemoved(int uniqGameID) {
    TACGame game = getComingGameByUniqID(uniqGameID);
    if (game != null) {
      removeComingGame(game);
    }
    return game;
  }

  public TACGame[] getComingGames() {
    return comingGames;
  }

//   protected void setParticipants(int uniqGameID, TACGame game) {
//     int index = TACGame.indexOfUniqID(comingGames, uniqGameID);
//     if (index >= 0) {
//       comingGames[index].updateParticipants(game);
//       gameChanged(comingGames[index], AGENT_JOINED);
//     } else {
//       throw new IllegalArgumentException("game with id " + uniqGameID
// 					 + " not found");
//     }
//   }

  protected void addComingGame(TACGame game) {
    if (comingGames != null) {
      // Add the games in time order.
      long time = game.getStartTimeMillis();
      int i = comingGames.length - 1;
      while (i >= 0 && time < comingGames[i].getStartTimeMillis()) i--;
      comingGames = (TACGame[])
	ArrayUtils.insert(comingGames, i + 1, 1);
      comingGames[i + 1] = game;
    } else {
      comingGames = new TACGame[] { game };
    }
    gameChanged(game, ADDED);
  }

  protected void removeComingGame(TACGame game) {
    int index = ArrayUtils.indexOf(comingGames, game);
    if (index >= 0) {
      comingGames = (TACGame[]) ArrayUtils.remove(comingGames, index);
      gameChanged(game, REMOVED);
    }
  }

  public TACGame getComingGameByUniqID(int id) {
    int index = TACGame.indexOfUniqID(comingGames, id);
    return index >= 0 ? comingGames[index] : null;
  }

  public abstract TACGameResult getLatestGameResult(int agentID,
						    Competition competition,
						    int maxNumberOfGames);

  public abstract
    TACGameResult getLatestGameResult(int agentID, int lowestGameID,
				      int maxNumberOfGames);


  /*********************************************************************
   * Data copying utilites
   *********************************************************************/

  // Add all users from the specified store
  public void addData(TACStore store) {
    TACUser[] u = store.getUsers();
    if (u != null) {
      for (int i = 0, n = u.length; i < n; i++) {
	TACUser user = u[i];
	String name = user.getName();
	try {
	  log.fine("copying user " + name);
	  createUser(name, user.getPassword(), user.getEmail());
	} catch (Exception e) {
	  log.log(Level.SEVERE, "could not add user " + name, e);
	}
      }
    }

    String[] names = store.intNames;
    int[] values = store.intValues;
    if (names != null) {
      for (int i = 0, n = names.length; i < n; i++) {
	log.fine("copying int value " + names[i]);
	setInt(names[i], values[i]);
      }
    }

    TACGame[] games = store.getComingGames();
    if (games != null) {
      for (int i = 0, n = games.length; i < n; i++) {
	log.fine("copying coming game " + games[i].getID());
	addComingGame(games[i]);
      }
    }

    Competition[] comps = store.getComingCompetitions();
    if (comps != null) {
      for (int i = 0, n = comps.length; i < n; i++) {
	log.fine("copying competition " + comps[i].getName());
	addCompetition(comps[i]);
      }
    }
  }

} // TACStore
