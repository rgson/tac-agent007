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
 * GameScheduler
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 8 April, 2002
 * Updated : $Date: 2004/07/11 21:37:53 $
 *	     $Revision: 1.19 $
 */

package se.sics.tac.is;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.botbox.util.ArrayUtils;
import org.mortbay.http.HttpException;
import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import org.mortbay.util.ByteArrayISO8859Writer;

public class GameScheduler extends HttpPage {

  private static final Logger log =
    Logger.getLogger(GameScheduler.class.getName());

  private static final boolean PARENT_COMPETITION = true;

  private static SimpleDateFormat dateFormat = null;
  private InfoServer infoServer;
  private String header;

  public GameScheduler(InfoServer is, String header) {
    this.infoServer = is;
    this.header = header;
  }

  public void handle(String pathInContext, String pathParams,
		     HttpRequest req, HttpResponse response)
    throws HttpException, IOException
  {
    String userName = req.getAuthUser();
    TACStore store = infoServer.getTACStore();
    TACUser user = store.getUser(userName);
    if (user == null || user != store.getAdministrator()) {
      return;
    }

    TACUser[] users = store.getUsers();
    int userNumber = users == null ? 0 : users.length;
    StringBuffer page = new StringBuffer();
    page.append("<html><head><title>TAC Classic - Competition Scheduler"
		+ "</title></head>"
		+ "<body>\r\n")
      .append(header)
      .append("<font face='arial' size='+2'>Competition Scheduler"
	      + "</font><p>\r\n");
    if (req.getParameter("submit") != null) {
      int nrUsers = 0;
      int[] agentIDs = new int[] { -1 };
      String[] agentNames = new String[] { "dummy" };
      StringBuffer messageBuffer = null;

      try {
	Competition parentCompetition = null;
	String parentIDStr =
	  PARENT_COMPETITION ? trim(req.getParameter("parent")) : null;
	if (PARENT_COMPETITION && parentIDStr != null) {
	  int parentID = Integer.parseInt(parentIDStr);
	  parentCompetition = store.getCompetition(parentID);
	  if (parentCompetition == null || parentID <= 0) {
	    throw new IllegalArgumentException("could not find parent "
					       + "competition "
					       + parentIDStr);
	  }

	  page.append("<p>\r\n"
		      + "<font face='arial' size='+1'>Agents in parent "
		      + "competition ")
	    .append(parentCompetition.getName())
	    .append("</font><p>\r\n");

	  TACUser[] pUsers = parentCompetition.getParticipants();
	  if (pUsers != null) {
	    for (int i = 0, n = pUsers.length; i < n; i++) {
	      if (i > 0) page.append(", ");
	      page.append(pUsers[i].getName());
	    }
	  }
	}

	page.append("<p>\r\n"
		    + "<font face='arial' size='+1'>Agents in "
		    + "competition</font>"
		    + "<p>\r\n");

	for (int i = 0; i < userNumber; i++) {
	  if (req.getParameter("join-" + users[i].getID()) != null) {
	    if (nrUsers > 0) page.append(", ");
	    page.append(users[i].getName());
	    nrUsers++;
	    agentIDs = ArrayUtils.add(agentIDs, users[i].getID());
	    agentNames = (String[])
	      ArrayUtils.add(agentNames, users[i].getName());
	  }
	}

	String addIDs = req.getParameter("agents");
	if (addIDs != null && addIDs.length() > 0) {
	  StringTokenizer stok = new StringTokenizer(addIDs, ", \r\n\t");
	  while (stok.hasMoreTokens()) {
	    String token = stok.nextToken();
	    TACUser usr = store.getUser(token);
	    if (usr != null) {
	      if (nrUsers > 0) page.append(", ");
	      page.append(usr.getName());
	      nrUsers++;
	      agentIDs = ArrayUtils.add(agentIDs, usr.getID());
	      agentNames = (String[])
		ArrayUtils.add(agentNames, usr.getName());
	    } else if (messageBuffer == null) {
	      messageBuffer = new StringBuffer().append(token);
	    } else {
	      messageBuffer.append(", ").append(token);
	    }
	  }
	}

	if (messageBuffer != null) {
	  page.append("<p>\r\n"
		      + "<font face='arial' size='+1'>"
		      + "Agents that could not be found</font>"
		      + "<p>\r\n<font color=red>")
	    .append(messageBuffer).append("</font>\r\n");
	}

	page.append("<p>\r\n");
	if (nrUsers == 0) {
	  throw new IllegalArgumentException("No agents in competition");
	}

	String totalGamesStr = req.getParameter("games");
	if (totalGamesStr == null || totalGamesStr.length() == 0) {
	  throw new
	    IllegalArgumentException("Total number of games not specified");
	}
	int totalGames = Integer.parseInt(totalGamesStr);
	long time = parseServerTimeDate(req.getParameter("time"));
	int gameLength = Integer.parseInt(req.getParameter("gameLength"));
	int gameLengthMillis = gameLength * 60000;
	if (gameLength < 3 || gameLength > 12) {
	  throw new IllegalArgumentException("game length must be between 3 and 12 minutes");
	}

	float weight = Float.parseFloat(req.getParameter("weight"));
	boolean useWeight = req.getParameter("useWeight") != null;
	boolean staticWeight = req.getParameter("staticWeight") != null;
	boolean startWeightDuringWeekends =
	  req.getParameter("weekend") != null;
	boolean lowestScoreAsZero =
	  req.getParameter("lowestscore") != null;
	String name = trim(req.getParameter("name"));
	if (name == null || name.length() == 0) {
	  throw new IllegalArgumentException("No competition name");
	}
	String descr = trim(req.getParameter("description"));
// 	int reserveTime = Integer.parseInt(req.getParameter("reserveTime"));
// 	int nextRes = Integer.parseInt(req.getParameter("nextres"));
	boolean totalAgent = "agent".equals(req.getParameter("type"));

	String scoreGenerator = trim(req.getParameter("scoregen"));
	if (scoreGenerator != null) {
	  // Test if score generator is valid
	  try {
	    ScoreGenerator generator = (ScoreGenerator)
	      Class.forName(scoreGenerator).newInstance();
	  } catch (ThreadDeath e) {
	    throw e;
	  } catch (Throwable e) {
	    throw (IllegalArgumentException)
	      new IllegalArgumentException("could not create score generator "
					   + "of type '"
					   + scoreGenerator + '\'')
	      .initCause(e);
	  }
	}

	int[][] games = scheduleGames(nrUsers);
	int minGames = games.length;
	int perAgent = gamesPerAgent(nrUsers);

	int rounds;
	if (totalAgent) {
	  rounds = totalGames / perAgent;
	} else {
	  rounds = totalGames / minGames;
	}

	page.append("<font face='arial' size='+1'>Competition Data</font>"
		    + "<p>\r\n<table border='0'>"
		    + "<tr><td>Competition name:</td><td>")
	  .append(name)
	  .append("</td></tr\r\n>"
		  + "<tr><td>Competition description:</td><td>")
	  .append(descr == null ? "&nbsp;" : descr);
	if (PARENT_COMPETITION && parentCompetition != null) {
	  page.append("</td></tr\r\n>"
		      + "<tr><td>Continuation of competition:</td><td>")
	    .append(parentCompetition.getName()).append(" (")
	    .append(parentCompetition.getDescription()).append(')');
	}
	if (scoreGenerator != null) {
	  page.append("</td></tr\r\n>"
		      + "<tr><td>Competition score table generator</td><td>")
	    .append(scoreGenerator);
	}
	page.append("</td></tr\r\n>"
		    + "<tr><td>Total number of players:</td><td>")
	  .append(nrUsers)
	  .append("</td></tr\r\n>"
		  + "<tr><td>Requested number of games:</td><td>")
	  .append(totalGames);
	if (totalAgent) {
	  page.append(" per agent");
	}
	page.append("</td></tr\r\n>"
		    + "<tr><td>Number of games scheduled:</td><td>")
	  .append(rounds * minGames)
	  .append("</td></tr\r\n>"
		    + "<tr><td>Number of games per round:</td><td>")
	  .append(minGames)
	  .append("</td></tr>"
		  + "<tr><td>Number of rounds:</td><td>" + rounds
		  + "</td></tr\r\n>"
		  + "<tr><td>Number of games per agent/round:</td><td>"
		  + perAgent
		  + "</td></tr\r\n>"
		  + "<tr><td>Number of games per agent:</td><td>")
	  .append(perAgent*rounds)
	  .append("</td></tr\r\n>"
		  + "<tr><td>Game Length:</td><td>")
	  .append(gameLength)
	  .append(" min</td></tr\r\n>"
		  + "<tr><td>Time between games:</td><td>")
	  .append(InfoServer.DELAY_BETWEEN_GAMES / 60000)
	  .append(" min</td></tr\r\n>"
		  + "<tr><td>Game Type:</td><td>")
	  .append(getGameType(gameLength))
	  .append("</td></tr\r\n>"
		  + "<tr><td>Start Time:</td><td>")
	  .append(formatServerTimeDate(time))
	  .append("</td></tr\r\n>"
		  + "<tr><td>End Time:</td><td>")
	  .append(formatServerTimeDate(time + (rounds * minGames)
				       * (gameLengthMillis
					  + InfoServer.DELAY_BETWEEN_GAMES)))
	  .append("</td></tr\r\n>"
		  + "<tr><td>Start Weight:</td><td>");
	if (useWeight) {
	  page.append(weight);
	  if (staticWeight) {
	    page.append(" (use only start weight)");
	  } else if (startWeightDuringWeekends) {
	    page.append(" (use start weight during weekends)");
	  }
	} else {
	  page.append("(does not use weighted scores)");
	}
	if (lowestScoreAsZero) {
	  page.append("</td></tr\r\n>"
		      + "<tr><td>Score for zero games</td>"
		      + "<td>Use lowest score if smaller than zero");
	}
	page.append("</td></tr\r\n>");
// 	page.append("<tr><td>Time / round reserved for admin:</td><td>")
// 	  .append(reserveTime)
// 	  .append("</td></tr\r\n>"
// 		  + "<tr><td>Minimum played games between admin time:"
// 		  + "</td><td>")
// 	  .append(nextRes)
// 	  .append("</td></tr\r\n>");
	page.append("</table>\r\n<p>\r\n"
		  + "<font face='arial' size='+1'>Example round</font><p>\r\n"
		  + "<table border=1><tr><th>Game</th><th>Agents</th></tr>");
	for (int i = 0; i < minGames; i++) {
	  page.append("<tr><td>").append(i + 1).append("</td><td>");
	  for (int a = 0; a < 8; a++) {
	    page.append(agentNames[games[i][a]]).append(' ');
	  }
	  page.append("</td></tr>");
	}
	page.append("</table>\r\n<p>\r\n"
		    + "<form method=post>"
		    + "<input type=hidden name=agentNo value=")
	  .append(nrUsers)
	  .append("><input type=hidden name=rounds value=")
	  .append(rounds)
	  .append('>');
	for (int i = 0; i < nrUsers; i++) {
	  page.append("<input type=hidden name=agent").append(i)
	    .append(" value=").append(agentIDs[i + 1]).append('>');
	}
	page.append("<input type=hidden name=time value='")
	  .append(time)
	  .append("'><input type=hidden name=gameLength value='")
	  .append(gameLength)
	  .append("'><input type=hidden name=weight value='")
	  .append(weight)
	  .append("'>");
	if (useWeight) {
	  page.append("<input type=hidden name=useWeight value='true'>");
	  if (staticWeight) {
	    page.append("<input type=hidden name=staticWeight value='true'>");
	  }
	  if (startWeightDuringWeekends) {
	    page.append("<input type=hidden name=weekend value='true'>");
	  }
	}
	if (lowestScoreAsZero) {
	  page.append("<input type=hidden name=lowestscore value='true'>");
	}
// 	page.append("<input type=hidden name=reserveTime value="
// 		    + reserveTime + '>');
// 	page.append("<input type=hidden name=nextres value=" + nextRes + '>');
	page.append("<input type=hidden name=name value='")
	  .append(name).append("'>");
	if (descr != null) {
	  page.append("<input type=hidden name=description value='")
	    .append(descr).append("'>");
	}
	if (PARENT_COMPETITION && parentCompetition != null) {
	  page.append("<input type=hidden name=parent value='")
	    .append(parentCompetition.getID()).append("'>");
	}
	if (scoreGenerator != null) {
	  page.append("<input type=hidden name=scoregen value='")
	    .append(scoreGenerator).append("'>");
	}
	page.append("\r\n<input type=submit name=execute "
		  + "value='Create Competition'> &nbsp; "
		  + "<input type=submit name=cancel "
		  + "value='Cancel'>"
		  + "</form>\r\n");
      } catch (Exception e) {
	log.log(Level.WARNING, "could not schedule games", e);
	page.append("Could not schedule games: <font color=red>")
	  .append(e)
	  .append("</font><p>Try to go back and enter correct information!");
      }
    } else if (req.getParameter("execute") != null) {
      // Create the games!!!
      try {
	int nrUsers = Integer.parseInt(req.getParameter("agentNo"));
	int rounds = Integer.parseInt(req.getParameter("rounds"));
	long startTime = Long.parseLong(req.getParameter("time"));
	int gameLength = Integer.parseInt(req.getParameter("gameLength"));
	int gameLengthMillis = gameLength * 60000;
	float weight = Float.parseFloat(req.getParameter("weight"));
	boolean useWeight =
	  req.getParameter("useWeight") != null;
	boolean staticWeight = req.getParameter("staticWeight") != null;
	boolean startWeightDuringWeekends =
	  req.getParameter("weekend") != null;
	boolean lowestScoreAsZero =
	  req.getParameter("lowestscore") != null;

	String name = req.getParameter("name");
	String desc = trim(req.getParameter("description"));
	Competition parentCompetition = null;
	String parentIDStr =
	  PARENT_COMPETITION ? trim(req.getParameter("parent")) : null;
	if (PARENT_COMPETITION && parentIDStr != null) {
	  int parentID = Integer.parseInt(parentIDStr);
	  parentCompetition = store.getCompetition(parentID);
	  if (parentCompetition == null || parentID <= 0) {
	    throw new IllegalArgumentException("could not find parent "
					       + "competition " + parentIDStr);
	  }
	}
// 	int reserveTime = Integer.parseInt(req.getParameter("reserveTime"));
// 	int reserveTimeMillis = reserveTime * 60000;
// 	int nextRes = Integer.parseInt(req.getParameter("nextres"));
// 	long nextReserve = startTime
// 	  + (gameLengthMillis + InfoServer.DELAY_BETWEEN_GAMES) * nextRes;
	String scoreGenerator = trim(req.getParameter("scoregen"));

	TACUser[] participants = new TACUser[nrUsers];
	for (int i = 0; i < nrUsers; i++) {
	  int userID = Integer.parseInt(req.getParameter("agent" + i));
	  TACUser usr = store.getUser(userID);
	  if ((usr == null) || (usr.getID() != userID)) {
	    throw new IllegalStateException("user " + userID + " not found");
	  }
	  participants[i] = new TACUser(usr);
	}

	int[] idMap = new int[nrUsers + 1];
	idMap[0] = -1;
	for (int i = 0; i < nrUsers; i++) {
	  idMap[i + 1] = participants[i].getID();
	}

	long currentTime = infoServer.getServerTimeMillis();
	if ((startTime - currentTime) < 60000) {
	  throw new IllegalArgumentException("competition start time too soon (must be at least one minute into the future)");
	}

	// Creating Games...
	TACGame[] scheduledGames = null;
	String gameType = getGameType(gameLength);
	for (int i = 0; i < rounds; i++) {
	  int[][] games = scheduleGames(nrUsers);
	  for (int g = 0, n = games.length; g < n; g++) {
	    TACGame game = new TACGame(-1, -1, gameType, startTime,
				       gameLengthMillis, 8);
	    int dummy = -1;
	    startTime += gameLengthMillis + InfoServer.DELAY_BETWEEN_GAMES;
	    for (int a = 0; a < 8; a++) {
	      // The dummy has position zero!
	      if (games[g][a] == 0) {
		game.joinGame(dummy--);
	      } else {
		game.joinGame(idMap[games[g][a]]);
	      }
	    }
	    scheduledGames = (TACGame[])
	      ArrayUtils.add(TACGame.class, scheduledGames, game);
	  }
	  // After each "round" there can be downtime scheduled...
// 	  if (reserveTime > 0 && (nextReserve < startTime)) {
// 	    game = new TACGame(-1, -1, null, startTime, reserveTimeMillis, 0);
// 	    startTime += reserveTimeMillis + InfoServer.DELAY_BETWEEN_GAMES;
// 	    scheduledGames = (TACGame[])
// 	      ArrayUtils.add(TACGame.class, scheduledGames, game);
// 	    nextReserve = startTime
// 	      + (gameLengthMillis + InfoServer.DELAY_BETWEEN_GAMES) * nextRes;
// 	  }
	}

	if (scheduledGames == null) {
	  page.append("No games created");
	} else {
	  int id = store.getInt("lastCompetitionID", 0) + 1;
	  store.setInt("lastCompetitionID", id);
	  // Use the name as description if no description been specified
	  if (desc == null) {
	    desc = name;
	  }
	  Competition comp =
	    new Competition(id, scheduledGames.length, participants,
			    name, desc, weight, scoreGenerator);
	  if (PARENT_COMPETITION && parentCompetition != null) {
	    comp.setParentCompetitionID(parentCompetition.getID());
	    comp.setParentCompetition(parentCompetition);
	  }
	  if (useWeight) {
	    if (staticWeight) {
	      comp.setFlags(comp.getFlags() | Competition.STATIC_WEIGHT);
	    }
	    if (startWeightDuringWeekends) {
	      comp.setFlags(comp.getFlags() | Competition.WEEKEND_LOW);
	    }
	  } else {
	    comp.setFlags(comp.getFlags() | Competition.NO_WEIGHT);
	  }
	  if (lowestScoreAsZero) {
	    comp.setFlags(comp.getFlags() | Competition.LOWEST_SCORE_FOR_ZERO);
	  }
	  infoServer.scheduleGames(scheduledGames, comp);
	  page.append("Scheduled ").append(scheduledGames.length)
	    .append(" games in competition ").append(desc)
	    .append(".<p>");
	}
      } catch (Exception e) {
	log.log(Level.WARNING, "could not create games", e);
	page.append("Games could not be created: <font color=red>")
	  .append(e).append("</font>");
      }
    } else {
      // No submission: simply web page access

      Competition[] comps = store.getComingCompetitions();
      if (comps != null) {
	Competition currentComp = store.getCurrentCompetition();
	page.append("<table border=1 width='100%'>"
		    + "<tr><th colspan=6>Existing Competitions");
	if (currentComp != null) {
	  page.append(" (now running: ").append(currentComp.getName())
	    .append(')');
	}
	page.append("</th></tr>"
		    + "<tr><th>ID</th>"
		    + "<th>Name</th>"
		    + "<th>Description</th>"
		    + "<th>Start Time</th><th>End Time</th>"
		    + "<th>Games</th></tr>");
	for (int i = 0, n = comps.length; i < n; i++) {
	  Competition comp = comps[i];
	  page.append("<tr><td>");
	  if (PARENT_COMPETITION && comp.hasParentCompetition()) {
	    page.append(comp.getParentCompetitionID())
	      .append(" -&gt; ");
	  }
	  page.append(comp.getID())
	    .append("</td><td>").append(comp.getName()).append("</td><td>")
	    .append(comp.getDescription()).append("</td><td>")
	    .append(formatServerTimeDate(comp.getStartTime()))
	    .append("</td><td>")
	    .append(formatServerTimeDate(comp.getEndTime()))
	    .append("</td><td>");
	  if (comp.hasGameID()) {
	    page.append(comp.getStartGameID())
	      .append(" - ").append(comp.getEndGameID());
	  } else {
	    page.append("? - ?");
	  }
	  page.append(" (<em>").append(comp.getStartGame())
	    .append(" - ").append(comp.getEndGame())
	    .append("</em>)</td></tr>");
	}
	page.append("</table><p>\r\n");
      }

      page.append("<p><font face='arial' size='+1'>"
		  + "Create new competition:</font>\r\n"
		  + "<form method=post>\r\n"
		  + "<table border='0'>\r\n"
		  + "<tr><td>Name of competition (unique)</td>"
		  + "<td><input type=text name=name size=32></td></tr>\r\n"
		  + "<tr><td>Description of competition</td>"
		  + "<td><input type=text name=description size=32></td>"
		  + "</tr>");
      if (PARENT_COMPETITION) {
	page.append("<tr><td>Continuation of competition</td>"
		    + "<td><input type=text name=parent size=32></td>"
		    + "</tr>\r\n");
      }
      page.append("<tr><td><select name=type>"
		  + "<option value=total>Total number of games (int)"
		  + "<option value=agent>Number of games per agent (int)"
		  + "</select>"
		  + "</td><td><input type=text name=games size=32></td></tr>"
		  + "<tr><td>Start Time (YYYY-MM-DD HH:mm)</td>"
		  + "<td><input type=text name=time size=32 value='")
	.append(formatServerTimeDate(infoServer.getServerTimeMillis()))
	.append("'></td></tr>"
		+ "<tr><td>Game Length (minutes)</td>"
		+ "<td><input type=text name=gameLength value='9' size=32></td></tr\r\n>"
		+ "<tr><td>Start Weight (float)</td>"
		+ "<td><input type=text name=weight value='1.0' size=32></td></tr\r\n>"
		+ "<tr><td>&nbsp;</td><td><input type=checkbox name=weekend> "
		+ "Use start weight during weekends</td></tr>"
		+ "<tr><td>&nbsp;</td>"
		+ "<td><input type=checkbox name=staticWeight> "
		+ "Only use start weight (no increasing weights)</td></tr>"
		+ "<tr><td>&nbsp;</td><td><input type=checkbox name=useWeight> "
		+ "Use weighted scores</td></tr>"

		// Lowest score or zero for zero games (agent missing games)
		+ "<tr><td>Score for zero games</td>"
		+ "<td><input type=checkbox name=lowestscore> "
		+ "Use lowest score if smaller than zero</td></tr>"

// 		+ "<tr><td>Time to reserve for admin per round (minutes)</td>"
// 		+ "<td><input type=text name=reserveTime value=0 size=32></td></tr\r\n>"
// 		+ "<tr><td>Minimum played games between admin time (int)</td>"
// 		+ "<td><input type=text name=nextres value=0 size=32></td></tr\r\n>"
		+ "<tr><td>Competition score table generator</td>"
		+ "<td><input type=text name=scoregen size=32></td></tr\r\n>"
		+ "<tr><td colspan=2>&nbsp;</td></tr\r\n>"
		+ "<tr><td colspan=2>"
		+ "Specify agents that should be scheduled as comma separated "
		+ "list of agent names<br>"
		+ "(you can also select agents in the list below)"
		+ "</td></tr><tr><td colspan=2>"
		+ "<textarea name=agents cols=75 rows=6></textarea>"
		+ "</td></tr>"
		+ "<tr><td colspan=2>"
		+ "<input type=submit name=submit value='Preview Schedule!'>"
		+ "</td></tr><tr><td colspan=2>&nbsp;</td></tr>\r\n"
		// Agents
		+ "<tr><td colspan=2>"
		+ "<table border=0 width='100%' bgcolor=black "
		+ "cellspacing=0 cellpadding=1><tr><td>"
		+ "<table border=0 bgcolor='#f0f0f0' width='100%' "
		+ "cellspacing=0>"
		+ "<tr><td colspan=6><b>Available agents:</b></td></tr\r\n>"
		+ "<tr>");
      if (userNumber == 0) {
	page.append("<td colspan=5><em>No agents are available</em></td>");
      } else {
	for (int i = 0; i < userNumber; i++) {
	  if (i % 5 == 0 && i > 0) {
	    page.append("</tr><tr>");
	  }
	  page.append("<td><input type=checkbox name=join-")
	    .append(users[i].getID()).append('>')
	    .append(users[i].getName()).append("</td>");
	}
	if ((userNumber % 5) > 0) {
	  page.append("<td colspan=").append(5 - (userNumber % 5))
	    .append(">&nbsp;</td>");
	}
      }
      page.append("</tr></table></td></tr></table></td></tr></table><p>\r\n"
		  + "</form>\r\n");
    }
    page.append("</body></html>\r\n");

    ByteArrayISO8859Writer writer = new ByteArrayISO8859Writer();
    writer.write(page.toString());
    response.setContentType(HttpFields.__TextHtml);
    response.setContentLength(writer.size());
    writer.writeTo(response.getOutputStream());
    response.commit();
    req.setHandled(true);
  }

  private String getGameType(int gameLength) {
//     if (gameLength < 12 && gameLength > 2) {
//       return ("tacClassic" + (gameLength - 12)).intern();
//     }
    return "tacClassic";
  }

  private String trim(String text) {
    return text != null && (text = text.trim()).length() > 0
      ? text
      : null;
  }

  // Returns a matrix of games to play (and which agents that are
  // going to play them
  public static int gamesPerAgent(int noAgents) {
    if (noAgents < 9) {
      return 1;
    }
    int noPlayAgents = (noAgents % 8) + 8;
    int perAgent = 8;
    int noRounds = noPlayAgents;
    while ((noRounds % 2) == 0) {
      noRounds = noRounds / 2;
      perAgent = perAgent / 2;
    }
    return perAgent;
  }

  public static int[][] scheduleGames(int noAgents) {
    int[][] games = null;
    if (noAgents <= 8) {
      games = new int[1][8];
      for (int i = 0; i < noAgents; i++) {
	games[0][i] = i + 1;
      }
    } else {
      int noPlayAgents = (noAgents % 8) + 8;
      int restAgents = (noAgents - noPlayAgents) / 8;
      int noGames = noPlayAgents / 8;
      int noRounds = noPlayAgents;
      int rotation = 1;
      int perAgent = 8;
      while ((noRounds % 2) == 0) {
	noRounds = noRounds / 2;
	rotation = rotation * 2;
	perAgent = perAgent / 2;
      }
//       System.out.println("Number of rounds: " + noRounds +
// 			 " games/round: " + noGames +
// 			 " per agent " + perAgent);

      int totalGames = noGames * noRounds + restAgents * perAgent;
      games = new int[totalGames][8];
      for (int round = 0; round < noRounds; round++) {
	for (int game = 0; game < noGames; game++) {
	  for (int a = 0; a < 8; a++) {
	    games[round * noGames + game][a] =
	      ((game * 8 + round * rotation + a) % noPlayAgents) + 1;
	  }
	}
      }

      int agNo = noPlayAgents + 1;
      for (int i = noGames * noRounds; i < totalGames; i++) {
	for (int a = 0; a < 8; a++) {
	  games[i][a] = agNo++;
	  if (agNo > noAgents) {
	    agNo = noPlayAgents + 1;
	  }
	}
      }

      for (int i = 0; i < totalGames * 48; i++) {
	int pos1 = (int)(Math.random() * 8);
	int pos2 = (int)(Math.random() * 8);
	int game1 = (int)(Math.random() * totalGames);
	int game2 = i % totalGames;

	int agent1 = games[game1][pos1];
	int agent2 = games[game2][pos2];

	boolean found = false;
	for (int a = 0; a < 8 && !found; a++) {
	  found = (games[game1][a] == agent2) ||
	    (games[game2][a] == agent1);
	}
	if (!found) {
	  /*
	  System.out.println("Swapping agent " + agent1 + " in game " + game1 +
			     " with " + agent2 + " in " + game2);
	  */
	  games[game1][pos1] = agent2;
	  games[game2][pos2] = agent1;
	}
      }
    }
    return games;
  }


  /*********************************************************************
   * Date handling
   *********************************************************************/

  public static synchronized String formatServerTimeDate(long time) {
    if (dateFormat == null) {
      dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
      dateFormat.setTimeZone(new java.util.SimpleTimeZone(0, "UTC"));
    }
    return dateFormat.format(new Date(time));
  }

  public static synchronized long parseServerTimeDate(String date)
    throws ParseException
  {
    if (dateFormat == null) {
      dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
      dateFormat.setTimeZone(new java.util.SimpleTimeZone(0, "UTC"));
    }
    return dateFormat.parse(date).getTime();
  }



  /*********************************************************************
   * Test main
   *********************************************************************/

  public static void main (String[] args) {
    int gameNr = 0;
    if (args.length < 1) {
      System.out.println("Usage: GameScheduler <NoAgents>");
      System.exit(0);
    }

    try {
      gameNr = Integer.parseInt(args[0]);
    } catch (Exception e) {
      System.out.println("Error in nr");
    }

    int[][] games = scheduleGames(gameNr);
    int[] agentGames = new int[gameNr + 1];
    for (int i = 0, n = games.length; i < n; i++) {
      System.out.print("Game " + i + " | ");
      for (int j = 0; j < 8; j++) {
	System.out.print("" + games[i][j] + ' ');
	agentGames[games[i][j]]++;
      }
      System.out.println();
    }
    for (int i = 0; i < gameNr + 1; i++) {
      System.out.println("Agent " + i + " played " + agentGames[i]);
    }
  }
}
