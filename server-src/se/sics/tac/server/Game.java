/**
 * SICS TAC Server
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
 * Game
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-06
 * Updated : $Date: 2004/06/06 11:32:00 $
 *	     $Revision: 1.2 $
 */

package se.sics.tac.server;

public final class Game {

  protected final int id;
  protected int gameID = -1;

  /** The start time for this game in milliseconds */
  protected long startTime;

  /** Length of this game in milliseconds */
  protected int gameLength;

//   protected String comment;
  protected User[] participants;
  private User[] participantCache;
  protected int participantNumber;
  protected final int participantsInGame;

  protected final String gameType;

  public Game(String gameType, int gameLength, int participantsInGame) {
    this(Market.getNextGameUnique(), gameType, gameLength, participantsInGame);
  }

  Game(int uid, String gameType, int gameLength, int participantsInGame) {
    this.id = uid;
    this.gameType = gameType;
    this.gameLength = gameLength;
    this.participantsInGame = participantsInGame;
  }

  public int getID() {
    return id;
  }

  public int getGameID() {
    return gameID;
  }

  void setGameID(int gameID) {
    if (this.gameID >= 0) {
      throw new IllegalStateException("game id already set");
    }
    this.gameID = gameID;
  }

  // NULL indicates reserved time (no game)
  public String getGameType() {
    return gameType;
  }

  public long getStartTime() {
    return startTime;
  }

  void setStartTime(long startTime) {
    if (this.startTime > 0) {
      throw new IllegalStateException("start time already set");
    }
    this.startTime = startTime;
  }

  public long getEndTime() {
    return startTime + gameLength;
  }

  public int getGameLength() {
    return gameLength;
  }

//   void setComment() {
//     this.comment = comment;
//   }

//   public String getComment() {
//     return comment;
//   }

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

  public User getParticipant(int index) {
    if (index < 0 || index >= participantNumber) {
      throw new IndexOutOfBoundsException();
    }
    return participants[index];
  }

  public int indexOfParticipant(User user) {
    for (int i = 0; i < participantNumber; i++) {
      if (participants[i] == user) {
	return i;
      }
    }
    return -1;
  }

  public boolean isParticipant(User user) {
    return indexOfParticipant(user) >= 0;
  }

  public User[] getParticipants() {
    User[] users = this.participantCache;
    if (users == null) {
      synchronized (this) {
	if (participantNumber > 0
	    && ((users = this.participantCache) == null)) {
	  if (participantNumber == participants.length) {
	    users = this.participantCache = participants;
	  } else {
	    users = new User[participantNumber];
	    System.arraycopy(participants, 0, users, 0, participantNumber);
	    this.participantCache = users;
	  }
	}
      }
    }
    return users;
  }

  public synchronized boolean addParticipant(User user) {
    if (user == null) {
      throw new NullPointerException();
    }
    if (participantNumber == participantsInGame) {
      // This game is full and no more users can be added
      // (this function returns false if the user is already added).
      return false;
    } else if (participants == null) {
      participants = new User[participantsInGame];
    } else if (indexOfParticipant(user) >= 0) {
      // Participant already added
      return false;
    }

    participants[participantNumber++] = user;
    participantCache = null;
    return true;
  }

  public StringBuffer toCsv(StringBuffer sb) {
    sb.append(',').append(id).append(',').append(gameID)
      .append(',').append(gameType)
      .append(',').append(startTime / 1000)
      .append(',').append(gameLength / 1000)
      .append(',').append(participantsInGame);
    for (int i = 0; i < participantNumber; i++) {
      sb.append(',').append(participants[i].getID());
    }
    return sb;
  }

} // Game
