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
 * Ticker
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : Mon Nov 11 17:54:12 2002
 * Updated : $Date: 2004/05/04 15:48:18 $
 *           $Revision: 1.1 $
 * Purpose :
 *
 */
package se.sics.tac.server;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

final class Ticker extends TimerTask {

  private static final Logger log = Logger.getLogger(Ticker.class.getName());

  private final Market market;
  private long maxDelay = 0L;

  public Ticker(Market market) {
    this.market = market;
  }

  public void run() {
    maxDelay -= 1000;
    if (maxDelay <= 0) {
      try {
	long currentTime = market.infoManager.getServerTime();
	long nextEarliestTime = market.tickPerformed(currentTime);
	maxDelay = nextEarliestTime - currentTime;
      } catch (Exception e) {
	log.log(Level.SEVERE, "could not perform tick", e);
      }
    }
  }

} // Ticker
