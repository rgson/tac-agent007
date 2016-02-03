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
 * TACGameResult
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 15 May, 2002
 * Updated : $Date: 2004/07/05 11:54:23 $
 *	     $Revision: 1.2 $
 */

package se.sics.tac.is;
import com.botbox.util.ArrayUtils;

public class TACGameResult {

  private final int agentID;

  private int[] gameID;
  private int[] utility;
  private float[] score;
  private int[] penalty;
  private float[] weight;
  private int[] flags;
  private int numberOfGames;
  private boolean reversed;

  public TACGameResult(int agentID) {
    this(agentID, 10, false);
  }

  public TACGameResult(int agentID, int startSize) {
    this(agentID, startSize, false);
  }

  public TACGameResult(int agentID, int startSize, boolean reversed) {
    this.agentID = agentID;
    this.reversed = reversed;
    ensureCapacity(startSize);
  }

  public void addGameResult(int gameID, int utility, float score,
			    int penalty, float weight, int flags) {
    ensureCapacity(numberOfGames);
    this.gameID[numberOfGames] = gameID;
    this.utility[numberOfGames] = utility;
    this.score[numberOfGames] = score;
    this.penalty[numberOfGames] = penalty;
    this.weight[numberOfGames] = weight;
    this.flags[numberOfGames] = flags;
    numberOfGames++;
  }

  private void ensureCapacity(int size) {
    if (gameID == null) {
      gameID = new int[size];
      utility = new int[size];
      score = new float[size];
      penalty = new int[size];
      weight = new float[size];
      flags = new int[size];
    } else if (gameID.length >= size) {
      gameID = ArrayUtils.setSize(gameID, size + 20);
      utility = ArrayUtils.setSize(utility, size + 20);
      score = ArrayUtils.setSize(score, size + 20);
      penalty = ArrayUtils.setSize(penalty, size + 20);
      weight = ArrayUtils.setSize(weight, size + 20);
      flags = ArrayUtils.setSize(flags, size + 20);
    }
  }

  public int getNumberOfGames() {
    return numberOfGames;
  }

  public int getGameID(int index) {
    return gameID[reversed ? (numberOfGames - index - 1) : index];
  }

  public int getUtility(int index) {
    return utility[reversed ? (numberOfGames - index - 1) : index];
  }

  public float getScore(int index) {
    return score[reversed ? (numberOfGames - index - 1) : index];
  }

  public int getPenalty(int index) {
    return penalty[reversed ? (numberOfGames - index - 1) : index];
  }

  public float getWeight(int index) {
    return weight[reversed ? (numberOfGames - index - 1) : index];
  }

  public int getFlags(int index) {
    return flags[reversed ? (numberOfGames - index - 1) : index];
  }

} // TACGameResult
