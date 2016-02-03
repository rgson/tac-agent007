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
 * GamePage
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 10 April, 2002
 * Updated : $Date: 2004/07/16 15:03:54 $
 *	     $Revision: 1.5 $
 */

package se.sics.tac.is;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

import com.botbox.util.ArrayQueue;
import org.mortbay.http.HttpException;
import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import org.mortbay.util.ByteArrayISO8859Writer;
import se.sics.tac.util.TACFormatter;

public class GamePage extends HttpPage {

  private static final Logger log =
    Logger.getLogger(GamePage.class.getName());

  private final static int MAX_VISIBLE_GAMES = 100;
  private final static int TIMEOUT = 5000;

  private ArrayQueue eventQueue = new ArrayQueue();
  private InfoServer infoServer;
  private TACStore store;

  private String timeLimitedMessage = null;
  private long timeLimit = 0L;

  public GamePage(InfoServer info) {
    this.infoServer = info;
    this.store = info.getTACStore();
  }

  public void setTimeLimitedMessage(String message, long time) {
    this.timeLimit = time;
    this.timeLimitedMessage = message;
  }

  public void gameRemoved(TACGame game, String reason) {
    if (game.isReservation()) {
      // The game was simply a time reservation
    } else {
      StringBuffer sb = new StringBuffer();
      if (reason != null) {
        sb.append("<font color=red>");
      }
      sb.append("<em>The game starting at ")
        .append(infoServer.getServerTimeAsString(game.getStartTimeMillis()))
        .append(" has been scratched");
      if (reason != null) {
        sb.append(": ").append(reason).append("</em></font>\r\n");
      } else if (game.isEmpty()) {
        sb.append(" because it had no participants.</em>\r\n");
      } else {
	sb.append("</em>\r\n");
      }
      setTimeLimitedMessage(sb.toString(), game.getEndTimeMillis());
    }
  }

  public synchronized void gameCreated(TACGame game) {
    String gameType = game.getGameType();
    if (!game.isReservation() && gameType != null) {
      for (int i = 0, n = eventQueue.size(); i < n; i++) {
	Event event = (Event) eventQueue.get(i);
	if (event.isWaitingForGame && gameType.equals(event.gameType)) {
	  String message = "A new game starting at "
	    + infoServer.getServerTimeAsString(game.getStartTimeMillis())
	    + " was created";
	  eventQueue.remove(i);
	  event.notifyResult(message);
	  break;
	}
      }
    }
  }

  public synchronized void gameJoined(int uniqGameID, int agentID) {
    for (int i = 0, n = eventQueue.size(); i < n; i++) {
      Event event = (Event) eventQueue.get(i);
      if (!event.isWaitingForGame
	  && (agentID == event.agentID)
	  && (event.game != null && uniqGameID == event.game.getID())) {
	// Remove the HTTP request and the user information from the queue
	eventQueue.remove(i);
	i--;
	n--;
	event.notifyResult(store.getUserName(agentID)
			   + " successfully joined game "
			   + getGameIDAsString(event.game));
      }
    }
  }

  public void handle(String pathInContext, String pathParams,
		     HttpRequest req, HttpResponse response)
    throws HttpException, IOException
  {
    String userName = req.getAuthUser();
    TACUser user = store.getUser(userName);
    if (user == null){
      response.sendError(HttpResponse.__403_Forbidden);
      return;
    }

    int agentID = user.getID();
    String message = null;
    String gameType = InfoServer.TAC_CLASSIC;

    if (HttpRequest.__POST.equals(req.getMethod())) {
      String type = req.getParameter("gameType");
      if (type != null && type.length() > 0) {
	gameType = type;
      }

      if (req.getParameter("creategame") != null) {
	if (infoServer.isWebJoinActive()) {
	  try {
	    agentID = Integer.parseInt(req.getParameter("agent_no"))
	      + user.getID();
	  } catch (Exception e) {}
	  Event event = new Event(gameType);
	  addEvent(event);
	  if (infoServer.createGame(gameType)) {
	    message = event.waitForResult();
	    if (message == null) {
	      removeEvent(event);
	    }
	  }
	  if (message == null) {
	    message = "<font color=red>Could not create the game at "
	      + "this time. Please try again later.</font>\r\n";
	  }
	} else {
	  message = "<font color=red>Game creation is prohibited "
	    + "for the moment. Please try again later.</font>\r\n";
	}

      } else {
	Set names = req.getParameterNames();
	Iterator nameIterator = names.iterator();
	while (nameIterator.hasNext()) {
	  String name = (String) nameIterator.next();
	  if (name.startsWith("jg_")) {
	    if (infoServer.isWebJoinActive()) {
	      try {
		int uniqGameID = Integer.parseInt(name.substring(3));
		agentID = Integer.parseInt(req.getParameter("agent_no"))
		  + user.getID();
		TACGame game = store.getComingGameByUniqID(uniqGameID);
		if (game == null) {
		  message = "Could not find game " + uniqGameID;
		  break;
		} else if (game.isParticipant(agentID)) {
		  message = "Agent " + store.getUserName(agentID)
		    + " is already participating in game "
		    + getGameIDAsString(game);
		  break;
		} else if (game.isFull()) {
		  message = "Game " + getGameIDAsString(game)
		    + " is already full";
		  break;
		} else {
		  Event event = new Event(game, agentID);
		  addEvent(event);
		  if (infoServer.joinGame(uniqGameID, agentID)) {
		    message = event.waitForResult();
		    if (message == null) {
		      removeEvent(event);
		    }
		  }
		  if (message == null) {
		    message = "Failed to join game " + getGameIDAsString(game);
		  }
		}
	      } catch (Exception e) {
		message = "Could not join game: " + e.getMessage();
	      }
	    } else {
	      message = "<font color=red>Could not join game at this time. "
		+ "Please try again later.</font>\r\n";
	      break;
	    }
	  }
	}
      }
    }
    displayPage(req, response, message, user, agentID, gameType);
  }

  private String getGameIDAsString(TACGame game) {
    if (game == null) {
      return "";
    }

    int gid = game.getGameID();
    return gid < 0
      ? "starting at "
      + infoServer.getServerTimeAsString(game.getStartTimeMillis())
      : Integer.toString(gid);
  }

  private void displayPage(HttpRequest req, HttpResponse response,
			   String message, TACUser user, int agentID,
			   String gameType)
    throws HttpException, IOException
  {
    StringBuffer page = new StringBuffer();
    // Use local variable in case some other threads changes this
    String messageToAll = infoServer.getServerMessage();
    String timeLimitedMessage = this.timeLimitedMessage;
    Competition currentComp = store.getCurrentCompetition();
    boolean allowJoin = currentComp == null
	&& infoServer.isWebJoinActive();
    TACGame[] games = store.getComingGames();
    int gamesLen = games == null ? 0 : games.length;
    long currentTime = infoServer.getServerTimeMillis();
    String serverName = infoServer.getServerName();

    if (!InfoServer.ALLOW_GAME_TYPE || gameType == null) {
      gameType = InfoServer.TAC_CLASSIC;
    }

    page.append("<html><head><title>TAC Classic - Coming Games at ")
      .append(serverName)
      .append("</title></head>\r\n"
	      + "<body><font face='arial' size='+2'>Coming Games at ")
      .append(serverName)
      .append("</font>\r\n"
	      + "<p>The Trading Agent Competition Game page is used "
	      + "to view and create TAC games. To View games click "
	      + "<a href='/applet/' target=tgt>"
	      + "<b>Launch Game Viewer</b></a>.");
    if (allowJoin && (gamesLen <= MAX_VISIBLE_GAMES)) {
      // Do not show the create game button if there are too many
      // coming games (the newly created games will not be visible anyway).
      page.append("To create a game, click on the <b>Create Game</b> "
		  + "button below.\r\n");
    }
    page.append("<p>");
    if (messageToAll != null) {
      page.append(messageToAll).append("\r\n<p>");
    }

    page.append("<hr noshade color='#202080'>\r\n");

    if (timeLimitedMessage != null) {
      if (timeLimit <= currentTime) {
	// Time to remove message
	this.timeLimitedMessage = null;
      } else {
	page.append(timeLimitedMessage).append("\r\n<p>");
      }
    }

    if (message != null) {
      page.append("<h3>").append(message).append("</h3>");
    }

    if (allowJoin) {
      page.append("<form method=post>");
    }

    if (gamesLen == 0) {
      page.append("<p>Current server time is ")
	.append(InfoServer.getServerTimeAsString(currentTime))
	.append("<p>No games scheduled\r\n");
    } else {
      String name = user.getName();
      String columnStart;
      Competition nextCompetition = null;
      String nextCompetitionStart = null;
      int numberOfGames = gamesLen;
      int minAgentID= user.getID();
      int maxAgentID = minAgentID + 10;
      int startGame = -1, endGame = -1;
      if (currentComp != null) {
	page.append("<p><h3>Playing competition ")
	  .append(currentComp.getDescription());
	if (currentComp.hasGameID()) {
	  page.append(" (game ").append(currentComp.getStartGameID())
	    .append(" - ").append(currentComp.getEndGameID()).append(')');
	}
	page.append("</h3>\r\n")
	  .append("<em>")
	  .append("Competition started at ")
	  .append(InfoServer.getServerTimeAsString(currentComp.getStartTime()))
	  .append(" and ends at ")
	  .append(InfoServer.getServerTimeAsString(currentComp.getEndTime()))
	  .append(".</em>\r\n");
	endGame = currentComp.getEndGame();
      } else {
	Competition[] competitions = store.getComingCompetitions();
	if (competitions != null) {
	  // Find the next competition since the coming competition might
	  // also contain old competitions
	  for (int i = 0, n = competitions.length; i < n; i++) {
	    if (competitions[i].getStartTime() > currentTime) {
	      nextCompetition = competitions[i];
	      nextCompetitionStart =
		InfoServer.getServerTimeAsString(nextCompetition
						 .getStartTime());
	      page.append("<b>Next competition '")
		.append(nextCompetition.getDescription())
		.append("' starts at ")
		.append(nextCompetitionStart)
		.append("</b><p>\r\n");
	      startGame = nextCompetition.getStartGame();
	      endGame = nextCompetition.getEndGame();
	      break;
	    }
	  }
	}
      }
      if (allowJoin) {
	page.append("<em>Note: please do not schedule your agent in many "
		    + "games in advanced since it makes it hard for other "
		    + "teams to practice.</em>\r\n<p>"
		    + "Select agent for joining: "
		    + "<select name=agent_no><option value=0>")
	  .append(name);
	int id = agentID - user.getID();
	if (id < 0 || id > 10) {
	  id = 0;
	}
	for (int i = 1; i < 11; i++) {
	  page.append("<option value=").append(i);
	  if (i == id) {
	    page.append(" selected");
	  }
	  page.append('>').append(name).append(i - 1)
	    .append("</option>\r\n");
	}
	page.append("</select>");
      }
      page.append("<p>Current server time is ")
	.append(InfoServer.getServerTimeAsString(currentTime));
      page.append("\r\n<table border=1><tr><th>Game</th><th>Time</th>"
		  + "<th>Type</th>"
		  + "<th>Participants</th><th>Status</th><th>Join</th>"
		  + "</tr\r\n>");
      if (numberOfGames > MAX_VISIBLE_GAMES) {
	numberOfGames = MAX_VISIBLE_GAMES;
      }
      for (int i = 0, n = numberOfGames; i < n; i++) {
	TACGame game = games[i];
	int uniqGameID = game.getID();
	int gameID = game.getGameID();
	boolean isRunning = game.getStartTimeMillis() < currentTime;
	if (isRunning) {
	  columnStart = "<td bgcolor='#e0e0ff'>";
	} else {
	  columnStart = "<td>";

	  // The game can only start a competition if the game has not
	  // already started
	  if (startGame == uniqGameID && nextCompetition != null) {
	    page.append("<tr><td bgcolor='#e0e0ff' colspan=6>&nbsp;</td></tr>"
			+ "<tr><td colspan=6 align=center>"
			+ "<font size=+1 color='#800000'><b>Competition ")
	      .append(nextCompetition.getDescription())
	      .append(" starts");
	    if (nextCompetitionStart != null) {
	      page.append(" (").append(nextCompetitionStart)
		.append(')');
	    }
	    page.append("</b></font></td></tr\r\n>"
			+ "<tr><td bgcolor='#e0e0ff' colspan=6>&nbsp;"
		      + "</td></tr>");
	  }
	}
	page.append("<tr>").append(columnStart);
	if (gameID > 0) {
	  page.append(gameID);
	} else {
	  page.append("&nbsp;");
	}
	page.append("</td>").append(columnStart);
	infoServer.appendTimeMillis(page, game.getStartTimeMillis());

	page.append(" (");
	TACFormatter.formatDelayAsHtml(page, game.getGameLength());
	page.append(')');

	page.append("</td>").append(columnStart);
	page.append(infoServer.getGameTypeName(game.getGameType()))
	  .append("</td>").append(columnStart);
	for (int p = 0, np = game.getNumberOfParticipants(); p < np; p++) {
	  int participant = game.getParticipant(p);
	  if (p > 0) {
	    page.append(", ");
	  }
	  if (participant >= minAgentID && participant <= maxAgentID) {
	    page.append("<font size=+1 color='#800000'><b>")
	      .append(store.getUserName(participant))
	      .append("</b></font>");
	  } else if (participant < 0) {
	    page.append("<em>")
	      .append(store.getUserName(participant))
	      .append("</em>");
	  } else {
	    page.append(store.getUserName(participant));
	  }
	}
	page.append("&nbsp;</td>")
	  .append(columnStart)
	  .append(isRunning ? "Running" : "Coming")
	  .append("</td>").append(columnStart);
	if (isRunning || !allowJoin || game.isFull()) {
	  page.append("&nbsp;");
	} else {
	  page.append("<input type=submit value='Join' name='jg_").
	    append(uniqGameID).append("'>");
	}
	page.append("</td></tr\r\n>");
	if (endGame == uniqGameID) {
	  page.append("<tr><td bgcolor='#e0e0ff' colspan=6>&nbsp;</td></tr>"
		      + "<tr><td colspan=6 align=center>"
		      + "<font size=+1 color='#800000'><b>Competition ");
	  if (currentComp != null) {
	    page.append(currentComp.getDescription());
	  } else if (nextCompetition != null) {
	    page.append(nextCompetition.getDescription());
	  }
	  page.append(" ends")
	    .append("</b></font></td></tr\r\n>"
		    + "<tr><td bgcolor='#e0e0ff' colspan=6>&nbsp;"
		    + "</td></tr>");
	}
      }
      page.append("</table>");
      if (numberOfGames < gamesLen) {
	page.append("<br><em>(Only showing the first "
		    + MAX_VISIBLE_GAMES + " of the coming ")
	  .append(gamesLen).append(" games)</em>");
      }
    }
    // Only allow creation of new games if not in a competition
    if (allowJoin && (gamesLen <= MAX_VISIBLE_GAMES)) {
      String[] gameTypes = InfoServer.ALLOW_GAME_TYPE
	? infoServer.getGameTypes()
	: null;
      page.append("<p>");
      if (!InfoServer.ALLOW_GAME_TYPE
	  || gameTypes == null || gameTypes.length == 0) {
	page.append("<input type=hidden value='" + gameType
		    + "' name='gameType'>");
      } else {
	page.append("<select name='gameType'>");
	for (int i = 0, n = gameTypes.length; i < n; i++) {
	  String type = gameTypes[i];
	  page.append("<option value='").append(type)
	    .append('\'');
	  if (type.equals(gameType)) {
	    page.append(" selected");
	  }
	  page.append('>').append(infoServer.getGameTypeName(type))
	    .append("</option>\r\n");
	}
	page.append("</select>\r\n");
      }
      page.append("<input type=submit value='Create Game' "
		  + "name='creategame'>\r\n</form>\r\n");
    }
    page.append(InfoServer.HTTP_FOOTER)
      .append("</body></html>\r\n");

    ByteArrayISO8859Writer writer = new ByteArrayISO8859Writer();
    writer.write(page.toString());
    response.setContentType(HttpFields.__TextHtml);
    response.setContentLength(writer.size());
    writer.writeTo(response.getOutputStream());
    response.commit();
  }



  // -------------------------------------------------------------------
  // Timeout handling
  // -------------------------------------------------------------------

  private synchronized void addEvent(Event event) {
    eventQueue.add(event);
  }

  private synchronized void removeEvent(Event event) {
    int index = eventQueue.indexOf(event);
    if (index >= 0) {
      eventQueue.remove(index);
    }
  }

  private static class Event {
    public boolean isWaitingForGame;
    public String gameType;
    public TACGame game;
    public int agentID;

    private String message;

    public Event(String gameType) {
      this.gameType = gameType;
      this.isWaitingForGame = true;
    }

    public Event(TACGame game, int agentID) {
      this.game = game;
      this.agentID = agentID;
    }

    public synchronized void notifyResult(String message) {
      this.message = message;
      notify();
    }

    private synchronized String waitForResult() {
      if (message == null) {
	try {
	  wait(TIMEOUT);
	} catch (Exception e) {
	}
      }
      return message;
    }
  }

} // GamePage
