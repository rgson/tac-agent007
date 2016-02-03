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
 * FastOptimizer
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 24 October, 2001
 * Updated : $Date: 2004/07/08 12:30:20 $
 *	     $Revision: 1.2 $
 * Purpose : Simple Branch and Bound optimizer for TAC games
 *
 *
 * Branch and Bound optimizer for TAC games
 * Example from game: 5716 with score 9324
 * alloc
 * for each client a byte containing:
 * bit 0-4 -> stays respective day.
 * after in_flight -> the in_fligt bit is set
 * after out_flight -> the bits between in_fligth & out_flight are set
 * Measurement at home (joakime)
 * Version ok bnb 2001-11-10 ->  (Which is 30% of the work the first had)
 * Score 9324, bnbs = 9145672, time = 12790, allocs = 1018
 *             [scheutz time = 46099]
 * Optimized 1 [2001-11-11] -> (Should be 15-18% of the first version)
 * Score 9324, bnbs = not_comparable (4910242), time = 6540, allocs = 1018
 *             [scheutz time = 29933]
 *
 */

package se.sics.tac.solver;
import java.util.logging.Logger;

public class FastOptimizer implements Solver {

  private final static String EOL = System.getProperty("line.separator",
						       "\r\n");

  private static final Logger log =
    Logger.getLogger(FastOptimizer.class.getName());

  private static final boolean DEBUG = false;

  private static final int MAX_FLIGHT_POS = 8;
  private static final int MAX_ALLOC_POS = 2 * 8;
  private static final int[] MAX_VAL = { 5, 4, 2, 5, 5, 5};
  private static final int NO_STAY = 4;
  private static final int NO_ENTERTAINMENT = 4;

  private static final int IN_FLIGHT = 0;
  private static final int OUT_FLIGHT = 1;
  private static final int HOTEL = 2;
  private static final int E1 = 3;
  private static final int E2 = 4;
  private static final int E3 = 5;

  private static final int HOTEL_ALLOC_POS = 6;
  private static final int HOTEL_MASK = 1 << HOTEL_ALLOC_POS; // = 0x40
  private static final int ENTER_ALLOC_POS = 0;

  private static final int ENTER1_BIT = 4;
  private static final int ENTER2_BIT = 5;
  private static final int ENTER3_BIT = 6;
  private static final int ENTER1_FLAG_MASK = 1 << ENTER1_BIT;
  private static final int ENTER2_FLAG_MASK = 1 << (1 + ENTER1_BIT);

  private static final int IN_FLIGHT_POS = 0;
  private static final int OUT_FLIGHT_POS = 16;
  private static final int GOOD_POS = 32;
  private static final int CHEAP_POS = 48;

  // Sorted just to test...
  // This game took 35 minutes on tac2 (800 Mhz) and around 17 minutes
  // On my 1.8 Ghz (bnb: 1428411567). Score shold be 9550.
  // With enter min optimization it takes around 50 seconds (bnb: 94907114).
  // Addition optimization with EnterMin gives
  /*int preferences[][] = new int[][] {
    {2,2,55,193,180,111},
    {2,2,110,68,193,148},
    {0,1,112,67,149,177},
    {1,1,69,87,142,189},
    {1,1,87,74,72,167},
    {1,2,72,77,78,37},
    {1,2,67,78,154,67},
    {0,2,140,141,3,23}
  };
  */

  int preferences[][] = new int[][] {{1,2,72,77,78,37},
				     {2,2,55,193,180,111},
				     {0,1,112,67,149,177},
				     {1,1,87,74,72,167},
				     {2,2,110,68,193,148},
				     {1,1,69,87,142,189},
				     {1,2,67,78,154,67},
				     {0,2,140,141,3,23}
  };

  int own[][] = new int[][] {{2,4,2,0},
			     {0,3,5,0},
			     {7,3,2,0},
			     {9,3,3,0},
			     {1,1,2,2},
			     {1,0,0,4},
			     {1,1,4,0}
  };

  // Client preferences (fligth, hotel g/c & entertainment 1, 2, 3)
  /*
  int preferences[][] = new int[][] { { 1, 1, 94, 75, 174, 161},
				      { 3, 3, 68, 174, 13, 43},
				      { 2, 3, 122, 175, 189, 187},
				      { 2, 3, 126, 77, 181, 79},
				      { 2, 2, 138, 135, 198, 12},
				      { 0, 3, 109, 13, 48, 17},
				      { 1, 3, 92, 84, 147, 103},
				      { 2, 3, 148, 94, 10, 1} };
  */
  int bestInFlight[][] = new int[][] { { 0, 1, 2, 3},
				       { 1, 0, 2, 3},
				       { 2, 1, 3, 0},
				       { 3, 2, 1, 0} };

  int bestOutFlight[][] = new int[][] { { 0, 1, 2, 3},
					{ 1, 2, 0, 3},
					{ 2, 3, 1, 0},
					{ 3, 2, 1, 0} };

  // What we own
  /*
  int own[][] = new int[][]  { { 1, 2, 4, 1},
			       { 1, 2, 1, 4},
			       { 1, 1, 4, 5},
			       { 0, 1, 0, 3},
			       { 0, 0, 0, 1},
			       { 0, 1, 1, 1},
			       { 3, 0, 1, 2}};
  */
  int enterMin[] = new int[3];
  int enterMax[] = new int[3];
  int scoreClient[] = new int[8];

  // From this the allocation can be extracted
  // Should be a better interface later...
  public long finalAlloc = 0;
  public long finalStay = 0;
  int bound = 0;

  // 8 bits/client: 4 bits stay, 3 bits entertainment
  long newStay = 0;
  // 8 bits/client: 1 bit good hotel, 2 bits/entertainment day per type,
  long newAlloc = 0;
  long newStuff1 = 0;
  long newStuff2 = 0;

  long allocs = 0L;
  long bnbs = 0L;
  long startTime = 0L;
  long calculationTime = -1L;

  int calcBestScore() {
    int score = 0;
    int arr[] = new int[8];
    for (int type = 0; type < 3; type++) {
      //System.arraycopy(entertainment[type], 0, arr, 0, 8);
      // Copy the clients preferences value for a specific entertainment type

      for (int i = 0; i < 8; i++) {
	arr[i] = preferences[i][3 + type];
// 	System.out.print(arr[i] + " ");
      }
//       System.out.println("");

      // Sum the number of entertainments of a specific type that
      // the agents owns ignoring client and day

      int sum = 0;
      for (int i = 0; i < 4; i++) {
	sum += own[4 + type][i];
// 	System.out.print(own[4 + type][i] + " ");
      }
//       System.out.println("");

      int max = -1, maxPos;
      for (int nr = 0; nr < sum; nr++) {
	maxPos = 0;
        max = 0;
	for (int i = 0; i < 8; i++) {
	  if (arr[i] > max) {
	    maxPos = i;
	    max = arr[i];
	  }
	}
// 	System.out.println("Found max: " + max + " type=" + type +
// 			   " nr=" + nr);
	score += max;
	arr[maxPos] = 0;
      }
      enterMin[type] = max;

      // This is to get the enterMax to be as low as possible... should
      // be correct since the other version of enter min is using one to
      // high (the score of the lowest of the clients that should get E).
      max = -1;
      for (int i = 0; i < 8; i++) {
	if (arr[i] > max) {
	  max = arr[i];
	}
      }
      enterMax[type] = max;
    }

    for (int i = 0; i < 8; i++) {
      score += (scoreClient[i] = 1000 + preferences[i][HOTEL]);
    }
    return score;
  }

  void bnb(int hscore, long stay, long alloc, int allocPos,
	   long stuff1, long stuff2, int flights) {
//      System.out.println("Alloc: " + allocPos + " Max: " + MAX_ALLOC_POS);
    bnbs++;
    if (allocPos >= MAX_ALLOC_POS) {
      int inf, outf, a, a2;
      int score = 0;
      int tmp1, tmp2;
      int[] prefs;
      allocs++;
      for (int i = 0; i < 8; i++) {
	a = (int) ((stay >> (i * 8)) & 0xff);
	if (a != 0) {
	  inf = (int) ((flights >> (i * 4) + 2) & 3) + 1;
	  outf = (int) ((flights >> (i * 4)) & 3) + 2;

	  prefs = preferences[i];
	  score += 1000
	    - ((((tmp1 = inf - 1 - prefs[0]) >= 0) ? tmp1 : -tmp1) +
	       (((tmp2 = outf - 2 - prefs[1]) >= 0) ? tmp2 : -tmp2))
	    * 100;
	  a2 = (int) ((alloc >> i * 8) & 0xff);
	  if ((a2 & HOTEL_MASK) > 0) {
	    score += prefs[HOTEL]; //hotel[i];
	  }
	  for (int e = 0; e < 3; e++) {
	    if ((a & (0x10 << e)) > 0) {
	      score += prefs[E1 + e];
	    }
	  }
	}
      }

      if (score > bound) {
	bound = score;
	finalAlloc = alloc;
	finalStay = stay;
	log.finest("New Bound: " + bound + ", h = " + hscore +
		   " time = " + (System.currentTimeMillis() - startTime));
	if (DEBUG) {
	  StringBuffer sb = new StringBuffer().append(EOL);
	  setLatestAlloc(sb, stay, alloc);
	  log.finest(sb.toString());
	}
      }
    } else if (allocPos < MAX_FLIGHT_POS) {
      // Allocate inflight for this client
      int user = allocPos;
      int user8 = user * 8;
      int[] prefs = preferences[user];
      int prefO = prefs[1];
      int prefI = prefs[0];
      int newScore;
      for (int i = 0; i < 4; i++) {
	int inday = bestInFlight[prefI][i];
	// Check if flight exists this day
	int inF = (int) (stuff1 >> (IN_FLIGHT_POS + inday * 4)) & 0xf;
	if (inF > 0) {
	  int inScore;
	  if (inday > prefI) {
	    inScore = hscore - (inday - prefI) * 100;
	  } else {
	    inScore = hscore - (prefI - inday) * 100;
	  }

	  if (inScore > bound) {
	    for (int j = 0; j < 4; j++) {
	      int outday = bestOutFlight[prefO][j];
	      int outF = (int) (stuff1 >> (OUT_FLIGHT_POS + outday * 4)) & 0xf;
	      if (outF > 0 && outday >= inday) {
		// We have an ok stay !!! Update variables!!!
		int outScore;
		if (outday > prefO) {
		  outScore = inScore - (outday - prefO) * 100;
		} else {
		  outScore = inScore - (prefO - outday) * 100;
		}
		if (outScore > bound) {
		  long bits = 0L;
		  for (int b = 1 << outday, min = 1 << inday;
		       b >= min; b = (b >> 1)) {
		    bits |= b;
		  }
		  long newStay = stay | (bits << user8);
		  long outStuff1 = stuff1
		    - (1L << (OUT_FLIGHT_POS + outday * 4))
		    - (1L << (IN_FLIGHT_POS + inday * 4));
		  int newFlights = flights
		    | (inday << (user * 4 + 2))
		    | (outday << (user * 4));

		outflight:
		  for (int h = 0; h < 2; h++) {
		    long newStuff1 = outStuff1;
		    int hotelPos = (h == 0) ? GOOD_POS : CHEAP_POS;
		    for (int d = inday; d <= outday; d++) {
		      if (((stuff1 >> (hotelPos + d * 4)) & 0xf) == 0) {
			// No hotel => can not stay this nights so we should
			// try next hotel type in this allocation.
			continue outflight;
		      } else {
			newStuff1 -= (1L << (hotelPos + d * 4));
		      }
		    }

		    if (hotelPos == GOOD_POS) {
		      newAlloc = alloc | (1L << (HOTEL_ALLOC_POS + user8));
		      newScore = outScore;
		    } else {
		      newScore = outScore - prefs[HOTEL];
		      newAlloc = alloc;
		    }
		    if (newScore > bound) {
		      // Inling the entertainment stuff here!!!
		      bnb(newScore, newStay,
			  newAlloc, allocPos + 1,
			  newStuff1, stuff2, newFlights);
		    }
		  }
		}
	      }
	    }
	    // Else this cant be an assignment...
	  }
	}
      }

      // this client does not go... is this correct?? CHECK THIS
      newScore = hscore - scoreClient[user];
      // This is possible but probably cost more than it tastes...
//  	if (newScore > bound) {
//  	if (enterMin[0] >= 0 && prefs[3] > enterMin[0]) {
//  	  newScore -= (prefs[3] - enterMin[0]);
//  	}
//  	if (enterMin[1] >= 0 && prefs[4] > enterMin[1]) {
//  	  newScore -= (prefs[4] - enterMin[1]);
//  	}
//  	if (enterMin[2] >= 0 && prefs[5] > enterMin[2]) {
//  	  newScore -= (prefs[5] - enterMin[2]);
//  	}
      if (newScore > bound) {
	bnb(newScore, stay, alloc, allocPos + 1,
	    stuff1, stuff2, flights);
      }
//        }

    } else {
      // Entertainment handling after all clients has allocated
      // flighs and hotels

      int user = (allocPos - MAX_FLIGHT_POS);
      int user8 = user * 8;

      int a = (int) ((stay >> user8) & 0xff);
      if (a == 0) {
	// client is not going => continue with next client
	int newScore = hscore;
	// This is possible but probably cost more than it tastes...
	int[] prefs = preferences[user];
	if (enterMin[0] >= 0 && prefs[3] > enterMin[0]) {
	  newScore -= (prefs[3] - enterMax[0]);
	}
	if (enterMin[1] >= 0 && prefs[4] > enterMin[1]) {
	  newScore -= (prefs[4] - enterMax[1]);
	}
	if (enterMin[2] >= 0 && prefs[5] > enterMin[2]) {
	  newScore -= (prefs[5] - enterMax[2]);
	}
	if (newScore > bound) {
	  bnb(newScore, stay, alloc, allocPos + 1, stuff1, stuff2, flights);
	}

      } else {
	int user4 = user * 4;
	int inf = (int) ((flights >> user4 + 2) & 3);
	int outf = (int) ((flights >> user4) & 3);
	int[] prefs = preferences[user];
	int e1score, e2score, newScore;
	int aPos = ENTER_ALLOC_POS + user8;

//  	System.out.println("client " + (user + 1) + " in=" + (inf + 1)
//  			   + " out=" + (outf + 2) + " alloc=" + allocPos
//  			   + " locs=" + allocs + " score=" + hscore
//  			   + " bounds=" + bound);

	// E1
	int start = inf, stop = outf + 1;

	// Check before e1d loop
	if ((prefs[3] < enterMin[0])
	    && ((hscore - (enterMax[0] - prefs[3])) <= bound)) {
	  start = stop;
	}
	for (int e1d = start; e1d <= stop; e1d++) {
	  // Assign entertainment day val
	  if ((e1d == stop) || (((stuff2 >> (e1d * 4)) & 0xf) != 0)) {
	    if (e1d < stop) {
	      if (prefs[3] < enterMin[0]) {
		// Take one from a high! - high has lowered...
		// but still maybe not enterMax?
		e1score = hscore - enterMax[0] + prefs[3];
	      } else {
		// I am high!
		e1score = hscore;
	      }
	    } else if (enterMin[0] >= 0 && prefs[3] >= enterMin[0]) {
	      // No Ent - I am high - remove me and add highest of others
	      e1score = hscore - prefs[3] + enterMax[0];
	    } else {
	      // No Ent - I am low...
	      e1score = hscore;
	    }
	    if (e1score > bound) {
	      for (int e2d = inf; e2d <= stop; e2d++) {
		int ent = (int) (stuff2 >> (16 + e2d * 4)) & 0xf;
		if (((ent != 0) && (e1d != e2d)) || (e2d == stop)) {
		  if (e2d < stop) {
		    if (prefs[4] < enterMin[1]) {
		      // Take one from a high! - high has lowered already...
		      // but still maybe not enterMax?
		      e2score = e1score - enterMax[1] + prefs[4];
		    } else {
		      // I am high!
		      e2score = e1score;
		    }
		  } else if (enterMin[1] >= 0 && prefs[4] >= enterMin[1]) {
		    // No Ent - I am high - remove me and add highest of others
		    e2score = e1score - prefs[4] + enterMax[1];
		  } else {
		    // No Ent - I am low...
		    e2score = e1score;
		  }

		  if (e2score > bound) {
		    for (int e3d = inf; e3d <= stop; e3d++) {
		      ent = (int) (stuff2 >> (32 + e3d * 4)) & 0xf;
		      if (((ent != 0) && (e1d != e3d) && (e2d != e3d))
			  || (e3d == stop)) {
			// Calc score
			if (e3d < stop) {
			  if (prefs[5] < enterMin[2]) {
			    // Take one from a high! - high has lowered...
			    newScore = e2score - enterMax[2] + prefs[5];
			  } else {
			    // I am high!
			    newScore = e2score;
			  }
			} else if (enterMin[2] >= 0 &&
				   prefs[5] >= enterMin[2]) {
			  // No Ent - high - remove me, add highest of others
			  newScore = e2score - prefs[5] + enterMax[2];
			} else {
			  // No Ent - I am low...
			  newScore = e2score;
			}

			if (newScore > bound) {
			  newStay = stay;
			  newStuff2 = stuff2;
			  newAlloc = alloc;
			  if (e1d < stop) {
			    newStay |= (1L << (user8 + ENTER1_BIT));
			    newAlloc |= (((long) e1d) << (aPos));
			    newStuff2 -= (1L << (e1d * 4));
			  }
			  if (e2d < stop) {
			    newStay |= (1L << (user8 + ENTER2_BIT));
			    newAlloc |= (((long) e2d) << (aPos + 2));
			    newStuff2 -= (1L << (16 + e2d * 4));
			  }
			  if (e3d < stop) {
			    newStay |= (1L << (user8 + ENTER3_BIT));
			    newAlloc |= (((long) e3d) << (aPos + 4));
			    newStuff2 -= (1L << (32 + e3d * 4));
			  }

			  bnb(newScore, newStay, newAlloc, allocPos + 1,
			      stuff1, newStuff2, flights);
			}
		      }
		    }
		  }
		}
	      }
	    }
	  }
	}
      }
    }
  }

  public int solve() {
    int startScore = calcBestScore();
    log.finest("Best Util: " + startScore);
    bound = 0;
    finalAlloc = allocs = 0L;
    finalStay = 0L;
    bnbs = 0L;
    calculationTime = -1L;

    long stuff1 = 0;
    long stuff2 = 0;
    // inflight day1-4 -> bit 0-4 *
    for (int i = 0; i < 4; i++) {
      stuff1 |= ((long) own[0][i]) << i * 4;
      stuff1 |= ((long) own[1][i]) << (i * 4 + 16);
      stuff1 |= ((long) own[2][i]) << (i * 4 + 32);
      stuff1 |= ((long) own[3][i]) << (i * 4 + 48);

      stuff2 |= ((long) own[4][i]) << i * 4;
      stuff2 |= ((long) own[5][i]) << (i * 4 + 16);
      stuff2 |= ((long) own[6][i]) << (i * 4 + 32);
    }
//      System.out.println("Stuff2: " + stuff2);
    startTime = System.currentTimeMillis();
    bnb(startScore, 0, 0L, 0, stuff1, stuff2, 0);

    StringBuffer sb = new StringBuffer();
    sb.append(EOL)
      .append("-------------------------------------------")
      .append(EOL)
      .append("Final score").append(EOL)
      .append("-------------------------------------------")
      .append(EOL);
    // THIS METHOD MUST BE CALLED BEFORE RETURNING BECAUSE IT WILL SET
    // LATESTALLOC!!!!
    setLatestAlloc(sb, finalStay, finalAlloc);
    calculationTime = (System.currentTimeMillis() - startTime);
    sb.append("-------------------------------------------")
      .append(EOL)
      .append("Time: ").append(calculationTime)
      .append("        \tAllocs: ").append(allocs).append(EOL)
      .append("Bnbs: ").append(bnbs).append("    \tBound: ").append(bound)
      .append(EOL)
      .append("-------------------------------------------");
    log.fine(sb.toString());
    startTime = 0L;
    return bound;
  }

  // This will store the latest allocation
  int[][] latestAlloc = new int[8][6];
  public int[][] getLatestAllocation() {
    return latestAlloc;
  }

  public long getCalculationTime() {
    return calculationTime;
  }

  private void setLatestAlloc(StringBuffer sb, long stay, long alloc) {
    int day;
    int total = 0;
    int[] clientAlloc;
    for (int i = 0; i < 8; i++) {
      int inf = -1;
      int outf = -1;
      int a = (int) ((stay >> (i * 8)) & 0xff);
      clientAlloc = latestAlloc[i];

      // This could actually be an 16 bytes array !!!!
      for (int f = 0; f < 4; f++) {
	if (((a >> f) & 1) == 1  && inf == -1) {
	  inf = f + 1;
	}
	if (((a >> f) & 1) == 0 && inf != -1 && outf == -1) {
	  outf = f + 1;
	}
      }
      if (outf == -1)
	outf = 5;

      int a2 = (int) (alloc  >> i * 8) & 0xff;
      if (inf != -1) {
	clientAlloc[0] = inf;
	clientAlloc[1] = outf;

	int score = 1000 - Math.abs(inf - 1 - preferences[i][0]) * 100 +
	  - Math.abs(outf - 2 - preferences[i][1]) * 100;
	sb.append("Client ").append(i + 1).append(" stays ")
	  .append(inf).append(" - ").append(outf);
	if ((a2 & HOTEL_MASK) > 0) {
	  sb.append(" on good hotel  ");
	  score += preferences[i][HOTEL];
	  clientAlloc[2] = 1;
	} else {
	  sb.append(" on cheap hotel ");
	  clientAlloc[2] = 0;
	}

	if ((a & 0x10) > 0) {
	  sb.append(" E1 day ").append(1 + (a2 & 3));
	  score += preferences[i][E1];
	  clientAlloc[3] = (1 + (a2 & 3));
	} else {
	  sb.append("         ");
	  clientAlloc[3] = 0;
	}
	if ((a & 0x20) > 0) {
	  sb.append(" E2 day ").append(1 + ((a2 >> 2) & 3));
	  score += preferences[i][E2];
	  clientAlloc[4] = (1 + ((a2 >> 2) & 3));
	} else {
	  sb.append("         ");
	  clientAlloc[4] = 0;
	}

	if ((a & 0x40) > 0) {
	  sb.append(" E3 day ").append(1 + ((a2 >> 4) & 3));
	  score += preferences[i][E3];
	  clientAlloc[5] = (1 + ((a2 >> 4) & 3));
	} else {
	  sb.append("         ");
	  clientAlloc[5] = 0;
	}

	sb.append(" score = ").append(score);
	total += score;
      } else {
	sb.append("Client ").append(i + 1).append(" does not go, score = 0");
	// MUST CLEAR latestAlloc if the client does not go!!!!
	for (int j = 0, m = clientAlloc.length; j < m; j++) {
	  clientAlloc[j] = 0;
	}
      }
      sb.append(EOL);
    }
    sb.append("Total score = ").append(total).append(EOL);
  }

  // Set a clients preferences based on an array of 8 x 6
  // In, Out, Hotel, E1, E2, E3
  // Owns is 5 x 7
  public void setClientData(int prefs[][], int owns[][]) {
    for (int c = 0; c < 8; c++) {
      // Modify Flight data
      preferences[c][0] = prefs[c][0] - 1;
      preferences[c][1] = prefs[c][1] - 2;
      for (int i = 2; i < 6; i++)
	preferences[c][i] = prefs[c][i];
    }
    for (int day = 0; day < 4; day++) {
      own[0][day] = max15(owns[day][0]);
      own[1][day] = max15(owns[day + 1][1]);
      for (int j = 2; j < 7; j++)
	own[j][day] = max15(owns[day][j]);
    }
  }

  // Make sure the value is NOT negative (in case there are some errors
  // in the server or an agent has sold more than it owns)
  private int max15(int own) {
    return own < 15 ? (own < 0 ? 0 : own) : 15;
  }


  /*********************************************************************
   * DEBUG OUTPUT
   *********************************************************************/

//   void showDebug() {
//     synchronized (System.out) {
//       System.out.println("> ====================================================================");
//       if (startTime > 0) {
// 	System.out.println("> Bound = " + bound + ", bnbs = " + bnbs
// 			   + ", allocs = " + allocs + ", time = "
// 			   + (System.currentTimeMillis() - startTime));
//       } else {
// 	System.out.println("> Optimizer is not running");
//       }
//       System.out.println("> ====================================================================");
//     }
//   }

  public static void main (String[] args) {
    new FastOptimizer().solve();
  }

} // FastOptimizer
