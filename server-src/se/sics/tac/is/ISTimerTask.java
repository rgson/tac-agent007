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
 * ISTimerTask
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 17 April, 2002
 * Updated : $Date: 2004/05/04 15:48:17 $
 *	     $Revision: 1.1 $
 */

package se.sics.tac.is;
import java.util.TimerTask;
import java.util.logging.*;

public class ISTimerTask extends TimerTask {

  private static final Logger log =
    Logger.getLogger(ISTimerTask.class.getName());

  private final InfoServer infoServer;

  // Used to check if the "server" is dead
  private long lastPong = System.currentTimeMillis();

  ISTimerTask(InfoServer server) {
    infoServer = server;
  }

  public void run() {
    checkPong();
  }

  void pong() {
    lastPong = System.currentTimeMillis();
  }

  private void checkPong() {
    long time = System.currentTimeMillis();
    if (time > (lastPong + 30000)) {
      log.severe("forcing reconnection to the server after "
		 + ((time - lastPong) / 1000) + " seconds");
      infoServer.reconnect();
      pong();
    } else if (time > (lastPong + 10000)) {
      infoServer.sendPing();
    }
  }

} // ISTimerTask
