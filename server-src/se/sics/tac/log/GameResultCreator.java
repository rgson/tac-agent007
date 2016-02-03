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
 * GameResultCreator
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 15 May, 2002
 * Updated : $Date: 2004/06/09 15:06:08 $
 *	     $Revision: 1.3 $
 */

package se.sics.tac.log;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.botbox.html.HtmlWriter;
import se.sics.isl.util.ArgumentManager;
import se.sics.isl.util.ConfigManager;
import se.sics.tac.util.TACFormatter;

public class GameResultCreator implements TACGameLogListener {

  private static final Logger log =
    Logger.getLogger(GameResultCreator.class.getName());

  private static final String POSITIVE_PARTICIPANT_COLOR = "#0000c0";
  private static final String NEUTRAL_PARTICIPANT_COLOR = null;
  private static final String NEGATIVE_PARTICIPANT_COLOR = "#c00000";

  private static final boolean USE_COLORS = true;

  private static SimpleDateFormat dFormat = null;
  private static Date date = null;

  private boolean addToTable = false;
  private String gameTablePrefix = "gametable-";
  private int gamesPerPage = 20;

  public GameResultCreator() {
  }

  public void init(ConfigManager config) {
    addToTable = config.getPropertyAsBoolean("addToTable", false);
  }

  public void addOptions(ArgumentManager manager) {
    manager.addOption("addToTable", "add game results to game table files");
  }

  public void gameOpened(String path, TACGameInfo game) {
    // Minor optimization: the quotes are not needed here
    game.setProperty(TACGameInfo.IGNORE_QUOTES, "true");
    game.setProperty(TACGameInfo.IGNORE_BIDS, "true");
  }

  public void gameClosed(String path, TACGameInfo game) {
    File fp = new File(path);
    path = fp.getParent();
    if ((game != null) && (path != null)) {
      generatePages(path, game, addToTable);
    }
  }

  public void finishedGames() {
  }



  // -------------------------------------------------------------------
  //
  // -------------------------------------------------------------------

  public int getGamesPerPage() {
    return gamesPerPage;
  }

  public void setGamesPerPage(int number) {
    this.gamesPerPage = number;
  }

  // Will create destination directory when needed
  public boolean generate(String rootPath, TACGameInfo game,
			  boolean addToTable) {
    String path = rootPath.endsWith(File.separator)
      ? (rootPath + game.getGameID())
      : (rootPath + File.separatorChar + game.getGameID());
    return generatePages(path, game, addToTable);
  }

  private boolean generatePages(String path, TACGameInfo game,
				boolean addToTable) {
    if (path.endsWith(File.separator)) {
      path = path.substring(0, path.length() - 1);
    }
    log.finest("generating game " + game.getGameID() + " in path " + path);

    try {
      File pathFile = new File(path);
      if (!pathFile.exists() && !pathFile.mkdirs()) {
	log.severe("could not create directory '" + path + '\'');
	log.severe("could not generate result for game "
		   + game.getGameID());
	return false;
      }

      if (game.isFinished()) {
	for (int i = 0, n = game.getNumberOfAgents(); i < n; i++) {
	  generateAgentResult(path, game, i);
	}
      }

      generateResult(path, game);

      if (addToTable) {
	addToTable(pathFile, game);
      }
      return true;
    } catch (Exception e) {
      log.log(Level.SEVERE, "could not generate result for game "
	      + game.getGameID(), e);
      return false;
    }
  }

  private void generateAgentResult(String path, TACGameInfo game, int agent)
    throws IOException
  {
    String file = path + File.separatorChar + "agent"
      + game.getAgentID(agent) + ".html";
    HtmlWriter out = new HtmlWriter(new FileWriter(file));
    try {
      int gameID = game.getGameID();
      String agentName = game.getAgentName(agent);
      String title = "Game results for " + agentName + " in game "
	+ gameID;
      initWriter(out);
      out.pageStart(title);
      out.h3(title + " played at "
	     + formatServerTimeDate(game.getStartTime()));
      generateClientAllocation(out, game, agent);

      generateClientPrefs(out, game, agent);

      generateEndowments(out, game, agent);

      generateGoods(out, game, agent);

      generateTransactions(out, game, agent, false);
    } finally {
      out.close();
    }
  }


  private void generateResult(String path, TACGameInfo game)
    throws IOException
  {
    String file = path + File.separatorChar + "index.html";
    HtmlWriter out = new HtmlWriter(new FileWriter(file));
    try {
      int gameID = game.getGameID();
      String title = "Game results for game " + gameID;
      long startTime = game.getStartTime();
      long endTime = game.getEndTime();
      initWriter(out);
      out.pageStart(title);
      out.h3(title + " played at " + formatServerTimeDate(startTime));
      if (endTime <= startTime) {
	// Game was scratched
	out.h3("<font color=red>This game was scratched!</font>");

      } else if (!game.isFinished()) {
	out.h3("<font color=red>This game was never finished!</font>");
	int numAgents = game.getNumberOfAgents();
	if (numAgents == 0) {
	  out.text("<p>No agents had joined the game").newLine();
	} else {
	  out.text("<p>The following agents had joined the game: ")
	    .text(game.getAgentName(0));
	  for (int i = 1; i < numAgents; i++) {
	    out.text(", ").text(game.getAgentName(1));
	  }
	  out.p().text("Download game data <a href='applet.log.gz'>here</a>.");
	}

      } else {
	out.table("border=1")
	  .colgroup(1)
	  .colgroup(3, "align=right")
	  .colgroup(1)
	  .th("Agent").th("Utility").th("Cost").th("Score")
	  .th("Allocation");

	for (int i = 0, n = game.getNumberOfAgents(); i < n; i++) {
	  int index = game.getAgentPosition(i);
	  out.tr().td(game.isBuiltinAgent(index)
		      ? ("<em>" + game.getAgentName(index) + "</em>")
		      : game.getAgentName(index))
	    .td(Integer.toString(game.getAgentUtility(index)))
	    .td(toString(game.getAgentCost(index)
			 + game.getAgentPenalty(index)))
	    .td(toString(game.getAgentScore(index), true))
	    .td().text("<a href=\"agent")
	    .text(game.getAgentID(index))
	    .text(".html\">view allocation</a>");
	}
	out.tableEnd();
	out.text("Download game data <a href='applet.log.gz'>here</a>.");
	generateTransactions(out, game, -1, true);
      }

      String serverName = game.getServerName();
      String serverVersion = game.getServerVersion();
      if (serverName != null) {
	out.text("<hr><font size=-1 color=black><em>");
	if (serverVersion != null) {
	  out.text("Game played at TAC Classic Java server " + serverName
		   + " version " + serverVersion);
	} else {
	  out.text("Game played at TAC Classic Java server " + serverName);
	}
	out.text("</em></font>");

      } else if (serverVersion != null) {
	out.text("<hr><font size=-1 color=black><em>");
	out.text("Game played at TAC Classic Java server version "
		 + serverVersion);
	out.text("</em></font>");
      }

    } finally {
      out.close();
    }
  }

  private void addToTable(File pathFile, TACGameInfo game)
    throws IOException
  {
    int gameID = game.getGameID();
    File fp = new File(pathFile.getParent(),
		       gameTablePrefix
		       + (((gameID - 1) / gamesPerPage) + 1)
		       + ".html");
    PrintWriter out =
      new PrintWriter(new BufferedWriter(new FileWriter(fp, true)));
    log.finest("adding game " + gameID + " to " + fp.getName());
    try {
      long startTime = game.getStartTime();
      long endTime = game.getEndTime();
      int length = game.getGameLength();
      out.print("<tr><td><a href=\"" + gameID + "/\">" + gameID
		+ "</a></td><td>" + formatServerTimeDate(startTime)
		+ "</td><td>" + TACFormatter.formatServerTime(endTime)
// 		+ " (" + TACFormatter.formatDelayAsHtml(length) + ')'
		+ "</td><td>");
      if (endTime <= startTime) {
	// Game was scratched
	out.print("<font color=red>This game was scratched!</font>");

      } else if (!game.isFinished()) {
	out.print("<font color=red>This game was never finished!</font>");
// 	int numAgents = game.getNumberOfAgents();
// 	if (numAgents == 0) {
// 	  out.print(": No agents had joined the game");
// 	} else {
// 	  out.print(": Joined agents ");
// 	  out.print(game.getAgentName(0));
// 	  for (int i = 1; i < numAgents; i++) {
// 	    out.print(", "); out.print(game.getAgentName(i));
// 	  }
// 	}

      } else {
	String currentColor = null;
	boolean isEm = false;
	for (int i = 0, n = game.getNumberOfAgents(); i < n; i++) {
	  int index = game.getAgentPosition(i);
	  float score = game.getAgentScore(index);
	  String color;
	  if (!USE_COLORS) {
	    // Ignore this
	  } else if (score < 0f) {
	    color = NEGATIVE_PARTICIPANT_COLOR;
	  } else if (score > 0f) {
	    color = POSITIVE_PARTICIPANT_COLOR;
	  } else {
	    color = NEUTRAL_PARTICIPANT_COLOR;
	  }

	  if (i > 0) {
	    out.print(' ');
	  }
	  if (USE_COLORS && currentColor != color) {
	    if (isEm) {
	      out.print("</em>");
	      isEm = false;
	    }
	    currentColor = setHtmlColor(out, currentColor, color);
	  }
	  if (isEm != (game.getAgentID(index) < 0)) {
	    out.print(isEm ? "</em>" : "<em>");
	    isEm = !isEm;
	  }
	  out.print(game.getAgentName(index));
	}
	if (isEm) {
	  out.print("</em>");
	}
	if (USE_COLORS) {
	  setHtmlColor(out, currentColor, null);
	}

// 	for (int i = 0, n = game.getNumberOfAgents(); i < n; i++) {
// 	  out.print(game.getAgentName(game.getAgentPosition(i)));
// 	  out.print(" ");
// 	}

	out.print(" (<a href=\"" + gameID + "/applet.log.gz\">data</a>)");
      }
      out.println("</td></tr>");
    } finally {
      out.close();
    }
  }

  private String setHtmlColor(PrintWriter out, String currentColor,
			      String newColor) {
    if (!USE_COLORS) {
      return null;
    }
    if (currentColor == newColor) {
      return currentColor;
    }
    if (currentColor != null) {
      out.print("</font>");
    }
    if (newColor != null) {
      out.print("<font color='" + newColor + "'>");
    }
    return newColor;
  }

  private void generateClientAllocation(HtmlWriter out, TACGameInfo game,
					int agent) {
    int penalty = game.getAgentPenalty(agent);
    int totalUtility = 0;
    float totalCost = 0;
    float totalScore = 0;
    float agentCost = game.getAgentCost(agent);
    out.table("border=1")
      .colgroup(5)
      .colgroup(3, "align=right")
      .th("Client").th("Arrival").th("Departure")
      .th("Hotel").th("Entertainment").th("Utility")
      .th("Cost").th("Score");

    for (int client = 0; client < 8; client++) {
      int inflight = game.getAllocatedDay(agent, client, TACGameInfo.INFLIGHT);
      if (inflight > 0) {
	int outflight =
	  game.getAllocatedDay(agent, client, TACGameInfo.OUTFLIGHT);
	int e1 = game.getAllocatedDay(agent, client,
				      TACGameInfo.ALLIGATOR_WRESTLING);
	int e2 = game.getAllocatedDay(agent, client,
				      TACGameInfo.AMUSEMENT_PARK);
	int e3 = game.getAllocatedDay(agent, client, TACGameInfo.MUSEUM);
	int hotelType = game.hasGoodHotel(agent, client)
	  ? TACGameInfo.ITEM_GOOD_HOTEL
	  : TACGameInfo.ITEM_CHEAP_HOTEL;
	int utility = 1000
	  - Math.abs(inflight -
		     game.getClientPreferences(agent, client,
					       TACGameInfo.INFLIGHT)) * 100
	  - Math.abs(outflight -
		     game.getClientPreferences(agent, client,
					       TACGameInfo.OUTFLIGHT)) * 100;
	float cost =
	  game.getUnitCost(agent, TACGameInfo.ITEM_INFLIGHT, inflight - 1)
	  + game.getUnitCost(agent, TACGameInfo.ITEM_OUTFLIGHT, outflight - 1);
	if (hotelType == TACGameInfo.ITEM_GOOD_HOTEL) {
	  utility += game.getClientPreferences(agent, client,
					     TACGameInfo.HOTEL);
	}
	for (int i = inflight; i < outflight; i++) {
	  cost += game.getUnitCost(agent, hotelType, i - 1);
	}

	out.tr().td(Integer.toString(client + 1))
	  .td("Day " + inflight)
	  .td("Day " + outflight)
	  .td(game.getItemName(hotelType)).td();
	if (e1 > 0) {
	  utility +=
	    game.getClientPreferences(agent, client,
				      TACGameInfo.ALLIGATOR_WRESTLING);
	  cost += game.getUnitCost(agent, TACGameInfo.ITEM_ALLIGATOR_WRESTLING,
				   e1 - 1);
	  out.text("<div align=left>Day ")
	    .text(e1)
	    .text(' ')
	    .text(game.getItemName(TACGameInfo.ITEM_ALLIGATOR_WRESTLING))
	    .text("</div>").newLine();
	}
	if (e2 > 0) {
	  utility += game.getClientPreferences(agent, client,
					     TACGameInfo.AMUSEMENT_PARK);
	  cost += game.getUnitCost(agent, TACGameInfo.ITEM_AMUSEMENT_PARK,
				   e2 - 1);
	  out.text("<div align=left>Day ")
	    .text(e2)
	    .text(' ')
	    .text(game.getItemName(TACGameInfo.ITEM_AMUSEMENT_PARK))
	    .text("</div>").newLine();
	}
	if (e3 > 0) {
	  utility +=
	    game.getClientPreferences(agent, client, TACGameInfo.MUSEUM);
	  cost += game.getUnitCost(agent, TACGameInfo.ITEM_MUSEUM, e3 - 1);
	  out.text("<div align=left>Day ")
	    .text(e3)
	    .text(' ')
	    .text(game.getItemName(TACGameInfo.ITEM_MUSEUM))
	    .text("</div>").newLine();
	}
	if (e1 == 0 && e2 == 0 && e3 == 0) {
	  out.text("&nbsp;");
	}
	out.td(toString(utility))
	  .td(toString(cost))
	  .td(toString(utility - cost, true));
	totalUtility += utility;
	totalCost += cost;
	totalScore += (utility - cost);
      }
    }

    if (((agentCost - totalCost) > 0.01) ||
	((agentCost - totalCost) < -0.01)) {
      out.tr().td("Sum &nbsp;", "colspan=5 align=right")
	.td(Integer.toString(totalUtility))
	.td(toString(totalCost))
	.td(toString(totalScore, true))
	.tr().td("Other costs (unused goods, transaction losses, etc) &nbsp;",
		 "colspan=5 align=right")
	.td("&nbsp;")
	.td(toString(agentCost - totalCost))
	.td("&nbsp;");
    }
    if (penalty > 0) {
      out.tr().td("<font color=red>Oversell Penalty &nbsp;</font>",
		  "colspan=5 align=right")
	.td("&nbsp;")
	.td("<font color=red>" + penalty + "</font>")
	.td("&nbsp;");
    }

    out.tr().td("Total &nbsp;", "colspan=5 align=right");
    out.td(Integer.toString(game.getAgentUtility(agent)))
      .td(toString(agentCost + penalty))
      .td(toString(game.getAgentScore(agent), true));
    out.tableEnd();
  }

  private void generateClientPrefs(HtmlWriter out, TACGameInfo game,
				   int agent) {
    out.h3("Client Preferences");
    out.table("border=1")
      .colgroup(3)
      .colgroup(4, "align=right")
      .th("Client").th("Arrival").th("Departure")
      .th("Hotel").th("AlligatorWrestling")
      .th("AmusementPark").th("Museum");
    for (int i = 0, n = 8; i < n; i++) {
      out.tr().td(Integer.toString(i + 1))
	.td("Day "
	    + game.getClientPreferences(agent, i, TACGameInfo.INFLIGHT))
	.td("Day "
	    + game.getClientPreferences(agent, i, TACGameInfo.OUTFLIGHT))
	.td(Integer.toString(game.getClientPreferences(agent, i,
						       TACGameInfo.HOTEL)))
	.td(Integer.toString(game.getClientPreferences(agent, i,
						       TACGameInfo.ALLIGATOR_WRESTLING)))
	.td(Integer.toString(game.getClientPreferences(agent, i,
						       TACGameInfo.AMUSEMENT_PARK)))
	.td(Integer.toString(game.getClientPreferences(agent, i,
						       TACGameInfo.MUSEUM)));
    }
    out.tableEnd();
  }

  private void generateEndowments(HtmlWriter out, TACGameInfo game,
				  int agent) {
    out.h3("Endowments");
    out.table("border=1")
      .th("Entertainment").th("Day 1").th("Day 2")
      .th("Day 3").th("Day 4");
    for (int i = TACGameInfo.ITEM_ALLIGATOR_WRESTLING;
	 i <= TACGameInfo.ITEM_MUSEUM; i++) {
      out.tr();
      out.td(game.getItemName(i));
      for (int day = 0; day < 4; day++) {
	out.td(Integer.toString(game.getEndowments(agent, i, day)));
      }
    }
    out.tableEnd();
  }

  private void generateGoods(HtmlWriter out, TACGameInfo game, int agent) {
    Transaction[] transactions = game.getTransactions();
    double totalCost = 0.0;
    boolean hasUnusedGoods = false;
    out.h3("Owned Goods");
    out.table("border=1")
      .colgroup(6)
      .colgroup(1, "align=right")
      .th("Goods")
      .th("Day 1").th("Day 2")
      .th("Day 3").th("Day 4").th("Day 5")
      .th("Cost");
    for (int i = 0; i <= TACGameInfo.ITEM_MUSEUM; i++) {
      float cost = 0f;
      out.tr().td(game.getItemName(i));
      for (int day = 0; day < 5; day++) {
	int own = game.getOwn(agent, i, day);
	int used = game.getUsed(agent, i, day);
	out.td(Integer.toString(own));
	// Only output used if the agent did not oversell
	if ((own >= 0) && ((own - used) != 0)) {
	  out.text(" <font color=red>(")
	    .text(own - used)
	    .text(")</font>");
	  hasUnusedGoods = true;
	}
      }

      // Calculate goods cost for each type
      if (transactions != null) {
	for (int j = 0, m = transactions.length; j < m; j++) {
	  Transaction trans = transactions[j];
	  if (game.getAuctionType(trans.getAuction()) == i) {
	    // Transaction of the correct type
	    if (trans.getBuyer() == agent) {
	      // Agent is buyer
	      cost += trans.getPrice() * trans.getQuantity();
	    } else if (trans.getSeller() == agent) {
	      // Agent is seller
	      cost -= trans.getPrice() * trans.getQuantity();
	    }
	  }
	}
      }
      out.td(toString(cost));
      totalCost += cost;
    }
    out.tr().td("Total &nbsp;", "colspan=6 align=right")
      .td(toString(totalCost))
      .tableEnd();
    if (hasUnusedGoods) {
      out.text("<i>(Unused goods is specified in parentheses)</i>").newLine();
    }
  }

  private void generateTransactions(HtmlWriter out, TACGameInfo game,
				    int agent, boolean allAgents) {
    Transaction[] transactions = game.getTransactions();
    out.h3("Transactions");
    out.table("border=1")
      .colgroup(5)
      .colgroup(2, "align=right")
      .th("Time").th("Auction").th("Day")
      .th("Buyer").th("Seller").th("Quantity")
      .th("Price");

    if (transactions != null) {
      Transaction trans;
      for (int i = 0, n = transactions.length; i < n; i++) {
	trans = transactions[i];
	if (allAgents || trans.isParticipant(agent)) {
	  int auction = trans.getAuction();
	  int day = game.getAuctionDay(auction);
	  int type = game.getAuctionType(auction);
	  String buyer = game.getAgentName(trans.getBuyer());
	  String seller = game.getAgentName(trans.getSeller());
	  out.tr().td(TACFormatter.formatServerTime(trans.getTime()))
	    .td(game.getItemName(type))
	    .td(Integer.toString(day))
	    .td(buyer)
	    .td(seller)
	    .td(Integer.toString(trans.getQuantity()))
	    .td(toString(trans.getPrice()));
	}
      }
    }
    out.tableEnd();
  }


  // Place to initialize the writer with another style
  private void initWriter(HtmlWriter out) {
  }

  private String toString(int i) {
    return Integer.toString(i);
  }

  private String toString(double v) {
    return TACFormatter.toString(v);
  }

  private String toString(double f, boolean color) {
    if (f < 0) {
      return "<font color=red>" + toString(f) + "</font>";
    } else {
      return toString(f);
    }
  }

  public static synchronized String formatServerTimeDate(long time) {
    if (dFormat == null) {
      dFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      dFormat.setTimeZone(new java.util.SimpleTimeZone(0, "UTC"));
      date = new Date(0L);
    }
    date.setTime(time);
    return dFormat.format(date);
  }

} // GameResultCreator
