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
 * Statistics
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 12 April, 2002
 * Updated : $Date: 2004/07/11 21:37:53 $
 *	     $Revision: 1.7 $
 */

package se.sics.tac.is;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import com.botbox.html.HtmlWriter;
import se.sics.tac.util.TACFormatter;

public class Statistics {

  private static final Logger log =
    Logger.getLogger(Statistics.class.getName());

  private final static int WIDTH = 550;
  private final static int HEIGHT = 320;
  private final static int MX = 24;
  private final static int MY = 20;
  private final static int TXT = 20;
  private final static Color LIGHTGRAY = new Color(210, 210, 210);

  private static boolean hasGUI = true;

  // Prevent instances of this class
  private Statistics() {
  }

  public static boolean createImage(String file, TACGameResult result) {
    if (!hasGUI) {
      return false;
    }

    int max = -50000;
    int min = 50000;
    int numberOfGames = result.getNumberOfGames();
    for (int i = 0; i < numberOfGames; i++) {
      int score = (int) result.getScore(i);
      if (max < score)
	max = score;
      if (min > score)
	min = score;
    }

    if (min < -3000) min = -3000;
    if (max < 100) max = 100;
    if (min > 0) min = 0;

    int tagSpacing = 2000;
    int interval = max - min;
    int zeroLine = min < 0 ? (20 + ((HEIGHT - 40)*max)/interval) : HEIGHT - 20;
    int tagNo = 5;
    log.finest("Max: " + max + " Min: " + min + " Interval: " + interval +
	       " Zero: " + zeroLine);
    if (interval > 3000) {
      tagSpacing = 1000;
      tagNo = (interval + 950) / 1000;
    } else if (interval > 1000) {
      tagSpacing = 500;
      tagNo = (interval + 490) / 500;
    } else if (interval > 500) {
      tagSpacing = 200;
    } else {
      tagSpacing = 100;
    }

    float resolution = (HEIGHT - 40f) / (tagNo * tagSpacing);
    float xResolution = 1.0f * (WIDTH - 2*MX - TXT) / numberOfGames;
    BufferedImage image;
    Graphics2D g2d;
    // Try to create the buffered image. If no graphics environment is
    // available, this will fail with an internal error which means
    // that no statistics images can be generated.
    try {
      image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
      g2d = image.createGraphics();
    } catch (InternalError e) {
      log.log(Level.SEVERE, "could not access graphics environment", e);
      hasGUI = false;
      return false;
    }

    g2d.setBackground(Color.white);
    g2d.setColor(Color.black);
    g2d.clearRect(0, 0, WIDTH, HEIGHT);

    int low = ((tagNo + 1) * min)/interval;
    int hi = ((tagNo + 1) * max)/interval;

    for (int i = low; i < hi; i++) {
      int score = i * tagSpacing;
      int ypos = zeroLine - (int) (resolution * score);
      g2d.setColor(LIGHTGRAY);
      g2d.drawLine(TXT + MX, ypos, WIDTH - MX, ypos);
      g2d.setColor(Color.black);
      g2d.drawString("" + i * tagSpacing, 4 , 4 + ypos);
    }

    g2d.drawLine(TXT + MX, MY, TXT + MX, HEIGHT - MY);
    g2d.drawLine(TXT + MX, zeroLine, WIDTH - MX, zeroLine);

    g2d.drawLine(TXT + MX, MY, TXT + MX + 4, MY + 6);
    g2d.drawLine(TXT + MX, MY, TXT + MX - 4, MY + 6);


    g2d.drawLine(WIDTH - MX, zeroLine, WIDTH - MX - 6, zeroLine + 4);
    g2d.drawLine(WIDTH - MX, zeroLine, WIDTH - MX - 6, zeroLine - 4);

    // This should probably generate maximum 100 games (100 latest???)
    float sc;
    float med = 0;
    float oldMed = 0;
    int medP = -1;
    int oldMedP = -1;
    for (int i = 0; i < numberOfGames; i++) {
      int xp = (int) (TXT + MX + i * xResolution);
      sc = result.getScore(i);
      med += sc;

      if (i > 15) {
	oldMed = med;
	med -= result.getScore(i - 15);
	oldMedP = medP;
	medP = zeroLine - (int) ((resolution * med)/15);
	if (oldMedP == -1)
	  oldMedP = medP;
	g2d.setColor(Color.red);
	g2d.drawLine(xp - (int)(8 * xResolution), oldMedP,
		     xp - (int)(7 * xResolution), medP);
      }
      if (sc < min) {
	sc = min;
      }
      int yp = zeroLine - (int) (resolution * sc);

      // System.out.println("Score " + i + " = " + sc);
      g2d.setColor(Color.black);
      g2d.drawLine(xp - 1, yp - 1, xp + 1, yp + 1);
      g2d.drawLine(xp + 1, yp -1, xp - 1, yp + 1);
    }

    try {
      return ImageIO.write(image, "png", new File(file));
    } catch (Exception ioe) {
      log.log(Level.SEVERE, "could not write statistics image "
	      + file, ioe);
      return false;
    }
  }

  public static boolean generateStatisticsPage(TACStore store,
					       String path, String urlGamePath,
					       Competition competition,
					       TACUser user,
					       boolean generateImage) {
    try {
      String file = path + File.separatorChar + user.getID() + ".html";
      HtmlWriter out =
	new HtmlWriter(new BufferedWriter(new FileWriter(file)));
      boolean isWeightUsed;
      boolean lowestScoreForZero = false;
      double zeroScore = 0.0;
      double wZeroScore = 0.0;
      int zeroCount = 0;
      TACGameResult result;
      boolean showBest = competition != null;

      if (competition != null && competition.hasParentCompetition()) {
	// Must sum the score from all parent competitions
	TACUser cache = new TACUser(user);
	cache.addScore(user);
	Competition parentCompetition = competition.getParentCompetition();
	while (parentCompetition != null) {
	  TACUser u = parentCompetition.getParticipant(user.getID());
	  if (u != null) {
	    cache.addScore(u);
	  }
	  parentCompetition = parentCompetition.getParentCompetition();
	}
	// Use the cache in the future
	user = cache;
	// Score without 10 worst games currently not possible with
	// chained competitions
	showBest = false;
      }

      if (competition != null && competition.hasGameID()) {
	isWeightUsed = competition.isWeightUsed();
	result = store.getLatestGameResult(user.getID(), competition, 1000);
	lowestScoreForZero =
	  (competition.getFlags() & Competition.LOWEST_SCORE_FOR_ZERO) != 0;

	if (lowestScoreForZero && result.getNumberOfGames() > 0) {
	  for (int i = 0, n = result.getNumberOfGames(); i < n; i++) {
	    double score = result.getScore(i);
	    if ((score == 0.0)
		|| (result.getFlags(i) & TACStore.ZERO_GAME) != 0) {
	      zeroScore += result.getScore(i);
	      wZeroScore += result.getScore(i) * result.getWeight(i);
	      zeroCount++;
	    }
	  }
	}

      } else {
	int startGame = 0;
	int numGames = 100;
	isWeightUsed = false;
	result = store.getLatestGameResult(user.getID(), startGame, numGames);
      }
      int lastNumberOfGames = result.getNumberOfGames();
      String title;
      if (competition != null) {
	title = "Statistics for " + user.getName()
	  + " in competition " + competition.getDescription();
      } else {
	title = "Statistics for " + user.getName();
      }
      if (urlGamePath == null) {
	urlGamePath = (competition != null)
	  ? "../../"
	  : "../";
      }
      out.pageStart(title).h2(title);
      if (generateImage) {
	if (lastNumberOfGames == 0) {
	  // No image if no games
	  generateImage = false;
	} else if (hasGUI) {
	  out.table("border=0 cellpadding=1 cellspacing=0 bgcolor=#0");
	  out.tr().td().text("<img src='").text(user.getID())
	    .text("_gst.png' alt='Agent Scores'>");
	  out.tableEnd();
	  out.text("<em>Scores of the last ")
	    .text(lastNumberOfGames)
	    .text(" games played</em><p>");
	} else {
	  generateImage = false;
	  log.severe("No graphics environment available. Could not generate "
		     + "statistics image for agent " + user.getName());
	}
      }

      out.table("border=1").tr();
      if (competition != null) {
	if (isWeightUsed) {
	  if (showBest) {
	    out.th("Avg WScore - 10");
	  }
	  out.th("Avg WScore")
	    .th("Avg WScore - Zero");
	} else if (showBest) {
	  out.th("Avg Score - 10");
	}
      }
      out.th("Avg Score").th("Avg Score - Zero");
      out.tr();

      // DEBUG OUTPUT
      if (lowestScoreForZero && user.getZeroGamesPlayed() != zeroCount) {
	log.log(Level.SEVERE, "Competition "
		+ (competition == null
		   ? "<NONE>" : Integer.toString(competition.getID()))
		+ ", participant " + user.getName()
		+ " has " + user.getZeroGamesPlayed() + " zero games but "
		+ " found " + zeroCount + " zero games",
		new IllegalStateException("mismatching zero games"));
      }

      if (competition != null) {
	if (showBest) {
	  out.td(toString(user.getWScoreBest()), "align=right");
	}
	if (isWeightUsed) {
	  out.td(toString(user.getWScore()), "align=right");
	  double wswz;
	  if (lowestScoreForZero) {
	    double wgames =
	      user.getGamesWPlayed() - user.getZeroGamesWPlayed();
	    if (wgames <= 0) {
	      wswz = 0.0;
	    } else {
	      wswz = (user.getTotalWScore() - wZeroScore) / wgames;
	    }
	  } else {
	    wswz = user.getWScoreWithoutZeroGames();
	  }
	  out.td(toString(wswz), "align=right");
	}
      }
      out.td(toString(user.getAvgScore()), "align=right");
      double aswzg;
      if (lowestScoreForZero) {
	int games = user.getGamesPlayed() - user.getZeroGamesPlayed();
	if (games == 0) {
	  aswzg = 0.0;
	} else {
	  aswzg = (user.getTotalScore() - zeroScore) / games;
	}
      } else {
	aswzg = user.getAvgScoreWithoutZeroGames();
      }
      out.td(toString(aswzg), "align=right");
      out.tableEnd();
      out.text("<font size='-1'><em>The scores were calculated after ")
	.text(user.getGamesPlayed()).text(" games of which ")
	.text(user.getZeroGamesPlayed())
	.text(" resulted in a zero score<br>");
      if (competition != null) {
	if (isWeightUsed) {
	  out.text("WScore = Weighted score, ");
	}
	if (showBest) {
	  out.text("-10 = without the 10 worst games, ");
	}
      }
      out.text("-Zero = without zero score games</em></font><p>\r\n");

      // 10 Worst games (GameID, Score)
      if (lastNumberOfGames > 0) {
	// Only generate table if at least one game has been played
	out.text("<b>The last ").text(lastNumberOfGames)
	  .text(" games played</b><br>");
	out.table("border=1");
	for (int i = 0; i < 4; i++) {
	  out.th("Game").th("Utility").th("Score");
	  if (i % 4 != 3) {
	    out.th("&nbsp;", "bgcolor='#e0e0e0'");
	  }
	}
	out.tr();

	for (int i = 0; i < lastNumberOfGames; i++) {
	  int gameID = result.getGameID(i);
	  double agentScore = result.getScore(i);
	  int flags = result.getFlags(i);
	  if (i % 4 == 0) {
	    out.tr();
	  } else {
	    out.td("&nbsp", "bgcolor='#e0e0e0'");
	  }
	  out.td("<a href='" + urlGamePath + gameID + "/'>" + gameID + "</a>",
		 "align=right")
	    .td(Integer.toString(result.getUtility(i)), "align=right");
	  if (lowestScoreForZero
	      && ((result.getFlags(i) & TACStore.ZERO_GAME) != 0)
	      && agentScore < 0) {
	    out.td("0 (" + toString(agentScore) + ')', "align=right");
	  } else {
	    out.td(toString(agentScore), "align=right");
	  }
	}
	out.tableEnd();
      }
      out.pageEnd();
      out.close();

      // Mark the user that it has a statistics page
      user.setStatisticsFlag(TACUser.STATISTICS_EXISTS);

      if (generateImage) {
	String imageFile =
	  path + File.separatorChar + user.getID() + "_gst.png";
	if (!createImage(imageFile, result)) {
	  log.severe("could not create statistics image for agent "
		     + user.getName());
	}
      }
      return true;
    } catch (Exception e) {
      log.log(Level.SEVERE, "could not create statistics for agent "
	      + user.getName(), e);
      return false;
    }
  }

  private static String toString(double score) {
    return score < 0
      ? ("<font color=red>" + TACFormatter.toString(score) + "</font>")
      : TACFormatter.toString(score);
  }


  /*********************************************************************
   * Test main
   **********************************************************************/

  public static void main(String[] a) {
    TACGameResult tgr = new TACGameResult(0);
    System.out.println("GUI: " + hasGUI);
    for (int i = 0; i < 250; i++) {
      tgr.addGameResult(1, 1, (float) (Math.random() * (5719 + 53) - 53),
			0, 0f, 0);
    }
    createImage("puck_gst.png", tgr);
    System.out.println("GUI: " + hasGUI);
  }

}
