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
 * ISTokenizer
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 10 April, 2002
 * Updated : $Date: 2004/07/07 15:26:08 $
 *	     $Revision: 1.4 $
 */

package se.sics.tac.log;
import java.util.StringTokenizer;

public class ISTokenizer extends StringTokenizer {

  public final static int AUTOJOIN = ('a' << 8) + 'j';
  /** Used by TAC Applets to subscribe for current game information */
  public final static int SUBSCRIBE = ('g' << 8) + 's';
  /** Used by TAC Applets to unsubscribe (stop listening) on game info */
  public final static int UNSUBSCRIBE = ('g' << 8) + 'x';
  public final static int ALLOCATION_REPORT = ('a' << 8) + 'r';
  public final static int SOLVE_REQUEST = ('s' << 8) + 'r';
  public final static int STATE = ('s' << 8) + 't';
  public final static int USER = ('u' << 8) + 's';
  public final static int EXIT = ('x' << 8) + 't';
  public final static int READY = ('r' << 8) + 'd';
  public final static int REQUEST_NEW_GAME = ('r' << 8) + 'f';
  public final static int REQUEST_REMOVE_GAME = ('r' << 8) + 'r';
  public final static int REQUEST_USER = ('r' << 8) + 'u';
  public final static int RESERVE_GAME_IDS = ('r' << 8) + 'i';
  public final static int REQUEST_LOCK_GAME = ('r' << 8) + 'l';
  public final static int GAME_IDS = ('i' << 8) + 'd';
  public final static int LOGIN = 'i';
  public final static int CREATE_GAME = ('c' << 8) + 'g';
  public final static int JOIN_GAME = ('j' << 8) + 'g';
  public final static int NEW_GAME = 'f';
  public final static int NEW_GAME_2 = ('f' << 8) + '2';
  public final static int FORCE_NEW_GAME_2 = ('f' << 16) + ('f' << 8) + '2';
  public final static int GAME_JOINED = ('g' << 8) + 'j';
  public final static int GAME_LOCKED = ('g' << 8) + 'l';
  public final static int GAME_TYPES = ('g' << 8) + 't';
  public final static int GAME_STARTED = 'g';
  public final static int GAME_ENDED = 'x';
  public final static int VERSION = 'v';
  /** Reported when the TAC server finished processing the log files
      i.e. when the log files contains all information to create the
      web pages.
   */
  public final static int GAME_FINISHED = ('x' << 8) + 'f';
  public final static int GAME_REMOVED = 'r';
  public final static int PING = ('p' << 8) + 'i';
  public final static int PONG = ('p' << 8) + 'o';
  public final static int AUCTION = 'u';
  public final static int AUCTION_CLOSED = 'z';
  public final static int AGENT = 'a';
  public final static int CLIENT = 'c';
  public final static int SEED = 'd';
  public final static int IDENTITY = 'i';
  public final static int BID = 'b';
  public final static int QUOTE = 'q';
  public final static int TRANSACTION = 't';
  public final static int SCORE = 's';
  public final static int OWN = 'o';
  public final static int ALLOCATION = 'l';
  public final static int ERROR = 'e';
  public final static int CHAT_MESSAGE = 'm';

  private String message;
  private String serverTimeAsString;
  private long serverTime;
  private int command = 0;
  private String commandString;

//   private String[] tokens = null;
//   private int numberOfTokens = 0;
//   private int currentToken = 0;

  public ISTokenizer(String message) {
    super(message, ",");
    this.message = message;
    serverTimeAsString = super.nextToken();

    commandString = super.nextToken();
    for (int i = 0, n = commandString.length(); i < n; i++) {
      command = (command << 8) + commandString.charAt(i);
    }
  }

  public String getMessage() {
    return message;
  }

//   public void reset() {
//     currentToken = 0;
//   }

//   public boolean hasMoreTokens() {
//     return currentToken < numberOfTokens
//       || super.hasMoreTokens();
//   }

//   public String nextToken() {
//     if (currentToken < numberOfTokens) {
//       return tokens[currentToken++];
//     }

//     String token = super.nextToken();
//     if (tokens == null) {
//       tokens = new String[10];
//     } else if (numberOfTokens == tokens.length) {
//       String[] tmp = new String[numberOfTokens + 10];
//       System.arraycopy(tokens, 0, tmp, 0, numberOfTokens);
//       tokens = tmp;
//     }
//     tokens[numberOfTokens++] = token;
//     currentToken = numberOfTokens;
//     return token;
//   }

//   public String nextToken(String delim) {
//     return nextToken();
//   }

//   public boolean hasMoreElements() {
//     return hasMoreTokens();
//   }

//   public Object nextElement() {
//     return nextToken();
//   }

//   public int countTokens() {
//     return (numberOfTokens - currentToken) + super.countTokens();
//   }

  public int getCommand() {
    return command;
  }

  public String getCommandAsString() {
    return commandString;
  }

  public long getServerTimeSeconds() {
    if (serverTimeAsString == null) {
      return serverTime;
    }

    try {
      serverTime = Long.parseLong(serverTimeAsString);
      serverTimeAsString = null;
      return serverTime;
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("ISTokenizer: could not parse server time '"
			 + serverTime + "' (using current time)");
      return System.currentTimeMillis() / 1000;
    }
  }

  public int nextAgentID() {
    String id = nextToken();
    if ("auction".equals(id)) {
      // Not an agent
      return Integer.MIN_VALUE;
    } else {
      try {
	return Integer.parseInt(id);
      } catch (Exception e) {
	// Not an agent
	e.printStackTrace();
	return Integer.MIN_VALUE;
      }
    }
  }

  public long nextTimeMillis() throws NumberFormatException {
    return Long.parseLong(nextToken().trim()) * 1000;
  }

  public int nextInt() throws NumberFormatException {
    return Integer.parseInt(nextToken().trim());
  }

  public long nextLong() throws NumberFormatException {
    return Long.parseLong(nextToken().trim());
  }

  public float nextFloat() throws NumberFormatException {
    return Float.parseFloat(nextToken().trim());
  }


//   public static void main(String[] args) {
//     ISTokenizer tok = new ISTokenizer(args[0]);
//     System.out.println("Parsing '" + args[0] + '\'');
//     System.out.println("Command: " + tok.getCommand() + "  time: "
// 		       + tok.getServerTimeSeconds());
//     while (tok.hasMoreTokens()) {
//       System.out.println("TOKEN: '" + tok.nextToken() + '\'');
//     }
//     System.out.println("no more tokens");
//   }

} // ISTokenizer
