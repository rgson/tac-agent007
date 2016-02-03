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
 * Auction
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-05
 * Updated : $Date: 2004/09/07 14:46:02 $
 *	     $Revision: 1.4 $
 */

package se.sics.tac.server;
import java.util.Random;
import java.util.Timer;

public abstract class Auction {

  public static final int ALLOW_WITHDRAW = 1;
  public static final int ALLOW_SELL = 2;
  public static final int ALLOW_BUY = 4;

  protected final Market market;
  protected final int auctionID;
  protected final Quote quote;
  protected final int type;
  protected final int day;
  private final long auctionRules;
  private long openTime;
  private long closeTime;
  private long nextQuoteTime = -1L;

  private int auctionStatus = Quote.AUCTION_INITIALIZING;

  protected Auction(Market market, int type, int day, long auctionRules) {
    this.market = market;
    this.type = type;
    this.day = day;
    this.auctionID = market.addAuction(this);
    this.quote = new Quote(this);
    this.auctionRules = auctionRules;
  }

  protected long getServerTime() {
    return market.infoManager.getServerTime();
  }

  protected Random getRandom() {
    return market.infoManager.getRandom();
  }

  protected Timer getTimer() {
    return market.infoManager.getTimer();
  }

  public Market getMarket() {
    return market;
  }

  public int getID() {
    return auctionID;
  }

  public int getType() {
    return type;
  }

  public int getDay() {
    return day;
  }

  public Quote getQuote() {
    return quote;
  }

  public long getOpenTime() {
    return openTime;
  }

  protected void setOpenTime(long openTime) {
    this.openTime = openTime;
  }

  public long getCloseTime() {
    return closeTime;
  }

  protected void setCloseTime(long closeTime) {
    this.closeTime = closeTime;
  }

  public long getNextQuoteTime() {
    return nextQuoteTime;
  }

  protected void setNextQuoteTime(long nextQuoteTime) {
    this.nextQuoteTime = nextQuoteTime;
  }

  public int getAuctionStatus() {
    return auctionStatus;
  }

  public boolean isOpen() {
    return auctionStatus == Quote.AUCTION_INTERMEDIATE_CLEAR
      || auctionStatus == Quote.AUCTION_FINAL_CLEAR;
  }

  public boolean isClosed() {
    return auctionStatus == Quote.AUCTION_CLOSED;
  }

  public synchronized final Bid submit(User user, BidList bidList)
    throws TACException
  {
    return submit(user, bidList, null, null);
  }

  public synchronized final Bid replace(User user, BidList bidList,
					int bidID, String oldBidHash)
    throws TACException
  {
    Bid oldBid = market.getBid(bidID);
    if ((oldBid == null)
	|| (oldBid.getAuction() != this)
	|| (oldBid.getUser() != user)) {
      throw new TACException(TACException.BID_NOT_FOUND);
    }
    return submit(user, bidList, oldBid, oldBidHash);
  }

  // NOTE: MAY ONLY BE CALLED SYNCHRONIZED
  private Bid submit(User user, BidList bidList,
		     Bid oldBid, String oldBidHash) throws TACException {
    if (isClosed()) {
      throw new TACException(TACException.AUCTION_CLOSED);
    }

    Bid bid = market.createBid(this, user, bidList);
    if (bidList.isSelfTransacting()) {
      bid.setRejected(Bid.SELF_TRANSACTION, getServerTime());
    } else if (((auctionRules & ALLOW_SELL) == 0L)
	       && bidList.hasSellPoints()) {
      bid.setRejected(Bid.SELL_NOT_ALLOWED, getServerTime());
    } else if (((auctionRules & ALLOW_BUY) == 0L)
	       && bidList.hasBuyPoints()) {
      bid.setRejected(Bid.BUY_NOT_ALLOWED, getServerTime());
    } else if (oldBid != null) {
      if (!oldBidHash.equals(oldBid.getBidHash())) {
	bid.setRejected(Bid.ACTIVE_BID_CHANGED, getServerTime());
      } else if (!isBidActive(oldBid)
		 && (oldBid.isActive()
		     || (oldBid.getProcessingState() != Bid.TRANSACTED))) {
	// Allow replacement of fully transacted bids as long as
	// the bid hash matches
	bid.setRejected(Bid.BID_NOT_ACTIVE, getServerTime());
      } else {
	submitBid(bid);
      }
    } else {
      submitBid(bid);
    }
    return bid;
  }

  protected abstract void submitBid(Bid bid);

  public synchronized final boolean withdraw(User user) {
    if ((auctionRules & ALLOW_WITHDRAW) == 0 || isClosed()) {
      return false;
    }
    Bid bid = getActiveBid(user);
    if (bid != null) {
      withdrawBid(bid);
      return true;
    }
    return false;
  }

  // Default is that withdrawal of bids are not supported.
  protected void withdrawBid(Bid bid) {
  }

  protected abstract boolean isBidActive(Bid bid);

  protected abstract Bid getActiveBid(User user);

  synchronized final void open(long time) {
    if (auctionStatus == Quote.AUCTION_INITIALIZING) {
      auctionStatus = Quote.AUCTION_INTERMEDIATE_CLEAR;
      openAuction(time);
    }
  }

  protected abstract void openAuction(long time);

  synchronized final void close(long time) {
    if (auctionStatus < Quote.AUCTION_CLOSED) {
      auctionStatus = Quote.AUCTION_CLOSED;
      try {
	closeAuction(time);
      } finally {
	nextQuoteTime = -1L;
	if (quote.getFinalClearTime() <= 0) {
	  quote.setFinalClearTime(quote.getLastClearTime());
	}
	quoteUpdated(time);
	market.auctionClosed(this);
      }
    }
  }

  protected abstract void closeAuction(long time);

  // Called whenever the quotes should be updated
  private void updateQuote(long time) {
    updateAuctionQuote(time);
    quoteUpdated(time);
  }

  protected abstract void updateAuctionQuote(long time);


  /*********************************************************************
   * Auction modifiers
   *********************************************************************/

  protected void setQuoteInfo(double lastAsk, double lastBid) {
    quote.setQuoteInfo(lastAsk, lastBid);
  }

  protected void setQuoteInfo(double lastAsk, double lastBid,
			      long nextQuoteTime) {
    quote.setQuoteInfo(lastAsk, lastBid);
    setNextQuoteTime(nextQuoteTime);
  }

  protected void setHQW(int[] hqw) {
    quote.setHQW(hqw);
  }

  protected void setClearInfo(long lastClearTime, double lastClearPrice,
			      long nextClearTime) {
    quote.setClearInfo(lastClearTime, lastClearPrice, nextClearTime);
  }

  protected void setFinalClearTime(long finalClearTime) {
    quote.setFinalClearTime(finalClearTime);
  }

  // Notify everyone that the quote has been updated
  protected void quoteUpdated(long time) {
    quote.setLastQuoteTime(time);
    market.quoteUpdated(quote);
  }


  /*********************************************************************
   * Interface to the ticker (notification about open, close, and quote update)
   **********************************************************************/

  synchronized final long tickPerformed(long currentTime) {
    switch (auctionStatus) {
    case Quote.AUCTION_INITIALIZING:
      if (openTime > 0) {
	if (currentTime >= openTime) {
	  // Time to open the auction
	  open(openTime);
	  return currentTime;
	} else {
	  return openTime;
	}
      }
      return currentTime;

    case Quote.AUCTION_INTERMEDIATE_CLEAR:
    case Quote.AUCTION_FINAL_CLEAR:
      if (currentTime >= nextQuoteTime && nextQuoteTime > 0) {
	long time = nextQuoteTime;
	nextQuoteTime = 0L;
	updateQuote(time);
      }
      if (closeTime > 0) {
	if (currentTime >= closeTime) {
	  // Time to close the auction
	  close(closeTime);
	  return Long.MAX_VALUE;
	} else {
	  return nextQuoteTime < closeTime && nextQuoteTime > 0
	    ? nextQuoteTime
	    : closeTime;
	}
      } else {
	return nextQuoteTime > 0 ? nextQuoteTime : Long.MAX_VALUE;
      }

    default:
      return Long.MAX_VALUE;
    }
  }

} // Auction
