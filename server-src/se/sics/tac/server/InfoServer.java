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
 * InfoServer
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-16
 * Updated : $Date: 2004/09/14 11:31:15 $
 *	     $Revision: 1.7 $
 * Purpose :
 *   The InfoServer object is responsible for communication with the
 *   free-standing Java InfoServer process and game log handling.
 *   It also inherits the InfoManager responsibility for configuration
 *   and system log handling.
 */

package se.sics.tac.server;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.botbox.util.ArrayQueue;
import com.botbox.util.ArrayUtils;
import se.sics.isl.util.ConfigManager;
import se.sics.tac.log.ISTokenizer;
import se.sics.tac.util.TACFormatter;

public class InfoServer extends InfoManager {

  private final static Logger log =
    Logger.getLogger(InfoServer.class.getName());

  private final static int ONLY_GAME = -1;
  private final static int ALL = 0;
  private final static int ONLY_IS = 1;

  private final TACServer server;
  private final LineServer infoServer;
  private boolean isConnected = false;

  /** Solve queue */
  private ArrayQueue solveQueue = new ArrayQueue();

  /** Game Logging */
  protected String gamePrefix;
  protected PrintWriter gameLog;

  public InfoServer(TACServer tacServer, ConfigManager config)
    throws IOException
  {
    super(config);
    this.server = tacServer;
    this.gamePrefix = "applet";
    this.infoServer =
      new LineServer(this, config
		     .getProperty("tac.host",
				  config.getProperty("server.host")),
		     config.getPropertyAsInt("info.port", 4039));
    this.infoServer.start();
  }

  public boolean isConnected() {
    return isConnected;
  }

  private synchronized void openGameLog(Market market) {
    int gameID = market.getGame().getGameID();
    closeGameLog(null);
    try {
      gameLog = new PrintWriter(new FileWriter(gamePrefix + gameID + ".log"));
      market.setGameLog(gameLog);
    } catch (Exception e) {
      log.log(Level.SEVERE, "could not open game file for game "
	      + gameID, e);
    }
  }

  private synchronized void closeGameLog(Market market) {
    if (gameLog != null) {
      gameLog.flush();
      if (market != null) {
	// Otherwise the market will still keep the game file open
	// because it might want to write the result in it later on
	gameLog.close();
	market.setGameLog(null);
      }
      gameLog = null;
    }
  }


  // -------------------------------------------------------------------
  // User updating
  // -------------------------------------------------------------------

  public void requestUserUpdate(String name) {
    send("" + getServerTimeSeconds() + ",ru," + name, ONLY_IS);
  }


  // -------------------------------------------------------------------
  // LineServer
  // -------------------------------------------------------------------

  void connectionOpened(LineServer server) {
  }

  void connectionClosed(LineServer server) {
    isConnected = false;
    log.warning("info connection " + server.getName() + " closed");
  }

  public void serverClosed(LineServer server) {
    log.severe("server " + server.getName() + " has closed");
  }

  void deliverMessage(String message) {
//     log.finer("received from info server: " + message);
    ISTokenizer tok = new ISTokenizer(message);
    try {
      switch (tok.getCommand()) {
      case ISTokenizer.ALLOCATION_REPORT: {
	int gameID = tok.nextInt();
	int aid = tok.nextInt();
	int utility = tok.nextInt();
	long calcTime = tok.nextLong();
	User user = getUser(aid);
	Market market;
	synchronized (solveQueue) {
	  market = solveQueue.size() > 0 ? (Market) solveQueue.get(0) : null;
	}
	if (market != null
	    && market.getGame().getGameID() == gameID
	    && user != null) {
	  double cost = market.getAgentCost(user);
	  int penalty = market.getAgentPenalty(user);
	  double score = utility - penalty - cost;
	  long time = getServerTimeSeconds();
	  String result = time + ",s," + gameID + ',' + aid + ','
	    + TACFormatter.toString4(score)
	    + ',' + penalty
	    + ',' + utility;

	  PrintWriter gameFile = market.getGameLog();
	  if (gameFile != null) {
	    String alloc = market.getAllocationInfo(user, tok);
	    if (alloc != null) {
	      gameFile.println("" + time + ',' + alloc);
	    }
	    gameFile.println(result + ',' + calcTime);
	  } else {
	    log.warning("no game log file for game " + gameID);
	  }
	  send(result, ONLY_IS);
	  market.solveReport();
	  if (market.isSolved()) {
	    if (gameFile != null) {
	      gameFile.flush();
	      gameFile.close();
	      market.setGameLog(null);
	    }
	    log.info("Game " + gameID + " have finished calculating score");
	    synchronized (solveQueue) {
	      if (solveQueue.size() > 0 && solveQueue.get(0) == market) {
		solveQueue.remove(0);
	      } else {
		log.warning("finished game " + gameID
			    + " was not found in solve queue");
	      }
	    }
	    send(time + ",xf," + gameID + ',' + market.getGame().getID(),
		 ONLY_IS);

	    System.gc();

	    sendSolveRequest();
	  }
	} else {
	  log.warning("unexpected solve result for game " + gameID
		      + " user "
		      + (user == null ? ("" + aid) : user.getName()));
	}
	break;
      }

      case ISTokenizer.AUTOJOIN:
	config.setProperty(InfoManager.AUTOJOIN, tok.nextToken());
	break;

      case ISTokenizer.PING:
	send(getServerTimeSeconds() + ",po", ONLY_IS);
	break;

      case ISTokenizer.NEW_GAME_2: {
	int ugid = tok.nextInt();
	int gid = tok.nextInt();
	String gameType = tok.nextToken();
	long startTime = tok.nextTimeMillis();
	int gameLength = tok.nextInt() * 1000;
	int participantsInGame = tok.nextInt();
	if (gameType != null) {
	  if ((gameType.length() == 0) || "null".equals(gameType)) {
	    gameType = null;
	  } else {
	    // Reuse same object for all games instead of many string objects
	    // for the same type
	    gameType = gameType.intern();
	  }
	}
	// No need to notify info server because the info server
	// requested this change.
	Game game = addGame(ugid, gid, gameType, startTime,
			    gameLength, participantsInGame);
	if (game != null) {
	  while (tok.hasMoreTokens()) {
	    User user = getUser(tok.nextInt());
	    if (user != null) {
	      joinGame(game, user, false);
	    }
	  }
	}
	break;
      }

      case ISTokenizer.CREATE_GAME: {
	String gameType;
	if (tok.hasMoreTokens()) {
	  gameType = tok.nextToken();
	  if ((gameType == null) || (gameType.length() == 0)
	      || "null".equals(gameType)) {
	    gameType = InfoManager.DEFAULT_GAME_TYPE;
	  } else {
	    // Reuse same object for all games instead of many string objects
	    // for the same type
	    gameType = gameType.intern();
	  }
	} else {
	  gameType = InfoManager.DEFAULT_GAME_TYPE;
	}
	try {
	  createGame(gameType);
	} catch (NoSuchElementException e) {
	  log.warning("could not create game for unknown game type '"
		      + gameType + '\'');
	}
	break;
      }

      case ISTokenizer.JOIN_GAME: {
	int gameUnique = tok.nextInt();
	int agentID = tok.nextInt();
	User user = getUser(agentID);
	if (user != null) {
	  if (!joinGame(gameUnique, user, true)) {
	    log.warning("could not join user " + user.getName()
			+ " to game " + gameUnique);
	    // Should inform InfoServer to lower the time out. FIX THIS!!!
	  }
	} else {
	  log.severe("could not find user " + agentID
		     + " to join game " + gameUnique);
	  // Should inform InfoServer to lower the time out. FIX THIS!!!
	}
	break;
      }

      case ISTokenizer.EXIT:
	infoServer.closeConnection();
	break;

      case ISTokenizer.STATE: {
	int gameID = tok.nextInt();
	int auctionID = tok.nextInt();
	int transID = tok.nextInt();
	int bidID = tok.nextInt();
	int uniqueGameID = tok.nextInt();
	int lastUniqGameID = tok.hasMoreTokens()
	  ? tok.nextInt()
	  : uniqueGameID;
	long time = getServerTimeSeconds();
	Market.initState(uniqueGameID, gameID, auctionID, transID, bidID);

	// Send supported game types
	StringBuffer sb = new StringBuffer()
	  .append(time).append(",gt");
	String[] gameTypes = getGameTypes();
	if (gameTypes != null) {
	  for (int i = 0, n = gameTypes.length; i < n; i++) {
	    sb.append(',').append(gameTypes[i])
	      .append(',').append(getGameTypeName(gameTypes[i]));
	  }
	}
	send(sb.toString(), ONLY_IS);

	// Send any coming games not known to the Info Server
	sendComingGames(lastUniqGameID, gameID, time);

	// Send current game if such is playing, in case the info
	// server needs to immediately display it for its viewers.
	Market market = getMarket();
	if (market != null && market.isRunning()) {
	  sendMarket(market, time, ONLY_IS);
	}

	// Send any pending solve requests

	send(time + ",rd", ONLY_IS);
	break;
      }

      case ISTokenizer.USER: {
	int id = tok.nextInt();
	String name = tok.nextToken();
	String password = tok.nextToken();
	addUser(id, name, password);
	break;
      }

      case ISTokenizer.IDENTITY: {
	String name = tok.nextToken();
	String password = tok.nextToken();

	if (config.getProperty("info.name", "solver").equals(name)
	    && config.getProperty("info.password", "sStruts3p")
	    .equals(password)) {
	  // Maybe check version
	  String version;
	  if (tok.hasMoreTokens()
	      && !InfoManager.FULL_VERSION.equals(version = tok.nextToken())) {
	    log.severe("wrong Java InfoServer version '" + version
		       + "' <=> " + InfoManager.FULL_VERSION);
	    infoServer.closeConnection();
	  } else {
	    log.info("new Java Info server connection");
	    isConnected = true;
	    sendGameState(getServerTimeSeconds(), true);

	    // Send any pending solve request
	    sendSolveRequest();
	  }
	} else {
	  log.warning("Java InfoServer login failed with name=" + name
		      + " password=" + password);
	  infoServer.closeConnection();
	}
	break;
      }

      case ISTokenizer.READY:
	server.setInitialized();
	break;

      case ISTokenizer.REQUEST_NEW_GAME: {
	int uid = tok.nextInt();
	String gameType = tok.nextToken();
	long startTime = tok.nextTimeMillis();
	int gameLength = tok.nextInt() * 1000;
	int participantsInGame = tok.nextInt();
	if (gameType != null) {
	  if ((gameType.length() == 0) || "null".equals(gameType)) {
	    gameType = null;
	  } else {
	    // Reuse same object for all games instead of many string objects
	    // for the same type
	    gameType = gameType.intern();
	  }
	}
	Game game = createGame(uid, gameType, startTime,
			       gameLength, participantsInGame,
			       true, false);
	if (game != null) {
	  while (tok.hasMoreTokens()) {
	    User user = getUser(tok.nextInt());
	    if (user != null) {
	      joinGame(game, user, false);
	    }
	  }
	  // Notify Java InfoServer about the new game (together with the
	  // participants)
	  gameCreated(game);
	}
	break;
      }

      case ISTokenizer.REQUEST_REMOVE_GAME: {
	while (tok.hasMoreTokens()) {
	  removeGame(tok.nextInt(), true);
	}
	break;
      }

      case ISTokenizer.RESERVE_GAME_IDS: {
	int number = tok.nextInt();
	if (number > 0) {
	  int startID = Market.reserveGameUnique(number);
	  send(getServerTimeSeconds() + ",id," + number + ',' + startID,
	       ONLY_IS);
	} else {
	  send(getServerTimeSeconds() + ",e,illegal argument for "
	       + tok.getCommandAsString() + ": " + number, ONLY_IS);
	}
	break;
      }

      case ISTokenizer.REQUEST_LOCK_GAME: {
	int number = tok.nextInt();
	if (number > 0) {
	  lockNextGames(number);
	} else {
	  send(getServerTimeSeconds() + ",e,illegal argument for "
	       + tok.getCommandAsString() + ": " + number, ONLY_IS);
	}
	break;
      }

      default:
	log.warning("unknown info command '" + message + '\'');
	send(getServerTimeSeconds() + ",e,unknown command "
			+ tok.getCommandAsString(), ONLY_IS);
	break;
      }

    } catch (NoSuchElementException e) {
      log.log(Level.SEVERE, "missing argument in message '"
	      + message + '\'', e);
      send(getServerTimeSeconds() + ",e,missing argument for "
		      + tok.getCommandAsString(), ONLY_IS);
    } catch (Exception e) {
      log.log(Level.SEVERE, "could not handle message '"
	      + message + '\'', e);
      send(getServerTimeSeconds() + ",e,could not handle message "
		      + tok.getCommandAsString(), ONLY_IS);
    }
  }



  /*********************************************************************
   * Solve handling
   *********************************************************************/

  private void sendSolveRequest() {
    Market market;
    synchronized (solveQueue) {
      market = solveQueue.size() > 0 ? (Market) solveQueue.get(0) : null;
    }
    if (market != null) {
      sendSolveRequest(market);
    }
  }

  private void sendSolveRequest(Market market) {
    String[] req = market.getSolveRequestInfo();
    if (req != null) {
      long time = getServerTimeSeconds();
      for (int i = 0, n = req.length; i < n; i++) {
	send("" + time + ',' + req[i], ONLY_IS);
      }

    } else {
      boolean retry;
      log.severe("Market for game " + market.getGame().getGameID()
		 + " did not generate a solve request "
		 + "(removing from solve queue)");
      synchronized (solveQueue) {
	if (solveQueue.size() > 0 && solveQueue.get(0) == market) {
	  solveQueue.remove(0);
	  retry = solveQueue.size() > 0;
	} else {
	  retry = false;
	}
      }
      if (retry) sendSolveRequest();
    }
  }


  /*********************************************************************
   * API towards the information senders
   *********************************************************************/

  protected void bidUpdated(Bid bid, char type) {
    StringBuffer sb = new StringBuffer();
    BidList list = bid.getBidList();
    sb.append(getServerTimeSeconds()).append(",b,").append(bid.getBidID())
      .append(',').append(bid.getUser().getID())
      .append(',').append(bid.getAuctionID())
      .append(',').append(type)
      .append(',').append(bid.getProcessingState());
    list.toCsv(sb);
    send(sb.toString(), ONLY_GAME);
  }

  protected void quoteUpdated(Quote quote) {
    quoteUpdated(quote, ALL);
  }

  private void quoteUpdated(Quote quote, int target) {
    StringBuffer sb = new StringBuffer();
    sb.append(quote.getLastQuoteTime() / 1000).append(",q");
    quote.toCsv(sb);
    send(sb.toString(), target);
  }

  protected void auctionClosed(Auction auction) {
    long time = auction.getQuote().getFinalClearTime() / 1000;
    if (time <= 0) {
      time = auction.getQuote().getLastQuoteTime() / 1000;
      if (time <= 0) {
	time = getServerTimeSeconds();
      }
    }
    send(time + ",z," + auction.getID(), ALL);
  }

  protected void transaction(Transaction transaction) {
    StringBuffer sb = new StringBuffer();
    sb.append(transaction.getClearTime() / 1000).append(",t");
    transaction.toCsv(sb);
    send(sb.toString(), ALL);
  }

  protected void gameCreated(Game game) {
    StringBuffer sb = new StringBuffer();
    sb.append(getServerTimeSeconds()).append(",f2");
    game.toCsv(sb);
    send(sb.toString(), ALL);
  }

  protected void gameJoined(Game game, User user) {
    send(getServerTimeSeconds() + ",gj," + game.getID() + ',' + user.getID(),
	 ONLY_IS);
  }

  protected void gameLocked(Game game) {
    send(getServerTimeSeconds() + ",gl," + game.getID()
	 + ',' + game.getGameID(), ONLY_IS);
  }

  protected void gameRemoved(Game game, String reason) {
    if (reason != null) {
      send(getServerTimeSeconds() + ",r," + game.getID() + ',' + reason,
	   ONLY_IS);
    } else {
      send(getServerTimeSeconds() + ",r," + game.getID(), ONLY_IS);
    }
  }

  protected void gameStarted(Market market) {
    openGameLog(market);
    sendMarket(market, getServerTimeSeconds(), ALL);
  }

  protected void gameStopped(Market market, boolean error) {
    long time = getServerTimeSeconds();
    Game game = market.getGame();
    send(time + ",x," + game.getGameID() + ',' + game.getID(), ALL);

    if (error) {
      closeGameLog(market);
    } else {
      synchronized (solveQueue) {
	solveQueue.add(market);
      }
      // Should not close the game log until the solving has finished or
      // next game starts. FIX THIS!!! FIX THIS!!! FIX THIS!!!
      closeGameLog(null);
      sendGameState(time, false);
      sendSolveRequest(market);
    }
  }



  /*********************************************************************
   * Message processing
   *********************************************************************/

  protected void send(String message, int target) {
    PrintWriter out;
    if (target >= ALL && isConnected) {
      infoServer.send(message);
    }
    if (target <= ALL && ((out = this.gameLog) != null)) {
      out.println(message);
    }
  }

  // Should handle removed games in case games are removed
  // when java infoserver is offline! FIX THIS!!!
  private void sendGameState(long time, boolean requestGames) {
    int lastUnique = Market.getLastGameUnique();
    send(time + ",st,"
	 + Market.getLastGameID()
	 + ',' + Market.getLastAuctionID()
	 + ',' + Market.getLastTransactionID()
	 + ',' + Market.getLastBidID()
	 + ',' + lastUnique
	 + (requestGames ? ("" + ',' + lastUnique) : ""), ALL);
  }

  private void sendComingGames(int uniqueGameID, int gameID, long time) {
    // Should be done more efficient!!! FIX THIS!!!
    int lastUniq = Market.getLastGameUnique();
    while (uniqueGameID < lastUniq) {
      Game game = getGameByUniq(uniqueGameID++);
      if (game != null) {
	StringBuffer sb = new StringBuffer();
	sb.append(time).append(",f2");
	game.toCsv(sb);
	send(sb.toString(), ONLY_IS);
      }
    }

    int lastGameID = Market.getLastGameID();
    while (gameID < lastGameID) {
      Game game = getGameByID(lastGameID++);
      if (game != null) {
	send(time + ",gl," + game.getID() + ',' + game.getGameID(),
	     ONLY_IS);
      }
    }
  }

  private void sendMarket(Market market, long time, int target) {
    Game game = market.getGame();
    send(time + ",g," + game.getGameID()
	 + ',' + (game.getStartTime() / 1000)
	 + ',' + (game.getEndTime() / 1000)
	 + ',' + game.getID()
	 + ',' + game.getGameType()
	 + ',' + game.getParticipantsInGame(), target);
    if (target == ALL || target == ONLY_GAME) {
      send(time + ",v," +  FULL_VERSION + ','
	   + server.getServerName().replace(',', ' '), ONLY_GAME);
    }
    for (int i = 0, n = game.getNumberOfParticipants(); i < n; i++) {
      User u = game.getParticipant(i);
      send(time + ",a," + u.getName() + ',' + u.getID(), target);
    }

    String[] prefs = market.getGamePreferencesInfo();
    if (prefs != null) {
      for (int i = 0, n = prefs.length; i < n; i++) {
	send("" + time + ',' + prefs[i], target);
      }
    }

    // send auctions
    Auction[] auctions = market.getAuctions();
    if (auctions != null) {
      for (int i = 0, n = auctions.length; i < n; i++) {
	Auction auction = auctions[i];
	if (auction != null) {
	  // Each auction is sent as its own message for backward
	  // compability. Newer game viewers and game log toolkit will
	  // handle all auctions in one message and this should later
	  // be changed (when everyone hopefully has upgraded). FIX
	  // THIS!!!
	  send("" + time + ",u," + auction.getID()
	       + ',' + auction.getType()
	       + ',' + auction.getDay(), target);
	}
      }
      for (int i = 0, n = auctions.length; i < n; i++) {
	Auction auction = auctions[i];
	if (auction != null) {
	  if (auction.getQuote().getLastQuoteTime() > 0) {
	    // This auction already have a quote
	    quoteUpdated(auction.getQuote(), target);
	  }
	}
      }
    }

    // send transactions including endowments
    Transaction[] trans = market.getTransactions();
    if (trans != null) {
      for (int i = 0, n = trans.length; i < n; i++) {
	Transaction t = trans[i];
	if (t != null) {
	  StringBuffer sb = new StringBuffer()
	    .append(t.getClearTime() / 1000).append(",t");
	  t.toCsv(sb);
	  send(sb.toString(), target);
	}
      }
    }
  }

} // InfoServer
