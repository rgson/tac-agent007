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
 * EngAscAuction
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-14
 * Updated : $Date: 2004/05/04 15:48:18 $
 *	     $Revision: 1.1 $
 * Purpose :
 *
 */

package se.sics.tac.server.classic;
import com.botbox.util.ArrayUtils;
import se.sics.tac.server.*;

public class EngAscAuction extends Auction {

  private int quotePeriod = 60000;
  private int unitsToSell = 16;
  private Bid[] activeBids = new Bid[8];
  private int bidNumber = 0;

  public EngAscAuction(Market market, int type, int day, long closeTime) {
    super(market, type, day, ALLOW_BUY);
    setCloseTime(closeTime);
  }

  // Sets the quote period in seconds.
  // Must be called before the auction is opened
  void setQuotePeriod(int quotePeriod) {
    this.quotePeriod = quotePeriod * 1000;
  }

  protected void openAuction(long openTime) {
    // Quote updates once per minute on the minute
    // ask, bid, nextQuoteTime
    setQuoteInfo(0, 0, openTime + quotePeriod);
  }

  protected synchronized void closeAuction(long time) {
    int[] hqw = performBidsUpdate(-1L);
    double askPrice = quote.getAskPrice();

    // Report transactions
    if (hqw != null) {
      for (int i = 0, n = hqw.length; i < n; i += 3) {
	int won = hqw[i + 2];
	if (won > 0) {
	  Bid bid = market.getBid(hqw[i + 1]);
	  bid.getBidList().removeBuyPoints(won, askPrice);
	  market.createTransaction(this, bid, null, won, askPrice, time);
	  bid.setTransacted(time);
	}
      }
    }

    // lastClearTime, lastClearPrice, nextClearTime
    setClearInfo(time, askPrice, -1L);
    setHQW(null);

    // All bids not completely transacted are now expired
    for (int i = 0; i < bidNumber; i++) {
      if (activeBids[i].isActive()) {
	activeBids[i].setExpired(time);
      }
    }
    bidNumber = 0;
  }

  protected synchronized boolean isBidActive(Bid bid) {
    return ArrayUtils.indexOf(activeBids, 0, bidNumber, bid) >= 0;
  }

  protected synchronized Bid getActiveBid(User user) {
    for (int i = 0; i < bidNumber; i++) {
      if (activeBids[i].getUser() == user) {
	return activeBids[i];
      }
    }
    return null;
  }

  protected synchronized void submitBid(Bid bid) {
    BidList list = bid.getBidList();
    int hqw = quote.getHQW(bid.getUser());
    double askPrice = quote.getAskPrice();
    if (hqw > 0) {
      if (!checkPriceBeat(list, hqw, askPrice)) {
	bid.setRejected(Bid.BID_NOT_IMPROVED, getServerTime());
      } else {
	addNewBid(bid);
      }
    } else if (!checkPriceBeat(list, 1, askPrice)) {
      bid.setRejected(Bid.PRICE_NOT_BEAT, getServerTime());
    } else {
      addNewBid(bid);
    }
  }

  // Note: MAY ONLY BE CALLED SYNCHRONIZED
  private void addNewBid(Bid bid) {
    User user = bid.getUser();
    long time = getServerTime();
    for (int i = 0; i < bidNumber; i++) {
      if (activeBids[i].getUser() == user) {
	activeBids[i].setReplaced(time);
	// The bids must be in order because earlier bids have higher
	// priority over later bids (with same bid price)
	bidNumber--;
	if (i < bidNumber) {
	  System.arraycopy(activeBids, i + 1,
			   activeBids, i, bidNumber - i);
	}
	activeBids[bidNumber] = null;
	break;
      }
    }

    bid.setValid(time);
    if (bidNumber == activeBids.length) {
      activeBids = (Bid[]) ArrayUtils.setSize(activeBids, bidNumber + 16);
    }
    activeBids[bidNumber++] = bid;
  }

  private boolean checkPriceBeat(BidList list, int units, double price) {
    // We already know that no sell points can exist in the bid list
    for (int i = 0, n = list.size(); i < n && units > 0; i++) {
      // Allow some truncation errors
      if (list.getPriceAt(i) >= (price + 0.99)) {
	units -= list.getQuantityAt(i);
      }
    }
    return units <= 0;
  }

  protected synchronized void updateAuctionQuote(long time) {
    // Normal quote update
    performBidsUpdate(time + quotePeriod);
  }

  // Note: MAY ONLY BE CALLED SYNCHRONIZED
  private int[] performBidsUpdate(long nextQuoteTime) {
    // Allow one more bid among the accepted bids because we need the
    // highest drop out bid for the bid price in the next quote.
    Bid[] bids = new Bid[unitsToSell + 1];
    double[] prices = new double[unitsToSell + 1];
    int bidsLen = 0;
    int index;

    for (int i = 0; i < bidNumber; i++) {
      Bid bid = activeBids[i];
      BidList list = bid.getBidList();
      for (int j = 0, n = list.size(); j < n; j++) {
	double price = list.getPriceAt(j);
	for (int p = 0, pn = list.getQuantityAt(j); p < pn; p++) {
	  if (bidsLen <= unitsToSell) {
	    // No bid for all units yet
	    bids[bidsLen] = bid;
	    prices[bidsLen++] = price;
	  } else {
	    index = getMinPrice(prices, bidsLen);
	    if (prices[index] < price) {
	      bids[index] = bid;
	      prices[index] = price;
	    } else {
	      // No more units for this price can be added.
	      // (this is also a constraint because a limited unit bids
	      //  can be added for a specific price which means this
	      //  iteration will break after at most unitsToSell loops)
	      break;
	    }
	  }
	}
      }
    }

    double bidPrice;
    double askPrice;
    int[] hqw;
    if (bidsLen == 0) {
      // No bids
      askPrice = 0.0;
      bidPrice = quote.getBidPrice();
      hqw = null;
    } else {
      // Generate hqw
      hqw = new int[bidNumber * 3];
      // Initialize the hypothetical quantity won
      for (int i = 0, j = 0; i < bidNumber; i++, j += 3) {
	Bid bid = activeBids[i];
	hqw[j] = bid.getUser().getID();
	hqw[j + 1] = bid.getBidID();
      }

      // Find new bid price and remove the drop out bid
      if (bidsLen > unitsToSell) {
	// The bid list contains the highest dropped out unit price
	index = getMinPrice(prices, bidsLen);
	bidPrice = prices[index];
	// Remove the drop out bid
	bidsLen--;
	prices[index] = prices[bidsLen];
	bids[index] = bids[bidsLen];
      } else {
	bidPrice = quote.getBidPrice();
      }

      for (int i = 0, hqwLen = hqw.length; i < bidsLen; i++) {
	index = indexOfBid(hqw, 0, hqwLen, bids[i]);
	if (index >= 0) {
	  hqw[index + 2]++;
	} else {
	  // This should not be possible. FIX THIS!!!
	  java.util.logging.Logger.global
	    .severe("COULD NOT FIND ACTIVE BID " + bids[i].getBidID()
		    + " AMONG ACTIVE BIDS!!!");
	}
      }

      // Calculate next ask price. The ask price is the lowest price among
      // the accepted bids and 0 if there is no bid for all the units.
      askPrice = bidsLen >= unitsToSell
	? prices[getMinPrice(prices, bidsLen)]
	: 0.0;
    }
    setQuoteInfo(askPrice, bidPrice, nextQuoteTime);
    setHQW(hqw);
    return hqw;
  }

  private int indexOfBid(int[] hqw, int start, int len, Bid bid) {
    int id = bid.getBidID();
    for (int i = start; i < len; i += 3) {
      if (hqw[i + 1] == id) {
	return i;
      }
    }
    return -1;
  }

  // Note: assumes len > 0
  private int getMinPrice(double[] prices, int len) {
    int index = 0;
    double price = prices[0];
    for (int i = 1; i < len; i++) {
      if (prices[i] < price) {
	index = i;
	price = prices[i];
      }
    }
    return index;
  }

} // EngAscAuction
