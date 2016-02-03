/**
 * SICS TAC Server
 * http://www.sics.se/tac/    tac-dev@sics.se
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
 * TACException
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : Wed Nov 13 16:23:51 2002
 * Updated : $Date: 2004/05/04 15:48:18 $
 *           $Revision: 1.1 $
 * Purpose :
 *
 */
package se.sics.tac.server;

public class TACException extends Exception {

  /** Command status */
  public final static int NO_ERROR = 0;
  public final static int INTERNAL_ERROR = 1;
  public final static int AGENT_NOT_AUTH = 2;
  public final static int GAME_NOT_FOUND = 4;
  public final static int NOT_MEMBER_OF_GAME = 5;
  public final static int GAME_FUTURE = 7;
  public final static int GAME_COMPLETE = 10;
  public final static int AUCTION_NOT_FOUND = 11;
  public final static int AUCTION_CLOSED = 12;
  public final static int BID_NOT_FOUND = 13;
  public final static int TRANS_NOT_FOUND = 14;
  public final static int CANNOT_WITHDRAW_BID = 15;
  public final static int BAD_BIDSTRING_FORMAT = 16;
  public final static int NOT_SUPPORTED = 17;
  /** Extension in 1.1 */
  public final static int GAME_TYPE_NOT_SUPPORTED = 18;

  private final static String[] statusName = {
    "no error",
    "internal error",
    "agent not auth",
    "game not found",
    "not member of game",
    "game future",
    "game complete",
    "auction not found",
    "auction closed",
    "bid not found",
    "trans not found",
    "cannot withdraw bid",
    "bad bidstring format",
    "not supported",
    "game type not supported"
  };
  private final static int[] statusCodes = {
    NO_ERROR,
    INTERNAL_ERROR,
    AGENT_NOT_AUTH,
    GAME_NOT_FOUND,
    NOT_MEMBER_OF_GAME,
    GAME_FUTURE,
    GAME_COMPLETE,
    AUCTION_NOT_FOUND,
    AUCTION_CLOSED,
    BID_NOT_FOUND,
    TRANS_NOT_FOUND,
    CANNOT_WITHDRAW_BID,
    BAD_BIDSTRING_FORMAT,
    NOT_SUPPORTED,
    GAME_TYPE_NOT_SUPPORTED
  };

  private final int statusCode;

  public TACException(int statusCode) {
    this.statusCode = statusCode;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getMessage() {
    return commandStatusToString(statusCode);
  }

  public static String commandStatusToString(int status) {
    for (int i = 0, n = statusCodes.length; i < n; i++) {
      if (statusCodes[i] == status) {
	return statusName[i];
      }
    }
    return Integer.toString(status);
  }

} // TACException
