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
 * GameTimer
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-10
 * Updated : $Date: 2004/05/04 15:48:18 $
 *	     $Revision: 1.1 $
 * Purpose :
 *
 */

package se.sics.tac.server;
import java.util.TimerTask;
import java.util.logging.Logger;
import java.util.logging.Level;

public class GameTimer extends TimerTask {

  public static final int CHECK_GAME = 0;
  public static final int INITIALIZE = 1;
  public static final int CHECK_CONNECTIONS = 2;
  protected final TACServer server;
  protected final int type;

  public GameTimer(TACServer server, int type) {
    this.server = server;
    this.type = type;
  }

  public void run() {
    try {
      if (type == CHECK_GAME) {
	server.checkGame();
      } else if (type == CHECK_CONNECTIONS) {
	server.checkAgentConnections();
      } else if (type == INITIALIZE) {
	server.setInitialized();
      } else {
	Logger.global.severe("GameTimer: unknown wakeup type " + type);
      }
    } catch (ThreadDeath e) {
      Logger.global.log(Level.SEVERE, "GameTimer: timer died", e);
      throw e;
    } catch (Throwable e) {
      Logger.global.log(Level.SEVERE, "GameTimer: server could not handle "
			+ type, e);
    }
  }

} // GameTimer
