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
 * DefaultScoreGenerator
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 26 July, 2002
 * Updated : $Date: 2004/08/12 13:40:47 $
 *	     $Revision: 1.5 $
 */

package se.sics.tac.is;
import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import se.sics.tac.util.TACFormatter;

public class DefaultScoreGenerator extends ScoreGenerator {

  private static final Logger log =
    Logger.getLogger(DefaultScoreGenerator.class.getName());

  private int agentsToAdvance = 0;
  private String advanceColor = null;
  private boolean isShowingCompetitionTimes = true;
  private boolean isShowingAllAgents = false;
  private boolean isShowingZeroGameAgents = true;
  private boolean isIgnoringWeight = false;
  private boolean isUsingBestScore = false;


  // -------------------------------------------------------------------
  // Settings
  // -------------------------------------------------------------------

  public int getAgentsToAdvance() {
    return agentsToAdvance;
  }

  public void setAgentsToAdvance(int agentsToAdvance) {
    this.agentsToAdvance = agentsToAdvance;
  }

  public String getAdvanceColor() {
    return advanceColor;
  }

  public void setAdvanceColor(String advanceColor) {
    this.advanceColor = advanceColor;
  }

  public boolean isShowingCompetitionTimes() {
    return isShowingCompetitionTimes;
  }

  public void setShowingCompetitionTimes(boolean isShowingCompetitionTimes) {
    this.isShowingCompetitionTimes = isShowingCompetitionTimes;
  }

  public boolean isShowingAllAgents() {
    return isShowingAllAgents;
  }

  public void setShowingAllAgents(boolean isShowingAllAgents) {
    this.isShowingAllAgents = isShowingAllAgents;
  }

  public boolean isShowingZeroGameAgents() {
    return isShowingZeroGameAgents;
  }

  public void setShowingZeroGameAgents(boolean isShowingZeroGameAgents) {
    this.isShowingZeroGameAgents = isShowingZeroGameAgents;
  }

  public boolean isIgnoringWeight() {
    return isIgnoringWeight;
  }

  public void setIgnoringWeight(boolean isIgnoringWeight) {
    this.isIgnoringWeight = isIgnoringWeight;
  }

  public boolean isUsingBestScore() {
    return isUsingBestScore;
  }

  public void setUsingBestScore(boolean isUsingBestScore) {
    this.isUsingBestScore = isUsingBestScore;
  }


  // -------------------------------------------------------------------
  // Score generation
  // -------------------------------------------------------------------

  public boolean generateScorePage(TACStore store,
				   String path,
				   Competition competition,
				   int gameID,
				   boolean update) {
    String scoreFile = path + File.separatorChar + "index.html";
    if (!update && new File(scoreFile).exists()) {
      return true;
    }

    try {
      StringBuffer page = new StringBuffer();
      TACUser[] users;
      boolean isWeighted;
      if (competition != null) {
	users = getCombinedParticipants(competition);
	isWeighted = !isIgnoringWeight && competition.isWeightUsed();
      } else {
	users = store.getUsers();
	isWeighted = false;
	isUsingBestScore = false;
      }

      generateScorePage(page, path, competition, users, gameID, isWeighted);

      FileWriter out = new FileWriter(scoreFile);
      out.write(page.toString());
      out.close();
      return true;

    } catch (Exception e) {
      log.log(Level.SEVERE, "could not create score page for game " + gameID
	      + " in " + scoreFile, e);
      return false;
    }
  }

  protected void generateScorePage(StringBuffer page, String path,
				   Competition competition,
				   TACUser[] users, int gameID,
				   boolean isWeighted) {
    boolean isUsingBestScore = isUsingBestScore();
    page.append("<html><head><title>TAC Classic - Score Page");
    if (competition != null) {
      page.append(" for ").append(competition.getDescription());
      if (competition.hasParentCompetition()) {
	// It is currently not possible to use best score with chained
	// competitions
	isUsingBestScore = false;
      }
    } else {
      isUsingBestScore = false;
    }
    page.append("</title></head>\r\n<body>\r\n"
		+ "<h3>Scores");
    if (competition != null) {
      boolean hasGameID = competition.hasGameID();
      page.append(" for ")
	.append(competition.getDescription());
      if (hasGameID) {
	page.append(" (game ");
	addCompetitionGames(page, competition);
	page.append(')');
      }
      page.append("</h3>\r\n");
      if (isShowingCompetitionTimes) {
	page.append("<em>");
	if (hasGameID && gameID >= competition.getStartGameID()) {
	  page.append("Competition started at ");
	} else {
	  page.append("Competition starts at ");
	}
	page.append(getRootStartTime(competition));
	if (hasGameID && gameID >= competition.getEndGameID()) {
	  page.append(" and ended at ");
	} else {
	  page.append(" and ends at ");
	}
	page.append(InfoServer
		    .getServerTimeAsString(competition.getEndTime()))
	  .append(".</em><br>\r\n");
      }
      if (gameID > 0)
	if (hasGameID && (gameID >= competition.getEndGameID())) {
	  // Last game in competition
	  page.append("<em>Final Scores.</em>");
	} else if (isWeighted) {
	  float w = competition.getWeight(gameID);
	  long nextUpdate = competition.getNextWeightUpdate(gameID);
	  page.append("<em>Current weight is ").append(w).append(". ");
	  if (nextUpdate > 0L) {
	    page.append("Next weight update is at ")
	      .append(InfoServer
		      .getServerTimeAsString(nextUpdate)).append("</em>.");
	  }
	}
      page.append("<p>\r\n");
    } else {
      page.append("</h3>\r\n");
    }

    if (users != null) {
      users = (TACUser[]) users.clone();

      Arrays.sort(users, isUsingBestScore
		  ? TACUser.getWScoreBestComparator()
		  : (isWeighted
		     ? TACUser.wScoreComparator
		     : TACUser.scoreComparator)
		  );
      page.append("<table border=1>"
		  + "<tr><th>Position</th><th>Agent</th>");
      if (isUsingBestScore) {
	page.append("<th>Avg&nbsp;Score (-worst)</th>");
      }
      if (isWeighted) {
	page.append("<th>Avg&nbsp;Weighted Score</th>");
      }
      page.append("<th>Avg&nbsp;Score</th>"
		  + "<th>Games&nbsp;Played</th>"
		  + "<th>Zero&nbsp;Games</th></tr\r\n>");
      int pos = 1;
      for (int i = 0, n = users.length; i < n; i++) {
	TACUser usr = users[i];

	if (!isShowingAllAgents
	    && (isShowingZeroGameAgents
		? (usr.getGamesPlayed() <= 0)
		: (usr.getGamesPlayed() <= usr.getZeroGamesPlayed()))) {
	  continue;
	}

	String userName = createUserName(path, usr);
	String color = getAgentColor(usr, pos, n);
	String td, tdright;
	if (color != null) {
	  td = "<td bgcolor='" + color + "'>";
	  tdright = "<td bgcolor='" + color + "' align=right>";
	} else {
	  td = "<td>";
	  tdright = "<td align=right>";
	}

	page.append("<tr>").append(td).append(pos++).append("</td>")
	  .append(td).append(userName).append("</td>");
	if (isUsingBestScore) {
	  page.append(tdright)
	    .append(TACFormatter.toString(usr.getWScoreBest()))
	    .append("</td>");
	}
	if (isWeighted) {
	  page.append(tdright)
	    .append(TACFormatter.toString(usr.getWScore()))
	    .append("</td>");
	}
	page.append(tdright)
	  .append(TACFormatter.toString(usr.getAvgScore()))
	  .append("</td>").append(tdright)
	  .append(usr.getGamesPlayed())
	  .append("</td>").append(tdright)
	  .append(usr.getZeroGamesPlayed())
	  .append("</td></tr\r\n>");
      }
      page.append("</table>\r\n");
      generateFooter(page, gameID);
    } else {
      page.append("No TAC agents registered\r\n");
    }
    page.append("</body></html>\r\n");
  }

  protected String createUserName(String path, TACUser usr) {
    String userName = usr.getName();
    int statistics = usr.getStatisticsFlag();
    boolean statisticsExists;

    if (statistics == TACUser.STATISTICS_UNCHECKED) {
      statisticsExists = new File(path + File.separatorChar +
				  usr.getID() + ".html").exists();
      usr.setStatisticsFlag(statisticsExists
			    ? TACUser.STATISTICS_EXISTS
			    : TACUser.STATISTICS_NON_EXISTENCE);
    } else {
      statisticsExists = statistics == TACUser.STATISTICS_EXISTS;
    }
    if (statisticsExists) {
      userName = "<a href='" + usr.getID() + ".html'>" + userName +
	"</a>";
    }
    return userName;
  }

  protected String getAgentColor(TACUser agent, int pos, int numberOfAgents) {
    if (pos <= agentsToAdvance) {
      return advanceColor;
    }
    return null;
  }

  private static void addCompetitionGames(StringBuffer page,
					  Competition competition) {
    Competition parentCompetition = competition.getParentCompetition();
    if (parentCompetition != null && parentCompetition.hasGameID()) {
      addCompetitionGames(page, parentCompetition);
      page.append(", ");
    }
    page.append(competition.getStartGameID())
      .append(" - ").append(competition.getEndGameID());
  }

  private String getRootStartTime(Competition competition) {
    Competition parentCompetition = competition.getParentCompetition();
    if (parentCompetition != null) {
      return getRootStartTime(parentCompetition);
    }
    return InfoServer.getServerTimeAsString(competition.getStartTime());
  }

} // DefaultScoreGenerator
