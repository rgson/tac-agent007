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
 * InfoServer
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 8 April, 2002
 * Updated : $Date: 2004/09/07 15:22:04 $
 *	     $Revision: 1.25 $
 */

package se.sics.tac.is;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.mortbay.http.HttpContext;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpServer;
import org.mortbay.http.NCSARequestLog;
import org.mortbay.http.SecurityConstraint;
import org.mortbay.http.SocketListener;
import org.mortbay.http.handler.NotFoundHandler;
import org.mortbay.http.handler.ResourceHandler;
import org.mortbay.http.handler.SecurityHandler;
import org.mortbay.util.InetAddrPort;
import se.sics.isl.util.AdminMonitor;
import se.sics.isl.util.ArgumentManager;
import se.sics.isl.util.ConfigManager;
import se.sics.isl.util.LogFormatter;
import se.sics.tac.line.LineConnection;
import se.sics.tac.line.LineListener;
import se.sics.tac.log.GameResultCreator;
import se.sics.tac.log.ISTokenizer;
import se.sics.tac.solver.Solver;

/**
 * The Java InfoServer is, among other things, responsible for
 * calculating game results and generating game result web pages,
 * persistence of agent, game, and server data.
 *
 * The Java InfoServer also handles the web interaction with users and
 * the TAC Applet communication, leaving the TAC Server free for its
 * main purpose: handling the marketplace.
 */
public class InfoServer implements LineListener {

  public final static boolean ALLOW_GAME_TYPE = false;

  private final static String SERVER_MESSAGE_FILE = "SERVER_MESSAGE.txt";

  private final static int MIN_TIME_BEFORE_CHANGE = 10000;

  // The minimum delay between two TAC games
  public final static int DELAY_BETWEEN_GAMES = 3 * 60 * 1000;

  /** Version information */
  public final static String VERSION = "1.0";
  public final static String FULL_VERSION = VERSION + " beta 11";

  public final static String TAC_CLASSIC = "tacClassic";

  public final static String HTTP_FOOTER =
    "<p><hr noshade color='#202080'>\r\n"
    + "<center><font face='Arial,Helvetica,sans-serif' size='-2'>"
    + "SICS TAC Classic Java Server " + FULL_VERSION
    + "</font></center>\r\n";

  private static final Logger log =
    Logger.getLogger(InfoServer.class.getName());

  private LogFormatter formatter;
  private String serverHost;
  private int serverPort;

  private String serverName;

  private String isName;
  private String isPassword;

  private Hashtable gameTypeTable = null;
  private String[] gameTypes;

  private TACGame[] schedulingGames = null;
  private int schedulingNewGames;
  private Competition schedulingCompetition;

  private TACStore store;
  private long timeDiff = 0L;

  /** The HTTP handling */
  private HttpServer httpServer;
//   private HttpContext archiveContext;
//   private ResourceHandler archiveResourceHandler;
  private HttpContext httpContext;
  private ResourceHandler httpResourceHandler;
  private SocketListener httpSocketListener;
  private PageHandler pageHandler;
  private int httpPort;

  private AppletServer appletServer;
  private int appletPort;
  private SolveServer solveServer;
  private GameArchiver gameArchiver;

  private LineConnection tacConnection;
  private boolean isConnectionInitialized = false;
  private int tacConnectionCounter = 0;

  private Timer timer = new Timer();
  private ISTimerTask timerTask;

  private boolean webJoin = true;
  private boolean autoJoin = true;

  private static final String ADMIN_HEADER =
    "<table border=0 bgcolor=black cellspacing=0 cellpadding=1 width='100%'>"
    + "<tr><td>"
    + "<table border=0 bgcolor='#e0e0e0' "
    + "cellspacing=0 width='100%'><tr><td align=center>"
    + "<font face=arial>"
    + "<a href='/admin/'>Administration</a> | "
    + "<a href='/admin/games/'>Game Manager</a> | "
    + "<a href='/admin/competition/'>Competition Manager</a> | "
    + "<a href='/schedule/'>Competition Scheduler</a> | "
    + "<a href='http://www.sics.se/tac/docs/classic/server/1.0b11/admin.html' "
    + "target='cadmin'>Help</a>"
    + "</font>"
    + "</td></tr></table></td></tr></table>\r\n<p>";

  private GamePage gamePage;
  private String serverMessage;

  private InfoServer(ConfigManager config,
		     LogFormatter formatter,
		     TACStore store, String gamePath,
		     String backupPrefix)
    throws IOException
  {
    this.formatter = formatter;
    this.store = store;
    this.serverHost =
      config.getProperty("tac.host",
			 config.getProperty("server.host", "127.0.0.1"));
    this.serverPort = config.getPropertyAsInt("info.port", 4039);
    this.isName = config.getProperty("info.name", "solver");
    this.isPassword = config.getProperty("info.password", "sStruts3p");
    this.autoJoin = config.getPropertyAsBoolean("autojoin", false);
    this.httpPort = config.getPropertyAsInt("http.port", 8080);
    this.appletPort = config.getPropertyAsInt("viewer.port", 4040);

    String gameURL = config.getProperty("gameURL");
    this.gameArchiver =
      new GameArchiver(this, gamePath, gameURL, backupPrefix,
		       config.getProperty("runAfterGame"));

    // create applet handler...
    String infoHost = config.getProperty("info.host",
					 config.getProperty("server.host"));
    appletServer = new AppletServer(this, infoHost, appletPort);

    this.serverName = config.getProperty("server.name", null);
    if (serverName == null) {
      serverName = appletServer.getHost();
      if (serverName == null) {
	serverName = appletServer.getLocalHostName();
	if (serverName == null) {
	  serverName = "127.0.0.1";
	}
      }
    }

    this.serverMessage = readFile(SERVER_MESSAGE_FILE);

    this.httpServer = new HttpServer();
    if (infoHost != null) {
      InetAddrPort addr = new InetAddrPort(infoHost, this.httpPort);
      this.httpSocketListener = new SocketListener(addr);
    } else {
      this.httpSocketListener = new SocketListener();
      this.httpSocketListener.setPort(httpPort);
    }
    this.httpSocketListener.setMaxThreads(50);
    this.httpServer.addListener(httpSocketListener);

//     this.archiveContext = httpServer.getContext("/archive/");
//     this.archiveContext.setResourceBase("file:" + gamePath);
//     this.archiveResourceHandler = new ResourceHandler();
//     this.archiveResourceHandler.setDirAllowed(false);
//     this.archiveResourceHandler.setAllowedMethods(new String[] {
//       HttpRequest.__GET, HttpRequest.__HEAD
//     });
//     this.archiveResourceHandler.setAcceptRanges(true);
//     this.archiveContext.addHandler(this.archiveResourceHandler);
//     this.archiveContext.addHandler(new NotFoundHandler());

    this.httpContext = httpServer.getContext("/");
    this.httpContext.setResourceBase("./public_html/");

    String accesslog = config.getProperty("http.accesslog");
    if (accesslog != null) {
      NCSARequestLog rLog = new NCSARequestLog(accesslog);
      // Keep all logs
      rLog.setRetainDays(0);
      rLog.setAppend(true);
      rLog.setBuffered(false);
      this.httpServer.setRequestLog(rLog);
    }

    this.httpResourceHandler = new ResourceHandler();
    this.httpResourceHandler.setDirAllowed(false);
    this.httpResourceHandler.setAllowedMethods(new String[] {
      HttpRequest.__GET, HttpRequest.__HEAD
    });
    this.httpResourceHandler.setAcceptRanges(true);

    AgentRealm agentRealm = new AgentRealm(this, "TAC Classic");
    TACUser admin = store.getAdministrator();
    if (admin != null) {
      String adminName = admin.getName();
      String adminPassword = admin.getPassword();
      agentRealm.setAdminUser(adminName, adminPassword);
    }
    this.httpContext.addHandler(new SecurityHandler());
    this.httpContext.setRealm(agentRealm);

    this.pageHandler = new PageHandler();
    this.httpContext.addHandler(this.pageHandler);
    this.httpContext.addHandler(this.httpResourceHandler);
    this.httpContext.addHandler(new NotFoundHandler());

    SecurityConstraint security = new SecurityConstraint("TAC Classic", "*");
    this.httpContext
      .addSecurityConstraint("/games/*", security);
    this.httpContext
      .addSecurityConstraint("/applet/*", security);

    if (config.getPropertyAsBoolean("admin.pages", false)) {
      security = new SecurityConstraint("TAC Classic", AgentRealm.ADMIN_ROLE);
      this.httpContext
	.addSecurityConstraint("/admin/*", security);
      this.httpContext
	.addSecurityConstraint("/schedule/*", security);
      pageHandler.addPage("/admin/*",
			  new AdminPage(this, ADMIN_HEADER, gameURL));
      pageHandler.addPage("/schedule/",
			  new GameScheduler(this, ADMIN_HEADER));
    }

    String registrationURL = config.getProperty("users.registration");
    if (config.getPropertyAsBoolean("standalone", true)) {
      String page = "<html><head><title>TAC Classic Server "
	+ serverName + "</title>"
	+ "</head>\r\n<frameset border=0 rows='110,*'><frame src='/top/'>"
	+ "<frameset border=0 cols='155,*'>\r\n"
	+ "<frame src='/menu/'>\r\n"
	+ "<frame src='/status/' name='content'>\r\n"
	+ "</frameset></frameset>\r\n"
	+ "</html>\r\n";
      pageHandler.addPage("/", new StaticPage("/", page));

      page = "<html><body style='margin-bottom: -25'>\r\n"
	+ "<table border=0 width='100%'><tr><td>"
	+ "<img src='http://www.sics.se/tac/images/taclogo.gif' "
	+ "alt='TAC LOGO'>"
	+ "</td><td valign=top align=right><font face=arial>"
	+ "<b>Trading Agent Competition</b></font><br>"
	+ "<font face=arial size='-1' color='#900000'>"
	+ "SICS TAC Classic Java Server " + InfoServer.FULL_VERSION
	+ "</font></td></tr></table><hr>"
	+ "</body></html>\r\n";
      pageHandler.addPage("/top/", new StaticPage("/top/", page));

      page = "<html><body style='margin-right: -25'>\r\n"
	+ "<table border=0 width='100%'><tr><td bgcolor='#202080'>"
	+ "<font face=arial color=white><b>Menu</b></font></td></tr>"
	+ "<tr><td><a href='"
	+ (registrationURL == null ? "/register/" : registrationURL)
	+ "' target=content><font face=arial>"
	+ "Register new user"
	+ "</font></a>\r\n</td></tr><tr><td><a href='/games/' target=content>"
	+ "<font face=arial>Coming games (watch, create)</font></a></td></tr>"
	+ "<tr><td><a href='/history/' target=content>"
	+ "<font face=arial>Game History</font>"
	+ "</a></td></tr>"
	+ "<tr><td><a href='/score/' target=content>"
	+ "<font face=arial>Score Table</font>"
	+ "</a></td></tr></table></body></html>\r\n";
      pageHandler.addPage("/menu/", new StaticPage("/menu/", page));

      page = "<html><body><h2>TAC Classic Java Server "
	+ serverName + " is running.</h2>"
	+ "<em>Note: please do not schedule your agent in many "
	+ "games in advanced since it makes it hard for other "
	+ "teams to practice.</em>"
	+ "</body></html>";
      pageHandler.addPage("/status/", new StaticPage("/status/", page));

      pageHandler.addPage("/history/*",
			  new HistoryPage("/history/", this, gamePath));
    }
    pageHandler.addPage("/score/", new ScorePage(this, gameURL));
    if (registrationURL == null) {
      String notification = config.getProperty("users.notification");
      pageHandler.addPage("/register/",
			  new RegistrationPage(this, notification));
    }
    // Always have the user notify page running to enable user
    // information update during runtime
    pageHandler.addPage("/notify/", new UserNotificationPage(this));
    pageHandler.addPage("/games/", gamePage = new GamePage(this));
    pageHandler.addPage("/applet/", new AppletPage(this, appletPort));

    solveServer = new SolveServer(this);
    timerTask = new ISTimerTask(this);

    // Register the menu and status
    try {
      this.httpServer.start();
    } catch (Exception e) {
      throw (IOException)
	new IOException("could not start HTTP server").initCause(e);
    }

    // Start the viewer server
    appletServer.start();

    connect();
    schedule(timerTask, 0, 10000);
  }

  public String getServerName() {
    return serverName == null ? AppletServer.getLocalHostName() : serverName;
  }

  public void lineRead(LineConnection source, String line) {
//     log.finest("read: " + line); // DEBUG INFO_IN

    if (line == null) {
      source.close();
      return;
    }

    if (!isConnectionInitialized) {
      // Connection established => time to send state information

      TACGame[] comingGames = store.getComingGames();
      int maxGameID = 0;
      if (comingGames != null) {
	for (int i = 0, n = comingGames.length; i < n; i++) {
	  if (!comingGames[i].isFull()) {
	    // Always request from the first non-filled game because
	    // the prolog might have added some participants.
	    break;
	  } else {
	    // But we already know everything about the filled coming games
	    // because the prolog can not change them.
	    maxGameID = comingGames[i].getID();
	  }
	}
      }

      isConnectionInitialized = true;
      send("0,st,"
	   + store.getInt(TACStore.LAST_GAME_ID, 0) + ','
	   + store.getInt(TACStore.LAST_AUCTION_ID, 0) + ','
	   + store.getInt(TACStore.LAST_TRANSACTION_ID, 0) + ','
	   + store.getInt(TACStore.LAST_BID_ID, 0) + ','
	   + store.getInt(TACStore.LAST_UNIQUE_GAME_ID, 0) + ','
	   + maxGameID);

      TACUser[] users = store.getUsers();
      if (users != null) {
	for (int i = 0, n = users.length; i < n; i++) {
	  TACUser u = users[i];
	  send("0,us," + u.getID() + ',' + u.getName()
	       + ',' + u.getPassword());
	}
      }
    }

    timerTask.pong();

    ISTokenizer tok = new ISTokenizer(line);
    // Must handle the message before notifying the applets in case
    // the server time or coming games are changed.
    try {
      messageReceived(tok, line);
    } catch (Exception e) {
      log.log(Level.SEVERE, "could not parse command from server: " + line, e);
    }
    appletServer.messageReceived(tok.getCommand(), line);
  }

  private void messageReceived(ISTokenizer tok, String line) {
    switch (tok.getCommand()) {
    case ISTokenizer.GAME_STARTED:
      {
	int gid = tok.nextInt();
	long startTime = tok.nextTimeMillis();
	long endTime = tok.nextTimeMillis();
	Competition comp = store.getCurrentCompetition();
	if (tok.hasMoreTokens()) {
	  int uid = tok.nextInt();
	  String gameType = tok.nextToken();
	  int participantsInGame = tok.nextInt();
	  if ("null".equals(gameType)) {
	    gameType = null;
	  }
	  store.gameStarted(uid, gid, gameType, startTime,
			    (int) (endTime - startTime), participantsInGame);
	} else {
	  store.gameStarted(gid, gid, TAC_CLASSIC, startTime,
			    (int) (endTime - startTime), 8);
	}
	Competition comp2 = store.getCurrentCompetition();
	if (comp2 != null && comp2 != comp) {
	  if (comp2.hasGameID()) {
	    lockCompetitionGames(comp2, gid);
	  } else {
	    log.warning("starting competition " + comp2.getName() + " ("
			+ comp2.getID() + ") has no game ids!!!");
	  }
	  // A new competition has started: make sure the score files
	  // have been created
	  gameArchiver.prepareCompetition(gid - 1, comp2);
	}
      }
      break;
    case ISTokenizer.PONG:
      break;

    case ISTokenizer.GAME_IDS:
      if (schedulingGames != null) {
	try {
	  int noGames = tok.nextInt();
	  int firstReservedID = tok.nextInt();
	  if (noGames == schedulingNewGames) {
	    scheduleGames(schedulingGames, schedulingCompetition,
			  firstReservedID, schedulingNewGames);
	  } else {
	    log.severe("Not correct number of IDs for scheduled games ("
		       + noGames + " <=> "
		       + (schedulingNewGames) + ')');
	  }
	} finally {
	  schedulingGames = null;
	  schedulingCompetition = null;
	  schedulingNewGames = 0;
	}

      } else {
	log.severe("No scheduled games to use ID for");
      }
      break;

    case ISTokenizer.READY:
      // TAC server has sent all its state information
      break;
    case ISTokenizer.STATE:
      setServerTime(tok.getServerTimeSeconds());
      {
	int gid = tok.nextInt();
	int aid = tok.nextInt();
	int tid = tok.nextInt();
	int bid = tok.nextInt();
	int guid = tok.nextInt();
	store.setMaxInt(TACStore.LAST_GAME_ID, gid);
	store.setMaxInt(TACStore.LAST_AUCTION_ID, aid);
	store.setMaxInt(TACStore.LAST_TRANSACTION_ID, tid);
	store.setMaxInt(TACStore.LAST_BID_ID, bid);
	store.setMaxInt(TACStore.LAST_UNIQUE_GAME_ID, guid);

	if (tok.hasMoreTokens()) {
	  int lguid = tok.nextInt();

	  TACGame[] comingGames = store.getComingGames();
	  if (comingGames != null) {
	    TACGame game;

	    long time = getServerTimeMillis();
	    for (int i = 0, n = comingGames.length; i < n; i++) {
	      game = comingGames[i];
	      if (game.getEndTimeMillis() < time) {
		log.severe("detected existing coming game "
			   + game.getGameID() + " (" + game.getID()
			   + ") prior to current time");
		store.removeComingGame(game);
	      }
	    }

	    // Some games might have been removed
	    comingGames = store.getComingGames();
	    if (comingGames != null) {
	      for (int i = 0, n = comingGames.length; i < n; i++) {
		game = comingGames[i];
		// Send only the games the prolog does not yet know about
		if (game.getID() > lguid) {
		  send(game.getFutureGame(false));
		}
	      }
	    }
	  }
	  // Indicate for the TAC server that all information has
	  // been sent.
	  send("0,rd");
	}
      }
      break;
    case ISTokenizer.REQUEST_USER: {
      if (tok.hasMoreTokens()) {
	String name = tok.nextToken();
	updateAgent(name);
      }
      break;
    }
    case ISTokenizer.USER:
      break;
      // TO SOLVER!
    case ISTokenizer.SOLVE_REQUEST:
    case ISTokenizer.OWN:
    case ISTokenizer.CLIENT:
      solveServer.addCommand(tok);
      break;
    case ISTokenizer.ALLOCATION:
      break;
    case ISTokenizer.EXIT:
      // WHAT HERE???
      break;
    case ISTokenizer.GAME_ENDED:
      {
	int gid = tok.nextInt();
	int uid = tok.hasMoreTokens() ? tok.nextInt() : gid;
	log.fine("game " + gid + " ended");
	store.gameStopped(uid, gid);
      }
      break;
    case ISTokenizer.GAME_FINISHED:
      {
	int gid = tok.nextInt();
	int uid = tok.nextInt();
	log.fine("game " + gid + " is finished");
	gameArchiver.gameFinished(gid);
      }
      break;
    case ISTokenizer.GAME_REMOVED: {
      int uid = tok.nextInt();
      TACGame game = store.gameRemoved(uid);
      if (game != null) {
	TACGame[] comingGames = store.getComingGames();
	if ((comingGames == null) || (comingGames.length == 0)
	    || (comingGames[0].getStartTimeMillis()
		>= game.getStartTimeMillis())) {
	  // Display a warning if the first game has been scratched
	  String reason = tok.hasMoreTokens() ? tok.nextToken() : null;
	  gamePage.gameRemoved(game, reason);
	}
      }
      break;
    }
    case ISTokenizer.GAME_JOINED:
      {
	int uid = tok.nextInt();
	int aid = tok.nextInt();
	try {
	  store.gameJoined(uid, aid);
	  gamePage.gameJoined(uid, aid);
	} catch (TACException e) {
	  log.log(Level.SEVERE, "server out of sync: could not join "
		  + aid + " in game with id " + uid + ": ", e);
	}
      }
      break;
    case ISTokenizer.NEW_GAME_2:
      setServerTime(tok.getServerTimeSeconds());
      if (tok.hasMoreTokens()) {
	int uid = tok.nextInt();
	int gid = tok.nextInt();
	String gameType = tok.nextToken();
	long startTime = tok.nextTimeMillis();
	int gameLength = tok.nextInt() * 1000;
	int participantsInGame = tok.nextInt();
	if (gameType != null
	    && ((gameType.length() == 0) || "null".equals(gameType))) {
	  gameType = null;
	}

// 	log.fine("new coming game " + uid + " (gameid="
// 		 + gid + ',' + gameType + ')');

	// Make sure to store the highest seen game id
	store.setMaxInt(TACStore.LAST_UNIQUE_GAME_ID, uid);
	store.setMaxInt(TACStore.LAST_GAME_ID, gid);
	// int len = tok.nextInt();
	TACGame game = store.createGame(uid, gid, gameType,
					startTime, gameLength,
					participantsInGame);
	while (tok.hasMoreTokens()) {
	  game.joinGame(tok.nextInt());
	}
	if (!game.isEmpty()) {
	  // This is a hack to avoid storing the game for each participant. FIX THIS!!!
	  store.gameChanged(game, TACStore.AGENT_JOINED);
	}
	gamePage.gameCreated(game);
      }
      break;

    case ISTokenizer.GAME_LOCKED: {
      int uid = tok.nextInt();
      int gid = tok.nextInt();
      TACGame game = store.getComingGameByUniqID(uid);
      if (game != null && game.getGameID() != gid) {
	store.gameLocked(game, gid);

	// Check if this game is included in any competition.
	// (should be made more efficient! FIX THIS!!!)
	Competition comp = store.getCompetitionByUniq(uid);
	if (comp != null && !comp.hasGameID()) {
	  int compStartGame = comp.getStartGame();
	  if (compStartGame == uid) {
	    comp.setStartGameID(gid);
	  } else {
	    comp.setStartGameID(gid - (uid - compStartGame));
	  }
	  store.competitionLocked(comp);

	  lockCompetitionGames(comp, gid);
	}
      }
      break;
    }

    case ISTokenizer.GAME_TYPES: {
      ArrayList types = new ArrayList();
      Hashtable table = new Hashtable();
      while (tok.hasMoreTokens()) {
	String type = tok.nextToken();
	table.put(type, tok.nextToken());
	types.add(type);
      }
      gameTypeTable = table;
      gameTypes = (String[]) types.toArray(new String[types.size()]);
      break;
    }

    case ISTokenizer.AGENT: {
      // Make sure the agent is added to the current game
      TACGame game = store.getCurrentGame();
      if (game != null) {
	tok.nextToken();	// agent name
	game.joinGame(tok.nextInt());
      }
      break;
    }

    case ISTokenizer.AUCTION:
    case ISTokenizer.AUCTION_CLOSED:
    case ISTokenizer.QUOTE:
    case ISTokenizer.TRANSACTION:
      break;

    case ISTokenizer.SCORE:
//       if (tok.hasMoreTokens()) {
// 	int gid = tok.nextInt();
// 	int aid = tok.nextInt();
// 	float score = tok.nextFloat();
// 	int penalty = tok.nextInt();
// 	int util = tok.nextInt();
// 	store.updateScore(gid, aid, score, penalty, util, 0);
//       }
      break;

    case ISTokenizer.ERROR:
      log.log(Level.SEVERE, "Error: " + line);
      break;
    default:
      log.log(Level.SEVERE, "could not handle command: " + line);
      break;
    }
  }

  private void lockCompetitionGames(Competition competition, int gid) {
    if (competition.hasGameID()) {
      int startGameID = competition.getStartGameID();
      int numGames = competition.getGameCount() - (gid - startGameID);
      if (numGames > 0) {
	// Make sure the following games in the competition are locked
	log.finer("requesting lock of " + numGames
		  + " games due to start competition "
		  + competition.getName());
	send("0,rl," + numGames);
      }
    }
  }


  /**********************************************************************
   *
   **********************************************************************/

  String getServerMessage() {
    return serverMessage;
  }

  void setServerMessage(String serverMessage) {
    if (serverMessage == null) {
      if (this.serverMessage != null) {
	this.serverMessage = null;
	new File(SERVER_MESSAGE_FILE).delete();
      }

    } else if (!serverMessage.equals(this.serverMessage)) {
      this.serverMessage = serverMessage;
      saveFile(SERVER_MESSAGE_FILE, serverMessage);
    }
  }

  boolean isWebJoinActive() {
    return webJoin;
  }

  void setWebJoinActive(boolean webJoin) {
    this.webJoin = webJoin;
  }

  boolean isAutoJoinActive() {
    return autoJoin;
  }

  void setAutoJoin(boolean autoJoin) {
    this.autoJoin = autoJoin;
    send("0,aj," + autoJoin);
  }

  void schedule(TimerTask task, long delay, long period) {
    timer.schedule(task, delay, period);
  }

  void schedule(TimerTask task, long delay) {
    timer.schedule(task, delay);
  }



  // -------------------------------------------------------------------
  //
  // -------------------------------------------------------------------

  private boolean send(String text) {
    if (tacConnection != null) {
      // DEBUG INFO_OUT
//       log.finest("to server: '" + text + '\'');
      tacConnection.write(text);
      return true;
    }
    return false;
  }

  private void connect() {
    try {
      if (tacConnection != null) {
	tacConnection.closeImmediately();
      }
      isConnectionInitialized = false;
      tacConnection = new LineConnection("tac-" + (++tacConnectionCounter),
					 serverHost, serverPort, this, true);
      tacConnection.start();
      send("0,i," + isName + ',' + isPassword + ',' + FULL_VERSION);
    } catch (Exception e) {
      log.log(Level.SEVERE, "could not connect to TAC server:", e);
    }
  }


  // -------------------------------------------------------------------
  // API towards ISTimerTask
  // -------------------------------------------------------------------

  void sendPing() {
    send("0,pi");
  }

  void reconnect() {
    log.severe("Reconnecting to TAC server at "
	       + serverHost + ':' + serverPort);
    if (tacConnection != null) {
      tacConnection.close();
    }
    // Should wait a few seconds before reconnecting... FIX THIS!!!
    connect();
  }


  // -------------------------------------------------------------------
  //
  // -------------------------------------------------------------------

  public void solveFinished(Solver solver, int result, int gid, int aid) {
    int[][] latestAlloc = solver.getLatestAllocation();
    StringBuffer msg = new StringBuffer();
    msg.append("0,ar,").append(gid).append(',').append(aid)
      .append(',').append(result)
      .append(',').append(solver.getCalculationTime());
    for (int c = 0, cn = latestAlloc.length; c < cn; c++) {
      int[] alloc = latestAlloc[c];
      for (int t = 0, tn = alloc.length; t < tn; t++) {
	msg.append(',').append(alloc[t]);
      }
    }
    send(msg.toString());
  }



  /*********************************************************************
   * Competition and game scheduling
   *********************************************************************/

  private void checkFreeTime(long startTime, long endTime) {
    long currentTime = getServerTimeMillis();
    // The new addition must be at least some seconds into the future
    if (startTime <= (currentTime + MIN_TIME_BEFORE_CHANGE)) {
      throw new IllegalArgumentException("Start time already passed "
					 + "or too close into the future");
    }

    // Check for overlapping competitions
    Competition[] comingCompetitions = store.getComingCompetitions();
    if (comingCompetitions != null) {
      for (int i = 0, n = comingCompetitions.length; i < n; i++) {
	Competition comp = comingCompetitions[i];
	long cStart = comp.getStartTime();
	long cEnd = comp.getEndTime();
	if (cStart <= startTime) {
	  if (cEnd > startTime) {
	    throw new IllegalArgumentException("Overlapping competition "
					       + comp.getName());
	  }
	} else if (cStart < endTime) {
	  // cStart > startTime
	  throw new IllegalArgumentException("Overlapping competition "
					     + comp.getName());
	}
      }
    }

    // Check for any non-removable game
    TACGame[] comingGames = store.getComingGames();
    if (comingGames != null) {
      for (int i = comingGames.length - 1; i >= 0; i--) {
	TACGame game = comingGames[i];
	// The games are sorted
	if (game.getEndTimeMillis() < startTime) {
	  break;
	}

	if (game.getStartTimeMillis() < endTime) {
	  // Possible conflicting game
	  if (game.hasGameID()) {
	    // Game already has an assigned game id and can not be removed
	    throw new IllegalArgumentException("Conflict with game "
					       + game.getID() + " ("
					       + game.getGameID() + ')');
	  }
	}
      }
    }
  }

  public boolean reserveTime(long startTime, int reserveLength) {
    checkFreeTime(startTime, startTime + reserveLength);
    return send("0,rf,0,null," + (startTime / 1000) + ','
		+ (reserveLength / 1000) + ",0");
  }

  public void scheduleGames(TACGame[] games, Competition comp) {
    if (schedulingGames != null) {
      throw new IllegalStateException("Already scheduling games");
    }
    if (games == null || games.length == 0) {
      throw new IllegalArgumentException("no games");
    }
    int gamesLen = games.length;
    long startTime = games[0].getStartTimeMillis();
    long endTime = games[gamesLen - 1].getEndTimeMillis()
      + DELAY_BETWEEN_GAMES;
    checkFreeTime(startTime, endTime);

    schedulingGames = games;
    schedulingCompetition = comp;
    schedulingNewGames = gamesLen;

    if (!send("0,ri," + schedulingNewGames)) {
      throw new IllegalStateException("could not contact TAC server at this time");
    }
  }

  private void scheduleGames(TACGame[] games, Competition competition,
			     int firstID, int noGames) {
    int gamesLen = games.length;
    for (int i = 0; i < gamesLen; i++) {
      TACGame game = games[i];
      StringBuffer sb = new StringBuffer();
      game.setID(firstID++);
      sb.append("0,rf,").append(game.getID())
	.append(',').append(game.getGameType())
	.append(',').append(game.getStartTimeMillis() / 1000)
	.append(',').append(game.getGameLength() / 1000)
	.append(',').append(game.getParticipantsInGame());
      for (int j = 0, m = game.getNumberOfParticipants(); j < m; j++) {
	sb.append(',').append(game.getParticipant(j));
      }
      send(sb.toString());
    }

    log.fine(">>> setting competition ids: " + games[0].getID() + " - " +
	     (games[gamesLen - 1].getID()));
    if (competition != null) {
      competition.setGames(games[0], games[gamesLen - 1]);
      store.addCompetition(competition);
      gameArchiver.prepareCompetition(0, competition);
    }
  }

  public void removeCompetition(Competition competition) {
    store.removeCompetition(competition);
  }

  public void removeGame(TACGame game) {
    if (game.getGameID() > 0) {
      throw new IllegalStateException("game is locked and can no longer be removed");
    }

    Competition[] comingCompetitions = store.getComingCompetitions();
    int uid = game.getID();
    if (comingCompetitions != null) {
      for (int i = 0, n = comingCompetitions.length; i < n; i++) {
	Competition comp = comingCompetitions[i];
	if (comp.getStartGame() <= uid && comp.getEndGame() >= uid) {
	  throw new IllegalStateException("game is in competition "
					  + comp.getName());
	}
      }
    }

    send("0,rr," + uid);
  }

//   public void removeGame(TACGame[] games) {
//     if (games != null) {
//       StringBuffer sb = new StringBuffer().append("0,rr");
//       for (int i = 0, n = games.length; i < n; i++) {
// 	sb.append(',').append(games[i].getID());
//       }
//       send(sb.toString());
//     }
//   }

  // User Notification
  public void updateAgent(int agentID) {
    TACUser user = store.updateUser(agentID);
    if (user != null) {
      log.finer("updated information about user " + user.getName());
      // Must inform the TAC server about the new or updated user
      send("0,us," + user.getID() + ',' + user.getName() + ','
	   + user.getPassword());
    }
  }

  // User Notification
  public void updateAgent(String agentName) {
    TACUser user = store.updateUser(agentName);
    if (user != null) {
      log.finer("updated information about user " + agentName);

      // Must inform the TAC server about the new or updated user
      send("0,us," + user.getID() + ',' + user.getName() + ','
	   + user.getPassword());
    }
  }

  public int registerAgent(String agent, String password, String email)
    throws TACException
  {
    TACUser user = store.createUser(agent, password, email);
    // Must inform the TAC server about the new user
    send("0,us," + user.getID() + ',' + user.getName() + ','
	 + user.getPassword());
    return user.getID();
  }

  public boolean createGame(String gameType) {
    return send("0,cg," + gameType);
  }

  public TACUser getUser(String agent, String password) {
    TACUser user = store.getUser(agent);
    if (user != null && password != null &&
	password.equals(user.getPassword())) {
      return user;
    }
    return null;
  }

  public TACUser getAdministrator() {
    return store.getAdministrator();
  }

  // The result is sent back to the HTTP
  public boolean joinGame(int uniqGameID, int agent) throws TACException {
    return send("0,jg," + uniqGameID + ',' + agent);
  }

  TACStore getTACStore() {
    return store;
  }

  GameArchiver getGameArchiver() {
    return gameArchiver;
  }

  String[] getGameTypes() {
    return gameTypes;
  }

  String getGameTypeName(String gameType) {
    if (gameType == null) {
      return "reserved";
    }
    String name = gameTypeTable != null
      ? (String) gameTypeTable.get(gameType)
      : null;
    return name == null ? gameType : name;
  }


  /*********************************************************************
   * Server time handling
   *********************************************************************/

  public long getServerTimeSeconds() {
    return (System.currentTimeMillis() / 1000) + timeDiff;
  }

  public long getServerTimeMillis() {
    return System.currentTimeMillis() + timeDiff * 1000;
  }

  public StringBuffer appendServerTime(StringBuffer sb) {
    long td = getServerTimeSeconds();
    long sek = td % 60;
    long minutes = (td / 60) % 60;
    long hours = (td / 3600) % 24;
    if (hours < 10) sb.append('0');
    sb.append(hours).append(':');
    if (minutes < 10) sb.append('0');
    sb.append(minutes).append(':');
    if (sek < 10) sb.append('0');
    sb.append(sek);
    return sb;
  }

  private void setServerTime(long serverTime) {
    timeDiff = serverTime - (System.currentTimeMillis() / 1000);
    formatter.setLogTime(serverTime * 1000);
  }

  public static String getServerTimeAsString(long serverTime) {
    // FIX THIS!!!
    return GameResultCreator.formatServerTimeDate(serverTime);
  }

  public StringBuffer appendTimeMillis(StringBuffer sb, long td) {
    td /= 1000;
    long sek = td % 60;
    long minutes = (td / 60) % 60;
    long hours = (td / 3600) % 24;
    if (hours < 10) sb.append('0');
    sb.append(hours).append(':');
    if (minutes < 10) sb.append('0');
    sb.append(minutes).append(':');
    if (sek < 10) sb.append('0');
    sb.append(sek);
    return sb;
  }



  // -------------------------------------------------------------------
  // File IO utilities
  // -------------------------------------------------------------------

  private String readFile(String filename) {
    try {
      BufferedReader reader = new BufferedReader(new FileReader(filename));
      StringBuffer data = new StringBuffer();
      String line;
      try {
	while ((line = reader.readLine()) != null) {
	  data.append(line).append('\n');
	}
      } finally {
	reader.close();
      }
      return data.toString();
    } catch (FileNotFoundException e) {

    } catch (Exception e) {
      log.log(Level.WARNING, "could not load text from "
	      + filename, e);
    }
    return null;
  }

  private void saveFile(String filename, String text) {
    try {
      FileWriter writer = new FileWriter(filename, false);
      try {
	writer.write(text);
      } finally {
	writer.close();
      }

    } catch (Exception e) {
      log.log(Level.SEVERE, "could not save text to "
	      + filename + " (" + text + ')', e);
    }
  }


  // -------------------------------------------------------------------
  // InfoServer startup handling
  // -------------------------------------------------------------------

  public static void main(String[] args) throws IOException {
    ArgumentManager config = new ArgumentManager("InfoServer", args);
    config.addOption("config", "configfile", "set the config file to use");
    config.addOption("server.name", "serverName", "set the server name");
    config.addOption("gamePath", "path", "set the path to store game data");
    config.addOption("useSQL", "use SQL database for persistence");
    config.addOption("useGUI", "use admin GUI");
    config.addOption("copyData", "copy server state");
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

    // It is possible to specify an absolute URL to where the games
    // are found but the default should suffice in most cases.
    //   "/history/"
    //   "http://www.sics.se/tac/games/"
    boolean useSQL = config.getPropertyAsBoolean("sql.use", false);
    if (!useSQL) {
      useSQL = config.getPropertyAsBoolean("useSQL", false);
      if (useSQL) {
	config.setProperty("sql.use", "true");
      }
    }
    boolean useGUI = config.getPropertyAsBoolean("useGUI", false);
    int consoleLevel = config.getPropertyAsInt("log.consoleLevel", 3);
    int fileLevel = config.getPropertyAsInt("log.fileLevel", 0);
    Level consoleLogLevel = LogFormatter.getLogLevel(consoleLevel);
    Level fileLogLevel = LogFormatter.getLogLevel(fileLevel);
    Level logLevel = consoleLogLevel.intValue() < fileLogLevel.intValue()
      ? consoleLogLevel : fileLogLevel;

    // Game path is specified in relation to the configuration file
    String gamePath = config.getProperty("gamePath", "public_html/history");
    if (gamePath.endsWith("/") || gamePath.endsWith(File.separator)) {
      gamePath = gamePath.substring(0, gamePath.length() - 1);
    }
    // Get absolute path to the game directory
    File fp = new File(gamePath);
    gamePath = fp.getAbsolutePath();
    if ((!fp.exists() && !fp.mkdirs()) || !fp.isDirectory()) {
      System.err.println("game directory '" + gamePath
			 + "' does not exist or is not a directory");
      System.exit(1);
    }

    String logDirectory = config.getProperty("log.directory", "logs");
    String logName = getLogDirectory(logDirectory, "is");
    String backupDirectory =
      getLogDirectory(config.getProperty("log.games", logDirectory), "");

    Logger root = Logger.getLogger("");
    root.setLevel(logLevel);

    LogFormatter.setConsoleLevel(consoleLogLevel);
//     LogFormatter.setLevelForAllHandlers(logLevel);
    FileHandler fileHandler = new FileHandler(logName + "%g.log",
					      1000000, 10);
    fileHandler.setLevel(fileLogLevel);
    root.addHandler(fileHandler);
    LogFormatter formatter = new LogFormatter();
    formatter.setAliasLevel(2);
    LogFormatter.setFormatterForAllHandlers(formatter);

    log.fine("Using game path: " + gamePath);

    TACStore store = null;
    if (useSQL) {
      do {
	try {
	  store = new SQLTACStore(config);
	} catch (Exception e) {
	  log.log(Level.SEVERE, "could not access database", e);
	  log.severe("will wait and retry database setup in 60 seconds...");
	  try {
	    Thread.sleep(60000);
	  } catch (InterruptedException e2) {
	    // Ignore interrupts
	  }
	}
      } while (store == null);

    } else {
      store = new FileTACStore();
    }

    if (config.hasArgument("copydata")) {
      // Simply copy the data and exit
      TACStore target;
      if (useSQL) {
	target = new FileTACStore();
      } else {
	target = new SQLTACStore(config);
      }
      target.addData(store);
      log.info("data has been copied");
      System.exit(0);
    }

    // Ease for the garbage collector
    configFile = null;
    fp = null;

    if (store.getAdministrator() == null) {
      LogFormatter.separator(log, Level.SEVERE,
			     "You should register an admin user with the "
			     + "name '" + TACStore.ADMIN_NAME + "'!");
    }

    InfoServer server =
      new InfoServer(config, formatter, store, gamePath, backupDirectory);

    if (useGUI || config.getPropertyAsBoolean("admin.gui", false)
	|| config.getPropertyAsBoolean("info.admin.gui", false)) {
      AdminMonitor adminMonitor = AdminMonitor.getDefault();
      if (adminMonitor != null) {
	String bounds = config.getProperty("info.admin.bounds",
					   config.getProperty("admin.bounds"));
	if (bounds != null) {
	  adminMonitor.setBounds(bounds);
	}
	adminMonitor.setTitle("info@" + server.getServerName());
	adminMonitor.start();
      }
    }
  }

  private static String getLogDirectory(String logDirectory, String name)
    throws IOException
  {
    if (logDirectory == null) {
      return name;
    }

    // Create directories for logs
    File fp = new File(logDirectory);
    if ((!fp.exists() && !fp.mkdirs()) || !fp.isDirectory()) {
      throw new IOException("could not create directory '"
			    + logDirectory + '\'');
    }
    return name == null
      ? fp.getAbsolutePath()
      : (fp.getAbsolutePath() + File.separatorChar + name);
  }

}
