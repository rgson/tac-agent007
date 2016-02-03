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
 * AdminPage
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 9 April, 2002
 * Updated : $Date: 2004/09/14 11:25:04 $
 *	     $Revision: 1.11 $
 */

package se.sics.tac.is;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.mortbay.http.HttpException;
import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import org.mortbay.util.ByteArrayISO8859Writer;
import se.sics.tac.util.TACFormatter;

public class AdminPage extends HttpPage {

  private static final String SEPARATOR = "<p><hr noshade color='#202080'>";

  private InfoServer infoServer;
  private String header;
  private String serverName;
  private String gameURLPath;

  public AdminPage(InfoServer is, String header, String gameURLPath) {
    this.infoServer = is;
    this.header = header;
    this.serverName = is.getServerName();
    this.gameURLPath = gameURLPath;
  }


  // -------------------------------------------------------------------
  // Page Generation and main "switch" because this object provides
  // several sub pages.
  // -------------------------------------------------------------------

  public void handle(String pathInContext, String pathParams,
		     HttpRequest request, HttpResponse response)
    throws HttpException, IOException
  {
    TACStore store = infoServer.getTACStore();
    String userName = request.getAuthUser();
    TACUser user = store.getUser(userName);
    if (user == null || user != store.getAdministrator()){
      return;
    }
    String baseURL = getBase(pathInContext);
    String name = getName(pathInContext);
    StringBuffer page = null;
    int error = 0;
    try {
      if ("admin".equals(name) || "".equals(name)) {
	page = generateAdmin(baseURL, request, user);
      } else if ("competition".equals(name)) {
	page = generateCompetition(baseURL, request);
      } else if ("games".equals(name)) {
	page = generateGames(baseURL, request);
      } else {
	error = 404;
      }
    } catch (Exception e) {
      Logger.global.log(Level.WARNING, "AdminPage: could not generate page "
			+ name, e);
    } finally {
      if (error > 0) {
	response.sendError(error);
	request.setHandled(true);
      } else if (page == null) {
	response.sendError(500);
	request.setHandled(true);
      } else {
	ByteArrayISO8859Writer writer = new ByteArrayISO8859Writer();
	writer.write(page.toString());
	response.setContentType(HttpFields.__TextHtml);
	response.setContentLength(writer.size());
	writer.writeTo(response.getOutputStream());
	response.commit();
	request.setHandled(true);
      }
    }
  }

  private String getBase(String url) {
    int start = url.indexOf('/', 1);
    if (start > 1) {
      return url.substring(0, start + 1);
    } else if (!url.endsWith("/")) {
      return url + '/';
    } else {
      return url;
    }
  }

  private String getName(String url) {
    int start = url.indexOf('/', 1);
    if (start > 1) {
      int end = url.indexOf('/', start + 1);
      return end > 0
	? url.substring(start + 1, end)
	: url.substring(start + 1);
    }
    return "admin";
  }

  // -------------------------------------------------------------------
  // Web page utilities
  // -------------------------------------------------------------------

  private StringBuffer pageStart(String baseURL, String title) {
    StringBuffer page = new StringBuffer();
    page.append("<html><head><title>TAC Classic - ")
      .append(title).append('@').append(serverName)
      .append("</title></head>\r\n"
	      + "<body>\r\n")
      .append(header)
      .append("<font face='arial' size='+2'>")
      .append(title).append(" at ").append(serverName)
      .append("</font><p>\r\n");
    return page;
  }

  private StringBuffer pageEnd(StringBuffer page) {
    return page.append("</body>\r\n</html>\r\n");
  }

  private String trim(String text) {
    return (text == null || (text = text.trim()).length() == 0)
      ? null
      : text;
  }


  /*********************************************************************
   * Main administration page
   *********************************************************************/

  private StringBuffer generateAdmin(String baseURL, HttpRequest req,
				     TACUser user) {
    StringBuffer page = pageStart(baseURL, "Administration");
    if (req.getParameter("generateScore") != null) {
      try {
	String id = trim(req.getParameter("gameID"));
	if (id == null) {
	  throw new IllegalArgumentException("no game id specified");
	}
	int gameID = Integer.parseInt(id);
	infoServer.getGameArchiver().generateScore(gameID);
	page.append(SEPARATOR + "<p>Score page generated for game ")
	  .append(gameID).append(SEPARATOR + "<p>\r\n");
      } catch (Exception e) {
	Logger.global.log(Level.WARNING,
			  "AdminPage: could not generate score page", e);
	page.append(SEPARATOR
		    + "<p><font color=red>could not generate score page: ")
	  .append(e).append("</font>" + SEPARATOR + "<p>\r\n");
      }

    } else if (req.getParameter("setautojoin") != null) {
      try {
	boolean join = trim(req.getParameter("autojoin")) != null;
	infoServer.setAutoJoin(join);
	page.append(SEPARATOR + "<p>Auto join values has been set."
		    + SEPARATOR + "<p>\r\n");
      } catch (Exception e) {
	Logger.global.log(Level.WARNING,
			  "AdminPage: could not set auto join", e);
	page.append(SEPARATOR + "<p><font color=red>"
		    + "could not set auto join values: ")
	  .append(e).append("</font>" + SEPARATOR + "<p>\r\n");
      }

    } else if (req.getParameter("setwebjoin") != null) {
      infoServer.setWebJoinActive(trim(req.getParameter("webjoin")) != null);
      page.append(SEPARATOR + "<p>Web join values has been set."
		  + SEPARATOR + "<p>\r\n");

    } else if (req.getParameter("reserve") != null) {
      String num = trim(req.getParameter("reserveLength"));
      String tim = trim(req.getParameter("reserveTime"));
      if (num != null && tim != null) {
	try {
	  int minutes = Integer.parseInt(num);
	  long startTime = GameScheduler.parseServerTimeDate(tim);
	  page.append(SEPARATOR + "<p>\r\n");
	  if (infoServer.reserveTime(startTime, minutes * 60000)) {
	    page.append("Requested reservation of ")
	      .append(minutes).append(" minutes starting at ")
	      .append(GameScheduler.formatServerTimeDate(startTime));
	  } else {
	    page.append("<font color=red>Could not request time reservation at this time.</font>");
	  }
	  page.append(SEPARATOR + "<p>\r\n");
	} catch (Exception e) {
	  Logger.global.log(Level.WARNING,
			    "AdminPage: could not reserve time", e);
	  page.append(SEPARATOR + "<p><font color=red>"
		      + "Could not reserve time: ")
	    .append(e).append("</font>" + SEPARATOR + "<p>\r\n");
	}
      }
    } else if (req.getParameter("setServerMessage") != null) {
      String message = req.getParameter("message");
      if (message != null && ((message = message.trim()).length() == 0)) {
	message = null;
      }
      infoServer.setServerMessage(message);
      if (message == null) {
	page.append(SEPARATOR + "<p>Server message removed");
      } else {
	page.append(SEPARATOR + "<p>Server message set to '")
	  .append(message).append('\'');
      }
      page.append(SEPARATOR + "<p>\r\n");

//     } else if (req.getParameter("scratchGame") != null) {
//       String id = trim(req.getParameter("scratchID"));
//       try {
// 	if (id == null) {
// 	  throw new IllegalArgumentException("no game id specified");
// 	}
// 	int gameID = Integer.parseInt(id);
// 	infoServer.scratchGame(gameID);
// 	page.append("<hr>Game ").append(gameID)
// 	  .append(" has been scratched! Do not forget to manually "
// 		  + "change the history pages to indicate this and also "
// 		  + "to remove the game log!!!")
// 	  .append("<hr><p>\r\n");
//       } catch (Exception e) {
// 	Logger.global.log(Level.WARNING,
// 			  "AdminPage: could not scratch game '" + id
// 			  + '\'', e);
// 	page.append("<hr><font color=red>could not scratch game '")
// 	  .append(id).append("': ")
// 	  .append(e).append("</font><hr><p>\r\n");
//       }
    }

    // Server time
    long serverTime = 0;
    String serverTimeAsString = null;
    String timeMessage = null;

    if (req.getParameter("timeToString") != null) {
      try {
	String t = trim(req.getParameter("serverTime"));
	if (t == null) {
	  timeMessage = "<font color=red>no time specified</font>";
	} else {
	  serverTime = Long.parseLong(t);
	  serverTimeAsString = InfoServer.getServerTimeAsString(serverTime);
	}
      } catch (Exception e) {
	timeMessage = "<font color=red>ERROR: " + e + "</font>";
      }
    }
    if (serverTime == 0) {
      serverTime = infoServer.getServerTimeMillis();
    }
    page.append("<font face=arial size='+1'>Server time</font><p>\r\n"
		+ "<form method=post>"
		+ "Server time: <input name=serverTime type=text value='")
      .append(serverTime).append("'> &nbsp; ");
    if (serverTimeAsString != null) {
      page.append(" => ").append(serverTimeAsString).append(" &nbsp; ");
    }
    page.append("<input type=submit name=timeToString value='Show as date'>");
    if (timeMessage != null) {
      page.append(" &nbsp; ").append(timeMessage);
    }
    page.append("</form><p>\r\n");

    // Agent information
    String agentName = null;
    String agentID = null;
    String agentMessage = null;
    if (req.getParameter("agentToID") != null) {
      try {
	agentName = trim(req.getParameter("agentName"));
	if (agentName == null) {
	  agentMessage = "<font color=red>no agent name specified</font>";
	} else {
	  TACStore store = infoServer.getTACStore();
	  TACUser agentUser = store.getUser(agentName);
	  if (agentUser == null) {
	    // Perhaps an agent id???
	    try {
	      int id = Integer.parseInt(agentName);
	      agentID = store.getUserName(id);
	    } catch (Exception e) {
	      agentMessage = "<font color=red>ERROR: could not find agent '"
		+ agentName + "'</font>";
	    }
	  } else {
	    agentID = Integer.toString(agentUser.getID());
	  }
	}
      } catch (Exception e) {
	agentMessage = "<font color=red>ERROR: " + e + "</font>";
      }
    }

    page.append("<font face=arial size='+1'>Agent Info</font><p>\r\n"
		+ "<form method=post>"
		+ "Agent Name: <input name=agentName type=text value='");
    if (agentName != null) {
      page.append(agentName);
    }
    page.append("'> &nbsp; ");
    if (agentID != null) {
      page.append(" => ").append(agentID).append(" &nbsp; ");
    }
    page.append("<input type=submit name=agentToID value='Show Agent ID'>");
    if (agentMessage != null) {
      page.append(" &nbsp; ").append(agentMessage);
    }
    page.append("</form><p>\r\n");

    // Score page generation
    page.append("<font face=arial size='+1'>Generate Score Page</font><p>\r\n"
		+ "<form method=post>"
		+ "Game ID: <input name=gameID type=text> \r\n"
		+ "<input type=submit name=generateScore "
		+ "value='Generate Score'>"
		+ "</form>\r\n");

//     page.append("<font face=arial size='+1'>Scratch Game</font><br><br>\r\n"
// 		+ "<form method=post>"
// 		+ "<font color=red>WARNING: MAKE SURE YOU KNOW WHAT YOU ARE "
// 		+ "DOING WHEN SCRATCHING GAMES!!!</font>"
// 		+ "<br>"
// 		+ "Game ID: <input name=scratchID type=text> "
// 		+ "<input type=submit name=scratchGame "
// 		+ "value='Scratch Game'>\r\n"
// 		+ "<br>"
// 		+ "<font color=red>WARNING: MAKE SURE YOU KNOW WHAT YOU ARE "
// 		+ "DOING WHEN SCRATCHING GAMES!!!</font>"
// 		+ "</form>\r\n");

      // Auto join feature
    page.append("<p><font face=arial size='+1'>Game Join Settings"
		+ "</font><p>\r\n"
	      + "<form method=post>"
	      + "Auto Join: <input name='autojoin' type='checkbox'");
    if (infoServer.isAutoJoinActive()) {
      page.append(" checked");
    }
    page.append("> &nbsp; <input type='submit' value='Set' "
		+ "name='setautojoin'>"
		+ "<br>Web Join: <input name='webjoin' type='checkbox'");
    if (infoServer.isWebJoinActive()) {
	page.append(" checked");
    }
    page.append("> &nbsp; <input type='submit' value='Set' name='setwebjoin'>"
		+ "</form>\r\n");

    // Game reservation
    page.append("<p><font face=arial size='+1'>Time reservation</font><p>\r\n"
		+ "<form method=post>"
		+ "Reserve <input type=text name=reserveLength> minutes "
		+ "starting at "
		+ "<input type=text name=reserveTime value='")
      .append(GameScheduler.formatServerTimeDate(serverTime))
      .append("'> &nbsp; <input type='submit' value='Reserve' name='reserve'>"
	      + "</form>\r\n");

    // Server message
    String message = infoServer.getServerMessage();
    page.append("<p><font face=arial size='+1'>Server Message</font><p>\r\n"
		+ "<form method=post>"
		+ "<textarea cols=30 rows=6 name=message wrap=soft "
		+ "style='width: 90%;'>");
    if (message != null) {
      page.append(message);
    }
    page.append("</textarea><br> <input type='submit' "
		+ "value='Set Server Message' "
		+ "name='setServerMessage'></form>\r\n");

    page.append(SEPARATOR + "<font size=-1><em>"
		+ "Please note that some of the values above are the "
		+ "last values known to the Java InfoServer and might not be "
		+ "the actual values used by the TAC server.</em></font>");
    return pageEnd(page);
  }



  // -------------------------------------------------------------------
  // Game manager page
  // -------------------------------------------------------------------

  private StringBuffer generateGames(String baseURL, HttpRequest req) {
    TACStore store = infoServer.getTACStore();
    TACGame[] games = store.getComingGames();
    StringBuffer page = pageStart(baseURL, "Game Manager");
    long currentTime = infoServer.getServerTimeMillis();
    Set params = req.getParameterNames();
    Iterator paramIterator = params.iterator();
    while (paramIterator.hasNext()) {
      String p = paramIterator.next().toString();
      if (p.startsWith("remove")) {
	p = p.substring(6);
	try {
	  int index = TACGame.indexOfUniqID(games, Integer.parseInt(p));
	  if (index >= 0) {
	    TACGame g = games[index];
	    infoServer.removeGame(g);
	    page.append("<b>Requested that game ")
	      .append(g.getGameID())
	      .append(" <em>(").append(g.getID())
	      .append(")</em> should be removed</b><p>\r\n");
	    p = null;
	  } else {
	    p += ": not found";
	  }

	} catch (Exception e) {
	  p += ": " + e;
	} finally {
	  if (p != null) {
	    page.append("<font color=red>Could not remove game ")
	      .append(p).append("</font><p>\r\n");
	  }
	}
	break;
      }
    }

    page.append("<p>Current server time is ")
      .append(InfoServer.getServerTimeAsString(currentTime))
      .append("<p><form method=post>\r\n"
	      + "<table border=1>\r\n"
	      + "<tr><th>Game</th><th>Time</th><th>Participants</th>"
	      + "<th>Status</th>"
	      + "<th>&nbsp;</th>\r\n");
    if (games == null) {
      page.append("<tr><td colspan=5><em>No games scheduled</em></td></tr>");
    } else {
      for (int i = 0, n = games.length; i < n; i++) {
	TACGame g = games[i];
	page.append("<tr><td>");
	if (g.isReservation()) {
	  page.append("reservation");
	} else {
	  if (g.hasGameID()) {
	    page.append(g.getGameID());
	  } else {
	    page.append('?');
	  }
	  page.append(" (<em>").append(g.getID()).append("</em>) <nobr>")
	    .append(g.getGameType()).append("</nobr>");
	}
	page.append("</td><td>")
	  .append(InfoServer.getServerTimeAsString(g.getStartTimeMillis()))
	  .append(" - ");
	TACFormatter.formatServerTime(page, g.getEndTimeMillis());
	page.append("</td><td>");
	for (int j = 0, m = g.getNumberOfParticipants(); j < m; j++) {
	  if (j > 0) {
	    page.append(", ");
	  }
	  page.append(store.getUserName(g.getParticipant(j)));
	}
	page.append("&nbsp;")
	  .append("</td><td>")
	  .append(g.getStartTimeMillis() <= currentTime
		  ? "Running" : "Coming")
	  .append("</td><td><input type=submit name='remove")
	  .append(g.getID()).append("' value=Remove></td></tr>\r\n");
      }
    }
    page.append("</table>\r\n"
		+ "</form>\r\n");
    return pageEnd(page);
  }



  // -------------------------------------------------------------------
  // Competition manager page
  // -------------------------------------------------------------------

  private StringBuffer generateCompetition(String baseURL, HttpRequest req) {
    TACStore store = infoServer.getTACStore();
    Competition[] competitions = store.getComingCompetitions();
    StringBuffer page = pageStart(baseURL, "Competition Manager");
    if (req.getParameter("setLastFinished") != null) {
      try {
	int last = Integer.parseInt(req.getParameter("lastFinished"));
	store.setInt(TACStore.LAST_FINISHED_COMPETITION, last);
	page.append("<b>After next restart (and if using a SQL database) only "
		    + " competitions newer than competition id ")
	  .append(last).append(" will be loaded</b><p>\r\n");
      } catch (Exception e) {
	page.append("<font color=red><b>could not parse competition id: ")
	  .append(e).append("</b></font><p>\r\n");
      }

    } else {
      Set params = req.getParameterNames();
      Iterator paramIterator = params.iterator();
      while (paramIterator.hasNext()) {
	String p = paramIterator.next().toString();
	if (p.startsWith("remove")) {
	  p = p.substring(6);
	  try {
	    int index = Competition.indexOf(competitions, Integer.parseInt(p));
	    if (index >= 0) {
	      Competition c = competitions[index];
	      infoServer.removeCompetition(c);
	      competitions = store.getComingCompetitions();
	      p = null;
	      page.append("<b>Competition ")
		.append(c.getDescription())
		.append(" has been removed</b><p>\r\n");
	    } else {
	      p += ": not found";
	    }

	  } catch (Exception e) {
	    p += ": " + e;
	  } finally {
	    if (p != null) {
	      page.append("<font color=red>Could not remove competition ")
		.append(p).append("</font><p>\r\n");
	    }
	  }
	  break;
	}
      }
    }

    // This is only possible when standalone. FIX THIS!!!
    String competitionPath = gameURLPath == null
      ? "/history/competition/"
      : gameURLPath + "competition/";
    page.append("<form method=post>\r\n"
		+ "<table border=1>\r\n"
		+ "<tr><th>ID</th><th>Name</th><th>Description</th>"
		+ "<th>Start Time</th>"
		+ "<th>End time</th><th>Games</th><th>&nbsp;</th>\r\n");
    if (competitions == null || competitions.length == 0) {
      page.append("<tr><td colspan=7><em>No competitions found</em>"
		  + "</td></tr>");

    } else {
      for (int i = 0, n = competitions.length; i < n; i++) {
	Competition c = competitions[i];
	page.append("<tr><td>");
	if (c.getParentCompetition() != null) {
	  page.append(c.getParentCompetitionID())
	    .append(" -&gt; ");
	}
	page.append("<a href='")
	  .append(competitionPath)
	  .append(c.getID())
	  .append("/'>")
	  .append(c.getID())
	  .append("</a></td><td>").append(c.getName())
	  .append("</td><td>").append(c.getDescription())
	  .append("</td><td>")
	  .append(InfoServer.getServerTimeAsString(c.getStartTime()))
	  .append("</td><td>")
	  .append(InfoServer.getServerTimeAsString(c.getEndTime()))
	  .append("</td><td>");
	if (c.hasGameID()) {
	  page.append(c.getStartGameID())
	    .append(" - ").append(c.getEndGameID());
	} else {
	  page.append("? - ?");
	}
	page.append(" (<em>").append(c.getStartGame())
	  .append(" - ").append(c.getEndGame())
	  .append("</em>)</td><td><input type=submit name='remove")
	  .append(c.getID()).append("' value=Remove></td></tr>\r\n");
      }
    }
    page.append("</table>\r\n"
		+ "<p>Do not load the competition with this id or older competitions (only from next server restart and if SQL database is used): "
		+ "<input type=text name=lastFinished value='")
      .append(store.getInt(TACStore.LAST_FINISHED_COMPETITION, 0))
      .append("'> <input type=submit name='setLastFinished' value='Set'>\r\n"
	      + "</form>\r\n");
    return pageEnd(page);
  }

} // AdminPage
