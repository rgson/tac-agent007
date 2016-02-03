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
 * TACServer
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-03
 * Updated : $Date: 2004/06/06 14:43:11 $
 *	     $Revision: 1.10 $
 */

package se.sics.tac.server;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.botbox.util.ArrayUtils;
import com.botbox.util.ThreadPool;
import se.sics.isl.inet.InetServer;
import se.sics.isl.util.AMonitor;
import se.sics.isl.util.AdminMonitor;
import se.sics.isl.util.ArgumentManager;
import se.sics.isl.util.ConfigManager;
import se.sics.isl.util.LogFormatter;

public class TACServer extends InetServer implements AMonitor {

  private static final Logger log =
    Logger.getLogger(TACServer.class.getName());

  /** Time before a silent agent connection is disconnected */
  private static final int ALIVE_TIME = 60 * 60 * 1000;

  /** Time period when to check for silent (dead) agent connections */
  private static final int CHECK_ALIVE_PERIOD = 10 * 60000;

  private static final String STATUS_NAME = "Agent";

  private String serverName;
  private ThreadPool threadPool;

  /** Message handlers */
  private Hashtable systemMessageHandlers = new Hashtable();
  private Hashtable gameMessageHandlers = new Hashtable();

  /** Tracking of TAC agent connections in order to detect
      silent (dead) connections.
   */
  private Object agentLock = new Object();
  private TACConnection[] agentConnections = new TACConnection[20];
  private int agentConnectionNumber = 0;
  private int connectionID = 0;

  // The maximal number of connections allowed simultaneous from one
  // agent (< 1 means unlimited number of connections)
  private int maxConnectionsPerAgent = 10;

  /** The Information Manager (and Information Server) */
  private final InfoServer infoServer;

  public TACServer(ConfigManager config) throws IOException {
    super("tac", config.getProperty("tac.host",
				    config.getProperty("server.host")),
	  config.getPropertyAsInt("tac.port", 6500));

    this.threadPool = ThreadPool.getThreadPool("tac");
    this.threadPool.setMinThreads(5);
    this.threadPool.setMaxThreads(100);
    this.threadPool.setMaxIdleThreads(25);
    this.threadPool.setInterruptThreadsAfter(120000);

    this.maxConnectionsPerAgent =
      config.getPropertyAsInt("tac.maxConnectionsPerAgent",
			      maxConnectionsPerAgent);

    AdminMonitor adminMonitor = AdminMonitor.getDefault();
    if (adminMonitor != null) {
      adminMonitor.addMonitor(STATUS_NAME, this);
    }

    this.serverName = config.getProperty("server.name", getHost());
    if (serverName == null) {
      serverName = getLocalHostName();
      if (serverName == null) {
	serverName = "127.0.0.1";
      }
    }

    // The info server must be started as soon as possible after the
    // configuration and server has been created because it is
    // responsible for logging. (It is also the InfoManager.)
    this.infoServer = new InfoServer(this, config);
    log.info("waiting for Info Server to start...");

    // The game list should be checked once per minute on the minute
    long currentTime = System.currentTimeMillis();
    // Games are always started/stopped on a minute so we need only to
    // check this once a minute.
    long nextTime = (currentTime / (60 * 1000)) * 60000 + 60000;
    long delay = nextTime - currentTime;
    Timer timer = infoServer.getTimer();
    timer.scheduleAtFixedRate(new GameTimer(this, GameTimer.CHECK_GAME),
			      delay, 60000);
    // Start the timer for checking for silent (dead) agent connections
    timer.scheduleAtFixedRate(new GameTimer(this, GameTimer.CHECK_CONNECTIONS),
			      delay + CHECK_ALIVE_PERIOD, CHECK_ALIVE_PERIOD);
  }

  // Used to inform the TACServer that all information been retrieved
  // from the Java InfoServer.
  synchronized void setInitialized() {
    if (!isRunning()) {
      try {
	start();
      } catch (ThreadDeath e) {
	throw e;
      } catch (Throwable e) {
	log.log(Level.SEVERE, "could not start TAC server at "
		+ getBindAddress() + " (will retry)", e);
      } finally {
	if (!isRunning()) {
	  // Try initializing again in 10 seconds
	  infoServer.getTimer()
	    .schedule(new GameTimer(this, GameTimer.INITIALIZE), 10000);
	}
      }
    }
  }


  public String getServerName() {
    return serverName;
  }


  // -------------------------------------------------------------------
  //  Inet Server
  // -------------------------------------------------------------------

  protected void serverStarted() {
    log.info("tac server started at " + getBindAddress());
  }

  protected void serverShutdown() {
    TACConnection[] connections;
    int connectionNumber;
    synchronized (agentLock) {
      connections = this.agentConnections;
      connectionNumber = this.agentConnectionNumber;
      this.agentConnections = null;
      this.agentConnectionNumber = 0;
    }

    if (connectionNumber > 0) {
      for (int i = 0; i < connectionNumber; i++) {
	connections[i].close();
      }
    }
    log.severe("tac server has closed");
  }

  protected void newConnection(Socket socket) throws IOException {
    TACConnection channel =
      new TACConnection(getName() + '-' + (++connectionID), this, socket);
    addAgentConnection(channel);
    channel.setThreadPool(threadPool);
    channel.start();
    log.fine("new agent: " + channel.getName());
  }



  // -------------------------------------------------------------------
  // Server handling
  // -------------------------------------------------------------------

  public void addMessageHandler(String messageType, MessageHandler handler,
				boolean inGameContext) {
//     log.finest("adding message handler for '" + messageType + '\'');
    if (inGameContext) {
      gameMessageHandlers.put(messageType, handler);
    } else {
      systemMessageHandlers.put(messageType, handler);
    }
  }



  // -------------------------------------------------------------------
  // Game Tick Handling
  // -------------------------------------------------------------------

  // Called by the GameTimer every minute to check if a game should be
  // started or stopped.
  void checkGame() {
    infoServer.checkGame();
  }



  // -------------------------------------------------------------------
  // TAC protocol handling - interface towards TACConnection
  // -------------------------------------------------------------------

  private void addAgentConnection(TACConnection connection) {
    synchronized (agentLock) {
      if (agentConnections.length == agentConnectionNumber) {
	agentConnections = (TACConnection[])
	  ArrayUtils.setSize(agentConnections, agentConnectionNumber + 20);
      }
      agentConnections[agentConnectionNumber++] = connection;
    }
  }

  void removeAgentConnection(TACConnection connection) {
    synchronized (agentLock) {
      int index = ArrayUtils.indexOf(agentConnections,
				     0, agentConnectionNumber,
				     connection);
      if (index >= 0) {
	agentConnectionNumber--;
	agentConnections[index] = agentConnections[agentConnectionNumber];
	agentConnections[agentConnectionNumber] = null;
      }
    }
  }

  void connectionAuthenticated(TACConnection connection) {
    // Check if the agent has too many connections
    String userName = connection.getUserName();
    if (userName != null && maxConnectionsPerAgent > 0) {
      TACConnection oldestConnection = null;
      int numConnections = 0;
      synchronized (agentLock) {
	for (int i = 0; i < agentConnectionNumber; i++) {
	  TACConnection c = agentConnections[i];
	  if (userName.equals(c.getUserName())) {
	    numConnections++;
	    if ((c != connection) &&
		((oldestConnection == null)
		 || oldestConnection.getConnectTime() > c.getConnectTime())) {
	      oldestConnection = c;
	    }
	  }
	}
      }
      if (numConnections > maxConnectionsPerAgent
	  && oldestConnection != null) {
	LogFormatter.separator(log, Level.WARNING, "agent " + userName
			       + " has too many connections ("
			       + numConnections + "): disconnecting "
			       + oldestConnection.getName());
	oldestConnection.close();
	// Make sure the connection has been removed
	removeAgentConnection(connection);
      }
    }
  }

  void deliverMessage(TACMessage message) {
    try {
      String type = message.getType();
      MessageHandler handler = (MessageHandler) gameMessageHandlers.get(type);
      if (handler != null) {
	handler.handleMessage(infoServer, message);

      } else if ((handler = (MessageHandler) systemMessageHandlers.get(type))
		 != null) {
	handler.handleMessage(infoServer, message);

      } else {
	log.warning("no handler for message " + message.getName());
	message.replyError(TACException.NOT_SUPPORTED);
      }
    } catch (TACException e) {
      int code = e.getStatusCode();
      log.log(Level.WARNING, "replying status " + e.getMessage()
	      + " to message " + message.getName());
      message.replyError(e.getStatusCode());

    } catch (Exception e) {
      log.log(Level.SEVERE, "could not handle message "
	      + message.getName(), e);
      message.replyError(TACException.INTERNAL_ERROR);

    } finally {
      // This can only be done until the message queue is implemented...
      if (!message.hasReply()) {
	log.warning("no reply for TAC message " + message.getName());
	message.replyError(TACException.INTERNAL_ERROR);
      }
    }
  }



  // -------------------------------------------------------------------
  // Handling of dead connections
  // -------------------------------------------------------------------

  void checkAgentConnections() {
    if (agentConnectionNumber > 0) {
      ArrayList list = null;
      long deadTime = System.currentTimeMillis() - ALIVE_TIME;
      synchronized (agentLock) {
	for (int i = 0; i < agentConnectionNumber; i++) {
	  if (agentConnections[i].getLastAliveTime() < deadTime) {
	    if (list == null) {
	      list = new ArrayList();
	    }
	    list.add(agentConnections[i]);
	  }
	}
      }
      if (list != null) {
	for (int i = 0, n = list.size(); i < n; i++) {
	  TACConnection connection = (TACConnection) list.get(i);
	  // Recheck that the connection is open and still silent
	  if (connection.getLastAliveTime() < deadTime) {
	    LogFormatter.separator(log, Level.WARNING,
				   "disconnecting silent connection "
				   + connection.getName());
	    connection.close();
	    // Make sure the connection has been removed
	    removeAgentConnection(connection);
	  }
	}
      }
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
    sb.append("--- Agent Connections ---");
    synchronized (agentLock) {
      if (agentConnectionNumber > 0) {
	for (int i = 0; i < agentConnectionNumber; i++) {
	  TACConnection channel = agentConnections[i];
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
    }

    return sb.toString();
  }


  // -------------------------------------------------------------------
  // Startup handling
  // -------------------------------------------------------------------

  public static void main(String[] args) throws IOException {
    ArgumentManager config = new ArgumentManager("TACServer", args);
    config.addOption("config", "configfile", "set the config file to use");
    config.addOption("useGUI", "use admin GUI");
    config.addOption("log.consoleLevel", "level",
		     "set the console log level");
    config.addOption("log.fileLevel", "level", "set the file log level");
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

    TACServer server = new TACServer(config);

    // Add Classic TAC as the default game manager
    GameManager gameManager = new se.sics.tac.server.classic.ClassicGameManager();
    gameManager.init(server.infoServer);
    // Tell the classic game manager to register its message handlers
    gameManager.registerAt(server);

    if (config.getPropertyAsBoolean("useGUI", false)
	|| config.getPropertyAsBoolean("admin.gui", false)
	|| config.getPropertyAsBoolean("tac.admin.gui", false)) {
      AdminMonitor adminMonitor = AdminMonitor.getDefault();
      if (adminMonitor != null) {
	String bounds = config.getProperty("tac.admin.bounds",
					   config.getProperty("admin.bounds"));
	if (bounds != null) {
	  adminMonitor.setBounds(bounds);
	}
	adminMonitor.setTitle("tac@" + server.getServerName());
	adminMonitor.start();
      }
    }
  }

} // TACServer
