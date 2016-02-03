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
 * DummyAgent
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-25
 * Updated : $Date: 2004/09/14 11:28:42 $
 *	     $Revision: 1.4 $
 */

package se.sics.tac.server.classic;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import se.sics.tac.server.Auction;
import se.sics.tac.server.BidList;
import se.sics.tac.server.BuiltinAgent;
import se.sics.tac.server.Market;
import se.sics.tac.server.Quote;
import se.sics.tac.server.User;
import se.sics.tac.solver.PriceOptimizer;
import se.sics.tac.solver.PriceSolver;
import se.sics.tac.solver.SolveListener;

public class DummyAgent extends BuiltinAgent implements SolveListener {

  private final static String EOL = System.getProperty("line.separator",
						       "\r\n");

  private final static Logger log =
    Logger.getLogger(DummyAgent.class.getName());

  private final static int AUCTION_NO = 28;
  private PriceSolver solver;
  private int[][] prices = new int[AUCTION_NO][9];
  private long[] lastBidTime = new long[AUCTION_NO];
  private final double[] hotelInc = {
    1.15, 1.25, 1.25, 1.15, 1.15, 1.25, 1.25, 1.15
  };
  private int[] startPrices = {
    // Start prices for flights
    250, 250, 250, 250,
    250, 250, 250, 250,
    // Start prices for hotels
    50, 100, 100, 70,
    85, 140, 135, 100,
    // Start prices for entertainment
    90, 90, 90, 90,
    90, 90, 90, 90,
    90, 90, 90, 90
  };

  private double priceIncrease = 1.05;
  private double priceDecrease = 0.95;

  private int gameLengthMillis;
  private AgentTimer solveTimer;

  public DummyAgent(Market market, User user) {
    super(market, user);
    this.gameLengthMillis = market.getGame().getGameLength();

    Random random = getRandom();
    int gameLengthMinutes = gameLengthMillis / 60000;
    if (gameLengthMinutes < 9) {
      // Somewhat higher prices for hotels on shorter games
      // because several auctions might clos at the same time. TEST. FIX THIS!!
      double inc = (19.0 - gameLengthMinutes) / 10.0;
      for (int i = 8; i < 16; i++) {
	startPrices[i] *= hotelInc[i - 8] * inc;
      }
    }

    // Randomize the start prices somewhat for hotels and entertainment
    // TEST. FIX THIS!!!
    int goodDelta = random.nextInt(20) - 15;
    for (int i = 8, n = startPrices.length; i < n; i++) {
      int delta = random.nextInt(30) - 10;
      if (i > 11) {
	delta += goodDelta;
      }
      startPrices[i] += delta;
    }

    solver = new PriceOptimizer();
  }

  protected void gameStarted() {
    solve();
  }

  protected void gameStopped() {
    AgentTimer timer = solveTimer;
    if (timer != null) {
      timer.cancel();
    }
    solveTimer = null;
    if (solver.isSolving()) {
      solver.stopSolver(false);
    }
  }

  protected void quoteUpdated(Quote quote) {
    int a = getAuctionIndex(quote.getAuction());
    if (a < 7) {
      flightQuoteUpdated(quote, a);
    } else if (a >= 8 && a < 16) {
      hotelQuoteUpdated(quote, a);
    } else {
      entertainmentQuoteUpdated(quote, a);
    }
  }

  private void flightQuoteUpdated(Quote quote, int a) {
  }

  private void hotelQuoteUpdated(Quote quote, int a) {
    // Perform a new solve if we will miss some hotel
    if (getAllocation(a) > (getOwn(a) + quote.getHQW(user))) {
      requestSolve();
    }
  }

  private void entertainmentQuoteUpdated(Quote quote, int a) {
    // Do not allow bid placement too often.
    long serverTime = getServerTime();
    if ((serverTime - lastBidTime[a]) < 30000) {
      // Bid placed in the last 30 seconds.  Do not place again now.
      return;
    }

    int alloc = getAllocation(a) - getOwn(a);
    if (alloc > 0) {
      BidList list = new BidList();
      list.addBidPoint(alloc, getEventBuyPrice());
      // Must check if old bid has been placed to avoid double sell/buy. FIX THIS!!!
      submitBid(quote.getAuction(), list);
      lastBidTime[a] = serverTime;
    } else if (alloc < 0) {
      BidList list = new BidList();
      list.addBidPoint(alloc, getEventSellPrice());
      // Must check if old bid has been placed to avoid double sell/buy. FIX THIS!!!
      submitBid(quote.getAuction(), list);
      lastBidTime[a] = serverTime;
    }
  }

  private double getEventBuyPrice() {
    long time = getGameTime();
    double price = 50 + (70 - 50 * getRandom().nextDouble())
      * time / gameLengthMillis;
    return price > 180.0 ? 180.0 : price;
  }

  private double getEventSellPrice() {
    long time = getGameTime();
    double price = 50 + (150 - 100 * getRandom().nextDouble())
      * (gameLengthMillis - time) / gameLengthMillis;
    return price < 20.0 ? 20.0 : price;
  }

  protected void auctionClosed(Auction auction) {
    requestSolve();
  }


  /*********************************************************************
   * Timer and solve handling
   *********************************************************************/

  private synchronized void requestSolve() {
    if (solveTimer == null) {
      solveTimer = new AgentTimer(this, 10000);
    }
  }

  public void wakeup(AgentTimer timer) {
    if (timer == solveTimer) {
      solveTimer = null;
      solve();
    }
  }

  private void solve() {
    if (!isRunning()) {
      // Agent is not running. Ignore the solve request.
      return;
    }

    log.finest("Agent " + user.getName() + " is awaiting solver");
    if (solver.isSolving()) {
      solver.stopSolver(true);
    }
    setPrices();
    int[][] o = new int[7][4];
    for (int i = 0; i < AUCTION_NO; i++) {
      o[i / 4][i % 4] = getOwn(i);
    }
    solver.startSolver(this, market.getGamePreferences(user),
		       o, prices);
    log.finest("Agent " + user.getName() + " finished solving");
    if (isRunning()) {
      placeOrders();
    }
    // Show allocation
    printOwn();
  }

  private void setPrices() {
    for (int a = 0, an = prices.length; a < an; a++) {
      Auction auction = getAuction(a);
      if (auction != null) {
	Quote quote = auction.getQuote();
	boolean isClosed = auction.isClosed();
	int buyPrice = (int) quote.getAskPrice();
	int sellPrice = (int) quote.getBidPrice();
	int own = getOwn(a) + quote.getHQW(user);
	int[] vector = prices[a];
	int p;
	if (buyPrice < startPrices[a]) {
	  buyPrice = startPrices[a];
	}
	if (sellPrice < 80) {
	  sellPrice = 80;
	}
	for (int i = 0; i < 9; i++) {
	  if (i < own) {
	    // Sell if possible
	    if (a >= 16 && !isClosed) {
	      // Entertainment
	      p = (int) (80 * (i - own) * Math.pow(priceDecrease, own - i));
// 	      p = (i - own) * sellPrice;
	    } else {
	      p = 0;
	    }
	  } else if (i == own) {
	    p = 0;
	  } else if (isClosed) {
	    p = PriceSolver.SUP;
	  } else if (a < 16 && a >= 8) {
	    // Hotel increase
	    p = (i - own) * buyPrice;
	    if (i > own) {
	      double v = Math.pow(hotelInc[a - 8], i - own - 1);
	      if (v > 1) {
		p = (int) (p * v);
	      }
	    }

	    if (p <= vector[i]) {
	      // Always make sure the price is increasing for all hotel
	      // auctions.
	      p = vector[i] + 1;
	    }
	  } else if (a >= 16) {
	    // Entertainment
	    p = (int) (100 * (i - own) * Math.pow(priceIncrease, i - own));
	  } else {
	    p = (int) ((i - own) * buyPrice * priceIncrease);
	  }
	  vector[i] = p;
	}
      }
    }
  }

  private void placeOrders() {
    for (int i = 0; i < AUCTION_NO; i++) {
      Auction auction = getAuction(i);
      if (auction != null && !auction.isClosed()) {
	int hqw = auction.getQuote().getHQW(user);
	int allocation = getAllocation(i);
	int own = getOwn(i);
	int delta = allocation - own - hqw;
	if (delta > 0) {
	  BidList list = new BidList();
	  double unitPrice;
	  delta += hqw;
	  if (i < 8) {
	    // Bid 1000 for flights
	    unitPrice = 1000;
	  } else if (i < 16) {
	    unitPrice = prices[i][allocation] / delta;
	    if (unitPrice < 0) {
	      // DEBUG
	      log.severe("negative price when buying " + delta
			 + " items of type " + i + " for " + unitPrice);
	      unitPrice = -unitPrice;
	    }
	  } else {
	    unitPrice = getEventBuyPrice();
	  }
	  // DEBUG
	  log.info("Agent " + user.getName() + " is buying " + delta
		   + " of type " + i + " for price " + unitPrice
		   + " allocation=" + allocation
		   + " own=" + own);
	  list.addBidPoint(delta, unitPrice);
	  // Must check that another bid not already been placed to
	  // avoid double buy/sell. FIX THIS!!!
	  submitBid(auction, list);
	} else if (delta < 0 && i >= 16) {
	  // Sell entertainment
	  BidList list = new BidList();
	  double unitPrice = getEventSellPrice(); // prices[i][allocation] / delta;
	  list.addBidPoint(delta, unitPrice);
	  // DEBUG
	  log.info("Agent " + user.getName() + " is selling " + (-delta)
		   + " of type " + i + " allocation=" + allocation
		   + " for price " + unitPrice);
	  // Must check that another bid not already been placed to
	  // avoid double buy/sell. FIX THIS!!!
	  submitBid(auction, list);
	}
      }
    }
  }


  /*********************************************************************
   * SolveListener interface
   *********************************************************************/

  public boolean solveReport(int utility, int score, long calculationTime,
			     // [Client][InDay,OutDay,GoodHotel,
			     //		 E1Day,E2Day,E3Day]
			     int[][] latestAllocation) {
    int[] allocation = new int[AUCTION_NO];
    for (int client = 0, cn = latestAllocation.length; client < cn; client++) {
      int[] row = latestAllocation[client];
      int inday = row[0];
      if (inday > 0) {
	int outday = row[1];
	// Client is traveling
	allocation[inday - 1]++;
	allocation[4 + outday - 2]++;

	int hotel = 8;
	if (row[2] > 0) {
	  // Good hotel
	  hotel += 4;
	}
	for (int i = inday; i < outday; i++) {
	  allocation[hotel + i - 1]++;
	}
	for (int i = 0; i < 3; i++) {
	  int eday = row[3 + i];
	  if (eday > 0) {
	    allocation[16 + i * 4 + eday - 1]++;
	  }
	}
      }
    }
    clearAllocation();
    for (int i = 0; i < AUCTION_NO; i++) {
      setAllocation(i, allocation[i]);
    }

    // Stop after first report for now
    return false;
  }


  /*********************************************************************
   * printOwn
   *********************************************************************/

  // inflight((AllocDay1-Own|ProbablyOwn-BidQ[R][C])...)
  public void printOwn() {
    StringBuffer sb = new StringBuffer();
    sb.append("DummyAgent ").append(user.getName()).append(EOL);
    printOwn("inflights", 0, 4, sb);
    sb.append(',');
    printOwn("outflights", 4, 8, sb);
    sb.append(EOL);
    printOwn("good hotel", 8, 12, sb);
    sb.append(',');
    printOwn("cheap hotel", 12, 16, sb);
    sb.append(EOL);
    printOwn("wrestling", 16, 20, sb);
    sb.append(',');
    printOwn("amusement", 20, 24, sb);
    sb.append(',');
    printOwn("museum", 24, 28, sb);
    sb.append(EOL);
    log.fine(sb.toString());
  }

  private void printOwn(String type, int startAuction, int endAuction,
			StringBuffer sb) {
    sb.append(type).append('(');
    for (int i = startAuction; i < endAuction; i++) {
      Auction auction = getAuction(i);
//       Bid bid = getBid(i);
      Quote quote = auction.getQuote();
      sb.append('(').append(getAllocation(i)).append('-')
	.append(getOwn(i)).append('|')
	.append(quote.getHQW(user))
// 	.append('-').append(bid != null ? bid.getQuantity() : 0)
	;
      if (auction.isClosed()) {
	sb.append('C');
      }
      sb.append(')');
    }
    sb.append(')');
  }

} // DummyAgent
