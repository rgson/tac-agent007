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
 * TACGame
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 9 April, 2002
 * Updated : $Date: 2004/05/24 09:58:52 $
 *	     $Revision: 1.2 $
 */

package se.sics.tac.is;

public class TACGame implements java.io.Serializable {

  private static final long serialVersionUID = -2743279501781002233L;

  private int uid;
  private int gameID = -1;
  private long startTime;
  private final int gameLength;
  private final String gameType;

  private int[] participants;
  private int participantNumber;
  private int participantsInGame;

  protected TACGame(int uid, int gameID, String gameType, long startTime,
		    int gameLength, int participantsInGame) {
    this.uid = uid;
    this.gameID = gameID;
    this.gameType = gameType;
    this.startTime = startTime;
    this.gameLength = gameLength;
    this.participantsInGame = participantsInGame;
  }

  void setID(int uid) {
    if (this.uid == -1) {
      this.uid = uid;
    } else {
      throw new IllegalStateException("game already initialized");
    }
  }

  public int getID() {
    return uid;
  }

  public boolean hasGameID() {
    return gameID > 0;
  }

  public int getGameID() {
    return gameID;
  }

  void setGameID(int gameID) {
    if (this.gameID >= 0) {
      throw new IllegalStateException("gameID already set");
    }
    this.gameID = gameID;
  }

  public long getStartTimeMillis() {
    return startTime;
  }

  void setStartTimeMillis(long startTime) {
    if (this.startTime > 0) {
      throw new IllegalStateException("game already initialized");
    }
    this.startTime = startTime;
  }

  public long getEndTimeMillis() {
    return startTime + gameLength;
  }

  public int getGameLength() {
    return gameLength;
  }

  public boolean isReservation() {
    return gameType == null;
  }

  public String getGameType() {
    return gameType;
  }

  public boolean isParticipant(int id) {
    for (int i = 0; i < participantNumber; i++) {
      if (id == participants[i]) {
	return true;
      }
    }
    return false;
  }

  public boolean isEmpty() {
    return participantNumber == 0;
  }

  public boolean isFull() {
    return participantNumber == participantsInGame;
  }

  // Return the total number of participants in this type of game
  // (not the currently joined participants)
  public int getParticipantsInGame() {
    return participantsInGame;
  }

  public int getNumberOfParticipants() {
    return participantNumber;
  }

  public int getParticipant(int index) {
    if (index >= participantNumber)
      return -1;
    return participants[index];
  }

  protected void updateParticipants(TACGame game) {
    if (game.participants == null) {
      this.participants = null;
    } else {
      this.participants = new int[game.participants.length];
      System.arraycopy(game.participants, 0, this.participants, 0,
		       game.participantNumber);
    }
    this.participantNumber = game.participantNumber;
  }

  protected boolean joinGame(int userID) {
    if (participantNumber == participantsInGame) {
      // Game is full
      return false;
    }

    int index = indexOf(participants, 0, participantNumber, userID);
    if (index < 0) {
      if (participants == null) {
	participants = new int[participantsInGame];
//       } else if (participants.length == participantNumber) {
// 	int[] tmp = new int[participantNumber + 8];
// 	System.arraycopy(participants, 0, tmp, 0, participantNumber);
      }
      participants[participantNumber++] = userID;
      return true;
    }
    return false;
  }

  String getFutureGame(boolean force) {
    StringBuffer sb = new StringBuffer().append("0,f");
    if (force) {
      sb.append('f');
    }
    sb.append("2,").append(uid).append(',').append(gameID)
      .append(',').append(gameType)
      .append(',').append(startTime / 1000)
      .append(',').append(gameLength / 1000)
      .append(',').append(participantsInGame);
    for (int j = 0, m = participantNumber; j < m; j++) {
      sb.append(',').append(participants[j]);
    }
    return sb.toString();
  }


  /*********************************************************************
   *
   *********************************************************************/

  private static int indexOf(int[] array, int start, int end, int id) {
    for (int i = start; i < end; i++) {
      if (array[i] == id) {
	return i;
      }
    }
    return -1;
  }

  public static int indexOfUniqID(TACGame[] array, int id) {
    if (array != null) {
      for (int i = 0, n = array.length; i < n; i++) {
	if (array[i].uid == id) {
	  return i;
	}
      }
    }
    return -1;
  }

} // TACGame
