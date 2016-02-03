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
 * SQLTACStore
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 9 April, 2002
 * Updated : $Date: 2004/07/11 21:37:53 $
 *	     $Revision: 1.18 $
 */

package se.sics.tac.is;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.botbox.util.ArrayUtils;
import se.sics.isl.util.ArgumentManager;
import se.sics.isl.util.ConfigManager;
import se.sics.isl.util.LogFormatter;
import se.sics.tac.log.TACGameInfo;

public class SQLTACStore extends TACStore {

  private static final Logger log =
    Logger.getLogger(SQLTACStore.class.getName());

  private static final boolean DB = false;
  private static final boolean USERS = true;

  // The default Driver and database URL
  private String databaseURL = "jdbc:mysql://localhost:3306/mysql";
  private String driverName = "org.gjt.mm.mysql.Driver";
  private String databaseUser = null;
  private String databasePassword = null;
  private String dataBase = "tacserver";
  private Driver driver;
  private Connection dbConnection;

  private String userDatabaseURL = "jdbc:mysql://localhost:3306/mysql";
  private String userDriverName = "org.gjt.mm.mysql.Driver";
  private String userDatabaseUser = null;
  private String userDatabasePassword = null;
  private String userDataBase = null;
  private Driver userDriver;
  private Connection userConnection;
  private boolean useUserDatabase;

  public SQLTACStore(ConfigManager config) {
    this(config, false, false);
  }

  public SQLTACStore(ConfigManager config, boolean createDatabase) {
    this(config, createDatabase, false);
  }

  public SQLTACStore(ConfigManager config, boolean createDatabase,
		     boolean recalculateGames) {
    driverName = config.getProperty("sql.driver", driverName);
    dataBase = config.getProperty("sql.database", dataBase);
    databaseURL = config.getProperty("sql.url", databaseURL);
    databaseUser = config.getProperty("sql.user");
    databasePassword = config.getProperty("sql.password");

    userDataBase = config.getProperty("users.sql.database", userDataBase);
    userDriverName = config.getProperty("users.sql.driver", userDriverName);
    userDatabaseURL = config.getProperty("users.sql.url", userDatabaseURL);
    userDatabaseUser = config.getProperty("users.sql.user");
    userDatabasePassword = config.getProperty("users.sql.password");
    useUserDatabase = userDataBase != null;

    if (!useUserDatabase) {
      userDataBase = dataBase;
    } else {
      log.finer("using separate database " + userDataBase + " for users");
    }

    try {
      driver = (Driver) Class.forName(driverName).newInstance();

      if (useUserDatabase && userDriverName != null
	  && !userDriverName.equals(driverName)) {
	userDriver = (Driver) Class.forName(driverName).newInstance();
	log.finest("Database Driver: " + driver + " User database driver: "
		   + userDriver);
      } else {
	log.finest("Database Driver: " + driver);
      }
      connect(DB);
      if (useUserDatabase) {
	connect(USERS);
      }

      if (createDatabase) {
	createDB();
      } else {
	loadAttributes();
	loadUsers();
	loadGames();
	loadCompetitions(recalculateGames);
	if (recalculateGames) {
	  recalculateGames();
	}
      }

    } catch (Exception e) {
      log.log(Level.SEVERE, "could not open connection to DB " + dataBase, e);
      throw new IllegalStateException("could not access SQL database");
    }
  }

  private void connect(boolean users) throws SQLException {
    if (users && useUserDatabase) {
      if (userConnection == null || userConnection.isClosed()) {
	userConnection = connect(userDatabaseURL, userDatabaseUser,
				 userDatabasePassword);
	log.finest("User Database Connection: " + userConnection);
      }

    } else if (dbConnection == null || dbConnection.isClosed()) {
      dbConnection = connect(databaseURL, databaseUser, databasePassword);
      log.finest("Database Connection: " + dbConnection);
      if (!useUserDatabase) {
	userConnection = dbConnection;
      }
    }
  }

  private Connection connect(String databaseURL, String user, String password)
    throws SQLException
  {
    if (user != null) {
      log.finest("Connecting to database URL:" + databaseURL
		 + " as " + user);
      return DriverManager.getConnection(databaseURL, user,
					 password == null ? "" : password);
    } else {
      log.finest("Connecting to database URL:" + databaseURL);
      return DriverManager.getConnection(databaseURL, null);
    }
  }

  private void loadAttributes() throws SQLException {
    PreparedStatement stm =
      dbConnection.prepareStatement("SELECT * from " + dataBase + ".state");
    ResultSet result = stm.executeQuery();
    if (result.next()) {
      String[] intNames = new String[10];
      int[] intValues = new int[10];
      int count = 0;
      do {
	String name = result.getString(1);
	int value = result.getInt(2);
	if (count == intNames.length) {
	  intNames = (String[]) ArrayUtils.setSize(intNames, count + 10);
	  intValues = ArrayUtils.setSize(intValues, count + 10);
	}
	intNames[count] = name;
	intValues[count] = value;
	count++;
      } while (result.next());
      if (count < intNames.length) {
	intNames = (String[]) ArrayUtils.setSize(intNames, count);
	intValues = ArrayUtils.setSize(intValues, count);
      }
      this.intNames = intNames;
      this.intValues = intValues;
    }
    stm.close();
  }

  private void loadUsers() throws SQLException {
    PreparedStatement stm =
      sqlPrepare(USERS, "SELECT id,parent,name,password,email from "
		 + userDataBase + ".users ORDER BY id");
    ResultSet result = stm.executeQuery();
    ArrayList list = new ArrayList();
    while (result.next()) {
      TACUser user = new TACUser(result.getInt(1),
				 result.getInt(2),
				 result.getString(3),
				 result.getString(4),
				 result.getString(5));

      PreparedStatement stm2 = sqlPrepare(DB, "SELECT score,playedgames,"
					  + "zeroplayedgames from " + dataBase
					  + ".agentinfo WHERE id='"
					  + user.getID()
					  + "' LIMIT 1");
      ResultSet result2 = stm2.executeQuery();
      if (result2.next()) {
	user.setScore(result2.getDouble(1), result2.getInt(2));
	user.setZeroGames(result2.getInt(3));
      }
      stm2.close();

      list.add(user);
    }
    if (list.size() > 0) {
      setUsers((TACUser[]) list.toArray(new TACUser[list.size()]));
    }
  }

  private void loadGames() throws SQLException {
    PreparedStatement stm = sqlPrepare(DB, "SELECT * from " + dataBase
				       + ".cominggames ORDER BY starttime");
    ResultSet result = stm.executeQuery();
    ArrayList list = null;
    while (result.next()) {
      int guid = result.getInt(1);
      int gid = result.getInt(2);
      String gameType = result.getString(3);
      long startTime = result.getLong(4);
      int gameLength = result.getInt(5);
      int participantsInGame = result.getInt(6);
      String participants = result.getString(7);
      if (gameType != null) {
	if ((gameType.length() == 0) || "null".equals(gameType)) {
	  gameType = null;
	} else {
	  // Reuse same object for all games instead of many string objects
	  // for the same type
	  gameType = gameType.intern();
	}
      }
      TACGame game = new TACGame(guid, gid, gameType, startTime, gameLength,
				 participantsInGame);
      if (participants != null) {
	try {
	  StringTokenizer tok = new StringTokenizer(participants, ", ");
	  while (tok.hasMoreTokens()) {
	    game.joinGame(Integer.parseInt(tok.nextToken()));
	  }
	} catch (Exception e) {
	  log.log(Level.SEVERE, "could not parse participants '"
		  + participants + '\'', e);
	}
      }
      if (list == null) {
	list = new ArrayList();
      }
      list.add(game);

    }
    this.comingGames = (list != null)
      ? (TACGame[]) list.toArray(new TACGame[list.size()])
      : null;
  }

  private void loadCompetitions(boolean loadAll) throws SQLException {
    String select = "SELECT * from " + dataBase + ".competitions";
    if (!loadAll) {
      // Skip competitions that are finished i.e. all web pages have
      // been generated for them
      int lastID = getInt(LAST_FINISHED_COMPETITION, -1);
      if (lastID >= 0) {
	select += " WHERE id > '" + lastID + '\'';
      }
    }
    PreparedStatement stm = sqlPrepare(DB, select + " ORDER BY startgameid");
    ResultSet result = stm.executeQuery();
    Competition[] competitions = null;
    int currentComp = getInt("currentCompetition", -1);
    boolean hasCompetitionChain = false;
    ArrayList agentList = null;
    while (result.next()) {
      int id = result.getInt(1);
      int parentID = result.getInt(2);
      String name = result.getString(3);
      String description = result.getString(4);
      long startTime = result.getLong(5);
      long endTime = result.getLong(6);
      int startGame = result.getInt(7);
      int startGameID = result.getInt(8);
      int gameCount = result.getInt(9);
      float startWeight = result.getFloat(10);
      String className = result.getString(11);
      int flags = result.getInt(12);
      TACUser[] agents = null;

      PreparedStatement stm2 = sqlPrepare(DB, "SELECT * from " + dataBase +
					  ".participants WHERE competition='"
					  + id + '\'');
      ResultSet result2 = stm2.executeQuery();
      while (result2.next()) {
	int uid = result2.getInt(2);
	TACUser user = getUser(uid);
	if (user != null) {
	  log.fine("adding user " + user.getName() + " to competition "
		   + name);
	  user = new TACUser(user);
	  user.setScore(result2.getDouble(3), result2.getInt(4),
			result2.getDouble(5), result2.getDouble(6));
	  user.setCompetitionFlag(result2.getInt(7));

// 	  ResultSetMetaData rsmd = result2.getMetaData();
// 	  int numberOfColumns = rsmd.getColumnCount();
// 	  if (numberOfColumns > 8) {
	  user.setZeroGames(result2.getInt(8), result2.getDouble(9));
// 	  }
	  // Find the NO_WORST worst games for this agent
	  loadWorst(user, startGameID, startGameID + gameCount - 1,
		    TACUser.NO_WORST);

	  if (agentList == null) {
	    agentList = new ArrayList();
	  }
	  agentList.add(user);
	} else {
	  log.severe("could not find participant " + uid
		     + " for competition " + name);
	}
      }

      if (agentList != null && agentList.size() > 0) {
	agents = (TACUser[])
	  agentList.toArray(new TACUser[agentList.size()]);
	agentList.clear();
      } else {
	agents = null;
      }
      stm2.close();

      Competition competition =
	new Competition(id, gameCount, agents, name, description,
			startWeight, className);
      if (parentID > 0) {
	competition.setParentCompetitionID(parentID);
	hasCompetitionChain = true;
      }
      competition.setGameInfo(startGame, startTime, endTime);
      competition.setStartGameID(startGameID);
      competition.setFlags(flags);
      competitions = (Competition[]) ArrayUtils.add(Competition.class,
						    competitions, competition);
      if (id == currentComp) {
	currentCompetition = competition;
      }
    }
    stm.close();

    // Must identify all chained competitions
    if (hasCompetitionChain && competitions != null) {
      for (int i = 0, n = competitions.length; i < n; i++) {
	Competition competition = competitions[i];
	int parentID = competition.getParentCompetitionID();
	if (parentID > 0) {
	  int index = Competition.indexOf(competitions, parentID);
	  if (index >= 0) {
	    if (competitions[index].isParentCompetition(competition)) {
	      log.log(Level.SEVERE, "circular dependencies for competition "
		      + competition.getName());
	    } else {
	      competition.setParentCompetition(competitions[index]);
	    }
	  } else {
	    log.severe("could not find parent competition " + parentID
		       + " for competition " + competition.getName());
	  }
	}
      }

//       for (int i = 0, n = competitions.length; i < n; i++) {
// 	Competition competition = competitions[i];
// 	if (competition.hasParentCompetition()) {
// 	  TACUser[] agents = competition.getParticipants();
// 	  if (agents != null) {
// 	    int startID = competition.getStartGame();
// 	    int endID = competition.getEndGame();
// 	    for (int j = 0, m = agents.length; j < m; j++) {
// 	      // Find the NO_WORST worst games for this agent
// 	      loadWorst(agents[j], startID, endID, TACUser.NO_WORST);
// 	    }
// 	  } else {
// 	    log.severe("no participants for competition "
// 		       + competition.getName());
// 	  }
// 	}
//       }
    }

    this.comingCompetitions = competitions;
  }

  private void loadWorst(TACUser user, int startID, int endID, int limit)
    throws SQLException
  {
    int uid = user.getID();
    PreparedStatement stm3 =
      sqlPrepare(DB, "SELECT gameid,agentid,score,weight from " + dataBase +
		 ".gameresults WHERE agentid = '" + uid
		 + "' and gameid <='" + endID
		 + "' and gameid >= '" + startID
		 + "' ORDER BY (score-3000)*weight LIMIT "
		 + limit);
    ResultSet result3 = stm3.executeQuery();
    while (result3.next()) {
      int gid = result3.getInt(1);
      int agentID = result3.getInt(2);
      float sc = result3.getFloat(3);
      float w = result3.getFloat(4);
      log.finest(">>>>> AgentID: " + uid + " (" + agentID
		 + ")      Score: " + sc + " w=" + w);
      user.addToWorst(gid, sc, w);
    }
    stm3.close();
  }

  protected boolean hasGameResults(int gameID) {
    try {
      PreparedStatement stm = sqlPrepare(DB, "SELECT id from " + dataBase +
					 ".playedgames WHERE id='"
					 + gameID + "' LIMIT 1");
      ResultSet result = stm.executeQuery();
      boolean hasGameResults = result.next();
      stm.close();
      return hasGameResults;
    } catch (SQLException e) {
      log.log(Level.SEVERE, "could not lookup game " + gameID, e);
      return false;
    }
  }

  protected void gameStopped(TACGameInfo game) {
    try {
      PreparedStatement stm = sqlPrepare(DB, "INSERT INTO " + dataBase +
					 ".playedgames VALUES(?,?,?,?,?)");
      stm.setInt(1, game.getGameID());
      stm.setString(2, game.getGameType());
      stm.setLong(3, game.getStartTime());
      stm.setInt(4, game.getGameLength());
      stm.setInt(5, 0);
      int result = stm.executeUpdate();
      if (result == 0) {
	log.log(Level.SEVERE, "could not insert played game " +
		game.getGameID());
      }
      stm.close();
    } catch (SQLException e) {
      log.log(Level.SEVERE, "could not insert played game " +
	      game.getID(), e);
    }
  }

  public TACGameResult getLatestGameResult(int agentID,
					   Competition competition,
					   int maxNumberOfGames) {
    try {
      TACGameResult game = new TACGameResult(agentID, maxNumberOfGames, true);
      do {
	addLatestGameResult(game, agentID, competition, maxNumberOfGames);
      } while (((competition = competition.getParentCompetition()) != null)
	       && (maxNumberOfGames > game.getNumberOfGames()));
      return game;
    } catch (SQLException e) {
      log.log(Level.SEVERE, "could not retrieve game result for agent " +
	      agentID, e);
      return new TACGameResult(agentID);
    }
  }

  private void addLatestGameResult(TACGameResult game, int agentID,
				   Competition competition,
				   int maxNumberOfGames)
    throws SQLException
  {
    PreparedStatement stm = sqlPrepare(DB, "SELECT gameid,utility,score,"
				       + "penalty,weight,flags from "
				       + dataBase
				       + ".gameresults WHERE agentid="
				       + agentID
				       + " and gameid >= '"
				       + competition.getStartGameID()
				       + "' and gameid <= '"
				       + competition.getEndGameID()
				       + "' ORDER BY gameid DESC LIMIT "
				       + (maxNumberOfGames
					  - game.getNumberOfGames()));
    ResultSet result = stm.executeQuery();
    while (result.next()) {
      int gameID = result.getInt(1);
      int utility = result.getInt(2);
      float score = result.getFloat(3);
      int penalty = result.getInt(4);
      float weight = result.getFloat(5);
      int flags = result.getInt(6);
      game.addGameResult(gameID, utility, score, penalty, weight, flags);
    }
    stm.close();
  }

  public TACGameResult getLatestGameResult(int agentID, int lowestGameID,
					   int maxNumberOfGames) {
    try {
      TACGameResult game = new TACGameResult(agentID, maxNumberOfGames, true);
      PreparedStatement stm = sqlPrepare(DB, "SELECT gameid,utility,score,"
					 + "penalty,weight,flags from "
					 + dataBase
					 + ".gameresults WHERE agentid="
					 + agentID
					 + " and gameid >= '" + lowestGameID
					 + "' ORDER BY gameid DESC LIMIT "
					 + maxNumberOfGames);
      ResultSet result = stm.executeQuery();
      while (result.next()) {
	int gameID = result.getInt(1);
	int utility = result.getInt(2);
	float score = result.getFloat(3);
	int penalty = result.getInt(4);
	float weight = result.getFloat(5);
	int flags = result.getInt(6);
	game.addGameResult(gameID, utility, score, penalty, weight, flags);
      }
      stm.close();
      return game;
    } catch (SQLException e) {
      log.log(Level.SEVERE, "could not retrieve game result for agent " +
	      agentID, e);
      return new TACGameResult(agentID);
    }
  }

  private PreparedStatement sqlPrepare(boolean type, String sql) {
    try {
      connect(type);
      // Should empty queue before this update...
      return type == DB
	? dbConnection.prepareStatement(sql)
	: userConnection.prepareStatement(sql);
    } catch (Exception e) {
      // Should add update to queue of updates...
      log.log(Level.SEVERE, "could prepare SQL statement: " + sql, e);
      // Close connection???
      return null;
    }
  }

  private int sqlExecute(boolean type, String sql) {
    try {
      // Should empty queue before this update...
      PreparedStatement stm = sqlPrepare(type, sql);
      int result = stm.executeUpdate();
      stm.close();
      return result;
    } catch (Exception e) {
      // Should add update to queue of updates...
      log.log(Level.SEVERE, "could not execute SQL statement: " + sql, e);
      return 0;
    }
  }

  private int sqlExecute(PreparedStatement stm) {
    try {
      int result = stm.executeUpdate();
      stm.close();
      return result;
    } catch (Exception e) {
      // Should add update to queue of updates...
      log.log(Level.SEVERE, "could not execute SQL statement: " + stm, e);
      return 0;
    }
  }

  public TACUser updateUser(int userID) {
    try {
      PreparedStatement stm =
	sqlPrepare(USERS, "SELECT parent,name,password,email from "
		   + userDataBase + ".users WHERE id=" + userID);
      ResultSet result = stm.executeQuery();
      if (result.next()) {
	int parent = result.getInt(1);
	String name = result.getString(2);
	String password = result.getString(3);
	String email = result.getString(4);
	TACUser user = getUser(userID);
	if (user != null) {
	  user = changeUserInMemory(user, parent, name, password, email);
	} else {
	  // User not already in memory cache
	  user = addUserToMemory(userID, parent, name, password, email);
	}
	return user;
      }

    } catch (Exception e) {
      log.log(Level.SEVERE, "could not update user " + userID, e);
    }
    return null;
  }

  public TACUser updateUser(String userName) {
    try {
      PreparedStatement stm =
	sqlPrepare(USERS, "SELECT id,parent,password,email from "
		   + userDataBase + ".users WHERE name=?");
      stm.setString(1, userName);

      ResultSet result = stm.executeQuery();
      if (result.next()) {
	int userID = result.getInt(1);
	int parent = result.getInt(2);
	String password = result.getString(3);
	String email = result.getString(4);
	TACUser user = getUser(userName);
	if (user != null) {
	  user = changeUserInMemory(user, parent, userName, password, email);
	} else {
	  // User not already in memory cache
	  user = addUserToMemory(userID, parent, userName, password, email);
	}
	return user;
      }

    } catch (Exception e) {
      log.log(Level.SEVERE, "could not update user " + userName, e);
    }
    return null;
  }

  protected void attributeChanged(String name, int value, int operation) {
    log.fine("Setting attribute " + name + " to " + value);
    if (operation == ADDED) {
      if (sqlExecute(DB, "INSERT INTO " + dataBase + ".state VALUES('"
		     + name + "','" + value + "')") == 0) {
	log.log(Level.SEVERE, "could not add value for " + name);
      }
    } else if (operation == REMOVED) {
      sqlExecute(DB, "DELETE FROM " + dataBase + ".state WHERE name='"
		 + name + "' LIMIT 1");
    } else {
      // operation == CHANGED
      if (sqlExecute(DB, "UPDATE " + dataBase + ".state SET value='" +
		     value + "' WHERE name='" + name + '\'') == 0) {
	log.log(Level.SEVERE, "could not set value for " + name);
      }
    }
  }

  protected void setScore(Competition competition, int gameID,
			  TACUser user, float score,
			  int penalty, int util, float weight,
			  int flags) {
    log.fine("Setting score for " + gameID + " agent " + user.getName());
    int result = sqlExecute(DB, "INSERT INTO " + dataBase
			    + ".gameresults VALUES('"
			    + gameID + "','"
			    + user.getID() + "','"
			    + util + "','"
			    + score + "','"
			    + penalty + "','"
			    + weight + "','"
			    + flags + "')");
    if (result == 0) {
      log.severe("could not set result for " + gameID
		 + " agent " + user.getName());
    }

    // Can not use current competition because the score might arrive
    // after the competition has ended
    if (competition != null) {
      result = sqlExecute(DB, "UPDATE " + dataBase +
			  ".participants SET score='" +
			  user.getTotalScore() + "', playedgames='" +
			  user.getGamesPlayed() + "', wscore='" +
			  user.getTotalWScore() +
			  "', wplayedgames='" +
			  user.getGamesWPlayed() +
			  "', zeroplayedgames='" +
			  user.getZeroGamesPlayed() +
			  "', zerowplayedgames='" +
			  user.getZeroGamesWPlayed() +
			  "' WHERE competition='" + competition.getID() +
			  "' and agent='" + user.getID() + '\'');
    } else {
      result = sqlExecute(DB, "UPDATE " + dataBase + ".agentinfo SET score='" +
			  user.getTotalScore() +
			  "', playedgames='" +
			  user.getGamesPlayed() +
			  "', zeroplayedgames='" +
			  user.getZeroGamesPlayed() +
			  "', lastPlayed='" +
			  System.currentTimeMillis()
			  + "' WHERE id='" +
			  user.getID() + "'");
      if (result == 0) {
	result = sqlExecute(DB, "INSERT INTO " + dataBase
			    + ".agentinfo VALUES('" +
			    user.getID() +
			    "','" +
			    user.getTotalScore() +
			    "','" +
			    user.getGamesPlayed() +
			    "','" +
			    user.getZeroGamesPlayed() +
			    "','" +
			    System.currentTimeMillis() +
			    "')");
      }
    }
    if (result == 0) {
      log.severe("could not set result for " + gameID
		 + " agent " + user.getName());
    }
  }

  protected void userChanged(TACUser user, int operation) {
    if (operation == ADDED) {
      try {
	PreparedStatement stm = sqlPrepare(USERS, "INSERT INTO "
					   + userDataBase
					   + ".users VALUES('"
					   + user.getID() + "','"
					   + user.getParentID() + "',?,?,?)");
	stm.setString(1, user.getName());
	stm.setString(2, user.getPassword());
	stm.setString(3, user.getEmail());

	int result = stm.executeUpdate();
	if (result == 0) {
	  log.log(Level.SEVERE, "could not add user " + user.getName());
	} else {
	  result = sqlExecute(DB, "INSERT INTO " + dataBase
			      + ".agentinfo VALUES('"
			      + user.getID()
			      + "',0,0,0,0)");
	  if (result == 0) {
	    log.log(Level.SEVERE, "could not add agent info for "
		    + user.getName());
	  }
	}
      } catch (Exception e) {
	log.log(Level.SEVERE, "could not add user " + user.getName(), e);
      }

    } else if (operation == CHANGED) {
      try {
	PreparedStatement stm =
	  sqlPrepare(USERS, "UPDATE " + userDataBase
		     + ".users SET name=?, password=?, email=?"
		     + " WHERE id='" + user.getID() + '\'');
	stm.setString(1, user.getName());
	stm.setString(2, user.getPassword());
	stm.setString(3, user.getEmail());
	int result = stm.executeUpdate();
	if (result == 0) {
	  log.log(Level.SEVERE, "could not update user " + user.getName());
	}
      } catch (Exception e) {
	log.log(Level.SEVERE, "could not update user " + user.getName(), e);
      }

    } else if (operation == REMOVED) {
      sqlExecute(USERS, "DELETE FROM " + userDataBase + ".users WHERE id='"
		 + user.getID() + "' LIMIT 1");
      sqlExecute(DB, "DELETE FROM " + dataBase + ".agentinfo WHERE id='"
		 + user.getID() + "' LIMIT 1");
    }
  }

  protected void gameChanged(TACGame game, int operation) {
    int uid = game.getID();
    switch (operation) {
    case AGENT_JOINED: {
      StringBuffer sb = new StringBuffer();
      sb.append("UPDATE " + dataBase + ".cominggames SET participants='");
      for (int i = 0, n = game.getNumberOfParticipants(); i < n; i++) {
	if (i > 0) {
	  sb.append(',');
	}
	sb.append(game.getParticipant(i));
      }
      sb.append("' where uid='").append(uid).append('\'');
      log.fine("GAMES_CHANGED (agent joined): " + sb.toString());

      int result = sqlExecute(DB, sb.toString());
      if (result == 0) {
	log.log(Level.SEVERE, "could not join user in game " + uid);
      }
    } break;

    case ADDED: {
      StringBuffer sb = new StringBuffer();
      String gameType = game.getGameType();
      sb.append("INSERT INTO " + dataBase + ".cominggames VALUES('")
	.append(uid).append("','")
	.append(game.getGameID()).append("','");
      if (gameType != null) {
	sb.append(gameType);
      }
      sb.append("','")
	.append(game.getStartTimeMillis()).append("','")
	.append(game.getGameLength()).append("','")
	.append(game.getParticipantsInGame()).append("','");
      for (int i = 0, n = game.getNumberOfParticipants(); i < n; i++) {
	if (i > 0) {
	  sb.append(',');
	}
	sb.append(game.getParticipant(i));
      }
      sb.append("')");
      int result = sqlExecute(DB, sb.toString());
      if (result == 0) {
	log.log(Level.SEVERE, "could not add game " + uid);
      }
    } break;
    case REMOVED: {
      int result = sqlExecute(DB, "DELETE FROM " + dataBase
			      + ".cominggames WHERE uid='"
			      + uid + '\'');
      if (result == 0) {
	log.log(Level.SEVERE, "could not delete game " + uid);
      }
      break;
    }

    case LOCKED: {
      int result = sqlExecute(DB, "UPDATE " + dataBase
			      + ".cominggames SET gid='"
			      + game.getGameID()
			      + "' WHERE uid='"
			      + uid + '\'');
      if (result == 0) {
	log.log(Level.SEVERE, "could not lock game " + uid);
      }
      break;
    }
    }
  }

  protected void competitionChanged(Competition competition, int operation) {
    int id = competition.getID();
    switch (operation) {
    case ADDED: {
      StringBuffer sb = new StringBuffer();
      String scoreClassName = competition.getScoreClassName();
      sb.append("INSERT INTO " + dataBase + ".competitions VALUES(?,?,?,?,'")
	.append(competition.getStartTime()).append("','")
	.append(competition.getEndTime()).append("','")
	.append(competition.getStartGame()).append("','")
	.append(competition.getStartGameID()).append("','")
	.append(competition.getGameCount()).append("','")
	.append(competition.getStartWeight())
	.append("',?,'").append(competition.getFlags()).append("')");

      try {
	PreparedStatement stm = sqlPrepare(DB, sb.toString());
	String name = competition.getName();
	String description = competition.getDescription();
	stm.setInt(1, id);
	stm.setInt(2, competition.getParentCompetitionID());
	stm.setString(3, name);
	stm.setString(4, description != null ? description : "");
	stm.setString(5, scoreClassName);

	int result = sqlExecute(stm);
	stm.close();
	if (result == 0) {
	  log.log(Level.SEVERE, "could not add competition " + name);
	} else {
	  TACUser[] participants = competition.getParticipants();
	  if (participants != null) {
	    for (int i = 0, n = participants.length; i < n; i++) {
	      sb = new StringBuffer();
	      sb.append("INSERT INTO ")
		.append(dataBase).append(".participants VALUES('")
		.append(id).append("','")
		.append(participants[i].getID())
		.append("','0','0','0','0','0','0','0')");
	      stm = sqlPrepare(DB, sb.toString());
	      result = sqlExecute(stm);
	      if (result == 0) {
		log.log(Level.SEVERE, "could not add participant " +
			competition.getName());
	      }
	      stm.close();
	    }
	  }
	}
      } catch (Exception e) {
	log.log(Level.SEVERE, "could not add competition " + dataBase, e);
      }
    } break;

    case CHANGED: {
      try {
	PreparedStatement stm =
	  sqlPrepare(DB, "UPDATE " + dataBase
		     + ".competitions SET name=?"
		     + ", description=?"
		     + ", starttime='"
		     + competition.getStartTime()
		     + "', endtime='"
		     + competition.getEndTime()
		     + "', startgame='"
		     + competition.getStartGame()
		     + "', startgameid='"
		     + competition.getStartGameID()
		     + "', gamecount='"
		     + competition.getGameCount()
		     + "', startweight='"
		     + competition.getStartWeight()
		     + "', scoreclass=?"
		     + ", flags='"
		     + competition.getFlags()
		     + "' WHERE id='"
		     + competition.getID() + '\'');
	stm.setString(1, competition.getName());
	stm.setString(2, competition.getDescription());
	stm.setString(3, competition.getScoreClassName());
	int result = stm.executeUpdate();
	if (result == 0) {
	  log.severe("could not save updated competition "
		     + competition.getName());
	}
      } catch (Exception e) {
	log.log(Level.SEVERE, "could not save updated competition "
		+ competition.getName(), e);
      }
      break;
    }

    case REMOVED:
      int result = sqlExecute(DB, "DELETE FROM " + dataBase
			      + ".competitions WHERE id='" + id + '\'');
      if (result == 0) {
	log.log(Level.SEVERE, "could not delete competition "
		+ competition.getName() + " (" + id + ')');
      } else {
	// DO NOT REMOVE THE PARTICIPANTS FOR NOW UNTIL BETTER SECURITY
	// IS BUILT INTO THE ADMIN PAGES!!!
// 	result = sqlExecute(DB, "DELETE FROM " + dataBase
// 			    + ".participants WHERE competition='" + id + '\'');
// 	if (result == 0) {
// 	  log.log(Level.SEVERE, "could not delete participants in competition "
// 		  + competition.getName() + " (" + id + ')');
// 	}
      }
      break;
    case STARTED:
    case STOPPED:
      break;
    }
  }

  // Creates the tacserver database with its tables
  private void createDB() {
    createDB(DB, dbTableNames, dbTableConstructors, dbTableAlterations, true);
    createDB(USERS, userTableNames, userTableConstructors,
	     userTableAlterations, useUserDatabase);
    // Update table information
    try {
      loadAttributes();
      loadUsers();
      loadGames();
      loadCompetitions(true);

      recalculateGames();

    } catch (Exception e) {
      log.log(Level.SEVERE, "could not update zero games", e);
      System.exit(1);
    }
  }

  private void createDB(boolean type, String[] tableNames,
			String[] tableConstructors,
			String[][] tableAlterations,
			boolean createDatabase) {
    boolean[] tableReady = new boolean[tableNames.length];
    ResultSet rs = null;
    Connection cdb = type == DB ? this.dbConnection : this.userConnection;
    String dataBase = type == DB ? this.dataBase : this.userDataBase;

    try {
      DatabaseMetaData dbmd = cdb.getMetaData();
      rs = dbmd.getTables(dataBase, null, null, null);
    } catch (Exception e) {
      // Simply ignore any errors (rs will be null)
    }

    if (createDatabase) {
      try {
	PreparedStatement stm =
	  cdb.prepareStatement("CREATE DATABASE IF NOT EXISTS " + dataBase);
	int result = stm.executeUpdate();
	stm.close();

	if (result == 0) {
	  log.info("did not create database " + dataBase + " - maybe exists");
	}
      } catch (Exception e) {
	log.log(Level.SEVERE, "could not create database " + dataBase, e);
	System.exit(1);
      }
    }

    if (rs != null) {
      try {
	while (rs.next()) {
	  String name = rs.getString(3);
	  int index = ArrayUtils.indexOf(tableNames, name);
	  if (index < 0) {
	    log.fine("Ignoring table " + name);
	  } else {
	    log.fine("Checking table " + name);
	    tableReady[index] = true;
	    if (tableAlterations[index] != null) {
	      log.fine("Performing table alterations for " + name);
	      for (int i = 0, n = tableAlterations[index].length; i < n; i++) {
		try {
		  sqlExecute(type, "ALTER TABLE " + dataBase
			     + '.' + tableAlterations[index][i]);
		} catch (Exception ae) {
		  log.log(Level.WARNING,
			  "could not perform alteration in table "
			  + name + ": " + tableAlterations[index][i], ae);
		}
	      }
	    }
	  }
	}
      } catch (Exception e) {
	log.log(Level.SEVERE, "could not retrieve database names", e);
      }
    }

    // Create any tables not already existing
    for (int i = 0, n = tableReady.length; i < n; i++) {
      if (!tableReady[i]) {
	try {
	  log.fine("Creating table " + tableNames[i]);
	  sqlExecute(type, "CREATE TABLE IF NOT EXISTS " + dataBase
		     + '.' + tableConstructors[i]);
	} catch (Exception e) {
	  log.log(Level.SEVERE, "could not create table " + tableNames[i], e);
	  System.exit(1);
	}
      }
    }
  }

  private void recalculateGames() throws SQLException {
    // -------------------------------------------------------------------
    // Recalculating zero games does not handle linked competitions yet!!!
    // FIX THIS!!! FIX THIS!!! FIX THIS!!! FIX THIS!!! FIX THIS!!!
    // -------------------------------------------------------------------
    if (comingCompetitions != null) {
      for (int i = 0, n = comingCompetitions.length; i < n; i++) {
	Competition comp = comingCompetitions[i];
	TACUser[] parts = comp.getParticipants();
	if (parts != null && comp.hasGameID()) {
	  int compID = comp.getID();
	  int startGame = comp.getStartGameID();
	  int endGame = comp.getEndGameID();
	  log.fine("Updating zero games for competition " + comp.getName());
	  for (int j = 0, m = parts.length; j < m; j++) {
	    int id = parts[j].getID();
	    String stat =
	      "SELECT count(*), sum(weight) FROM "
	      + dataBase + ".gameresults WHERE agentid="
	      + id + " AND (score=0 OR (flags & " + ZERO_GAME + ") != 0)"
	      + " AND gameid >= "
	      + startGame + " AND gameid <= " + endGame;
	    PreparedStatement stm = dbConnection.prepareStatement(stat);
	    ResultSet rs = stm.executeQuery();
	    if (rs.next()) {
	      int zeroGames = rs.getInt(1);
	      double zeroWGames = rs.getDouble(2);
	      String stat2 =
		"UPDATE " + dataBase
		+ ".participants SET zeroplayedgames='" + zeroGames
		+ "', zerowplayedgames='" + zeroWGames
		+ "' WHERE agent="
		+ id + " AND competition=" + compID;
	      PreparedStatement stm2 = dbConnection.prepareStatement(stat2);
	      stm2.executeUpdate();
	      stm2.close();
	    }
	    stm.close();

	    stat =
	      "SELECT count(*), sum(score), sum(score*weight), sum(weight)"
	      + " FROM " + dataBase + ".gameresults WHERE agentid="
	      + id + " AND gameid >= "
	      + startGame + " AND gameid <= " + endGame;
	    stm = dbConnection.prepareStatement(stat);
	    rs = stm.executeQuery();
	    if (rs.next()) {
	      int numberOfGames = rs.getInt(1);
	      double score = rs.getDouble(2);
	      double weightScore = rs.getDouble(3);
	      double numberOfWeightGames = rs.getDouble(4);
	      String stat2 =
		"UPDATE " + dataBase
		+ ".participants SET playedgames='" + numberOfGames
		+ "', score='" + score
		+ "', wscore='" + weightScore
		+ "', wplayedgames='" + numberOfWeightGames
		+ "' WHERE agent="
		+ id + " AND competition=" + compID;
	      PreparedStatement stm2 = dbConnection.prepareStatement(stat2);
	      stm2.executeUpdate();
	      stm2.close();
	    }
	    stm.close();
	  }
	}
      }
    } else {
      TACUser[] users = getUsers();
      if (users != null) {
	// No competitions existed. Update the zeroplayedgames
	// for the normal games instead
	log.fine("Updating zero games for normal games");
	for (int i = 0, n = users.length; i < n; i++) {
	  int id = users[i].getID();
	  String stat =
	    "SELECT count(*) FROM " + dataBase + ".gameresults WHERE agentid="
	    + id + " AND (score=0 OR ((flags & " + ZERO_GAME + ") != 0))";
	  PreparedStatement stm = dbConnection.prepareStatement(stat);
	  ResultSet rs = stm.executeQuery();
	  if (rs.next()) {
	    int zeroGames = rs.getInt(1);
	    String stat2 =
	      "UPDATE " + dataBase
	      + ".agentinfo SET zeroplayedgames='" + zeroGames
	      + "' WHERE id=" + id;
	    PreparedStatement stm2 = dbConnection.prepareStatement(stat2);
	    stm2.executeUpdate();
	    stm2.close();
	  }
	  stm.close();
	}
      }
    }
  }

  public static void main(String[] args) {
    ArgumentManager config = new ArgumentManager("SQLTACStore", args);
    config.addOption("config", "configfile", "set the config file to use");
    config.addOption("create", "create database");
    config.addOption("recalculategames", "recalculate game data");
    config.addHelp("h", "show this help message");
    config.addHelp("help");
    config.validateArguments();

    String configFile = config.getArgument("config", "config/tacserver.conf");
    try {
      config.loadConfiguration(configFile);
      config.removeArgument("config");
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      config.usage(1);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }

    boolean useSQL = config.getPropertyAsBoolean("sql.use", false);
    boolean create = config.getPropertyAsBoolean("create", false);
    boolean recalculateGames =
      config.getPropertyAsBoolean("recalculategames", false);

    LogFormatter formatter = new LogFormatter();
    formatter.setAliasLevel(1);
    LogFormatter.setFormatterForAllHandlers(formatter);
    LogFormatter.setLevelForAllHandlers(Level.FINEST);
    Logger.getLogger("").setLevel(Level.ALL);

    if (!useSQL) {
      System.err.println("configuration file " + configFile
			 + " is not configured for SQL database");
      System.exit(1);
    }
    new SQLTACStore(config, create, recalculateGames);
  }


  // -------------------------------------------------------------------
  // Database definitions
  // -------------------------------------------------------------------

  private static final String[] userTableNames = new String[] {
    "users"
  };
  private static final String[] dbTableNames = new String[] {
    "agentinfo", "state", "cominggames", "playedgames", "gameresults",
    "competitions", "participants"
  };

  private static final String[] userTableConstructors = new String[] {
    "users (" +
    "id INT NOT NULL," +
    "parent INT NOT NULL DEFAULT -1," +
    "name VARCHAR(30) NOT NULL," +
    "password VARCHAR(30) NOT NULL," +
    "email VARCHAR(60) default NULL," +
    "PRIMARY KEY(id))"
  };
  private static final String[] dbTableConstructors = new String[] {
    "agentinfo (" +
    "id INT NOT NULL," +
    "score DOUBLE NOT NULL," +
    "playedgames INT NOT NULL," +
    "zeroplayedgames INT NOT NULL," +
    "lastplayed BIGINT DEFAULT 0 NOT NULL," +
    "PRIMARY KEY(id))",

    "state (" +
    "name VARCHAR(100) NOT NULL," +
    "value VARCHAR(100) NOT NULL," +
    "UNIQUE(name))",

    "cominggames (" +
    "uid INT NOT NULL AUTO_INCREMENT," +
    "gid INT DEFAULT -1," +
    "type VARCHAR(48) NOT NULL," +
    "starttime BIGINT NOT NULL," +
    "length INT NOT NULL," +
    "positions INT NOT NULL," +
    "participants VARCHAR(120)," +
    "PRIMARY KEY(uid))",

    "playedgames (" +
    "id INT NOT NULL," +
    "type VARCHAR(48) NOT NULL," +
    "starttime BIGINT NOT NULL," +
    "length INT DEFAULT 0 NOT NULL," +
    "status INT DEFAULT 0 NOT NULL," +
    "UNIQUE(id))",

    "gameresults ("
    + "gameid INT NOT NULL,"
    + "agentid INT NOT NULL,"
    + "utility INT NOT NULL,"
    + "score FLOAT NOT NULL,"
    + "penalty INT NOT NULL,"
    + "weight FLOAT DEFAULT '1.0' NOT NULL,"
    + "flags INT(32) DEFAULT 0 NOT NULL)",

    "competitions ("
    + "id INT NOT NULL,"
    + "parent INT DEFAULT 0 NOT NULL,"
    + "name VARCHAR(20) NOT NULL,"
    + "description VARCHAR(120),"
    + "starttime BIGINT NOT NULL,"
    + "endtime BIGINT NOT NULL,"
    + "startgame INT NOT NULL,"
    + "startgameid INT NOT NULL,"
    + "gamecount INT NOT NULL,"
    + "startweight FLOAT DEFAULT '1.0' NOT NULL,"
    + "scoreclass VARCHAR(80),"
    + "flags INT(32) DEFAULT 0 NOT NULL,"
    + "PRIMARY KEY(id))",

    "participants ("
    + "competition INT NOT NULL,"
    + "agent INT NOT NULL,"
    + "score DOUBLE NOT NULL,"
    + "playedgames INT NOT NULL,"
    + "wscore DOUBLE NOT NULL,"
    + "wplayedgames DOUBLE NOT NULL,"
    + "flags INT(32) DEFAULT 0 NOT NULL,"
    + "zeroplayedgames INT NOT NULL,"
    + "zerowplayedgames DOUBLE NOT NULL"
    + ')'
  };

  private static final String[][] userTableAlterations = new String[][] {
    null // users
  };
  private static final String[][] dbTableAlterations = new String[][] {
    null, // agentinfo
    null, // state
    null, // cominggames
    null, // playedgames
    { "gameresults ADD flags INT(32) DEFAULT 0 NOT NULL" }, // gameresults
    null, // competitions
    null  // participants
  };

} // SQLTACStore
