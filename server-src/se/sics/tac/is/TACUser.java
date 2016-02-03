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
 * TACUser
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 9 April, 2002
 * Updated : $Date: 2004/07/11 21:37:53 $
 *	     $Revision: 1.7 $
 */

package se.sics.tac.is;
import java.util.Comparator;

public class TACUser implements java.io.Serializable {

  public static final Comparator scoreComparator = new Comparator() {
      public int compare(Object o1, Object o2) {
	return (int) (1000 * (((TACUser) o2).getAvgScore() -
			      ((TACUser) o1).getAvgScore()));
      }

      public boolean equals(Object obj) {
	return obj == this;
      }
    };

  public static final Comparator wScoreComparator = new Comparator() {
      public int compare(Object o1, Object o2) {
	return (int) (1000 * (((TACUser) o2).getWScore() -
			      ((TACUser) o1).getWScore()));
      }

      public boolean equals(Object obj) {
	return obj == this;
      }
    };


  private static Comparator wScoreBestComparator;

  public static Comparator getWScoreBestComparator() {
    if (wScoreBestComparator == null) {
      wScoreBestComparator = getWScoreBestComparator(-1);
    }
    return wScoreBestComparator;
  }

  public static Comparator getWScoreBestComparator(final int noNumber) {
    return new Comparator() {
	public int compare(Object o1, Object o2) {
	  if (noNumber > 0) {
	    return (int) (1000 * (((TACUser) o2).getWScoreBest(noNumber) -
				  ((TACUser) o1).getWScoreBest(noNumber)));
	  } else {
	    return (int) (1000 * (((TACUser) o2).getWScoreBest() -
				  ((TACUser) o1).getWScoreBest()));
	  }
	}

	public boolean equals(Object obj) {
	  return obj == this;
	}
      };
  }

  private static final long serialVersionUID = 8815445201062270695L;
  public static final int NO_WORST = 10;
  public static final float PERCENT_WORST = 0.10f;

  public static final int STATISTICS_UNCHECKED = 0;
  public static final int STATISTICS_NON_EXISTENCE = 1;
  public static final int STATISTICS_EXISTS = 2;

  private final int id;
  private String name;
  private String email;
  private String password;
  private double totalScore;
  private double totalWScore;
  private int gamesPlayed;
  private double gamesWPlayed;
  private int zeroGamesPlayed;
  private double zeroGamesWPlayed;
  private int parentID;

  private int[] worstGameID;
  private float[] worstScore;
  private float[] worstWeight;
  private int worstCount = 0;

  private int competitionFlag;

  private transient int statisticsFlag;

  protected TACUser(int id, int parent,
		    String name, String password, String email) {
    if (name == null || password == null) {
      throw new NullPointerException();
    }
    this.id = id;
    this.parentID = parent;
    this.name = name;
    this.password = password;
    // Only use the email if it contains some characters
    if (email != null && email.length() > 0) {
      this.email = email;
    }
  }

  // Creates a copy of the specified user
  protected TACUser(TACUser orig) {
    this.id = orig.id;
    this.parentID = orig.parentID;
    this.name = orig.name;
    this.password = orig.password;
    this.email = orig.email;
  }

  void setUserInfo(String name, String password, String email) {
    if (name == null || password == null) {
      throw new NullPointerException();
    }
    this.name = name;
    this.password = password;
    this.email = email;
  }

  public int getCompetitionFlag() {
    return competitionFlag;
  }

  void setCompetitionFlag(int flag) {
    competitionFlag = flag;
  }

  int getStatisticsFlag() {
    return statisticsFlag;
  }

  void setStatisticsFlag(int flag) {
    this.statisticsFlag = flag;
  }

  void setScore(double totalScore, int totalGames) {
    this.gamesWPlayed = this.gamesPlayed = totalGames;
    this.totalWScore = this.totalScore = totalScore;
  }

  void setScore(double totalScore, int totalGames,
		double totalWScore, double totalWGames) {
    this.gamesPlayed = totalGames;
    this.totalScore = totalScore;
    this.gamesWPlayed = totalWGames;
    this.totalWScore = totalWScore;
  }

  void setZeroGames(int totalZeroGames) {
    this.zeroGamesWPlayed = this.zeroGamesPlayed = totalZeroGames;
  }

  void setZeroGames(int totalZeroGames, double totalWZeroGames) {
    this.zeroGamesPlayed = totalZeroGames;
    this.zeroGamesWPlayed = totalWZeroGames;
  }

  public int getParentID() {
    return parentID;
  }

  void setParentID(int parentID) {
    this.parentID = parentID;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getPassword() {
    return password;
  }

  public int getID() {
    return id;
  }

  public void clearScore() {
    totalScore = 0;
    totalWScore = 0;
    gamesPlayed = 0;
    gamesWPlayed = 0;
    zeroGamesPlayed = 0;
    zeroGamesWPlayed = 0;
    worstCount = 0;
  }

  public void addScore(TACUser user) {
    totalScore += user.totalScore;
    totalWScore += user.totalWScore;
    gamesPlayed += user.gamesPlayed;
    gamesWPlayed += user.gamesWPlayed;
    zeroGamesPlayed += user.zeroGamesPlayed;
    zeroGamesWPlayed += user.zeroGamesWPlayed;
  }

  public void addScore(int gameID, float score, float w, boolean zeroGame) {
    addToWorst(gameID, score, w);
    totalScore += score;
    totalWScore += score * w;
    gamesPlayed++;
    gamesWPlayed += w;
    if (zeroGame) {
      zeroGamesPlayed++;
      zeroGamesWPlayed += w;
    }
  }

  public int getGamesPlayed() {
    return gamesPlayed;
  }

  public double getGamesWPlayed() {
    return gamesWPlayed;
  }

  public int getZeroGamesPlayed() {
    return zeroGamesPlayed;
  }

  public double getZeroGamesWPlayed() {
    return zeroGamesWPlayed;
  }

  public double getTotalScore() {
    return totalScore;
  }

  public double getTotalWScore() {
    return totalWScore;
  }

  public float getAvgScore() {
    return gamesPlayed == 0 ? 0f : (float) (totalScore / gamesPlayed);
  }

  public float getAvgScoreWithoutZeroGames() {
    int games = gamesPlayed - zeroGamesPlayed;
    if (games <= 0) {
      return 0f;
    }
    return (float) (totalScore / games);
  }

  public float getWScore() {
    return gamesWPlayed == 0 ? 0f : (float) (totalWScore / gamesWPlayed);
  }

  public float getWScoreWithoutZeroGames() {
    double wgames = gamesWPlayed - zeroGamesWPlayed;
    if (wgames <= 0) {
      return 0f;
    }
    return (float) (totalWScore / wgames);
  }

  void addToWorst(int gameID, float score, float w) {
    if (worstGameID == null) {
      worstGameID = new int[NO_WORST];
      worstScore = new float[NO_WORST];
      worstWeight = new float[NO_WORST];
    }
    if (worstCount < NO_WORST) {
      worstGameID[worstCount] = gameID;
      worstScore[worstCount] = score;
      worstWeight[worstCount] = w;
      worstCount++;
      swapBest();
    } else if ((w * (score - 3000)) < ((worstScore[0] - 3000)
				       * worstWeight[0])) {
      worstGameID[0] = gameID;
      worstScore[0] = score;
      worstWeight[0] = w;
      swapBest();
    }
  }

  int getWorstGame(int index) {
    if (index >= worstCount) {
      throw new IndexOutOfBoundsException(index + "<=>" + worstCount);
    }
    return worstGameID[index];
  }

  float getWorstScore(int index) {
    if (index >= worstCount) {
      throw new IndexOutOfBoundsException(index + "<=>" + worstCount);
    }
    return worstScore[index];
  }

  float getWorstWeight(int index) {
    if (index >= worstCount) {
      throw new IndexOutOfBoundsException(index + "<=>" + worstCount);
    }
    return worstWeight[index];
  }

  int getWorstCount() {
    return worstCount;
  }


  // Note: assumes that the worst score arrays been created and contains
  // at least one worst value
  private void swapBest() {
    int best = 0;
    float bestScore = (worstScore[best] - 3000) * worstWeight[best];
    for (int i = 1; i < worstCount; i++) {
      if (bestScore < ((worstScore[i] - 3000) * worstWeight[i])) {
	best = i;
	bestScore = (worstScore[i] - 3000) * worstWeight[i];
      }
    }

    if (best > 0) {
      float sc = worstScore[best];
      float w = worstWeight[best];
      int gid = worstGameID[best];
      worstScore[best] = worstScore[0];
      worstWeight[best] = worstWeight[0];
      worstGameID[best] = worstGameID[0];
      worstScore[0] = sc;
      worstWeight[0] = w;
      worstGameID[0] = gid;
    }
  }

  public float getWScoreBest() {
    return getWScoreBest((int) (gamesPlayed * PERCENT_WORST));
  }

  float getWScoreBest(int noRemove) {
    if (noRemove > worstCount) {
      noRemove = worstCount;
    }

    if (noRemove == 0) {
      return getWScore();
    } else {
      double totScore = totalWScore;
      double totGames = gamesWPlayed;

      if (noRemove == worstCount) {
	for (int i = 0; i < noRemove; i++) {
	  totScore -= worstScore[i] * worstWeight[i];
	  totGames -= worstWeight[i];
	}
      } else {
	// Only some of the worst points should be removed
	float[] badScore = new float[worstCount];

	// calculate and copy scores...
	for (int i = 0, n = badScore.length; i < n; i++) {
	  badScore[i] = worstScore[i] * worstWeight[i];
	}

	// Remove each noRemove bad scores from the badScore array
	for (int i = 0; i < noRemove; i++) {
	  int wIndex = -1;
	  float worst = Float.MAX_VALUE;
	  // Find the i:th worst score
	  for (int x = 0, n = badScore.length; x < n; x++) {
	    if (worst > badScore[x]) {
	      worst = badScore[x];
	      wIndex = x;
	    }
	  }

	  // Remove this bad score from score
	  if (wIndex >= 0) {
	    badScore[wIndex] = Float.MAX_VALUE;
	    totScore -= worst;
	    totGames -= worstWeight[wIndex];
	  } else {
	    // We have removed all existing worst values
	    break;
	  }
	}
      }

      return totGames == 0 ? 0f : (float) (totScore / totGames);
    }
  }

  /*********************************************************************
   *
   *********************************************************************/

  public static int indexOf(TACUser[] array, int id) {
    if (array != null) {
      for (int i = 0, n = array.length; i < n; i++) {
	if (array[i].id == id) {
	  return i;
	}
      }
    }
    return -1;
  }

//   public static int indexOf(TACUser[] array, String name) {
//     if (array != null) {
//       for (int i = 0, n = array.length; i < n; i++) {
// 	if (array[i].name.equals(name)) {
// 	  return i;
// 	}
//       }
//     }
//     return -1;
//   }

//   public static void main(String[] arg) {
//     TACUser tu = new TACUser("", "", -11, 0);
//     for (int i = 0; i < 45; i++)
//       tu.addScore(0, i,1.0f);
//     System.out.println("WScoreBest: " + tu.getWScoreBest());
//     System.out.println("WScore: " + tu.getWScore());
//   }

} // TACUser
