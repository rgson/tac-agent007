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
 * PriceOptimizer
 * Optimizes the score based on prices and ...
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 24 October, 2001
 * Updated : $Date: 2004/05/04 15:48:18 $
 *	     $Revision: 1.1 $
 * Purpose : Simple Branch and Bound optimizer for TAC games
 *
 *
 * Branch and Bound optimizer for TAC games
 * Takes scores and tries to optimize based on scores - with prices.
 *
 * Code updated from tacagent/java/se/sics/tac/solver/PriceOptimizer4
 * at 2002-09-26.
 */

package se.sics.tac.solver;
import java.util.Arrays;
import java.util.logging.Logger;

public class PriceOptimizer implements PriceSolver {

  private static final String EOL = System.getProperty("line.separator",
						       "\r\n");

  private static final Logger log =
    Logger.getLogger(PriceSolver.class.getName());

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

  private static final int IN_FLIGHT_PRICE = 0;
  private static final int OUT_FLIGHT_PRICE = 4;
  private static final int HOTEL_PRICE = 8; // GOOD FIRST
  private static final int E1_PRICE = 16;
  private static final int E2_PRICE = 20;
  private static final int E3_PRICE = 24;

  private final static int[] inOutFlight = new int[20];
  private final static long[] inOutBits = new long[20];

  static {
    int pos = 0;
    for (int i = 0; i < 4; i++) {
      for (int o = i; o < 4; o++) {
	long bits = 0L;
	inOutFlight[pos++] = inOutFlight[pos++] = i * 4 + o;
	for (int b = 1 << o, min = 1 << i; b >= min; b = (b >> 1)) {
	  bits |= b;
	}
	inOutBits[pos - 2] = inOutBits[pos - 1] = bits;
      }
    }
  }

  private final static int[] sortOrder = new int[] {
    0, // 1-2
    4, // 1-3
    7, // 1-4
    9, // 1-5
    -1, // 2-2
    2, // 2-3
    6, // 2-4
    8, // 2-5
    -1, // 3-2
    -1, // 3-3
    3, // 3-4
    5, // 3-5
    -1, // 4-2
    -1, // 4-3
    -1, // 4-4
    1  // 4-5
//     0x12,0x45,0x23,0x34,
//     0x13,0x35,0x24,
//     0x14, 0x25,
//     0x15
  };


  private int[][] preferences = new int[][] {
    {1,2,72,77,78,37},
    {2,2,55,193,180,111},
    {0,1,112,67,149,177},
    {1,1,87,74,72,167},
    {2,2,110,68,193,148},
    {1,1,69,87,142,189},
    {1,2,67,78,154,67},
    {0,2,140,141,3,23}
  };

  private int[][] own = new int[][] {
    {2,4,2,0},
    {0,3,5,0},
    {7,3,2,0},
    {9,3,3,0},
    {1,1,2,2},
    {1,0,0,4},
    {1,1,4,0}
  };


  // Prices for all types of items
  private int[][] prices = new int[][] {
    {0,0,0,100,200,300,400,500,600},		// In flight 1
    {0,0,0,0,0,100,200,300,400},		// In flight 2
    {0,0,0,100,200,300,400,500,600},		// In flight 3
    {0,100,200,300,400,500,600,700,800},	// In flight 4

    {0,100,200,300,400,500,600,700,800},	// Out flight 2
    {0,0,0,0,100,200,300,400,500},		// Out flight 3
    {0,0,0,0,0,0,100,200,300},			// Out flight 4
    {0,100,200,300,400,500,600,700,800},	// Out flight 5

    {0,100,200,300,400,500,600,700,800},	// Hotel Good 1
    {0,100,200,300,400,500,600,700,800},	// Hotel Good 2
    {0,400,800,1200,1600,2000,2400,2800,3200},	// Hotel Good 3
    {0,100,200,300,400,500,600,700,800},	// Hotel Good 4

    {0,100,200,300,400,500,600,700,800},	// Hotel Bad 1
    {0,0,0,0,0,100,200,300,400},		// Hotel Bad 2
    {0,0,0,0,0,100,200,300,400},		// Hotel Bad 3
    {0,100,200,300,400,500,600,700,800},	// Hotel Bad 4

    {0,100,200,300,400,500,600,700,800},	// E1
    {0,100,200,300,400,500,600,700,800},	//
    {0,0,0,0,0,100,200,300,400},		//
    {0,100,200,300,400,500,600,700,800},	//

    {0,100,200,300,400,500,600,700,800},	// E2
    {0,100,200,300,400,500,600,700,800},	//
    {0,100,200,300,400,500,600,700,800},	//
    {0,100,200,300,400,500,600,700,800},	//

    {0,100,200,300,400,500,600,700,800},	// E3
    {0,100,200,300,400,500,600,700,800},	//
    {0,100,200,300,400,500,600,700,800},	//
    {0,100,200,300,400,500,600,700,800}		//
  };

  private int[][] deltaPrices = new int[28][8];

  // Tries to shorten the trip if possible
  private int bestInFlight[][] = new int[][] {
    { 0, 1, 2, 3},
    { 1, 2, 3, 0},
    { 2, 3, 1, 0},
    { 3, 2, 1, 0} };

  // Tries to shorten the trip if possible
  private int bestOutFlight[][] = new int[][] {
    { 0, 1, 2, 3},
    { 1, 0, 2, 3},
    { 2, 1, 0, 3},
    { 3, 2, 1, 0} };

//   private int bestInFlight[][] = new int[][] {
//     { 0, 1, 2, 3},
//     { 1, 0, 2, 3},
//     { 2, 1, 3, 0},
//     { 3, 2, 1, 0} };

//   private int bestOutFlight[][] = new int[][] {
//     { 0, 1, 2, 3},
//     { 1, 2, 0, 3},
//     { 2, 3, 1, 0},
//     { 3, 2, 1, 0} };

  private int[][] scoreClientDays = new int[8][4];
  private int[][] minEntValDays = new int[8][4];

  // From this the allocation can be extracted
  // Should be a better interface later...
  private long finalAlloc = 0;
  private long finalStay = 0;

  private int bound = 0;

  // 8 bits/client: 4 bits stay, 3 bits entertainment
  private long newStay = 0;
  // 8 bits/client: 1 bit good hotel, 2 bits/entertainment day per type,
  private long newAlloc = 0;
  private long newStuff1 = 0;
  private long newStuff2 = 0;

  private long allocs = 0L;
  private long bnbs = 0L;
  private long startTime = 0L;
  private long calculationTime = -1L;

  // This will store the latest allocation
  private int[][] latestAlloc = new int[8][6];

  private int[][] userCostTable = new int[8][20];

  private SolveListener solveListener;
  private boolean runSolver = false;

  private int calcBestScore() {
    int score = 0;
    int[] e;
    int tmp;
    for (int i = 0; i < 8; i++) {
      e = minEntValDays[i];
      score += 1000 + preferences[i][HOTEL] +
	(e[0] = preferences[i][E1]) + (e[1] = preferences[i][E2]) +
	(e[2] = preferences[i][E3]);
      e[3] = SUP;

      Arrays.sort(e);

      scoreClientDays[i][0] = 1000 + preferences[i][HOTEL] + e[2];
      scoreClientDays[i][1] = scoreClientDays[i][0] + e[1];
      scoreClientDays[i][2] = scoreClientDays[i][1] + e[0];
      scoreClientDays[i][3] = scoreClientDays[i][2];

      tmp = e[2];
      e[2] = e[0];
      e[3] = e[0];
      e[0] = tmp;

      // DEBUG
      if (DEBUG)
	for (int j = 0; j < 4; j++)
	  System.out.println("E = " + e[j]);
    }

    for (int i = 0; i < 28; i++) {
      score -= prices[i][0];
    }

    // Also calculate the deltaPrices
    for (int a = 0; a < 28; a++) {
      for (int i = 0; i < 8; i++) {
	if (prices[a][i] != SUP && prices[a][i + 1] != SUP)
	  deltaPrices[a][i] = prices[a][i + 1] - prices[a][i];
	else {
	  deltaPrices[a][i] = SUP;
	}
      }
    }
    return score;
  }

  private boolean bnb(int hscore, long stay, long alloc, int allocPos,
		      long stuff1, long stuff2, int flights) {
    //  System.out.println("Alloc: " + allocPos + " Max: " + MAX_ALLOC_POS);
    bnbs++;

    // Here we might be able to stop the solver !!!
    if (!runSolver) return false;

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
      int price = 0;
      for (int i = 0; i < 4; i++) {
	int inF = (int) (stuff1 >> (IN_FLIGHT_POS + i * 4)) & 0xf;
	int outF = (int) (stuff1 >> (OUT_FLIGHT_POS + i * 4)) & 0xf;

	int noGHotel = (int) (stuff1 >> (GOOD_POS + i * 4)) & 0xf;
	int noCHotel = (int) (stuff1 >> (CHEAP_POS + i * 4)) & 0xf;

	int e1 = (int) ((stuff2 >> (i * 4)) & 0xf);
	int e2 = (int) ((stuff2 >> (16 + i * 4)) & 0xf);
	int e3 = (int) ((stuff2 >> (32 + i * 4)) & 0xf);

	price += prices[IN_FLIGHT_PRICE + i][inF];
	price += prices[OUT_FLIGHT_PRICE + i][outF];


// 	System.out.println("Buying " + noGHotel + " good hotels day " +
// 			   (i + 1) + " for " +
// 			   prices[HOTEL_PRICE + i][noGHotel]);
// 	System.out.println("Buying " + noCHotel + " cheap hotels day " +
// 			   (i + 1) + " for " +
// 			   prices[HOTEL_PRICE + 4+ i][noCHotel]);

	price += prices[HOTEL_PRICE + i][noGHotel];
	price += prices[HOTEL_PRICE + 4 + i][noCHotel];

// 	System.out.println("Buying " + e1 + " E1 day " +
// 			   (i + 1) + " for " +
// 			   prices[E1_PRICE + i][e1]);

// 	System.out.println("Buying " + e2 + " E2 day " +
// 			   (i + 1) + " for " +
// 			   prices[E2_PRICE + i][e2]);

// 	System.out.println("Buying " + e3 + " E3 day " +
// 			   (i + 1) + " for " +
// 			   prices[E3_PRICE + i][e3]);

	price += prices[E1_PRICE + i][e1];
	price += prices[E2_PRICE + i][e2];
	price += prices[E3_PRICE + i][e3];
      }
      score -= price;
      if (score > bound) {
	bound = score;
	finalAlloc = alloc;
	finalStay = stay;
	long time = System.currentTimeMillis() - startTime;
	if (DEBUG) {
	  log.finest("New Bound: " + bound + ", h = " + hscore +
		     " price = " + price + " time = " + time);
	}
	setLatestAlloc(stay, alloc, price, time, null);
      }
    } else if (allocPos < MAX_FLIGHT_POS) {
      // Allocate inflight for this client
      int user = allocPos;
      int user8 = user * 8;
      int[] prefs = preferences[user];
      int prefO = prefs[1];
      int prefI = prefs[0];
      int prefH = prefs[HOTEL];
      int newScore;
      int price;
      int dPrice;
      int penalty;

      // Assume table exists!
      int[] costTable = userCostTable[user];
      int[] scd = scoreClientDays[user];
      int scdi;
      int gHotel;
      int cHotel;
      int pos = 0;
      for(int i = 0; i < 4; i++) {
	price =
	  deltaPrices[IN_FLIGHT_PRICE + i][(int)(stuff1 >> (IN_FLIGHT_POS + i*4)) & 0xf];
	if (i > prefI) {
	  price += (i - prefI) * 100;
	} else if (i < prefI) {
	  price += (prefI - i) * 100;
	}
	gHotel = cHotel = 0;
	for (int o = i, o4 = i * 4; o < 4; o++, o4 += 4) {
	  if (o > prefO) {
	    penalty = price + (o - prefO) * 100;
	  } else if (o < prefO) {
	    penalty = price + (prefO - o) * 100;
	  }  else {
	    penalty = price;
	  }
	  // Current users max utility for this number of days
	  scdi = scd[o - i];
	  dPrice = deltaPrices[OUT_FLIGHT_PRICE + o][(int)(stuff1 >> (OUT_FLIGHT_POS + o4)) & 0xf];
	  gHotel += deltaPrices[HOTEL_PRICE + o][(int)(stuff1 >> (GOOD_POS + o4)) & 0xf];
	  cHotel += deltaPrices[HOTEL_PRICE + 4 + o][(int)(stuff1 >> (CHEAP_POS + o4)) & 0xf];
	  costTable[pos++] = penalty - scdi + dPrice + gHotel;
	  costTable[pos++] = penalty - scdi + dPrice + cHotel + prefH;
	}
      }

      for (int i = 0; i < 20; i++) {
	price = costTable[0];
	pos = 0;
	for (int m = 1; m < 20; m++) {
	  if ((dPrice = costTable[m]) < price) {
	    price = dPrice;
	    pos = m;
	  }
	}

	costTable[pos] = SUP + i;
	if (price > 0) {
	  break;
	}
	int inday = inOutFlight[pos];
	int outday = inday & 3;
	inday = inday >> 2;

	// Move this check to the table???
	// NewScore is hscore - penalty + prices
	newScore = hscore - price - scd[3];
	if (newScore > bound) {

	  long newStay = stay | (inOutBits[pos] << user8);
	  // Increase the number of flights used!
	  long outStuff1 = stuff1
	    + (1L << (OUT_FLIGHT_POS + outday * 4))
	    + (1L << (IN_FLIGHT_POS + inday * 4));
	  int newFlights = flights
	    | (inday << (user * 4 + 2))
	    | (outday << (user * 4));

	  long tmpStuff = 0L;
	  for (int d = inday; d <= outday; d++) {
	    tmpStuff += (1L << (d * 4));
	  }

	  int hotelType = pos & 1;

	  if (hotelType == 0)
	    newAlloc = alloc | (1L << (HOTEL_ALLOC_POS + user8));
	  else
	    newAlloc = alloc;

	  // A trick to get GOOD_POS to CHEAP_POS when hotelType = 1
	  newStuff1 = outStuff1 + (tmpStuff << (GOOD_POS + hotelType * 16));

	  bnb(newScore, newStay,
	      newAlloc, allocPos + 1,
	      newStuff1, stuff2, newFlights);
	}
      }
      // this client does not go... is this correct?? CHECK THIS
      newScore = hscore - scoreClientDays[user][3];
      if (newScore > bound) {
	bnb(newScore, stay, alloc, allocPos + 1,
	    stuff1, stuff2, flights);
      }
    } else {
      // Entertainment handling after all clients has allocated
      // flighs and hotels

      int user = allocPos - MAX_FLIGHT_POS;
      int user8 = user * 8;

      int a = (int) ((stay >> user8) & 0xff);
      if (a == 0) {
	// client is not going => continue with next client
	int newScore = hscore;
	if (newScore > bound) {
	  bnb(newScore, stay, alloc, allocPos + 1, stuff1, stuff2, flights);
	}
      } else {

	int user4 = user * 4;
	int inf = (int) ((flights >> user4 + 2) & 3);
	int outf = (int) ((flights >> user4) & 3);
	int days = outf - inf;
	int[] prefs = preferences[user];
	int e1score, e2score, newScore;
	int aPos = ENTER_ALLOC_POS + user8;
	int[] minEVal = minEntValDays[user];

	// E1
	int start = inf, stop = outf + 1;
	int price;
	boolean useE1 = false;
	boolean useE2 = false;
	boolean useE3 = false;
	for (int e1d = start; e1d <= stop; e1d++) {
	  // Assign entertainment day val
	  int ent = (int) ((stuff2 >> (e1d * 4)) & 0xf);
	  if (e1d < stop &&
	      (price = deltaPrices[E1_PRICE + e1d][ent]) < prefs[3]) {
	    e1score = hscore - price;
	    useE1 = true;
	  } else {
	    // No entertainment - no buying either...
	    // Remove eval only if it is larger or equal to min eval counted.
	    // WARNING!!! WILL CUT TOO MUCH!!!
	    if (prefs[3] >= minEVal[days]) {
	      e1score = hscore - prefs[3];
	    } else {
	      e1score = hscore;
	    }

	    useE1 = false;
	  }
	  if (e1score > bound) {
	    for (int e2d = inf; e2d <= stop; e2d++) {
	      if (e2d == stop || e1d != e2d) {
		ent = (int) (stuff2 >> (16 + e2d * 4)) & 0xf;
		if (e2d < stop &&
		    (price = deltaPrices[E2_PRICE + e2d][ent]) < prefs[4]) {
		  e2score = e1score - price;
		  useE2 = true;
		} else {
		  // No entertainment
		  if (prefs[4] >= minEVal[days]) {
		    e2score = e1score - prefs[4];
		  } else {
		    e2score = e1score;
		  }
		  useE2 = false;
		}
		if (e2score > bound) {
		  for (int e3d = inf; e3d <= stop; e3d++) {
		    if (e3d == stop || (e1d != e3d) && (e2d != e3d)) {
		      ent = (int) (stuff2 >> (32 + e3d * 4)) & 0xf;
		      if (e3d < stop &&
			  (price = deltaPrices[E3_PRICE + e3d][ent]) < prefs[5]) {
			newScore = e2score - price;
			useE3 = true;
		      } else {
			// No entertainment
			if (prefs[5] >= minEVal[days]) {
			  newScore = e2score - prefs[5];
			} else {
			  newScore = e2score;
			}
			useE3 = false;
		      }
		      if (newScore > bound) {
			newStay = stay;
			newStuff2 = stuff2;
			newAlloc = alloc;
			if (useE1) {
			  newStay |= (1L << (user8 + ENTER1_BIT));
			  newAlloc |= (((long) e1d) << (aPos));
			  newStuff2 += (1L << (e1d * 4));
			}
			if (useE2) {
			  newStay |= (1L << (user8 + ENTER2_BIT));
			  newAlloc |= (((long) e2d) << (aPos + 2));
			  newStuff2 += (1L << (16 + e2d * 4));
			}
			if (useE3) {
			  newStay |= (1L << (user8 + ENTER3_BIT));
			  newAlloc |= (((long) e3d) << (aPos + 4));
			  newStuff2 += (1L << (32 + e3d * 4));
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
    return runSolver;
  }

  private boolean solve() {
    int startScore = calcBestScore();
    bound = 0;
    finalAlloc = allocs = 0L;
    finalStay = 0L;
    bnbs = 0L;
    calculationTime = -1L;

    long stuff1 = 0;
    long stuff2 = 0;

    startTime = System.currentTimeMillis();
    boolean res = bnb(startScore, 0, 0L, 0, stuff1, stuff2, 0);

    StringBuffer sb = new StringBuffer();
    sb.append(EOL)
      .append("-------------------------------------------")
      .append(EOL)
      .append("Final score    (Best Util: ").append(startScore).append(')')
      .append(EOL)
      .append("-------------------------------------------")
      .append(EOL);
    // THESE METHODS MUST BE CALLED BEFORE RETURNING BECAUSE THEY WILL SET
    // LATESTALLOC AND CALCULATION TIME!!!!
    calculationTime = (System.currentTimeMillis() - startTime);
    setLatestAlloc(finalStay, finalAlloc, 0, calculationTime, sb);
    sb.append("-------------------------------------------")
      .append(EOL)
      .append("Time: ").append(calculationTime)
      .append("        \tAllocs: ").append(allocs).append(EOL)
      .append("Bnbs: ").append(bnbs).append("    \tBound: ").append(bound)
      .append(EOL)
      .append("-------------------------------------------");
    log.fine(sb.toString());
    startTime = 0L;
    return res;
  }

  private long max15(long own) {
    // Make sure the value is NOT negative (in case there are some errors
    // in the server or an agent has sold more than it owns)
    return own < 15 ? (own < 0 ? 0 : own) : 15;
  }

  public int[][] getLatestAllocation() {
    return latestAlloc;
  }

  public long getCalculationTime() {
    return calculationTime;
  }

  private void setLatestAlloc(long stay, long alloc, int price, long time,
			      StringBuffer sb) {
    boolean writeAlloc = sb != null;
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
	if (writeAlloc) {
	  sb.append("Client ").append(i + 1).append(" stays ")
	    .append(inf).append(" - ").append(outf);
	}
	if ((a2 & HOTEL_MASK) > 0) {
	  if (writeAlloc)
	    sb.append(" on good hotel  ");
	  score += preferences[i][HOTEL];
	  clientAlloc[2] = 1;
	} else {
	  if (writeAlloc)
	    sb.append(" on cheap hotel ");
	  clientAlloc[2] = 0;
	}

	if ((a & 0x10) > 0) {
	  if (writeAlloc)
	    sb.append(" E1 day ").append(1 + (a2 & 3));
	  score += preferences[i][E1];
	  clientAlloc[3] = (1 + (a2 & 3));
	} else {
	  if (writeAlloc)
	    sb.append("         ");
	  clientAlloc[3] = 0;
	}
	if ((a & 0x20) > 0) {
	  if (writeAlloc)
	    sb.append(" E2 day ").append(1 + ((a2 >> 2) & 3));
	  score += preferences[i][E2];
	  clientAlloc[4] = (1 + ((a2 >> 2) & 3));
	} else {
	  if (writeAlloc)
	    sb.append("         ");
	  clientAlloc[4] = 0;
	}

	if ((a & 0x40) > 0) {
	  if (writeAlloc)
	    sb.append(" E3 day ").append(1 + ((a2 >> 4) & 3));
	  score += preferences[i][E3];
	  clientAlloc[5] = (1 + ((a2 >> 4) & 3));
	} else {
	  if (writeAlloc)
	    sb.append("         ");
	  clientAlloc[5] = 0;
	}

	if (writeAlloc)
	  sb.append(" score = ").append(score);
	total += score;
      } else {
	if (writeAlloc)
	  sb.append("Client ").append(i + 1).append(" does not go, score = 0");
	// MUST CLEAR latestAlloc if the client does not go!!!!
	for (int j = 0, m = clientAlloc.length; j < m; j++) {
	  clientAlloc[j] = 0;
	}
      }
      if (writeAlloc) {
	sb.append(EOL);
      }
    }
    if (writeAlloc) {
      sb.append("Total score = ").append(total - price).append(" utility = ")
	.append(total).append(" costs = ").append(price).append(EOL);
    }
    runSolver =
      solveListener.solveReport(total, total - price, time, latestAlloc);
  }

  public boolean isSolving() {
    return solveListener != null;
  }

  // This should be called by the runner of the solver...
  public boolean startSolver(SolveListener solveListener,
			     // [Client][InDay,OutDay,Hotel,E1,E2,E3]
			     int[][] preferences,
			     // [ItemType][Day]
			     int[][] own,
			     // [Auction][PriceVector (0-8)]
			     int[][] prices) {
    if (solveListener == null) {
      throw new NullPointerException();
    }

    if (setSolver(solveListener)) {
      try {
	setClientData(preferences, own);
	setPrices(prices);
	return solve();
      } finally {
	clearSolver();
      }
    } else {
      throw new IllegalStateException("Can not run two solvers");
    }
  }

  public synchronized boolean stopSolver(boolean waitForSolver) {
    if (solveListener != null) {
      runSolver = false;
      if (!waitForSolver) {
	return false;
      }

      try {
	while (solveListener != null) {
	  System.out.println("Wait for solver to stop");
	  wait();
	  System.out.println("Solver is stopped");
	}
      } catch (InterruptedException e) {
	return false;
      }
    }
    return true;
  }

  private synchronized boolean setSolver(SolveListener listener) {
    if (solveListener != null) {
      return false;
    }
    runSolver = true;
    this.solveListener = listener;
    return true;
  }

  private synchronized void clearSolver() {
    if (solveListener != null) {
      runSolver = false;
      solveListener = null;
      System.out.println("Notify that solver is stopped...");
      notify();
    }
  }


  // Set a clients preferences based on an array of 8 x 6
  // In, Out, Hotel, E1, E2, E3
  // Owns is 7 x 5
  private void setClientData(int[][] prefs, int[][] owns) {
    for (int c = 0; c < 8; c++) {
      // Modify Flight data
      preferences[c][0] = prefs[c][0] - 1;
      preferences[c][1] = prefs[c][1] - 2;
      for (int i = 2; i < 6; i++)
	preferences[c][i] = prefs[c][i];
    }
    for (int type = 0; type < 7; type++) {
      System.arraycopy(owns[type], 0, own[type], 0, 4);
    }
//       for (int day = 0; day < 4; day++) {
//       own[0][day] = owns[day][0];
//       own[1][day] = owns[day + 1][1];
//       for (int j = 2; j < 7; j++)
// 	own[j][day] = owns[day][j];
//       }
    sortClients();
  }

  private void sortClients() {
    int[] tmpPref;
    for (int i = 0; i < 7; i++) {
      int val = sortOrder[(preferences[i][0] << 2) |
			  preferences[i][1]];
      for (int j = i + 1; j < 8; j++) {
	int tmpVal = sortOrder[(preferences[j][0] << 2) |
			   preferences[j][1]];
	if (val > tmpVal) {
	  val = tmpVal;
	  tmpPref = preferences[i];
	  preferences[i] = preferences[j];
	  preferences[j] = tmpPref;
	}
      }
    }

    // Ensure that the ent prefs are different
    // FIX THIS!!! - Can have an array for this instead??!!
    for (int i = 0; i < 8; i++) {
      tmpPref = preferences[i];
      if (tmpPref[3] == tmpPref[4]) {
	tmpPref[3]++;
      }
      if (tmpPref[3] == tmpPref[5]) {
	tmpPref[5]++;
      }
      if (tmpPref[4] == tmpPref[5]) {
	tmpPref[5]++;
	if (tmpPref[3] == tmpPref[5]) {
	  tmpPref[5]++;
	}
      }
    }
  }

  private void setPrices(int[][] prices) {
    for (int i = 0, n = prices.length; i < n; i++) {
      int[] p = prices[i];
      int[] p2 = this.prices[i];
      System.arraycopy(p, 0, p2, 0, p2.length);
    }
  }


  /*********************************************************************
   * Parsing
   *********************************************************************/

//   private void writeToSolver(String result) {
//     if (out != null) {
//       try {
// 	System.out.println("SOLVER RESPONSE: {" + result);
// 	out.write(result);
// 	out.flush();
// 	System.out.println("}");
//       } catch (Exception e) {
// 	e.printStackTrace();
// 	out = null;
// 	runSolver = false;
//       }
//     } else {
//       System.out.println("COULD NOT SEND: " + result);
//     }
//   }

//   public void setSolverWriter(OutputStreamWriter writer) {
//     out = writer;
//     writeToSolver("ready.\r\n");
//   }

//   public boolean parseSolveRequest(String request) {
//     if (request.startsWith("solve_data([")) {
//       int index = request.indexOf("client_inflight");
//       int inFlight[] = parseFlight(request, index);
//       index = request.indexOf("client_outflight");
//       int outFlight[] = parseFlight(request, index);

//       index = request.indexOf("client_hotelvalue");
//       int hotelVal[] = parseHotel(request, index);

//       index = request.indexOf("client_eventvalue");
//       int eventVal[][] = parseEvent(request, index);

//       // Copy into preferences...
//       for (int i = 0; i < 8; i++) {
// 	preferences[i][0]= inFlight[i];
// 	preferences[i][1]= outFlight[i];
// 	preferences[i][2]= hotelVal[i];
// 	preferences[i][3]= eventVal[i][0];
// 	preferences[i][4]= eventVal[i][1];
// 	preferences[i][5]= eventVal[i][2];

// 	showPreferences(i);
//       }

//       sortClients();

//       System.out.println("Sorted:");

//       for (int i = 0; i < 8; i++) {
// 	showPreferences(i);
//       }

//       setPrices(request);
//       return true;
//     }
//     // Nothing to solve -> ready again!!!
//     writeToSolver("ready.\r\n");
//     return false;
//   }

//   private int[] parseFlight(String request, int index) {
//     int[] f = new int[8];
//     index = request.indexOf('[', index) + 1;
//     if (request.charAt(index) == '[')
//       index++;
//     int i2 = request.indexOf(',', index);
//     String sub;
//     for (int i = 0; i < 8; i++) {
//       sub = request.substring(index, i2);
//       try {
// 	f[i] = Integer.parseInt(sub) / -100;
//       } catch (Exception e) {
// 	e.printStackTrace();
//       }
//       index = request.indexOf('[', index) + 1;
//       i2 = request.indexOf(',', index);
//     }
//     return f;
//   }

//   private int[] parseHotel(String request, int index) {
//     int[] f = new int[8];
//     index = request.indexOf('[', index) + 1;
//     if (request.charAt(index) == '[')
//       index++;

//     int i2 = request.indexOf(',', index);
//     String sub;
//     for (int i = 0; i < 8; i++) {
//       sub = request.substring(index, i2);
//       try {
// 	f[i] = Integer.parseInt(sub);
//       } catch (Exception e) {
// 	e.printStackTrace();
//       }
//       index = i2 + 1;
//       if (i == 6)
// 	i2 = request.indexOf(']', index);
//       else
// 	i2 = request.indexOf(',', index);
//     }
//     return f;
//   }

//   private int[][] parseEvent(String request, int index) {
//     int[][] f = new int[8][3];
//     index = request.indexOf('[', index) + 1;
//     int i2 = request.indexOf(',', index);
//     String sub;
//     for (int i = 0; i < 8; i++) {
//       index = request.indexOf('[', index) + 1;
//       i2 = request.indexOf(',', index);
//       for (int et = 0; et < 3; et++) {
// 	sub = request.substring(index, i2);
// 	try {
// 	  f[i][et] = Integer.parseInt(sub);
// 	} catch (Exception e) {
// 	  e.printStackTrace();
// 	}
// 	index = i2 + 1;
// 	if (et == 1)
// 	  i2 = request.indexOf(']', index);
// 	else
// 	  i2 = request.indexOf(',', index);
//       }
//     }
//     return f;
//   }

//   private void setPrices(String request) {
//     // Should be 28 price vectors...
//     int index = 0;
//     int i2 = 0;
//     String type;
//     int day = 0;
//     int auction;
//     String sub;
//     for (int i = 0; i < 28; i++) {
//       index = request.indexOf("price", index) + 6;
//       i2 = request.indexOf(',', index);
//       type = request.substring(index, i2);
//       index = i2 + 1;
//       try {
// 	day = Integer.parseInt(request.substring(index, index + 1));
//       } catch (Exception e) {
// 	e.printStackTrace();
//       }
//       auction = getAuction(type, day);
//       // System.out.println("Prices for auction: " + auction);
//       index = request.indexOf('[', index) + 1;
//       for (int p = 0; p < 9; p++) {
// 	if (p < 8) {
// 	  i2 = request.indexOf(',', index);
// 	} else {
// 	  i2 = request.indexOf(']', index);
// 	}
// 	try {
// 	  sub = request.substring(index, i2);
// 	  if ("sup".equals(sub)) {
// 	    prices[auction][p] = SUP;
// 	    // System.out.print(" sup");
// 	  } else {
// 	    prices[auction][p] = Integer.parseInt(sub);
// 	    // System.out.print(" " + prices[auction][p]);
// 	  }
// 	} catch (Exception e) {
// 	  System.err.println("Auction: " + auction + " p: " + p);
// 	  e.printStackTrace();
// 	}
// 	index = i2 + 1;
//       }
//       // System.out.println();
//     }
//   }

//   private int getAuction(String cat, int day) {
//     switch (cat.charAt(0)) {
//     case 'i': // Inflight
//       return IN_FLIGHT_PRICE + day - 1;
//     case 'o': // Outflight
//       return  OUT_FLIGHT_PRICE + day - 2;
//     case 'g': // GoodHotel
//       return HOTEL_PRICE + day - 1;
//     case 'c': // CheapHotel
//       return 4 + HOTEL_PRICE + day - 1;
//     case 'w': // Wrestling
//       return E1_PRICE + day - 1;
//     case 'a': // Amusement
//       return E2_PRICE + day - 1;
//     case 'm': // Museum
//       return E3_PRICE + day - 1;
//     }
//     return -1;
//   }



  /*********************************************************************
   * DEBUG OUTPUT
   *********************************************************************/

  private void showPreferences(int i) {
    System.out.println("Client " + (i + 1) +
		       "  " + (preferences[i][0] + 1) +
		       " - " + (preferences[i][1] + 2) +
		       "   " + (preferences[i][2]) +
		       ", " + (preferences[i][3]) +
		       ", " + (preferences[i][4]) +
		       ", " + (preferences[i][5]));
  }

  void showDebug() {
    synchronized (System.out) {
      System.out.println("> ====================================================================");
      if (startTime > 0) {
	System.out.println("> Bound = " + bound + ", bnbs = " + bnbs
			   + ", allocs = " + allocs + ", time = "
			   + (System.currentTimeMillis() - startTime));
      } else {
	System.out.println("> Optimizer is not running");
      }
      System.out.println("> ====================================================================");
    }
  }

//   public static void main (String[] args) {
//     PriceOptimizer po = new PriceOptimizer();

//     po.parseSolveRequest("solve_data([client_inflight([[0,-100,-200,-300],[0,-100,-200,-300],[-100,0,-100,-200],[-200,-100,0,-100],[-100,0,-100,-200],[0,-100,-200,-300],[0,-100,-200,-300],[-100,0,-100,-200]]),client_outflight([[-100,0,-100,-200],[-200,-100,0,-100],[-300,-200,-100,0],[-300,-200,-100,0],[-200,-100,0,-100],[-200,-100,0,-100],[-200,-100,0,-100],[-200,-100,0,-100]]),client_hotelvalue([53,63,78,94,130,102,72,58]),client_eventvalue([[54,107,7],[136,3,26],[67,180,182],[162,135,24],[195,71,122],[109,4,139],[144,93,80],[107,117,156]]),price(inflight,1,[0,391,782,1173,1564,1955,2346,2737,3128]),price(inflight,2,[0,279,558,837,1116,1395,1674,1953,2232]),price(inflight,3,[0,275,550,825,1100,1375,1650,1925,2200]),price(inflight,4,[0,347,694,1041,1388,1735,2082,2429,2776]),price(outflight,2,[0,331,662,993,1324,1655,1986,2317,2648]),price(outflight,3,[0,335,670,1005,1340,1675,2010,2345,2680]),price(outflight,4,[0,275,550,825,1100,1375,1650,1925,2200]),price(outflight,5,[0,300,600,900,1200,1500,1800,2100,2400]),price(cheaphotel,1,[0,36,72,108,165,238,328,440,579]),price(cheaphotel,2,[0,121,242,363,605,945,1417,2067,2954]),price(cheaphotel,3,[0,122,244,366,610,953,1429,2084,2978]),price(cheaphotel,4,[0,46,92,138,211,304,419,563,740]),price(goodhotel,1,[0,105,210,315,483,694,958,1285,1689]),price(goodhotel,2,[0,165,330,495,825,1289,1933,2819,4028]),price(goodhotel,3,[0,175,350,525,875,1367,2050,2990,4272]),price(goodhotel,4,[0,125,250,375,575,826,1140,1530,2011]),price(wrestling,1,[0,105,220,347,486,638,804,984,1181]),price(wrestling,2,[-260,-205,-144,-76,0,105,220,347,486]),price(wrestling,3,[0,105,220,347,486,638,804,984,1181]),price(wrestling,4,[0,105,220,347,486,638,804,984,1181]),price(amusement,1,[-144,-76,0,105,220,347,486,638,804]),price(amusement,2,[-144,-76,0,105,220,347,486,638,804]),price(amusement,3,[0,105,220,347,486,638,804,984,1181]),price(amusement,4,[0,105,220,347,486,638,804,984,1181]),price(museum,1,[-260,-205,-144,-76,0,105,220,347,486]),price(museum,2,[0,105,220,347,486,638,804,984,1181]),price(museum,3,[0,105,220,347,486,638,804,984,1181]),price(museum,4,[0,105,220,347,486,638,804,984,1181])]).");

//     po.parseSolveRequest("solve_data([client_inflight([[0,-100,-200,-300],[0,-100,-200,-300],[0,-100,-200,-300],[-100,0,-100,-200],[-200,-100,0,-100],[-200,-100,0,-100],[-200,-100,0,-100],[-100,0,-100,-200]]),client_outflight([[-100,0,-100,-200],[0,-100,-200,-300],[-300,-200,-100,0],[-200,-100,0,-100],[-300,-200,-100,0],[-200,-100,0,-100],[-300,-200,-100,0],[-300,-200,-100,0]]),client_hotelvalue([61,81,97,114,120,124,140,83]),client_eventvalue([[177,183,35],[131,70,167],[74,2,187],[54,125,95],[87,55,111],[19,36,150],[127,178,109],[57,107,130]]),price(inflight,1,[0,377,754,1131,1508,1885,2262,2639,3016]),price(inflight,2,[0,383,766,1149,1532,1915,2298,2681,3064]),price(inflight,3,[0,252,504,756,1008,1260,1512,1764,2016]),price(inflight,4,[0,372,744,1116,1488,1860,2232,2604,2976]),price(outflight,2,[0,361,722,1083,1444,1805,2166,2527,2888]),price(outflight,3,[0,396,792,1188,1584,1980,2376,2772,3168]),price(outflight,4,[0,305,610,915,1220,1525,1830,2135,2440]),price(outflight,5,[0,391,782,1173,1564,1955,2346,2737,3128]),price(cheaphotel,1,[0,36,72,142,219,314,434,582,766]),price(cheaphotel,2,[0,121,242,496,775,1133,1591,2172,2905]),price(cheaphotel,3,[0,122,244,501,781,1143,1604,2190,2929]),price(cheaphotel,4,[0,46,92,182,279,402,555,744,978]),price(goodhotel,1,[0,105,210,416,638,918,1267,1700,2234]),price(goodhotel,2,[0,165,330,677,1057,1545,2170,2962,3961]),price(goodhotel,3,[0,175,350,718,1121,1639,2302,3142,4201]),price(goodhotel,4,[0,125,250,495,760,1093,1508,2023,2660]),price(wrestling,1,[0,105,220,347,486,638,804,984,1181]),price(wrestling,2,[-260,-205,-144,-76,0,105,220,347,486]),price(wrestling,3,[0,105,220,347,486,638,804,984,1181]),price(wrestling,4,[-260,-205,-144,-76,0,105,220,347,486]),price(amusement,1,[0,105,220,347,486,638,804,984,1181]),price(amusement,2,[0,105,220,347,486,638,804,984,1181]),price(amusement,3,[-144,-76,0,105,220,347,486,638,804]),price(amusement,4,[-144,-76,0,105,220,347,486,638,804]),price(museum,1,[0,105,220,347,486,638,804,984,1181]),price(museum,2,[0,105,220,347,486,638,804,984,1181]),price(museum,3,[0,105,220,347,486,638,804,984,1181]),price(museum,4,[0,105,220,347,486,638,804,984,1181])]).");

// solve_data([client_inflight([[0,-100,-200,-300],[-100,0,-100,-200],[-100,0,-100,-200],[0,-100,-200,-300],[0,-100,-200,-300],[-200,-100,0,-100],[-200,-100,0,-100],[0,-100,-200,-300]]),client_outflight([[-300,-200,-100,0],[-300,-200,-100,0],[-200,-100,0,-100],[-300,-200,-100,0],[-200,-100,0,-100],[-300,-200,-100,0],[-200,-100,0,-100],[-100,0,-100,-200]]),client_hotelvalue([91,105,88,131,94,68,111,92]),client_eventvalue([[187,76,4],[90,171,40],[15,162,199],[79,74,26],[158,176,80],[94,163,39],[7,6,187],[90,23,66]]),price(inflight,1,[0,0,0,0,0,264,528,792,1056]),price(inflight,2,[0,0,0,351,702,1053,1404,1755,2106]),price(inflight,3,[0,0,0,291,582,873,1164,1455,1746]),price(inflight,4,[0,408,816,1224,1632,2040,2448,2856,3264]),price(outflight,2,[0,362,724,1086,1448,1810,2172,2534,2896]),price(outflight,3,[0,0,348,696,1044,1392,1740,2088,2436]),price(outflight,4,[0,0,0,0,288,576,864,1152,1440]),price(outflight,5,[0,0,0,0,0,325,650,975,1300]),price(cheaphotel,1,[0,36,72,130,191,263,347,446,561]),price(cheaphotel,2,[0,125,250,540,864,1296,1866,2612,3583]),price(cheaphotel,3,[0,126,252,544,870,1306,1881,2633,3611]),price(cheaphotel,4,[0,46,92,166,244,336,444,570,717]),price(goodhotel,1,[0,110,220,399,585,805,1062,1364,1714]),price(goodhotel,2,[0,180,360,777,1244,1866,2687,3762,5159]),price(goodhotel,3,[0,185,370,799,1278,1918,2762,3866,5303]),price(goodhotel,4,[0,130,260,471,692,951,1256,1612,2026]),price(wrestling,1,[0,105,220,347,486,638,804,984,1181]),price(wrestling,2,[0,105,220,347,486,638,804,984,1181]),price(wrestling,3,[0,105,220,347,486,638,804,984,1181]),price(wrestling,4,[0,105,220,347,486,638,804,984,1181]),price(amusement,1,[0,105,220,347,486,638,804,984,1181]),price(amusement,2,[0,105,220,347,486,638,804,984,1181]),price(amusement,3,[-260,-205,-144,-76,0,105,220,347,486]),price(amusement,4,[-144,-76,0,105,220,347,486,638,804]),price(museum,1,[0,105,220,347,486,638,804,984,1181]),price(museum,2,[0,105,220,347,486,638,804,984,1181]),price(museum,3,[-144,-76,0,105,220,347,486,638,804]),price(museum,4,[-260,-205,-144,-76,0,105,220,347,486])]).");

//     po.runSolver();
//   }

} // PriceOptimizer
