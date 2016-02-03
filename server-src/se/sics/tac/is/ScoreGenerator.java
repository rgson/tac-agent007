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
 *
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 9 April, 2002
 * Updated : $Date: 2004/07/11 21:37:53 $
 *	     $Revision: 1.2 $
 */

package se.sics.tac.is;

public abstract class ScoreGenerator {

  private String serverName;
  private String serverVersion;

  final void setServerInfo(String serverName, String serverVersion) {
    this.serverName = serverName;
    this.serverVersion = serverVersion;
  }

  public abstract boolean generateScorePage(TACStore store,
					    String path,
					    Competition competition,
					    int gameID,
					    boolean update);

  /**
   * Returns all participants in the specified competition with
   * respect to any parent competition.
   */
  protected TACUser[] getCombinedParticipants(Competition competition) {
    TACUser[] parts = competition.getParticipants();
    if (!competition.hasParentCompetition() || parts == null) {
      return parts;
    }

    TACUser[] participants = new TACUser[parts.length];
    // This "lowest" competition should shield all other parent
    // competitions with regard to the agents
    for (int i = 0, n = parts.length; i < n; i++) {
      // Create cache object so we can change it freely
      participants[i] = new TACUser(parts[i]);
      participants[i].addScore(parts[i]);
    }

    Competition parentCompetition = competition.getParentCompetition();
    while (parentCompetition != null) {
      for (int i = 0, n = participants.length; i < n; i++) {
	TACUser cpart =
	  parentCompetition.getParticipant(participants[i].getID());
	if (cpart != null) {
	  participants[i].addScore(cpart);
	}
      }
      // Continue and add scores from all parent competitions
      parentCompetition = parentCompetition.getParentCompetition();
    }

    return participants;
  }

  protected void generateFooter(StringBuffer page, int gameID) {
    if (gameID > 0) {
      page.append("<font size=-1><em>Scores last updated after game ")
	.append(gameID);
      if (serverName != null && serverVersion != null) {
	page.append(" on server ").append(serverName).append(" version ")
	  .append(serverVersion);
      }
      page.append("</em></font>\r\n");
    } else if (serverName != null && serverVersion != null) {
      page.append("<em>Games played at server ").append(serverName)
	.append(" version ")
	.append(serverVersion)
	.append("</em>");
    }
  }

}
