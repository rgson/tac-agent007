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
 * SolveServer
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 11 April, 2002
 * Updated : $Date: 2004/06/01 08:51:46 $
 *	     $Revision: 1.2 $
 */

package se.sics.tac.is;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.botbox.util.ArrayQueue;
import se.sics.tac.log.ISTokenizer;
import se.sics.tac.solver.FastOptimizer;
import se.sics.tac.solver.Solver;

public class SolveServer implements Runnable {

  private static final Logger log =
    Logger.getLogger(SolveServer.class.getName());

  private final static boolean DEBUG = true;
  private final InfoServer infoServer;
  private Solver solver = new FastOptimizer();
  private ArrayQueue queue = new ArrayQueue();

  private int calcAID = 0;
  private int calcGID = -1;
  private boolean calcGoods = false;
  private int[][] calcPref = new int[8][6];
  private int calcClientNo = 0;
  private int[][] calcOwn = new int[5][7];

  public SolveServer(InfoServer is) {
    infoServer = is;
    new Thread(this, "solver").start();
  }

  public synchronized void addCommand(ISTokenizer tok) {
    queue.add(tok);
    notify();
  }

  public synchronized ISTokenizer getCommand() throws InterruptedException {
    while (queue.size() == 0) {
      wait();
    }
    return (ISTokenizer) queue.remove(0);
  }

  public void run() {
    while (true) {
      try {
	ISTokenizer tok = getCommand();
	switch (tok.getCommand()) {
	case ISTokenizer.SOLVE_REQUEST:
	  solveGame(tok);
	  break;
	case ISTokenizer.OWN:
	  setOwn(tok);
	  break;
	case ISTokenizer.CLIENT:
	  setClient(tok);
	  break;
	}
	if (calcGoods && (calcClientNo == 8)) {
	  // Should check that all is ok?
	  solver.setClientData(calcPref, calcOwn);
	  int result = solver.solve();
	  infoServer.solveFinished(solver, result, calcGID, calcAID);
	  // Must clean up because clients are received by the solver
	  // even as other games are being played... FIX THIS!!!
	  cleanUp();
	}
      } catch (Exception e) {
	log.log(Level.SEVERE, "could not handle solving", e);
      }
    }
  }

  public void solveGame(ISTokenizer tok) {
    try {
      int gID = tok.nextInt();
      int aID = tok.nextInt();
      cleanUp();
      calcGID = gID;
      calcAID = aID;
    } catch (Exception e) {
      log.log(Level.SEVERE, "could not parse game data:", e);
    }
  }

  private void cleanUp() {
    calcGID = -1;
    calcAID = 0;
    calcClientNo = 0;
    calcGoods = false;
  }

  public void setOwn(ISTokenizer tok) {
    try {
      int gID = tok.nextInt();
      int aID = tok.nextInt();
      if (gID == calcGID && aID == calcAID) {
	if (tok.hasMoreTokens()) {
	  for (int t = 0; t < 7; t++) {
	    for (int d = 0; d < 4; d++) {
	      if (t == 1) // OutFlight is the only one that gets into day 5...
		calcOwn[d + 1][t] = tok.nextInt();
	      else
		calcOwn[d][t] = tok.nextInt();
	    }
	  }
	} else {
	  // Agent had no goods
	  for (int d = 0; d < 5; d++) {
	    for (int j = 0; j < 7; j++) {
	      calcOwn[d][j] = 0;
	    }
	  }
	}
	calcGoods = true;
      }
    } catch (Exception e) {
      log.log(Level.SEVERE, "could not parse own data:", e);
    }
  }

  public void setClient(ISTokenizer tok) {
    try {
      // Must check game id because clients might be recieved when
      // next game starts...
      int gID = tok.nextInt();
      if (gID == calcGID) {
	int aID = tok.nextInt();
	if (aID == calcAID) {
	  if (DEBUG) System.out.println("setting client " + (calcClientNo + 1)
					+ " for calc game: " + gID);
	  while (tok.hasMoreTokens()) {
	    if (calcClientNo < 8) {
	      for (int i = 0; i < 6; i++) {
		calcPref[calcClientNo][i] = tok.nextInt();
	      }
	      calcClientNo++;
	    } else {
	      break;
	    }
	  }
	}
      }
    } catch (Exception e) {
      log.log(Level.SEVERE, "could not parse client data:", e);
    }
  }

} // SolveServer
