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
 * AppletServer
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 10 April, 2001
 * Updated : $Date: 2004/05/24 11:46:34 $
 *	     $Revision: 1.4 $
 */

package se.sics.tac.is;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.botbox.util.ArrayUtils;
import com.botbox.util.ThreadPool;
import se.sics.isl.inet.InetServer;
import se.sics.isl.util.AMonitor;
import se.sics.isl.util.AdminMonitor;
import se.sics.tac.line.LineConnection;
import se.sics.tac.line.LineListener;
import se.sics.tac.log.ISTokenizer;

public class AppletServer extends InetServer implements LineListener,
							AMonitor {

  private static final String APPLET_VERSION = "1.0";

  private static final String STATUS_NAME = "Viewer";

  private static final Logger log =
    Logger.getLogger(AppletServer.class.getName());

  private final InfoServer infoServer;
  private ThreadPool threadPool;

  // Information about current game
  // Game info, agent info, auction info, transactions, client information
  private String[] gameParameters = new String[50];
  private int numberOfGameParameters = 0;

  private final static String CHAT_LOG_FILE = "APPLET_CHAT.log";
  private final static int CHAT_CACHE_SIZE = 25;
  private final static int MAX_CACHE_RESTORE_SIZE = 5120;
  private String[] chatMessages = new String[CHAT_CACHE_SIZE];
  private int chatIndex = 0;
  private int chatNumber = 0;

  private PrintWriter chatWriter = null;

  // Auction IDs
  private int[] quotes = new int[28];
  // Auction quote lines
  private String[] quoteInfo = new String[28];
  private int numberOfQuotes = 0;

  private LineConnection[] applets = null;
  private int connectionID = 0;

  private TACGame lastSentGame = null;
  private int lastSentGameID = -1;

  public AppletServer(InfoServer infoServer, String host, int port)
    throws IOException
  {
    super("viewer", host, port);
    this.infoServer = infoServer;
    restoreChatMessages();

    this.threadPool = ThreadPool.getThreadPool("viewer");
    this.threadPool.setMinThreads(5);
    this.threadPool.setMaxThreads(60);
    this.threadPool.setMaxIdleThreads(25);
    this.threadPool.setInterruptThreadsAfter(120000);

    AdminMonitor adminMonitor = AdminMonitor.getDefault();
    if (adminMonitor != null) {
      adminMonitor.addMonitor(STATUS_NAME, this);
    }
  }


  // -------------------------------------------------------------------
  //  Inet Server
  // -------------------------------------------------------------------

  protected void serverStarted() {
    log.info("viewer server started at " + getBindAddress());
  }

  protected void serverShutdown() {
    LineConnection[] connections;
    synchronized (this) {
      connections = this.applets;
      this.applets = null;
    }

    if (connections != null) {
      for (int i = 0, n = connections.length; i < n; i++) {
	connections[i].close();
      }
    }
    log.severe("viewer server has closed");
  }

  protected void newConnection(Socket socket) throws IOException {
    LineConnection channel =
      new LineConnection(getName() + '-' + (++connectionID),
			 socket, this, false);
    channel.setThreadPool(threadPool);
    channel.start();
    log.fine("new viewer: " + channel.getName());

    // Send version information to the Applet
    channel.write(infoServer.getServerTimeSeconds()
		  + ",i,nil,nil," + APPLET_VERSION);

    // Inform the applet about the next game
    sendNextGame(channel, true);

    // Inform the new Applet about the running game if such exists
    if (numberOfGameParameters > 0) {
      channel.write(gameParameters[0]);
    }
    sendChatMessages(channel);
    addApplet(channel);
  }

  public synchronized void lineRead(LineConnection source, String line) {
    if (line == null) {
      // connection has closed
      log.fine("viewer " + source.getName() + " has closed");
      removeApplet(source);

    } else if (line.length() > 0) {
      try {
	handleAppletCommand(source, line);
      } catch (Exception e) {
	log.log(Level.SEVERE, "could not parse command from applet "
		+ source + ": " + line, e);
	source.write(infoServer.getServerTimeSeconds()
		     + ",e,could not parse command");
      }
    } else {
      // Empty line => ignore
    }
  }

  public synchronized void messageReceived(int command, String message) {
    switch (command) {
    case ISTokenizer.NEW_GAME_2:
      // Send next coming game because the Applets should not know of
      // games after that one.
      sendNextGame(null, false);
      break;
    case ISTokenizer.GAME_STARTED:
      clearGameState();
      addGameParameter(message);
      sendToAll(message);
      break;
    case ISTokenizer.GAME_ENDED:
      clearGameState();
      sendToAll(message);
      sendNextGame(null, true);
      break;

    case ISTokenizer.GAME_REMOVED:
      sendNextGame(null, false);
      break;

    case ISTokenizer.GAME_LOCKED: {
      sendNextGame(null, false);
      break;
    }

    case ISTokenizer.READY:
    case ISTokenizer.GAME_FINISHED:
    case ISTokenizer.GAME_TYPES:
    case ISTokenizer.SUBSCRIBE:
    case ISTokenizer.UNSUBSCRIBE:
    case ISTokenizer.ALLOCATION_REPORT:
    case ISTokenizer.SOLVE_REQUEST:
    case ISTokenizer.STATE:
    case ISTokenizer.USER:
    case ISTokenizer.REQUEST_USER:
    case ISTokenizer.EXIT:
    case ISTokenizer.GAME_JOINED:
    case ISTokenizer.PING:
    case ISTokenizer.PONG:
    case ISTokenizer.GAME_IDS:
    case ISTokenizer.OWN:
    case ISTokenizer.ALLOCATION:
      // Ignore these message types
      break;

    case ISTokenizer.AUCTION:
    case ISTokenizer.AUCTION_CLOSED:
    case ISTokenizer.AGENT:
    case ISTokenizer.CLIENT:
    case ISTokenizer.TRANSACTION:
      addGameParameter(message);
      sendToActiveApplets(message);
      break;

    case ISTokenizer.QUOTE:
      setQuote(message);
      sendToActiveApplets(message);
      break;

    case ISTokenizer.SCORE:
      sendToAll(message);
      break;

    case ISTokenizer.ERROR:
      log.severe("message error: " + message);
      break;

    default:
      log.severe("unknown command: " + message);
    }
  }

  // Note: must be synchronized on the AppletServer to be called!
  private void handleAppletCommand(LineConnection source, String line)
    throws NumberFormatException
  {
    ISTokenizer tokenizer = new ISTokenizer(line);
    TACStore store = infoServer.getTACStore();
    switch (tokenizer.getCommand()) {
    case ISTokenizer.SUBSCRIBE:
      log.finer("applet subscribed: " + source.getName());
      // Subscribe on current games
      if (numberOfGameParameters > 0) {
	// A game is currently running
	sendGameState(source);
      }
      // The Applet should start listening on all games
      source.setActive(true);
      break;
    case ISTokenizer.UNSUBSCRIBE:
      log.finer("applet unsubscribed: " + source.getName());
      // Applet did not want to listen on current game any more
      source.setActive(false);
      break;
    case ISTokenizer.LOGIN: {
      String name = tokenizer.nextToken();
      // Ignore the Applet default name
      if (!"anonymous".equals(name)) {
	source.setUserName(name);
	log.info("user " + name + " logged in as " + source.getName()
		 + " from " + source.getRemoteHost());
      }
	// Ignore password for now
// 	tokenizer.nextToken();
	// Ignore the version for now
// 	if (tokenizer.hasMoreTokens()) {
// 	  String version = tokenizer.nextToken();
// 	}
      break;
    }
    case ISTokenizer.EXIT:
      // LineConnection should be closed
      source.close();
      break;
    case ISTokenizer.CHAT_MESSAGE: {
      int index = findCommand(line);
      if (index > 0) {
	handleChatCommand(source, index, line);
      } else {
	addChatMessage(line);
	sendToAll(line);
      }
      break;
    }
    default:
      log.severe("unknown Applet command from " + source.getName()
		 + ": " + line);
      source.write(infoServer.getServerTimeSeconds()
		   + ",e,unknown command");
    }
  }

  private int findCommand(String line) {
    // Observe... Chat Messages looks like "serverTime,m,user> MESSAGE"
    int index = line.indexOf(',');
    // This command parsing is a hack. FIX THIS!!!
    if ((index > 0)
	&& ((index = line.indexOf(',', index + 1)) > 0)
	&& ((index = line.indexOf('>', index + 1)) > 0)) {
      int len = line.length();
      // Skip white spaces
      index++;
      while (index < len && line.charAt(index) == ' ') index++;
      // A command is identified by a '!'
      if (index < len && line.charAt(index++) == '!' && index < len) {
	return index;
      }
    }
    return -1;
  }

  private void handleChatCommand(LineConnection source, int index,
				 String line) {
    // Command detected
    if (line.regionMatches(index, "who", 0, 3)) {
      // Who command
      StringBuffer sb = new StringBuffer();
      sb.append(infoServer.getServerTimeSeconds())
	.append(",m,Online: ");

      LineConnection[] connections = this.applets;
      int anonymous = 0;
      boolean com = false;
      if (connections != null) {
	for (int i = 0, n = connections.length; i < n; i++) {
	  String name = connections[i].getUserName();
	  if (name == null) {
	    anonymous++;
	  } else {
	    if (com) {
	      sb.append(", ");
	    } else {
	      com = true;
	    }
	    sb.append(name);
	  }
	}
	if (anonymous > 0) {
	  if (com) {
	    sb.append(", and ");
	  }
	  sb.append(anonymous).append(" anonymous applets");
	}
	source.write(sb.toString());
      }

    } else if (line.regionMatches(index, "ip", 0, 2)) {
      // IP command
      StringBuffer sb = new StringBuffer();
      sb.append(infoServer.getServerTimeSeconds())
	.append(",m,Online: ");
      LineConnection[] applets = this.applets;
      if (applets != null && applets.length > 0) {
	String name = applets[0].getUserName();
	sb.append(applets[0].getRemoteHost());
	if (name != null) {
	  sb.append(" (").append(name).append(')');
	}
	for (int i = 1, n = applets.length; i < n; i++) {
	  name = applets[i].getUserName();
	  sb.append(", ").append(applets[i].getRemoteHost());
	  if (name != null) {
	    sb.append(" (").append(name).append(')');
	  }
	}
      }
      source.write(sb.toString());

    } else {
      log.warning("unknown Applet command from " + source.getName()
		  + ": " + line);
      source.write(infoServer.getServerTimeSeconds()
		   + ",m,SERVER> unknown command '"
		   + line.substring(index));
    }
  }

  // Note: must be synchronized on the AppletServer to be called!
  private void sendToAll(String message) {
    sendToAll(message, true);
  }

  // Note: must be synchronized on the AppletServer to be called!
  private void sendToActiveApplets(String message) {
    sendToAll(message, false);
  }

  // Note: must be synchronized on the AppletServer to be called!
  private void sendToAll(String data, boolean all) {
    LineConnection[] applets = this.applets;
    if (applets != null) {
      for (int i = 0, n = applets.length; i < n; i++) {
	if (!applets[i].isClosed()) {
	  if (all || applets[i].isActive()) {
	    applets[i].write(data);
	  }
	} else {
	  removeApplet(applets[i]);
	}
      }
    }
  }

  // Note: must be synchronized on the AppletServer to be called!
  private void addApplet(LineConnection connection) {
    this.applets = (LineConnection[])
      ArrayUtils.add(LineConnection.class, applets, connection);
  }

  // Note: must be synchronized on the AppletServer to be called!
  private void removeApplet(LineConnection connection) {
    this.applets = (LineConnection[])
      ArrayUtils.remove(applets, connection);
  }


  /*********************************************************************
   * Game state handling
   *********************************************************************/

  private TACGame getNextGame() {
    TACGame[] games = infoServer.getTACStore().getComingGames();
    if (games != null) {
      for (int i = 0, n = games.length; i < n; i++) {
	TACGame game = games[i];
	// Only if the game has a game type it is a game and not
	// simply a time reservation
	if (!game.isReservation()) {
	  return game;
	}
      }
    }
    return null;
  }

  // Note: must be synchronized on the AppletServer to be called!
  private void sendNextGame(LineConnection connection, boolean force) {
    String message = null;
    TACGame nextGame = getNextGame();
    if (nextGame != null) {
      int gameID = nextGame.getGameID();
      if ((lastSentGame != nextGame)
	  || (nextGame.getGameID() != lastSentGameID)
	  || force) {
	long currentTime = infoServer.getServerTimeSeconds();
	lastSentGame = nextGame;
	lastSentGameID = gameID;
	message = currentTime + ",f,"
	  + gameID + ',' + (nextGame.getStartTimeMillis() / 1000);
      }
    } else if (lastSentGame != null) {
      lastSentGame = null;
      force = true;
    }

    if (force && message == null) {
      // No coming games => nothing to say to the Applet but we should
      // at least inform it about the server time
      message = infoServer.getServerTimeSeconds() + ",f";
    }

    if (message != null) {
      if (connection != null) {
	connection.write(message);
      } else {
	sendToAll(message);
      }
    }
  }


  // Note: must be synchronized on the AppletServer to be called!
  private void sendGameState(LineConnection connection) {
    // Send game information and transactions
    for (int i = 0; i < numberOfGameParameters; i++) {
      connection.write(gameParameters[i]);
    }

    // Send latest quotes
    for (int i = 0; i < numberOfQuotes; i++) {
      connection.write(quoteInfo[i]);
    }
  }

  // Note: must be synchronized on the AppletServer to be called!
  private void clearGameState() {
    numberOfGameParameters = 0;
    numberOfQuotes = 0;
  }

  // Note: must be synchronized on the AppletServer to be called!
  private void addGameParameter(String parameter) {
    if (numberOfGameParameters == gameParameters.length) {
      gameParameters = (String[])
	ArrayUtils.setSize(gameParameters, numberOfGameParameters + 50);
    }
    gameParameters[numberOfGameParameters++] = parameter;
  }

  // Note: must be synchronized on the AppletServer to be called!
  private void setQuote(String message) {
    int index1 = message.indexOf(',', message.indexOf(',') + 1);
    int index2 = message.indexOf(',', index1 + 1);
    if (index2 > index1 && index1 > 0) {
      String aid = message.substring(index1 + 1, index2);
      try {
	setQuote(Integer.parseInt(aid), message);
      } catch (NumberFormatException e) {
	log.log(Level.SEVERE, "could not parse quote: " + message, e);
      }
    } else {
      log.severe("could not parse quote: " + message);
    }
  }

  // Note: must be synchronized on the AppletServer to be called!
  private void setQuote(int auctionID, String message) {
    int index = findAuction(auctionID);
    if (index < 0) {
      if (numberOfQuotes == quotes.length) {
	quotes = ArrayUtils.setSize(quotes, numberOfQuotes + 10);
	quoteInfo = (String[])
	  ArrayUtils.setSize(quoteInfo, numberOfQuotes + 10);
      }
      index = numberOfQuotes;
      quotes[numberOfQuotes++] = auctionID;
    }
    quoteInfo[index] = message;
  }

  // Note: must be synchronized on the AppletServer to be called!
  private int findAuction(int id) {
    for (int i = 0; i < numberOfQuotes; i++) {
      if (quotes[i] == id) {
	return i;
      }
    }
    return -1;
  }


  /*********************************************************************
   * Chat Message Handling
   *********************************************************************/

  // Note: may only be called from the constructor
  private void restoreChatMessages() {
    // Only possible before the chat file has been opened
    if (chatWriter == null) {
      try {
	RandomAccessFile fp = new RandomAccessFile(CHAT_LOG_FILE, "r");
	try {
	  long length = fp.length();
	  if (length > 0) {
	    String line;
	    long seek = length - MAX_CACHE_RESTORE_SIZE;
	    if (seek > 0) {
	      fp.seek(seek);
	    }
	    // Ignore the first line that might be half if we have
	    // skipped some part of the chat log file
	    if ((seek <= 0) || (fp.readLine() != null)) {
	      // Add all lines from this point forward
	      while ((line = fp.readLine()) != null) {
		int index = (chatIndex + chatNumber) % CHAT_CACHE_SIZE;
		chatMessages[index] = line;
		if (chatNumber < CHAT_CACHE_SIZE) {
		  chatNumber++;
		} else {
		  chatIndex = (chatIndex + 1) % CHAT_CACHE_SIZE;
		}
	      }
	    }
	  }
	} finally {
	  fp.close();
	}
      } catch (FileNotFoundException e) {
	// No chat log file to read from
      } catch (Exception e) {
	log.log(Level.WARNING, "could not restore chat messages from "
		+ CHAT_LOG_FILE, e);
      }
    }
  }

  // Note: must be synchronized on the AppletServer to be called!
  private void sendChatMessages(LineConnection connection) {
    if (chatNumber > 0) {
      for (int i = 0; i < chatNumber; i++) {
	connection.write(chatMessages[(chatIndex + i) % CHAT_CACHE_SIZE]);
      }
    }
  }

  // Note: must be synchronized on the AppletServer to be called!
  private void addChatMessage(String message) {
    int index = (chatIndex + chatNumber) % CHAT_CACHE_SIZE;
    chatMessages[index] = message;

    if (chatNumber < CHAT_CACHE_SIZE) {
      chatNumber++;
    } else {
      chatIndex = (chatIndex + 1) % CHAT_CACHE_SIZE;
    }

    // Log chat message to file
    if (chatWriter == null) {
      try {
	chatWriter =
	  new PrintWriter(new BufferedWriter(new FileWriter(CHAT_LOG_FILE,
							    true)));
      } catch (Exception e) {
	log.log(Level.SEVERE, "could open log chat file '"
		+ CHAT_LOG_FILE + "' to log: " + message, e);
      }
      if (chatWriter != null) {
	chatWriter.println(message);
	chatWriter.flush();
      }
    } else {
      chatWriter.println(message);
      chatWriter.flush();
    }
  }


  // -------------------------------------------------------------------
  // AMonitor API
  // -------------------------------------------------------------------

  public String getStatus(String propertyName) {
    if (propertyName != STATUS_NAME) {
      return null;
    }

    StringBuffer sb = new StringBuffer();
    sb.append("--- Viewer Connections ---");

    LineConnection[] connections = this.applets;
    if (connections != null) {
      for (int i = 0, n = connections.length; i < n; i++) {
	LineConnection channel = connections[i];
	sb.append('\n')
	  .append(i + 1).append(": ")
	  .append(channel.getName())
	  .append(" (")
	  .append(channel.getRemoteHost())
	  .append(':').append(channel.getRemotePort())
	  .append(')');
      }
    } else {
      sb.append("\n<no connections>");
    }

    return sb.toString();
  }

} // AppletServer
