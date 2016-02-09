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
 * PriceSolver
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-26
 * Updated : $Date: 2004/05/04 15:48:18 $
 *	     $Revision: 1.1 $
 * Purpose :
 *
 */

package se.sics.tac.solver;

public interface PriceSolver {

  // SUP means that it is not possible to buy anything at all...
  public static final int SUP = 1000000;

  public boolean isSolving();

  /**
   * Start the solver
   *
   * @param listener the listener on solver reports
   * @exception throws IllegalStateException if already solving
   * @return true if the solver searched the full tree and false if it was
   *	cancelled
   */
  public boolean startSolver(SolveListener solveListener,
			     // [Client][InDay,OutDay,Hotel,E1,E2,E3]
			     int[][] preferences,
			     // [ItemType][Day]
			     int[][] own,
			     // [Auction][PriceVector (0-8)]
			     int[][] prices);

  public boolean stopSolver(boolean waitForSolver);

} // PriceSolver
