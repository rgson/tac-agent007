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
 * InfoManager
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-10-04
 * Updated : $Date: 2004/09/14 11:31:15 $
 *	     $Revision: 1.9 $
 * Purpose :
 *   The InfoManager object is reponsible for server time, configuration,
 *   and system log handling among other things.
 */

package se.sics.tac.server;
import java.io.File;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.botbox.util.ArrayQueue;
import com.botbox.util.ArrayUtils;
import se.sics.isl.util.LogFormatter;
import se.sics.isl.util.ConfigManager;

public class InfoManager {

  /** Version information */
  public final static String VERSION = "1.0";
  public final static String FULL_VERSION = VERSION + " beta 11";

  /** Autojoin property */
  public final static String AUTOJOIN = "autojoin";

  public final static String DEFAULT_GAME_TYPE =
    se.sics.tac.server.classic.ClassicGameManager.TAC_CLASSIC;

  /** Minimum time between game instances */
  private final static int GAME_DELAY = 3 * 60 * 1000;

  /** Minimum time before game is assigned a game id and no longer can
      be cancelled.
   */
  private final static int MIN_GAME_LENGTH = 5 * 60 * 1000 + GAME_DELAY;

  private final static Logger log =
    Logger.getLogger(InfoManager.class.getName());

  /** Maximal number of allowed dummies */
  private final static int MAX_NUMBER_OF_DUMMIES = 50;

  protected final ConfigManager config;

  /** Contains the market for the current game if such is running */
  private Market market;

  /** User information */
  private User[] users;
  private int userNumber;
  private User[] dummies;
  private Hashtable userTable = new Hashtable();

  /** Game manager and type information */
  private Hashtable gameManagerTable = new Hashtable();
  private Hashtable gameTypeTable = new Hashtable();
  private String[] gameTypes = null;
  private int gameTypeNumber = 0;

  /** Coming games information */
  private int[] syncToTimes = null;
  private ArrayQueue gameQueue = new ArrayQueue();

  /** Time zone difference */
  private int timeDiff = 0;

  /** Timer */
  private final Timer timer = new Timer();

  private Random random;

  /** Logging */
  private LogFormatter formatter;
  private FileHandler rootFileHandler;

  private String logName;
  private String logPrefix;
  private FileHandler gameHandler;
  private String gameLogName;

  public InfoManager(ConfigManager config) throws IOException {
    this.config = config;

    String logDirectory = config.getProperty("log.directory", "logs");
    this.logName = getLogDirectory(logDirectory, "ts");
    this.logPrefix =
      getLogDirectory(config.getProperty("log.gamelogs", logDirectory), "");

    formatter = new LogFormatter();
    // Set shorter names for the log
    formatter.setAliasLevel(2);
    LogFormatter.setFormatterForAllHandlers(formatter);

    // Set the current time zone. Must have created the log formatter
    // before this method is called.
    setTimeZone(config.getPropertyAsInt("timeZone", 0));

    setLogging(config);

    String times = config.getProperty("syncToTimes", null);
    try {
      setTimeSync(times);
    } catch (Exception e) {
      log.log(Level.SEVERE, "malformed syncToTime '" + times + '\'', e);
    }
  }

  private String getLogDirectory(String logDirectory, String name)
    throws IOException
  {
    if (logDirectory != null) {
      // Create directories for logs
      File fp = new File(logDirectory);
      if ((!fp.exists() && !fp.mkdirs()) || !fp.isDirectory()) {
	throw new IOException("could not create directory '"
			      + logDirectory + '\'');
      }
      return name == null
	? fp.getAbsolutePath()
	: (fp.getAbsolutePath() + File.separatorChar + name);
    } else {
      return name;
    }
  }

  protected void setTimeSync(String times) throws NumberFormatException {
    if (times == null) {
      this.syncToTimes = null;
    } else {
      StringTokenizer tok = new StringTokenizer(times, ", \t\n");
      int tokens = tok.countTokens();
      if (tokens > 0) {
	int[] tmp = new int[tokens];
	for (int i = 0; i < tokens; i++) {
	  tmp[i] = Integer.parseInt(tok.nextToken()) * 60000;
	}
	this.syncToTimes = tmp;
      } else {
	this.syncToTimes = null;
      }
    }
  }


  /*********************************************************************
   * Configuration
   *********************************************************************/

  public ConfigManager getConfig() {
    return config;
  }


  /*********************************************************************
   * Timer
   *********************************************************************/

  protected Timer getTimer() {
    return timer;
  }

  protected Random getRandom() {
    // No need to synchronize because it does not matter if
    // two random objects happen to be created.
    if (random == null) {
      random = new Random();
    }
    return random;
  }


  /*********************************************************************
   * Server time handling
   *********************************************************************/

  public Market getMarket() {
    return market;
  }

  public long getServerTime() {
    return System.currentTimeMillis() + timeDiff;
  }

  public long getServerTimeSeconds() {
    return (System.currentTimeMillis() + timeDiff) / 1000;
  }

  public int getTimeZone() {
    return timeDiff / 3600000;
  }

  public void setTimeZone(int hoursFromUTC) {
    this.timeDiff = hoursFromUTC * 3600000;
    formatter.setLogTime(getServerTime());
  }


  /*********************************************************************
   * Game manager and game type handling
   *********************************************************************/

  public GameManager getGameManager(String gameType)
    throws NoSuchManagerException
  {
    GameManager manager = (GameManager) gameManagerTable.get(gameType);
    if (manager == null) {
      throw new NoSuchManagerException(gameType);
    }
    return manager;
  }

  public synchronized void addGameManager(String gameType, String gameTypeName,
					  GameManager manager) {
//     log.finest("adding game manager for '" + gameTypeName + "' ("
// 	       + gameType + ')');
    gameManagerTable.put(gameType, manager);
    gameTypeTable.put(gameType, gameTypeName);
    if (gameTypes == null) {
      gameTypes = new String[10];
    } else if (gameTypeNumber == gameTypes.length) {
      gameTypes = (String[])
	ArrayUtils.setSize(gameTypes, gameTypeNumber + 10);
    }
    gameTypes[gameTypeNumber++] = gameType;
  }

  public String[] getGameTypes() {
    String[] types = this.gameTypes;
    if (types != null && types.length > 0 && types[types.length - 1] == null) {
      synchronized (this) {
	// Trim the game type array
	if (gameTypes != null && gameTypeNumber != gameTypes.length) {
	  gameTypes = (String[]) ArrayUtils.setSize(gameTypes, gameTypeNumber);
	}
	types = gameTypes;
      }
    }
    return types;
  }

  public String getGameTypeName(String gameType) {
    return (String) gameTypeTable.get(gameType);
  }


  /*********************************************************************
   * User handling
   *********************************************************************/

  synchronized User getNextDummyUser(User previousDummy) {
    int id;
    if (previousDummy == null) {
      id = -1;
    } else if ((id = previousDummy.getID()) >= 0) {
      throw new IllegalArgumentException("not a dummy user");
    } else {
      id--;
    }
    return getDummyUser(id);
  }

  private synchronized User getDummyUser(int id) {
    int index = -id - 1;

    if (index < 0) {
      throw new IllegalArgumentException("not a dummy user");
    }
    if (index > MAX_NUMBER_OF_DUMMIES) {
      log.severe("too many dummies: " + index);
      return null;
    }

    if (dummies == null) {
      dummies = new User[index + 8];
    } else if (index >= dummies.length) {
      dummies = (User[]) ArrayUtils.setSize(dummies, index + 5);
    }
    if (dummies[index] == null) {
      dummies[index] = new User(id, "Dummy" + id, "");
    }
    return dummies[index];
  }

  // Return the requested user or NULL if no such user exists.
  // If the id is negative, the corresponding dummy agent is returned
  public User getUser(int id) {
    int index;
    if (id < 0) {
      // Check in the cache first to avoid synchronization if not needed
      index = -id - 1;
      if ((dummies != null) && (index < dummies.length)
	  && (dummies[index] != null)) {
	return dummies[index];
      }
      return getDummyUser(id);

    } else if ((index = id / 11) < userNumber) {
      User user = users[index];
      if (user != null) {
	return (user.getID() != id)
	  ? user.getChild(id)
	  : user;
      }
    }
    return null;
  }

  public User getUser(String name) {
    User user = (User) userTable.get(name);
    if (user == null) {
      int len = name.length();
      char c;
      if (len > 0 && Character.isDigit(c = name.charAt(len - 1))) {
	user = (User) userTable.get(name.substring(0, len - 1));
	if (user != null) {
	  user = user.getChild(user.getID() + 1 + (c - '0'));
	}
      }
    }
    return user;
  }

  public void requestUserUpdate(String name) {
  }

  synchronized User addUser(int id, String name, String password) {
    int index = id / 11;
    User user;
    if (userNumber > index && ((user = users[index]) != null)) {
      // User already exists
      String oldName = user.getName();
      if (!name.equals(oldName)) {
	user.setUserInfo(name, password);
	userTable.remove(oldName);
	userTable.put(name, user);
      } else if (!password.equals(user.getPassword())) {
	user.setUserInfo(name, password);
      }

    } else {
      // New user
      user = new User(id, name, password);
      if (users == null) {
	users = new User[index + 10];
      } else if (index >= users.length) {
	users = (User[]) ArrayUtils.setSize(users, index + 10);
      }
      // Since the user extraction is not synchronized we must set the
      // values before increasing userNumber
      users[index] = user;
      userTable.put(name, user);
      if (userNumber <= index) {
	userNumber = index + 1;
      }
    }
    return user;
  }



  /*********************************************************************
   * Game handling
   *********************************************************************/

  public synchronized Game getNextGame(User user, String gameType,
				       boolean autojoin)
    throws NoSuchManagerException
  {
    if (gameType == null) {
      gameType = DEFAULT_GAME_TYPE;
    }

    // Should be cached in variable. FIX THIS!!!
    if (autojoin || config.getPropertyAsBoolean(AUTOJOIN, false)) {
      GameManager manager = getGameManager(gameType);
      int gameLength = manager.getGameLength(gameType) + GAME_DELAY;
      int timeSync = getTimeSync(gameLength);
      long time = getNextStartTime(getServerTime() + 60000, timeSync);

      for (int i = 0, n = gameQueue.size(); i < n; i++) {
	Game game = (Game) gameQueue.get(i);
	int gid = game.getGameID();
	long startTime = game.getStartTime();

	if (gid >= 0) {
	  if (gameType.equals(game.getGameType())) {
	    if (game.isParticipant(user)) {
	      return game;
	    } else if (game.addParticipant(user)) {
	      gameJoined(game, user);
	      return game;
	    }
	  }

	  // Next game has already been assigned an id and we cannot create
	  // another game before it
	  time = getNextStartTime(game.getEndTime() + GAME_DELAY, timeSync);

	} else if ((startTime - time) > gameLength) {
	  // There is a free time lap to insert a game here
	  game = createGame(manager, gameType);
	  game.setStartTime(time);
	  game.addParticipant(user);
	  game.setGameID(Market.getNextGameID());
	  gameQueue.add(i, game);
	  gameCreated(game);
	  gameJoined(game, user);
	  return game;

	} else { // we know that gid < 0
	  if (game.getGameType() == null) {
	    // the game is simply a reserved game slot
	    game.setGameID(0);
	    gameLocked(game);
	  } else {
	    // Must assign a game id for the next game before continuing
	    game.setGameID(Market.getNextGameID());
	    gameLocked(game);

	    if (gameType.equals(game.getGameType())) {
	      if (game.isParticipant(user)) {
		return game;
	      } else if (game.addParticipant(user)) {
		gameJoined(game, user);
		return game;
	      }
	    }
	  }
	  time = getNextStartTime(game.getEndTime() + GAME_DELAY, timeSync);
	}
      }

      // Could not insert a new game earlier so we add it last
      Game game = createGame(manager, gameType);
      game.setStartTime(time);
      game.addParticipant(user);
      game.setGameID(Market.getNextGameID());
      gameQueue.add(game);
      gameCreated(game);
      gameJoined(game, user);
      return game;

    } else {
      return checkGame(user, gameType);
    }
  }

  protected int getTimeSync(int gameLength) {
    int[] syncTimes = this.syncToTimes;
    if (syncTimes != null) {
      for (int i = 0, n = syncTimes.length; i < n; i++) {
	if (gameLength <= syncTimes[i]) {
	  return syncTimes[i];
	}
      }
    }
    return -1;
  }

  protected long getNextStartTime(long time, int timeSync) {
    if (timeSync > 0) {
      // Use this synchronization time
      long fixedHour = (time / (60 * 60000)) * 60 * 60000;
      long t = fixedHour + ((time - fixedHour) / timeSync) * timeSync;
      // If t < time we might need to add another sync time which we
      // avoided to add at first in case time % sync = 0
      if (t < time) {
	t += timeSync;
      }
      return t;
    }
    long nextTime = (time / 60000) * 60000;
    if (nextTime < time) {
      nextTime += 60000;
    }
    return nextTime;
  }

  // gameType == null or "*" => check for any game where the agent
  // participates.  Possible to ask for patterns such as "tacClassic*"
  public synchronized Game checkGame(User user, String gameTypePattern) {
    for (int i = 0, n = gameQueue.size(); i < n; i++) {
      Game game = (Game) gameQueue.get(i);
      if (game.getGameID() < 0) {
	// This game has not yet received a game id which means that no
	// further game is initialized.
	break;
      }

      if (game.isParticipant(user)
	  && ((gameTypePattern == null)
	      || matchGameType(gameTypePattern, game.getGameType()))) {
	return game;
      }
    }
    return null;
  }

  private boolean matchGameType(String gameTypePattern, String gameType) {
    int len = gameTypePattern != null ? gameTypePattern.length() : 0;
    if (len == 0) {
      return false;
    } else if (gameTypePattern.charAt(len - 1) == '*') {
      return gameType != null
	&& gameType.regionMatches(0, gameTypePattern, 0, len - 1);
    } else {
      return gameTypePattern.equals(gameType);
    }
  }

  private Game createGame(GameManager manager, String gameType)
    throws NoSuchManagerException
  {
    Game game = manager.createGame(gameType);
    if (game == null) {
      throw new NoSuchManagerException(gameType);
    }
    return game;
  }

  public synchronized Game createGame(String gameType)
    throws NoSuchManagerException
  {
    // Insert the game in its proper position (first available position)
    GameManager manager = getGameManager(gameType);
    Game g = createGame(manager, gameType);
    int gameLength = g.getGameLength() + GAME_DELAY;
    int timeSync = getTimeSync(gameLength);
    long time = getNextStartTime(getServerTime() + 120000, timeSync);

    for (int i = 0, n = gameQueue.size(); i < n; i++) {
      Game game = (Game) gameQueue.get(i);
      int gid = game.getGameID();
      long startTime = game.getStartTime();

      if (gid >= 0) {
	// Next game has already been assigned an id and we cannot create
	// another game before it
	time = getNextStartTime(game.getEndTime() + GAME_DELAY, timeSync);

      } else if ((startTime - time) > gameLength) {
	// There is a free time lap to insert a game here
	g.setStartTime(time);
	// Do not assign a game id until someone joins the game
	gameQueue.add(i, g);
	gameCreated(g);
	return g;

      } else { // we know that gid < 0
	// Can simply skip the game because we will not assign a game id
	// for the new game anyway.

	// Should have a limit to how many empty games can be created!!!! FIX THIS!!!
	time = getNextStartTime(game.getEndTime() + GAME_DELAY, timeSync);
      }
    }

    // Could not insert a new game earlier so we add it last
    g.setStartTime(time);
    // Do not assign a game id until someone joins the game
    gameQueue.add(g);
    gameCreated(g);
    return g;
  }

  // Does not do any consistency checking or notify the InfoServer.
  // However does not do anything if the game already should be started.
  synchronized Game addGame(int ugid, int gameID,
			    String gameType, long startTime,
			    int gameLength, int participantsInGame) {
    Game game;
    long currentTime = getServerTime();
    if (currentTime >= startTime) {
      // Game already started
      game = new Game(ugid, gameType, gameLength, participantsInGame);
      gameRemoved(game, null);
      return null;
    } else {
      int index = 0;
      for (int n = gameQueue.size(); index < n; index++) {
	game = (Game) gameQueue.get(index);
	if (game.getID() == ugid) {
	  // game already exists
	  return game;
	} else if (game.getStartTime() > startTime) {
	  // Game should be inserted here
	  break;
	}
      }
      game = new Game(ugid, gameType, gameLength, participantsInGame);
      game.setStartTime(startTime);
      game.setGameID(gameID);
      gameQueue.add(index, game);
      return game;
    }
  }

  // Create a new game and replace any existing game if possible.
  // The specified unique game id is used if > 0 and otherwise a
  // new unique game id is generated.
  // Used by Java InfoServer when scheduling time reservations and
  // competitions. Returns NULL if the game could not be added.
  // Does only inform Java InfoServer about any removed games and
  // NOT the added game unless specified.
  synchronized Game createGame(int uid, String gameType, long startTime,
			       int gameLength, int participantsInGame,
			       boolean replaceGames, boolean notifyInfoServer) {
    Game game;
    int index = 0;
    // Find the right insertion point
    for (int n = gameQueue.size() - 1; n >= 0; n--) {
      game = (Game) gameQueue.get(n);
      if ((game.getEndTime() + GAME_DELAY) <= startTime) {
	// Game should be inserted here
	index = n + 1;
	break;
      }
    }

    long endTime = startTime + gameLength + GAME_DELAY;
    while (index < gameQueue.size()) {
      game = (Game) gameQueue.get(index);
      // Can not insert a game before a game already assigned a game id
      if (game.getGameID() > 0) {
	log.info("could not approve requested new game because it "
		 + "conflicts with game " + game.getID() + " ("
		 + game.getGameID() + ')');
	return null;

      } else if (game.getStartTime() <= endTime) {
	if (replaceGames) {
	  log.info("removing game " + game.getID() + " by replacment");
	  gameQueue.remove(index);
	  gameRemoved(game, "replaced");

	} else {
	  log.info("could not approve requested new game because it "
		   + "conflicts with game " + game.getID() + " ("
		   + game.getGameID() + ')');
	  return null;
	}

      } else {
	// No conflicting game
	break;
      }
    }

    // We should be able to safety insert the game here
    game = uid > 0
      ? new Game(uid, gameType, gameLength, participantsInGame)
      : new Game(gameType, gameLength, participantsInGame);
    game.setStartTime(startTime);
    gameQueue.add(index, game);
    if (notifyInfoServer) {
      gameCreated(game);
    }
    return game;
  }

  // Removes the specified game if possible. WILL NOTIFY INFOSERVER
  synchronized boolean removeGame(int ugid, boolean notifyInfoServer) {
    for (int i = gameQueue.size() - 1; i >= 0; i--) {
      Game game = (Game) gameQueue.get(i);
      if (game.getGameID() > 0) {
	// No futher games can be removed because they have been locked
	return false;
      }

      if (game.getID() == ugid) {
	gameQueue.remove(i);
	if (notifyInfoServer) {
	  gameRemoved(game, "by request");
	}
	return true;
      }
    }
    return false;
  }

  synchronized void lockNextGames(int number) {
    // Lock the next coming games
    for (int i = 0, len = gameQueue.size(), n = (len > number ? number : len);
	 i < n; i++) {
      Game game = (Game) gameQueue.get(i);
      if (game.getGameID() < 0 && !assignGameIDs(game)) {
	// Game had no game id and it was not possible to assign an id
	// at this time (perhaps the game was empty)
	break;
      }
    }
  }

  synchronized boolean joinGame(int gameUnique, User user,
				boolean notifyInfoServer) {
    for (int i = 0, n = gameQueue.size(); i < n; i++) {
      Game game = (Game) gameQueue.get(i);
      if (game.getID() == gameUnique) {
	return joinGame(game, user, notifyInfoServer);
      }
    }
    return false;
  }

  boolean joinGame(Game game, User user, boolean notifyInfoServer) {
    if (game.addParticipant(user)) {
      if (notifyInfoServer) {
	gameJoined(game, user);
      }
      return true;
    }
    return false;
  }

  public synchronized Game getGameByUniq(int uniqID) {
    for (int i = 0, n = gameQueue.size(); i < n; i++) {
      Game game = (Game) gameQueue.get(i);
      int uid = game.getID();
      if (uid == uniqID) {
	return game;
      } else if (uid > uniqID) {
	// The games are sorted in id order, no further seaching is needed
	break;
      }
    }
    return null;
  }

  public synchronized Game getGameByID(int gameID) {
    if (gameID <= 0) {
      return null;
    }

    for (int i = 0, n = gameQueue.size(); i < n; i++) {
      Game game = (Game) gameQueue.get(i);
      int id = game.getGameID();
      if (id == gameID) {
	return game;
      } else if ((id > gameID) || (id < 0)) {
	// The games are sorted in id order and if encountering a game
	// not yet assigned a game id, no further seaching is needed
	break;
      }
    }
    return null;
  }



  /*********************************************************************
   * Game startup/shutdown handling
   *********************************************************************/

  synchronized void checkGame() {
    if (gameQueue.size() > 0) {
      long currentTime = getServerTime();
      Game game = (Game) gameQueue.get(0);
      long startTime = game.getStartTime();
      if (currentTime >= startTime) {
	// Time to start the game if not already running
	Market market = getMarket();
	if (market != null && market.getGame() == game) {
	  // Game is already running
	  if (currentTime >= game.getEndTime()) {
	    // Time to stop the game
	    gameQueue.remove(0);
	    try {
	      market.stop();
	    } catch (Exception e) {
	      log.log(Level.SEVERE, "could not stop game " + game.getGameID(),
		      e);
	    } finally {
	      gameStopped(market, false);
	      exitGameLog();
	    }

	    // Should check game ids. FIX THIS!!!
	  } else {
	    // Nothing to do for now: let the game play
	  }

	} else if (game.getGameID() < 0 && !assignGameIDs(game)) {
	  // It was not possible to assign a game id so the game
	  // should be scratched
	  log.info("scratching started game without game id and "
		   + game.getNumberOfParticipants() + " participants");
	  gameQueue.remove(0);
	  gameRemoved(game, null);

	} else if (game.getGameType() == null) {
	  // Time reservation
	  if (currentTime >= game.getEndTime()) {
	    // Time to end the time reservation
	    gameQueue.remove(0);
	    gameRemoved(game, null);
	  }

	} else if (game.isEmpty()) {
	  // No participants => the game should be scratched
	  gameQueue.remove(0);
	  log.info("scratching game " + game.getGameID()
		   + " without participants");
	  gameRemoved(game, null);

	} else {
	  // Time to start the game
	  try {
	    GameManager manager = getGameManager(game.getGameType());
	    int inError = 0;
	    market = manager.createMarket(game);
	    enterGameLog(game.getGameID());
	    try {
	      market.setup();
	      gameStarted(market);
	      inError = 1;
	      market.start();
	      this.market = market;
	      inError = 2;
	    } finally {
	      if (inError != 2) {
		if (inError == 1) {
		  gameStopped(market, true);
		}
		exitGameLog();
	      }
	    }
	  } catch (Exception e) {
	    log.log(Level.SEVERE, "could not start game " + game.getGameID(),
		    e);
	    gameQueue.remove(0);
	    gameRemoved(game, "setup failed");
	  }
	}
      } else if ((startTime - currentTime) < MIN_GAME_LENGTH) {
	if (game.getGameID() < 0) {
	  assignGameIDs(game);
	}
      }
    }
  }

  // Note: may only be called synchronized on this object
  private boolean assignGameIDs(Game game) {
    // Time to generate game ids for the first games
    if (game.getGameType() == null) {
      // Time reservation
      game.setGameID(0);
      gameLocked(game);
      return true;
    } else if (!game.isEmpty()) {
      // Delay setting a game id until a participant joins
      // (otherwise we might want to scratch the game)
      game.setGameID(Market.getNextGameID());
      gameLocked(game);
      return true;
    } else {
      return false;
    }
  }



  /*********************************************************************
   * Information Listeners
   *********************************************************************/

  protected void bidUpdated(Bid bid, char type) {
  }

  protected void quoteUpdated(Quote quote) {
  }

  protected void auctionClosed(Auction auction) {
  }

  protected void transaction(Transaction transaction) {
  }

  protected void gameCreated(Game game) {
  }

  protected void gameJoined(Game game, User user) {
  }

  protected void gameStarted(Market market) {
  }

  protected void gameStopped(Market market, boolean error) {
  }

  protected void gameLocked(Game game) {
  }

  protected void gameRemoved(Game game, String reason) {
  }


  /*********************************************************************
   * Logging handling
   *********************************************************************/

  private synchronized
    void setLogging(ConfigManager config) throws IOException {
    int consoleLevel = config.getPropertyAsInt("log.consoleLevel", 0);
    int fileLevel = config.getPropertyAsInt("log.fileLevel", 0);
    Level consoleLogLevel = LogFormatter.getLogLevel(consoleLevel);
    Level fileLogLevel = LogFormatter.getLogLevel(fileLevel);
    Level logLevel = consoleLogLevel.intValue() < fileLogLevel.intValue()
      ? consoleLogLevel : fileLogLevel;

    Logger root = Logger.getLogger("");
    root.setLevel(logLevel);

    LogFormatter.setConsoleLevel(consoleLogLevel);
//     LogFormatter.setLevelForAllHandlers(logLevel);

    if (fileLogLevel != Level.OFF) {
      if (rootFileHandler == null) {
	rootFileHandler = new FileHandler(logName + "%g.log", 1000000, 10);
	rootFileHandler.setFormatter(formatter);
	root.addHandler(rootFileHandler);
      }
      rootFileHandler.setLevel(fileLogLevel);
      if (gameHandler != null) {
	gameHandler.setLevel(fileLogLevel);
      }
    } else if (rootFileHandler != null) {
      exitGameLog();
      root.removeHandler(rootFileHandler);
      rootFileHandler.close();
      rootFileHandler = null;
    }
  }

  synchronized void enterGameLog(int gameID) {
    exitGameLog();

    if (rootFileHandler != null) {
      LogFormatter.separator(log, Level.FINE, "Entering log for game "
			     + gameID);
      try {
	Logger root = Logger.getLogger("");
	String name = logPrefix + "GAME_" + gameID + ".log";
	gameHandler = new FileHandler(name, true);
	gameHandler.setFormatter(formatter);
	gameHandler.setLevel(rootFileHandler.getLevel());
	gameLogName = name;
	root.addHandler(gameHandler);
	root.removeHandler(rootFileHandler);
	LogFormatter.separator(log, Level.FINE, "Log for game "
			       + gameID + " started");
      } catch (Exception e) {
	log.log(Level.SEVERE, "could not open log file for game "
		+ gameID, e);
      }
    }
  }

  synchronized void exitGameLog() {
    if (gameHandler != null) {
      Logger root = Logger.getLogger("");
      LogFormatter.separator(log, Level.FINE, "Game log complete");

      root.addHandler(rootFileHandler);
      root.removeHandler(gameHandler);
      gameHandler.close();
      gameHandler = null;
      // Try to remove the lock file since it is no longer needed
      if (gameLogName != null) {
	new File(gameLogName + ".lck").delete();
	gameLogName = null;
      }
    }
  }

} // InfoManager
