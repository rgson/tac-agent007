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
 * Competition
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 9 April, 2002
 * Updated : $Date: 2004/07/11 21:37:53 $
 *	     $Revision: 1.4 $
 */

package se.sics.tac.is;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.logging.*;

public class Competition implements java.io.Serializable {

  private static final Logger log =
    Logger.getLogger(Competition.class.getName());

  private static final long serialVersionUID = -8920630125037980470L;

  public static final int WEEKEND_LOW = 1;
  public static final int NO_WEIGHT = 2;
  public static final int STATIC_WEIGHT = 1 << 2;
  public static final int LOWEST_SCORE_FOR_ZERO = 1 << 6;

  private int id;
  private int parentID;
  private Competition parentCompetition;

  private String name;
  private String description;
  private int startGame;
  private int startGameID;
  private int gameCount;
  private long startTime;
  private long endTime;
  private TACUser[] participants;
  private float startWeight;
  private int flags;
  private String scoreClassName = null;
  private ScoreGenerator scoreGenerator = null;
  private int startDay = -1;

  // FIX THIS!!! should be replaced with better stuff!!!!
  private static boolean forceWeightFlag = false;
  private static float forcedWeight;

  public Competition(int id, int gameCount, TACUser[] participants,
		     String name, String description, float startWeight) {
    this(id, gameCount, participants, name, description, startWeight, null);
  }

  public Competition(int id, int gameCount, TACUser[] participants,
		     String name, String description, float startWeight,
		     String className) {
    if (gameCount <= 0) {
      throw new IllegalArgumentException(gameCount + " games specified");
    }
    this.participants = participants;
    this.description = description;
    this.name = name;
    this.id = id;
    this.gameCount = gameCount;
    this.startWeight = startWeight;
    if (className != null && className.length() > 0) {
      this.scoreClassName = className;
    }
  }

  void setGameInfo(int startGame, long startTime, long endTime) {
    this.startGame = startGame;
    this.startTime = startTime;
    this.endTime = endTime;
  }

  void setStartGameID(int startGameID) {
    this.startGameID = startGameID;
  }

  void setGames(TACGame startGame, TACGame endGame) {
    this.startGame = startGame.getID();
    this.startGameID = startGame.getGameID();
    this.startTime = startGame.getStartTimeMillis();
    this.endTime = endGame.getEndTimeMillis();
  }

  // Returns the game length in minutes and at least 1 minute
  private int getGameLength() {
    int gameLength = (int) (endTime - startTime) / (60000 * gameCount);
    return gameLength <= 0 ? 1 : gameLength;
  }

  public static boolean isWeightForced() {
    return forceWeightFlag;
  }

  public static float getForcedWeight() {
    return forcedWeight;
  }

  public static void setForcedWeight(float weight, boolean force) {
    forcedWeight = weight;
    forceWeightFlag = force;
  }

  public boolean isWeightUsed() {
    return (flags & NO_WEIGHT) == 0;
  }

  public float getWeight(int gameID) {
    if (forceWeightFlag) {
      return forcedWeight;
    }
    if ((flags & NO_WEIGHT) != 0) {
      // No weight for this competition
      return startWeight;
    }
    if ((flags & STATIC_WEIGHT) != 0) {
      // No increasing weights for this competition
      return startWeight;
    }

    // Only produce weight if a start game id has been set
    if (startGameID <= 0) {
      return startWeight;
    }

    int deltaG = gameID - startGameID;
    int daysPassed = deltaG * getGameLength() / (24 * 60);

    if ((flags & WEEKEND_LOW) != 0) {
      if (startDay == -1) {
	TimeZone utc = TimeZone.getTimeZone("UTC");
	GregorianCalendar gc = new GregorianCalendar(utc);
	gc.setTimeInMillis(startTime);
	int dow = gc.get(Calendar.DAY_OF_WEEK);
	startDay = (7 + dow - gc.MONDAY) % 7;
	log.fine("Setting start day to: " + startDay);
      }
      int nowDay = (startDay + daysPassed) % 7;
      if (nowDay >= 5) {
	// If weekend return startweight
	return startWeight;
      }
    }
    return startWeight + daysPassed;
  }

  // Returns the time for next weight update or 0L if no more weight
  // update will be performed for this competition.
  public long getNextWeightUpdate(int gameID) {
    if ((flags & NO_WEIGHT) != 0) {
      return 0L;
    }
    if ((flags & STATIC_WEIGHT) != 0) {
      return 0L;
    }

    // Only produce weight if a start game id has been set
    if (startGameID <= 0) {
      return startTime;
    }

    // Assume 15 minutes per game for now. FIX THIS!!!!!
    int deltaG = gameID - startGameID;
    int gameLength = getGameLength();
    int gamesPerDay = (24 * 60) / gameLength;
    int daysPassed = deltaG * gameLength / (24 * 60);
    int timeToNextDay = gamesPerDay - (deltaG % gamesPerDay);

    if ((flags & WEEKEND_LOW) != 0) {
      if (startDay == -1) {
	TimeZone utc = TimeZone.getTimeZone("UTC");
	GregorianCalendar gc = new GregorianCalendar(utc);
	gc.setTimeInMillis(startTime);
	int dow = gc.get(Calendar.DAY_OF_WEEK);
	startDay = (7 + dow - gc.MONDAY) % 7;
	log.fine("Setting start day to: " + startDay);
      }
      int nowDay = (startDay + daysPassed) % 7;
      if (nowDay == 5) {
	timeToNextDay += gamesPerDay;
      }
    }
    long nextUpdate =
      startTime + (deltaG + timeToNextDay) * gameLength * 60000;
    return nextUpdate > endTime ? 0L : nextUpdate;
  }

//   public static void main(String[] args) {
//     Logger.getLogger("").setLevel(Level.ALL);
//     Competition c = new Competition(1, null, "test", "test", 1.0f);
//     long currentTime = System.currentTimeMillis();
//     long startTime = currentTime - 2 * 3600 * 1000;
//     long endTime = startTime + 2000 * 15 * 60 * 1000;
//     c.setGames(1, 2000, startTime, endTime);
//     c.setGameIDs(1, 2000);
//     c.setFlags(WEEKEND_LOW);
//     for (int i = 1, n = 500; i < n; i += 4) {
//       System.out.println("Weight for game " + i + ": " + c.getWeight(i)
// 			 + " next update at "
// 			 + se.sics.tac.util.GameResultCreator
// 			 .formatServerTimeDate(c.getNextWeightUpdate(i)));
//     }
//   }

  String getScoreClassName() {
    return scoreClassName;
  }

  public ScoreGenerator getScoreGenerator() {
    if (scoreGenerator == null && scoreClassName != null) {
      try {
	scoreGenerator = (ScoreGenerator)
	  Class.forName(scoreClassName).newInstance();
      } catch (Throwable t) {
	log.log(Level.SEVERE, "could not create score generator of type "
		+ scoreClassName, t);
	// Do not try to create the score generator again
	scoreClassName = null;
      }
    }
    return scoreGenerator;
  }

  public float getStartWeight() {
    return startWeight;
  }

  public int getID() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public int getStartGame() {
    return startGame;
  }

  public int getEndGame() {
    return startGame + gameCount - 1;
  }

  public boolean hasGameID() {
    return startGameID > 0;
  }

  public int getStartGameID() {
    return startGameID;
  }

  public int getEndGameID() {
    return startGameID + gameCount - 1;
  }

  public int getGameCount() {
    return gameCount;
  }

  public long getStartTime() {
    return startTime;
  }

  public long getEndTime() {
    return endTime;
  }

  public boolean isGameByUniq(int uniqGameID) {
    return startGame <= uniqGameID
      && uniqGameID < (startGame + gameCount);
  }

  public boolean isGameByID(int gameID) {
    return startGameID <= gameID
      && gameID < (startGameID + gameCount)
      && startGameID > 0;
  }

  public boolean isGame(TACGame game) {
    int gameID = game.getID();
    return startGame <= gameID
      && gameID < (startGame + gameCount);
  }

  public TACUser getParticipant(int aid) {
    int index = TACUser.indexOf(participants, aid);
    return index >= 0 ? participants[index] : null;
  }

  public boolean isParticipant(TACUser user) {
    return TACUser.indexOf(participants, user.getID()) >= 0;
  }

  public TACUser[] getParticipants() {
    return participants;
  }

  public int getFlags() {
    return flags;
  }

  void setFlags(int flags) {
    this.flags = flags;
  }



  // -------------------------------------------------------------------
  // Support for splitted competitions
  // -------------------------------------------------------------------

//   public int getRootID() {
//     return parentCompetition != null
//       ? parentCompetition.getRootID()
//       : id;
//   }

  public boolean hasParentCompetition() {
    return parentID > 0;
  }

  public int getParentCompetitionID() {
    return parentID;
  }

  void setParentCompetitionID(int parentID) {
    this.parentID = parentID;
  }

  public boolean isParentCompetition(Competition competition) {
    if (competition == this) {
      return true;
    }

    if (parentCompetition != null) {
      return parentCompetition.isParentCompetition(competition);
    }
    return false;
  }

  public Competition getParentCompetition() {
    return parentCompetition;
  }

  void setParentCompetition(Competition competition) {
    this.parentCompetition = competition;
  }


  // -------------------------------------------------------------------
  // Utilities
  // -------------------------------------------------------------------

  public static int indexOf(Competition[] array, int id) {
    if (array != null) {
      for (int i = 0, n = array.length; i < n; i++) {
	if (array[i].id == id) {
	  return i;
	}
      }
    }
    return -1;
  }

}
