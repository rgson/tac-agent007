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
 * TACApplet
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 24 October, 2001
 * Updated : $Date: 2004/07/13 11:57:13 $
 *	     $Revision: 1.3 $
 * Purpose : To visualize games in real time or based on stored game data.
 *
 * For a description of the TAC server - TAC InfoServer communication:
 * see the file '../../../../isprotocol.txt'.
 */

package se.sics.tac.applet;
import java.applet.Applet;
import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.SimpleTimeZone;
import java.util.StringTokenizer;
import java.util.zip.GZIPInputStream;

public class TACApplet extends Applet implements Runnable, MouseListener,
						 ActionListener {

  private final static String VERSION = "1.0";

  private static final boolean DEBUG = false;

  private final static int DEFAULT_PORT = 4040;
  private final static int IN_FLIGHT = 0;
  private final static int OUT_FLIGHT = 1;
  private final static int GOOD_HOTEL = 2;
  private final static int CHEAP_HOTEL = 3;
  private final static int E1 = 4;
  private final static int E2 = 5;
  private final static int E3 = 6;

  private final static int BUY = 0;
  private final static int SELL = 1;
  private final static String[] resourceStr = {
    "In Flight", "OutFlight",
    "Good Hotel", "Cheap Hotel",
    "Alligator", "Amusement", "Museum"
  };

  private final static Color hotelColor = new Color(0x00, 0xd0, 0x00);
  private final static Color enterColor = new Color(0xe0, 0x40, 0x40);
  private final static Color flightColor = new Color(0x00, 0x00, 0xff);
  private final static Color blockColor = new Color(0xf0, 0xf0, 0xf0);
  private final static Color dayColor = new Color(0xd0, 0xff, 0xd0);

  private final static Color hypoColor[] = {
    new Color(0x00, 0xb0, 0x00),
    new Color(0xc0, 0xff, 0xc0)
  };

  private int hypoNo = 0;

  private Socket socket;
  private PrintWriter out;
  private LineNumberReader in;
  private LineNumberReader oldGameStream;

  private int port = 0;
  private Component paintArea;

  private Graphics buffer;
  private Image image;

  private int auctionClosedWidth = -1;
  private String[] auctionClosedIndicator;
  private int clickCloseWidth = -1;

  private Thread mainThread = null;
  private boolean running;
  private boolean gameEnded = true;

  private Label status;
  private Label gameLabel;
  private Label time;

  private Button incButton;
  private Button decButton;
  private Button showButton;
  private Button currButton;
  private TextField gameNo;

  private TextArea chatArea;
  private int numberOfChatMessages = 0;
  private TextField chatMsg;
  private Button chatButton;
  private Button clearButton;

  private Dimension size;
  private int rowHeight;
  private int colWidth;
  private int startY;

  private long startTime;
  private long endTime;
  private long timeDiff;

  private int agentNo = 0;
  private int gameID = -1;

  private long nextKnownGameStartTime = -1;
  private boolean showNextGame = true;

  private String agentName = "anonymous";

  // Client data 8 clients, each with 5 days containing  items
  // (In, Out, H1, H2, E1, E2, E3) - own = bit 0-7, hqw = bit x-8
  private int[][][] state = new int[8][5][7];

  // 5 * 7 resources -> 5 * 7 auction ids
  private int[] auction2rsc = new int[5 * 7];
  private float[][] rscQuotes = new float[2][5 * 7];
  private int[] rscClosed = new int[5 * 7];
  private int numberOfClosedAuctions = 0;

  private String[] agents = new String[] { "A1", "A2", "A3", "A4",
					   "A5", "A6", "A7", "A8" };
  private int[] agentIDs = new int[8];
  private float[] cost = new float[8];
  private float[] score = new float[8];
  private int[] rank = new int[8];
  private boolean hasRanks = false;
  private int[] util = new int[8];
  private boolean[] hasScore = new boolean[8];

  // Infl, outfl, hval, e1val, e2val, e3val
  private int[][][] clientPrefs = new int[8][8][6];
  private int[] clientNo = new int[8];

  // Message showing...
  private static final int INFO_MSG = 1;  // Just a info msg
  private static final int CLIENT_PREFS_MSG = 2;
  private int messageType = 0;
  private String messageTitle = null;
  private String messageText = null;
  private int messageWidth = -1;
  private int messageStart = 0;
  private int showAgent = 0;

  private SimpleDateFormat dateFormat;
  private Date date;

  public void init() {
    String portS = getParameter("port");
    agentName = getParameter("agent");
    if (portS != null) {
      try {
	port = Integer.parseInt(portS);
      } catch (Exception e) {
      }
    }
    if (port == 0) {
      port = DEFAULT_PORT;
    }

    setBackground(Color.white);
    setForeground(Color.black);
    setLayout(new BorderLayout());
    Panel panel = new Panel(new BorderLayout());
    add(panel, BorderLayout.NORTH);
    panel.add(gameLabel = new Label("Showing Game: -     "),
	      BorderLayout.WEST);
    panel.add(time = new Label("Time: 00:00:00 / 00:00 "),
	      BorderLayout.EAST);

    panel.add(panel = new Panel(new FlowLayout(FlowLayout.CENTER, 3, 2)),
	      BorderLayout.CENTER);
    panel.add(decButton = createButton("<"));
    panel.add(incButton = createButton(">"));
    panel.add(gameNo = createTextField("1", 5));
    panel.add(showButton = createButton("Show!"));
    panel.add(currButton = createButton("Current Game!"));

    panel = new Panel(new BorderLayout());
    panel.add(status = new Label(), BorderLayout.NORTH);
    panel.add(chatArea = new TextArea("", 6, 40,
				      TextArea.SCROLLBARS_VERTICAL_ONLY),
	      BorderLayout.CENTER);
    chatArea.setBackground(Color.white);
    chatArea.setForeground(Color.black);
    chatArea.setEditable(false);

    add(panel, BorderLayout.SOUTH);
    panel.add(panel = new Panel(new BorderLayout()),
	      BorderLayout.SOUTH);
    panel.add(chatMsg = createTextField("", 0), BorderLayout.CENTER);
    Panel pan = new Panel(new FlowLayout(FlowLayout.CENTER, 0, 0));
    pan.add(chatButton = createButton("Send"));
    pan.add(clearButton = createButton("Clear"));
    panel.add(pan, BorderLayout.EAST);
    panel.add(new Label(agentName + ':'), BorderLayout.WEST);

    chatMsg.addActionListener(this);

    paintArea = new Component() {
	public void update(Graphics g) {
	  paint(g);
	}
	public void paint(Graphics g) {
//    	  updateTime();
	  repaintGame(g);
	}
      };

    add(paintArea, BorderLayout.CENTER);
    paintArea.addMouseListener(this);
  }

  private Button createButton(String name) {
    Button button = new Button(name);
    button.addActionListener(this);
    button.setBackground(Color.lightGray);
    button.setForeground(Color.black);
    return button;
  }

  private TextField createTextField(String value, int columns) {
    TextField field = columns == 0
      ? new TextField(value)
      : new TextField(value, columns);
    field.setBackground(Color.white);
    field.setForeground(Color.black);
    return field;
  }

  public void start() {
    running = true;
    currButton.setEnabled(true);
    showButton.setEnabled(true);
    // Must clear game before starting
    clearGame();

    mainThread = new Thread(this);
    mainThread.start();
    new Thread(new Runnable() {
	public void run() {
	  try {
	    while (running) {
	      sleep(gameEnded ? 1000 : 300);
	      updateTime();
	      TACApplet.this.repaint();
	    }
	  } catch (Exception e) {
	    System.err.println("Timer thread died!");
	    e.printStackTrace();
	  }
	}
      } ).start();
  }

  public void stop() {
    if (DEBUG) System.out.println("Stopping applet... xt");
    PrintWriter out = this.out;
    if (out != null) {
      out.println("0,xt");
      out.flush();
    }
    running = false;
    disconnect();
  }

  public void run() {
    if (Thread.currentThread() != mainThread) {
      if (oldGameStream != null) {
	try {
	  gameLoop(oldGameStream);
	  if (oldGameStream != null) {
	    oldGameStream.close();
	  }
	} catch (Exception e) {
	  System.err.println("# could not show old game:");
	  e.printStackTrace();
	}
	oldGameStream = null;
      }
      currButton.setEnabled(true);
      showButton.setEnabled(true);
      repaint();
      return;
    }

    while (running) {
      if (connect()) {
	String line = null;
	if (DEBUG) System.out.println("Connected...");
	try {
	  boolean isConnected = true;
	  while (isConnected && ((line = in.readLine()) != null)) {
	    if (DEBUG) System.out.println("Read Game line: '" + line + '\'');
	    if (line.length() == 0) {
	      continue;
	    }

	    StringTokenizer stok = new StringTokenizer(line, ",");
	    // Ignore server time for now
	    String serverTime = stok.nextToken();
	    String cmd = stok.nextToken();
	    switch (cmd.charAt(0)) {
	    case 'c':
	      setClient(stok);
	      break;
	    case 'd':
	      // Ignore seed
	      break;
	    case 'm':
	      showChatMessage(line);
	      break;
	    case 'f':
	      futureGame(serverTime, stok);
	      break;
	      // This means that the a game is running
	      // Maybe this should be 'n' for nextGame ???
	    case 's':
	      setScore(stok);
	      break;
	    case 'g':
	      setGame(stok);
	      // Ignore the new game if an old game is already showing...
	      if (oldGameStream == null) {
		isConnected = gameLoop(in);
	      }
	      break;
	    case 'i':
	      // Identification and version
	      checkVersion(stok);
	      break;
	    case 'x':
	    case 'z':
	      break;
	    default:
	      System.out.println("Ignored: " + line);
	      break;
	    }
	  }
	} catch (Exception e) {
	  System.err.println("Error in server communication: "
			     + (line != null ? line : ""));
	  e.printStackTrace();
	}
	disconnect();
	if (running) {
	  // Wait 1 second before trying to connect again ???
	  sleep(1000);
	}
      } else {
	setStatus("Failed to connect, will retry...");
	status.repaint();
	sleep(24000);
      }
    }
  }

  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (Exception e) {
      System.err.println("# interrupted during sleep: " + e);
    }
  }

  private boolean gameLoop(LineNumberReader in) {
    try {
      // This is the game loop that reads a complete game
      String line = null;
      boolean gameOn = true;
      boolean thisGameEnded = false;
      while (gameOn && (line = in.readLine()) != null) {
	if (DEBUG) System.out.println("Read line: '" + line + '\'');
	if (line.length() == 0) {
	  continue;
	}

	StringTokenizer stok = new StringTokenizer(line, ",");
	// Ignore server time for now
 	String serverTime = stok.nextToken();
	String cmd = stok.nextToken();
	switch (cmd.charAt(0)) {
	case 'a':
	  addAgent(stok);
	  break;
	case 'b':
	  // Ignore bids for now.
	  break;
	case 'c':
	  setClient(stok);
	  break;
	case 'd':
	  // Ignore random seed for now.
	  break;
	case 'e':
	  String error = stok.nextToken();
	  String message = "Server reported error '" + error + '\'';
	  System.err.println(message);
	  showInfoMessage("", message);
	  break;
	case 'f':
	  if (oldGameStream != in) {
	    futureGame(serverTime, stok);
	  }
	  break;
	case 'g':
	  setGame(stok);
	  break;
	case 'l':
	  // Ignore allocation for now. FIX THIS!!!
	  break;
	case 'm':
	  showChatMessage(line);
	  break;
	case 'q':
	  setQuote(stok);
	  break;
	case 's':
	  setScore(stok);
	  break;
	case 't':
	  addTransaction(stok);
	  break;
	case 'u':
	  setAuction(stok);
	  break;
	case 'v':
	  // Ignore version for now
	  break;
	case 'x':
	  // If this is the old game running we must continue to read
	  // to get the scores that are sent after the game has been
	  // stopped by x. In old games we should read until the
	  // game stream are closed.
	  if (DEBUG) System.out.println("Game ended");
	  if (oldGameStream != in) {
	    gameOn = false;
	    if (this.gameID >= 0) {
	      setStatus("Game " + this.gameID + " has ended");
	    } else {
	      setStatus("");
	    }
	    gameEnded = true;
	  } else {
	    thisGameEnded = true;
	  }
	  break;
	case 'z':
	  closeAuction(stok);
	  break;
	default:
	  System.err.println("Can not handle: " + line);
	}
      }
      if (oldGameStream == in && !thisGameEnded) {
	// No end of game found in the log for this old game
	showInfoMessage("Game not complete!",
			"The game has not been finished.");
      }
      return line != null;
    } catch (Exception e) {
      e.printStackTrace();
      setStatus("Communication with game server failed");
      return false;
    }
  }

  private void showInfoMessage(String title, String text) {
    messageTitle = title;
    messageText = text;
    messageType = INFO_MSG;
    messageWidth = -1;
    paintArea.repaint();
  }

  private void showClientPreferences(int agent) {
    showAgent = agent;
    messageType = CLIENT_PREFS_MSG;
    messageWidth = -1;
    paintArea.repaint();
  }

  private void clearMessage() {
    int oldType = messageType;
    messageType = 0;
    messageWidth = -1;
    // If an info message was shown, do not wait for next auto repaint
    // before clearing it
    if (oldType != 0) {
      paintArea.repaint();
    }
  }

  private void clearGame() {
    // Clear params...
    agentNo = 0;
    // -1 indicates no game
    gameID = -1;
    gameEnded = true;
    for (int i = 0; i < 8; i++) {
      agentIDs[i] = Integer.MIN_VALUE;
      agents[i] = "" + 'A' + (i + 1);
      cost[i] = 0;
      hasScore[i] = false;
      clientNo[i] = 0;
      for (int r = 0; r < 7; r++)
	for (int d = 0; d < 5; d++)
	  state[i][d][r] = 0;
    }
    hasRanks = false;

    for (int i = 0; i < 5 * 7; i++) {
      rscQuotes[0][i] = 0;
      rscQuotes[1][i] = 0;
      rscClosed[i] = 0;
    }
    numberOfClosedAuctions = 0;
  }

  private int getAgent(int ID) {
    for (int i = 0; i < 8; i++)
      if (agentIDs[i] == ID)
	return i;
    return -1;
  }

  private int getResource(int ID) {
    for (int i = 0; i < 7 * 5; i++)
      if (auction2rsc[i] == ID)
	return i;
    return -1;
  }

  private int parseAgentID(String text) {
    try {
      return Integer.parseInt(text);
    } catch (Exception e) {
      // Not an agent
      return Integer.MIN_VALUE;
    }
  }

  private void addTransaction(StringTokenizer stok) {
    try {
      int buyerID = parseAgentID(stok.nextToken());
      int sellerID = parseAgentID(stok.nextToken());

      int auctID = Integer.parseInt(stok.nextToken());
      int q = Integer.parseInt(stok.nextToken());
      float price = Float.valueOf(stok.nextToken()).floatValue();

      int auctPos = getResource(auctID);
      if (auctPos >= 0) {
	addTransaction(buyerID, auctPos, q, price);
	addTransaction(sellerID, auctPos, -q, price);
      } else {
	System.err.println("could not find transaction auction: " + auctID);
      }
    } catch (Exception e) {
      System.err.println("Error parsing transaction data: " + e);
    }
  }

  private void addTransaction(int agentID, int auctPos, int q, float price) {
    // Check that it is an agent and not an auction that is this part of
    // the transaction
    if (agentID != Integer.MIN_VALUE) {
      int aPos = getAgent(agentID);
      if (aPos >= 0) {
	state[aPos][auctPos / 7][auctPos % 7] += q;
	cost[aPos] += q * price;
	paintArea.repaint();
      } else {
	System.err.println("could not find transaction agent " + agentID);
      }
    }
  }

  private void checkVersion(StringTokenizer stok) {
    try {
      // Ignore name and password for now
      stok.nextToken();
      stok.nextToken();
      if (stok.hasMoreTokens()) {
	String version = stok.nextToken();
	if (!VERSION.equals(version)) {
	  // Not the right version
	  showInfoMessage("Newer Applet version detected",
			  "Please restart your browser to upgrade.");
	}
      }
    } catch (Exception e) {
      System.err.println("Error parsing version: " + e);
    }
  }

  private void setQuote(StringTokenizer stok) {
    try {
      int auctID = Integer.parseInt(stok.nextToken());
      int auctPos = getResource(auctID);

      if (auctPos >= 0) {
	float buy = Float.valueOf(stok.nextToken()).floatValue();
	float sell = Float.valueOf(stok.nextToken()).floatValue();

	if (buy < 0) buy = 0;
	else if (buy < 10) buy = (float) ((int)(buy * 100) / 100.0);
	else if (buy < 100) buy = (float) ((int)(buy * 10) / 10.0);

	if (sell < 0) sell = 0;
	else if (sell < 10) sell = (float) ((int)(sell * 100) / 100.0);
	else if (sell < 100) sell = (float) ((int)(sell * 10) / 10.0);

	rscQuotes[BUY][auctPos] = buy;
	rscQuotes[SELL][auctPos] = sell;

  	for (int i = 0; i < 8; i++) {
  	  state[i][auctPos / 7][auctPos % 7] &= 0xff;
  	}
	while (stok.hasMoreTokens()) {
	  int aPos = getAgent(Integer.parseInt(stok.nextToken()));
	  int hqw = Integer.parseInt(stok.nextToken());
	  state[aPos][auctPos / 7][auctPos % 7] |= (hqw << 8);
	}

	// System.out.println("SetQuote: " + auctPos +  " = " + sell);
	// Ignore other stuff for now!!!
	paintArea.repaint();
      } else {
	System.err.println("could not find auction " + auctID);
      }
    } catch (Exception e) {
      System.err.println("Error parsing auction data: " + e);
    }
  }

  private void setScore(StringTokenizer stok) {
    try {
      int gID = Integer.parseInt(stok.nextToken());
      if (gID == gameID) {
	int aID = Integer.parseInt(stok.nextToken());
	int apos = getAgent(aID);
	if (apos == -1) {
	  System.err.println("agent " + aID + " not found in game " + gameID);
	} else {
	  // Score
	  // Add 0.5 to allow simple truncation to give correct int value
	  score[apos] = Float.valueOf(stok.nextToken()).floatValue() + 0.5f;
	  // Penalty
	  stok.nextToken();
	  // Utility
	  util[apos] = Integer.parseInt(stok.nextToken());
	  hasScore[apos] = true;

	  if (hasAllScores()) {
	    // Show ranking
	    int[] agents = new int[8];
	    for (int i = 0, n = agents.length; i < n; i++) {
	      agents[i] = i;
	    }

	    // Simple sort (only 8 agents)
	    for (int i = 0, n = agents.length; i < n - 1; i++) {
	      for (int j = i + 1; j < n; j++) {
		if(score[agents[j]] > score[agents[i]]) {
		  int tmp = agents[i];
		  agents[i] = agents[j];
		  agents[j] = tmp;
		}
	      }
	    }

	    for (int i = 0, n = agents.length; i < n; i++) {
	      if (i > 0 && (score[agents[i]] == score[agents[i - 1]])) {
		rank[agents[i]] = rank[agents[i - 1]];
	      } else {
		rank[agents[i]] = i + 1;
	      }
	    }
	    hasRanks = true;
	  }
	}
      }

    } catch (Exception e) {
      System.err.println("could not parse score data:");
      e.printStackTrace();
    }
  }

  private boolean hasAllScores() {
    for (int i = 0, n = hasScore.length; i < n; i++) {
      if (!hasScore[i]) {
	return false;
      }
    }
    return true;
  }

  private void setAuction(StringTokenizer stok) {
    try {
      while (stok.hasMoreTokens()) {
	int aID = Integer.parseInt(stok.nextToken());
	int rsc = Integer.parseInt(stok.nextToken());
	int day = Integer.parseInt(stok.nextToken()) - 1;
	auction2rsc[rsc + day * 7] = aID;
      }
    } catch (Exception e) {
      System.err.println("could not parse auction data:");
      e.printStackTrace();
    }
  }

  private void setClient(StringTokenizer stok) {
    try {
      int gID = Integer.parseInt(stok.nextToken());
      // Should check game id...
      if (gID == gameID) {
	int aID = Integer.parseInt(stok.nextToken());

	int pos = getAgent(aID);
	if (pos >= 0) {
	  if (DEBUG) System.out.println("setting client for agent " + aID + " at " + pos);

	  while (stok.hasMoreTokens()) {
	    if (clientNo[pos] < 8) {
	      for (int i = 0; i < 6; i++)
		clientPrefs[pos][clientNo[pos]][i] =
		  Integer.parseInt(stok.nextToken());
	      clientNo[pos]++;
	    } else {
	      break;
	    }
	  }
	} else {
	  System.err.println("could not find agent " + aID
			     + " for setting client information");
	}
      }
    } catch (Exception e) {
      System.out.println("Error parsing client data: " + e);
    }
  }

  private boolean setGame(StringTokenizer stok) {
    try {
      int gameID = Integer.parseInt(stok.nextToken());
      long start = Long.parseLong(stok.nextToken()) * 1000;
      long end = Long.parseLong(stok.nextToken()) * 1000;
      if (oldGameStream == null) {
	boolean gameEnded = end <= (System.currentTimeMillis() + timeDiff);
	// Not showing an old game!!!
	if (showNextGame) {
	  clearGame();
	  setGameLabel(gameID);
	  this.gameID = gameID;
	  endTime = end;
	  startTime = start;
	  this.gameEnded = gameEnded;
	}
	if (!gameEnded) {
	  setStatus("Game " + gameID + " is running");
	}
      } else {
	endTime = 0L;
	gameEnded = true;
      }
      if ((gameID == this.gameID) && (start == end) && gameEnded) {
	showInfoMessage("", "Game " + gameID + " has been scratched!");
      }
      return true;
    } catch (Exception e) {
      System.out.println("Error parsing game data: " + e);
      e.printStackTrace();
    }
    return false;
  }

  private void futureGame(String serverTime, StringTokenizer stok) {
    long time = Long.parseLong(serverTime) * 1000;
    timeDiff = time - System.currentTimeMillis();
    if (stok.hasMoreTokens()) {
      int gameID = Integer.parseInt(stok.nextToken());
      long start = Long.parseLong(stok.nextToken()) * 1000;
      nextKnownGameStartTime = start;
      if (start <= time) {
	// Game is running
	setStatus(gameID < 0
		  ? "Game is running"
		  : "Game " + gameID + " is running");
      } else {
	StringBuffer sb = new StringBuffer();
	sb.append("Next game ");
	if (gameID > 0) {
	  sb.append(gameID).append(' ');
	}
	sb.append(" will start at ");
	appendTime(sb, nextKnownGameStartTime, true, true);
	setStatus(sb.toString());
      }
    } else {
      // No future game scheduled
      setStatus("No future game has been scheduled");
    }
  }

  private boolean addAgent(StringTokenizer stok) {
    if (agentNo < 8) {
      if (stok.hasMoreTokens()) {
	agents[agentNo] = stok.nextToken();
	if (stok.hasMoreTokens()) {
	  try {
	    agentIDs[agentNo] = Integer.parseInt(stok.nextToken());
	    agentNo++;
	    paintArea.repaint();
	    return true;
	  } catch (Exception e) {
	    System.err.println("Error adding agent: " + e);
	  }
	}
      }
    } else {
      System.err.println("Too many agents");
    }
    return false;
  }

  private boolean closeAuction(StringTokenizer stok) {
    if (stok.hasMoreTokens()) {
      int auctID = Integer.parseInt(stok.nextToken());
      int rscPos = getResource(auctID);
      rscClosed[rscPos] = ++numberOfClosedAuctions;
      return true;
    } else {
      return false;
    }
  }


  /********************************************************
   * Communication
   ********************************************************/

  private void setShowNextGame(boolean showNextGame) {
    if (this.showNextGame != showNextGame) {
      this.showNextGame = showNextGame;
      if (out != null) {
	// This start or stops listening on game updates from the server
	out.println(showNextGame ? "0,gs" : "0,gx");
	out.flush();
      }
    }
  }

//   private void requestClientData(int gid, int aID) {
//     if (aID != Integer.MIN_VALUE) {
//       out.println("rc, " + gid + "," + aID);
//       if (DEBUG) System.out.println("requesting client data for: "
// 				    + gid + " " + aID);
//     }
//   }

  private void disconnect() {
    Socket socket = this.socket;
    this.socket = null;
    if (socket != null) {
      try {
	LineNumberReader in = this.in;
	this.in = null;
	PrintWriter out = this.out;
	this.out = null;
	LineNumberReader oldGameStream = this.oldGameStream;
	this.oldGameStream = null;

	close(out);
	close(in);
	close(oldGameStream);
	socket.close();
      } catch (Exception e) {
	System.err.println("failed to close connection:");
	e.printStackTrace();
      }
    }
  }

  private void close(PrintWriter out) {
    if (out != null) {
      try {
	out.close();
      } catch (Exception e) {
	e.printStackTrace();
      }
    }
  }

  private void close(LineNumberReader in) {
    if (in != null) {
      try {
	in.close();
      } catch (Exception e) {
	e.printStackTrace();
      }
    }
  }

  private void connectOldGame(int oldGameID) {
    try {
      try {
	// We should show an old game!
	connectOldGame(oldGameID, true);
      } catch (FileNotFoundException e) {
	// The game might not be gzipped and we need to retry this.
	connectOldGame(oldGameID, false);
      }
    } catch (FileNotFoundException e) {
      System.err.println("Could not find game " + oldGameID + ": " + e);
      showInfoMessage("", "Could not find game " + oldGameID);
    } catch (Exception e) {
      System.err.println("failed to connect to old game " + oldGameID + ':');
      e.printStackTrace();
      showInfoMessage("", "Could not find game " + oldGameID);
    }
  }

  private void connectOldGame(int oldGameID, boolean gzip) throws IOException {
    URL url = gzip
      ? new URL(getCodeBase(), "/history/"
		+ oldGameID + "/applet.log.gz")
      : new URL(getCodeBase(), "/history/" + oldGameID + "/applet.log");
    InputStream input = url.openStream();
    if (gzip) {
      input = new GZIPInputStream(input);
    }
    oldGameStream = new LineNumberReader(new InputStreamReader(input));
    clearGame();
    currButton.setEnabled(false);
    showButton.setEnabled(false);
    gameID = oldGameID;
    setGameLabel(oldGameID);
    new Thread(this).start();
  }

  private boolean connect() {
    try {
      // This is a live connection to a real game!!!
      URL url = getCodeBase();
      String host = url.getHost();
      disconnect();
      setStatus("Connecting to game server at " + host + ':' + port);
      status.repaint();
      System.out.println("Connecting to host: " + host + ':' + port);

      socket = new Socket(host, port);
      in = new LineNumberReader(new InputStreamReader(socket.getInputStream()));
      out = new PrintWriter(socket.getOutputStream(), true);
      setStatus("Connected to game server at " + host + ':' + port);
      out.println("0,i," + agentName + ",applet," + VERSION);
      if (showNextGame) {
	out.println("0,gs");
      }
      out.flush();

      chatArea.setText("");
      numberOfChatMessages = 0;
      return true;
    } catch (Exception e) {
      System.err.println("failed to connect:");
      e.printStackTrace();
    }
    return false;
  }

  private void setStatus(String txt) {
    status.setText("Status: " + txt);
    validate();
  }

  private void setGameLabel(int id) {
    gameLabel.setText("Showing Game: " + id);
    gameLabel.invalidate();
    validate();
  }

  /********************************************************
   * Repaint
   ********************************************************/

  private void updateTime() {
    long td = System.currentTimeMillis() + timeDiff;
    StringBuffer sb = new StringBuffer().append("Time ");
    appendTime(sb, td, true, true);
    sb.append(" / ");
    if ((td > endTime) || gameEnded) {
      sb.append("00:00");
    }  else {
      appendTime(sb, endTime - td, false, true);
    }
    time.setText(sb.toString());

    hypoNo = (hypoNo + 1) % hypoColor.length;
  }

  private void appendTime(StringBuffer sb, long td, boolean hour,
			  boolean seconds) {
    long minutes = (td / 60000) % 60;
    if (hour) {
      long hours = (td / 3600000) % 24;
      if (hours < 10) sb.append('0');
      sb.append(hours).append(':');
    }
    if (minutes < 10) sb.append('0');
    sb.append(minutes);
    if (seconds) {
      long sek = (td / 1000) % 60;
      sb.append(':');
      if (sek < 10) sb.append('0');
      sb.append(sek);
    }
  }

  private void repaintGame(Graphics gr) {
    // This is the repaint of the canvas!!!
    Graphics g = null;
    if (size == null) {
      size = paintArea.getSize();

      image = createImage(size.width, size.height);
      buffer = image.getGraphics();

      rowHeight = (int) (size.height - 114) / 8;
      colWidth = (int) (size.width - 120) / 5;
      startY = 20;
    }

    // Get the graphics for the image!!
    g = buffer;
    g.setColor(Color.white);
    g.fillRect(0, 0, size.width, size.height);

    FontMetrics metrics = g.getFontMetrics();
    if (auctionClosedWidth < 0) {
      // Initialize the auction closed width
      auctionClosedIndicator = new String[8];
      for (int i = 0; i < 8; i++) {
	auctionClosedIndicator[i] = "C-" + (i + 1);

	int w = metrics.stringWidth(auctionClosedIndicator[i]);
	if (w > auctionClosedWidth) {
	  auctionClosedWidth = w;
	}
      }
      auctionClosedWidth += 2;
    }

    if (hasRanks) {
      g.setColor(Color.lightGray);
      for (int i = 0; i < 8; i++) {
	g.drawString("" + '(' + rank[i] + ')',
		     102, startY + 12 + (int) (rowHeight * (i + 0.5)));
      }
    }

    g.setColor(Color.black);
    for (int i = 0; i < 8; i++) {
      g.drawString(agents[i], 8, startY + (int) (rowHeight * (i + 0.5)));
      if (hasScore[i]) {
	g.drawString("Score " + ((int) score[i]),
		     8, startY + 12 + (int) (rowHeight * (i + 0.5)));
      } else {
	g.drawString("Cost " + ((int) cost[i]),
		     8, startY + 12 + (int) (rowHeight * (i + 0.5)));
      }
    }

    int descY = startY + 8 * rowHeight + 6;
    for (int i = 0; i < 7; i++) {
      g.drawString(resourceStr[i], 14, descY + 7 + i * 13);
      for (int day = 0; day < 5; day++) {
	if ((day == 0 && i == OUT_FLIGHT) || (day == 4 && i != OUT_FLIGHT)) {
	  g.drawString("-", 124 + 14 + day * colWidth, descY + 7 + i * 13);
	} else {
	  int auctPos = day * 7 + i;
	  int y = descY + 7 + i * 13;
	  float buy = rscQuotes[BUY][auctPos];
	  float sell = rscQuotes[SELL][auctPos];
	  int auctionClosed = rscClosed[auctPos];
	  StringBuffer sb = new StringBuffer();
	  String quote;
	  if (buy < 100) {
	    sb.append(buy);
	  } else {
	    sb.append((int) buy);
	  }
	  sb.append(" / ");
	  if (sell < 100) {
	    sb.append(sell);
	  } else {
	    sb.append((int) sell);
	  }
	  quote = sb.toString();

	  int w = (colWidth - 14 - auctionClosedWidth
		   - metrics.stringWidth(quote)) / 2;
	  g.drawString(sb.toString(), 122 + 10 + day * colWidth + w, y);
// 	  g.drawString(sb.toString(), 124 + 10 + day * colWidth, y);
	  if (auctionClosed > 0) {
	    String closed = (auctionClosed < 9)
	      ? auctionClosedIndicator[auctionClosed - 1]
	      : "C";
	    g.drawString(closed,
			 120 + ((day + 1) * colWidth) - auctionClosedWidth, y);
	  }
	}
      }
    }

    // Draw "days"
    g.setColor(dayColor);
    g.fillRect(1, 1, 118 + 5 * colWidth, startY - 1);

    g.setColor(Color.black);
    g.drawString("Agent ", 8, 15);
    for (int i = 0; i < 5; i++) {
      g.drawString("Day " + (i + 1), 150 + (i * colWidth), 15);
    }

    g.setColor(Color.gray);
    for (int i = 0; i < 9; i++) {
      g.drawLine(0, startY + rowHeight * i,
		 size.width, startY + rowHeight * i);
    }
    g.drawLine(0, 0, size.width, 0);
    g.drawLine(0, size.height - 1, size.width, size.height - 1);

    // Draw columns
    for (int i = 0; i < 5; i++) {
      g.drawLine(120 + i * colWidth, 0, 120 + i * colWidth, size.height);
    }

    // Write out the "State"
    g.setColor(flightColor);
    g.draw3DRect(4, descY, 5, 5, true);
    g.fill3DRect(4, descY + 13, 6, 6, true);
    for (int i = 0; i < 5; i++) {
      g.draw3DRect(124 + i * colWidth, descY, 5, 5, true);
      g.fill3DRect(124 + i * colWidth, descY + 13, 6, 6, true);
    }

    for (int ag = 0; ag < 8; ag++) {
      for (int i = 0; i < 5; i++) {
	int in = state[ag][i][IN_FLIGHT];
	int out = state[ag][i][OUT_FLIGHT];
	for (int nr = 0; nr < in; nr++) {
	  drawRect(g, ag, i, nr, 0);
	}
	for (int nr = in; nr < out + in; nr++) {
	  fillRect(g, ag, i, nr, 0);
	}
      }
    }

    g.setColor(hotelColor);
    g.draw3DRect(4, descY + 13 * 2, 5, 5, true);
    g.fill3DRect(4, descY + 13 * 3, 6, 6, true);
    for (int i = 0; i < 5; i++) {
      g.draw3DRect(124 + i * colWidth, descY + 13 * 2, 5, 5, true);
      g.fill3DRect(124 + i * colWidth, descY + 13 * 3, 6, 6, true);
    }

    for (int ag = 0; ag < 8; ag++) {
      for (int i = 0; i < 5; i++) {
	int in = state[ag][i][GOOD_HOTEL] & 0xff;
	int out = state[ag][i][CHEAP_HOTEL] & 0xff;
	for (int nr = 0; nr < in; nr++) {
	  drawRect(g, ag, i, nr, 2);
	}
	for (int nr = in; nr < out + in; nr++) {
	  fillRect(g, ag, i, nr, 2);
	}
      }
    }

    g.setColor(hypoColor[hypoNo]);
    for (int ag = 0; ag < 8; ag++) {
      for (int i = 0; i < 5; i++) {
	int add = (state[ag][i][GOOD_HOTEL] & 0xff) +
	  state[ag][i][CHEAP_HOTEL] & 0xff;
	int in = state[ag][i][GOOD_HOTEL] >> 8;
	int out = state[ag][i][CHEAP_HOTEL] >> 8;
	for (int nr = add; nr < add + in; nr++) {
	  drawRect(g, ag, i, nr, 2);
	}
	for (int nr = add + in; nr < add + out + in; nr++) {
	  fillRect(g, ag, i, nr, 2);
	}
      }
    }

    g.setColor(enterColor);
    g.draw3DRect(4, descY + 13 * 4, 5, 5, true);
    drawAmusement(g, 4, descY + 13 * 5, 6, 6);
    g.fill3DRect(4, descY + 13 * 6, 6, 6, true);
    for (int i = 0; i < 5; i++) {
      g.draw3DRect(124 + i * colWidth, descY + 13 * 4, 5, 5, true);
      drawAmusement(g, 124 + i * colWidth, descY + 13 * 5, 6, 6);
      g.fill3DRect(124 + i * colWidth, descY + 13 * 6, 6, 6, true);
    }

    for (int ag = 0; ag < 8; ag++) {
      for (int i = 0; i < 5; i++) {
	int e1 = state[ag][i][E1] & 0xff;
	int e2 = state[ag][i][E2] & 0xff;
	int e3 = state[ag][i][E3] & 0xff;
	if (e1 > 0x80) e1 = 0;
	if (e2 > 0x80) e2 = 0;
	if (e3 > 0x80) e3 = 0;
	for (int nr = 0; nr < e1; nr++) {
	  drawRect(g, ag, i, nr, 4);
	}
	for (int nr = e1; nr < e1 + e2; nr++) {
	  drawAgentAmusement(g, ag, i, nr, 4);
	}
	for (int nr = e1 + e2; nr < e1 + e2 + e3; nr++) {
	  fillRect(g, ag, i, nr, 4);
	}
      }
    }

    if (messageType != 0) {
      showMessage(g);
    }

    // Paint the image
    gr.drawImage(image, 0, 0, Color.white, null);
  }

  private void showChatMessage(String line) {
    // Observe... Chat Messages looks like "serverTime,m,MESSAGE"
    int index = line.indexOf(',');
    if (index > 0) {
      String serverTime = line.substring(0, index).trim();
      index = line.indexOf(',', index + 1);
      if (index > 0 && (++index < line.length())) {
	line = line.substring(index);
      }
      try {
	long time = Long.parseLong(serverTime) * 1000;
	if (dateFormat == null) {
	  dateFormat = new SimpleDateFormat("d MMM HH:mm");
 	  dateFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
	  date = new Date(time);
	} else {
	  date.setTime(time);
	}
	line = dateFormat.format(date) + ' ' + line;
      } catch (Exception e) {
	System.err.println("could not parse chat time: " + e);
      }
    }
    // Only add line ends (\n) when needed. This saves one row in
    // the Applets limited text area.
    String text = chatArea.getText();
    if (text.length() > 0) {
      text = text + '\n' + line;
    } else {
      text = line;
    }
    // Remove the first line if the chat size is too large (avoids too
    // large chat area if people leaves the Applet running for long
    // times).
    if (numberOfChatMessages >= 25) {
      index = text.indexOf('\n');
      if (index > 0) {
	text = text.substring(index);
      }
    } else {
      numberOfChatMessages++;
    }
    chatArea.setText(text);
    // Make sure the last message is visible (TextField does not
    // automatically do this because we are using setText)
    chatArea.setCaretPosition(text.length());
  }

  void showMessage(Graphics g) {
    switch (messageType) {
    case INFO_MSG:
      {
	if (messageWidth < 0) {
	  FontMetrics metrics = g.getFontMetrics();
	  int m1 = metrics.stringWidth(messageTitle);
	  int m2 = metrics.stringWidth(messageText);
	  messageWidth = (m1 > m2 ? m1 : m2) + 20;
	  messageStart = (size.width - messageWidth) / 2;
	}
	drawWindow(g, messageStart, 100, messageWidth, 70);
	g.drawString(messageTitle, messageStart + 10, 114);
	g.drawString(messageText, messageStart + 10, 130);
      }
      break;
    case CLIENT_PREFS_MSG:
      int agent = showAgent;
      int nr = clientNo[agent];
      String agentName = agents[agent];
      FontMetrics metrics = g.getFontMetrics();
      drawWindow(g, 150, 100, 260, 60 + nr * 15);
      g.drawString("Client Preferences for " + agentName, 160, 114);

      g.drawString("Client", 160, 133);
      g.drawString("In - Out", 200, 133);
      g.drawString("Hotel", 245, 133);
      g.drawString("E1", 287, 133);
      g.drawString("E2", 317, 133);
      g.drawString("E3", 347, 133);
      for (int i = 0; i < nr; i++) {
	g.drawString(Integer.toString(i + 1), 170, 148 + i * 15);
	g.drawString("" + clientPrefs[agent][i][0]
		     + " - " + clientPrefs[agent][i][1],
		     204, 148 + i * 15);

	String hotel = Integer.toString(clientPrefs[agent][i][2]);
	int w = metrics.stringWidth(hotel);
	g.drawString(hotel, 270 - w, 148 + i * 15);
	for (int j = 0; j < 3; j++) {
	  String e = Integer.toString(clientPrefs[agent][i][3 + j]);
	  w = metrics.stringWidth(e);
	  g.drawString(e, 300 + j * 30 - w, 148 + i * 15);
	}

// 	g.drawString(Integer.toString(clientPrefs[agent][i][2]),
// 		     245, 148 + i * 15);
// 	for (int j = 0; j < 3; j++)
// 	  g.drawString(Integer.toString(clientPrefs[agent][i][3 + j]),
// 		       287 + j * 30, 148 + i * 15);
      }
      if (nr < 8) {
	g.drawString("waiting for data... ", 160, 148 + nr * 15);
      }
      break;
    }
  }

  void drawWindow(Graphics g, int x, int y, int w, int h) {
    g.setColor(blockColor);
    g.fillRect(x, y, w, h);
    g.setColor(Color.black);
    g.drawRect(x, y, w, h);
    g.drawRect(x, y, w + 1, h + 1);

//     g.drawString("<click to close>", x + 10, y + h - 4);

    if (clickCloseWidth < 0) {
      FontMetrics metrics = g.getFontMetrics();
      clickCloseWidth = metrics.stringWidth("<click to close>");
    }
    g.drawString("<click to close>", x + (w - clickCloseWidth) / 2, y + h - 4);
  }

  void drawRect(Graphics g, int agent, int day, int nr, int row) {
    int xadd = nr * 8;
    int yadd = row * 7;
    if (xadd > colWidth - 8) {
      yadd += 7;
      // shold have vars for this...
      xadd -= 8 * ((int) (colWidth / 8));
    }
    // Only draw if not too many...
    if (xadd <= (colWidth - 8)) {
      g.draw3DRect(122 + day * colWidth + xadd,
		   startY + yadd + 2 + agent * rowHeight,
		   5, 5, true);
    }
  }

  private void drawAgentAmusement(Graphics g, int agent,
			       int day, int nr, int row) {
    int xadd = nr * 8;
    int yadd = row * 7;
    if (xadd > colWidth - 8) {
      yadd += 7;
      // shold have vars for this...
      xadd -= 8 * ((int) (colWidth / 8));
    }
    if (xadd <= (colWidth - 8)) {
      drawAmusement(g, 122 + day * colWidth + xadd,
		    startY + yadd + 2 + agent * rowHeight,
		    6, 6);
    }
  }

  private void drawAmusement(Graphics g, int x, int y, int width, int height) {
//      g.draw3DRect(x, y, width, height, true);
    g.drawLine(x, y, x + width - 1, y + height - 1);
    g.drawLine(x, y + height - 1, x + width - 1, y);
  }

  void fillRect(Graphics g, int agent, int day, int nr, int row) {
    int xadd = nr * 8;
    int yadd = row * 7;
    if (xadd > colWidth - 8) {
      yadd += 7;
      // shold have vars for this...
      xadd -= 8 * ((int) (colWidth / 8));
    }
    if (xadd <= (colWidth - 8)) {
      g.fill3DRect(122 + day * colWidth + xadd,
		   startY + yadd + 2 + agent * rowHeight,
		   6, 6, true);
    }
  }

  public void update(Graphics g) {
    paint(g);
  }

  public void actionPerformed(ActionEvent ae) {
    Object source = ae.getSource();
    if (source == incButton) {
      int gid = getGameFromField();
      if (gid >= 0) {
	gameNo.setText(Integer.toString(gid + 1));
      }
    } else if (source == decButton) {
      int gid = getGameFromField();
      if (gid > 0) {
	gameNo.setText(Integer.toString(gid - 1));
      }
    } else if (source == showButton) {
      clearMessage();
      int showOldGame = getGameFromField();
      if (showOldGame >= 0) {
	// Do not show next game automatically
	setShowNextGame(false);
	if (showOldGame == gameID) {
	  // Already showing the specified game. Show dialog?? FIX THIS!!!
	  showInfoMessage("", "You are already looking at game " + gameID);
	} else {
	  connectOldGame(showOldGame);
	}
      }
    } else if (source == currButton) {
      clearMessage();
      if (gameEnded) {
	if (nextKnownGameStartTime > 0) {
	  long currentTime = System.currentTimeMillis() + timeDiff;
	  if (nextKnownGameStartTime > currentTime) {
	    StringBuffer sb = new StringBuffer();
	    sb.append("Next game will start at ");
	    appendTime(sb, nextKnownGameStartTime, true, true);
	    showInfoMessage("Will show next game when started", sb.toString());
	  }
	} else {
	  showInfoMessage("Will show next game when started",
			  "(No games scheduled yet)");
	}
      } else {
	if (gameID >= 0) {
	  gameNo.setText(Integer.toString(gameID));
	}
	showInfoMessage("", "You are already looking at current game");
      }
      setShowNextGame(true);
    } else if (source == chatButton || source == chatMsg) {
      if (out != null) {
	String message = chatMsg.getText().trim();
	if (message.length() > 0) {
	  long serverTime = (System.currentTimeMillis() + timeDiff) / 1000;
	  out.println(serverTime + ",m," + agentName + "> " + message);
	  out.flush();
	}
	chatMsg.setText("");
      } else {
	showInfoMessage("Not connected",
			"You must be connected to the server "
			+ "when sending chat messages");
      }
    } else if (source == clearButton) {
      chatArea.setText("");
    }
  }

  private int getGameFromField() {
    try {
      return Integer.parseInt(gameNo.getText().trim());
    } catch (Exception e) {
      showInfoMessage("", "Illegal game id, must be a number");
      gameNo.setText(gameID >= 0 ? Integer.toString(gameID) : "1");
      return -1;
    }
  }


  /*********************************************************************
   * MouseListener
   *********************************************************************/

  public void mouseClicked(MouseEvent me) {
    clearMessage();

    int mx = me.getX();
    int my = me.getY() - startY;
    if ((mx < 100) && (my >= 0)) {
      if (gameID < 0) {
	showInfoMessage("No Game", "Please select a game to show!");
      } else {
	// Check position!!
	int agent = my / rowHeight;
	if (agent < 8) {
	  if (agentIDs[agent] == Integer.MIN_VALUE) {
	    showInfoMessage("", "No agent in position " + (agent + 1));
	  } else {
	    if (clientNo[agent] == 0) {
	      // what should be done here??? FIX THIS!!!
// 	      requestClientData(gameID, agentIDs[agent]);
	    }
	    showClientPreferences(agent);
	  }
	}
      }
    }
  }

  public void mouseExited(MouseEvent me) {
  }

  public void mouseEntered(MouseEvent me) {
  }

  public void mousePressed(MouseEvent me) {
  }

  public void mouseReleased(MouseEvent me) {
  }

} // TACApplet
