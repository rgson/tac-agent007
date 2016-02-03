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
 * Quote
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-05
 * Updated : $Date: 2004/05/04 15:48:18 $
 *	     $Revision: 1.1 $
 */

package se.sics.tac.server;
import se.sics.tac.util.TACFormatter;

public class Quote {

  public final static int AUCTION_INITIALIZING = 0;
  public final static int AUCTION_INTERMEDIATE_CLEAR = 1;
  public final static int AUCTION_FINAL_CLEAR = 2;
  public final static int AUCTION_CLOSED = 3;

  private final static String[] statusName = new String[] {
    "Initializing", "Intermediate Clear", "Final Clear", "Closed"
  };

  protected final Auction auction;

  protected double askPrice;
  protected double bidPrice;

  protected long lastQuoteTime = 0L;

  protected double lastClearPrice = 0.0;
  protected long lastClearTime = 0L;
  protected long finalClearTime = -1L;
  protected long nextClearTime = -1L;

  // HQW in the form AgentID,BidID,HQW
  protected int[] hqw;

  public Quote(Auction auction) {
    this.auction = auction;
  }

  public int getAuctionID() {
    return auction.getID();
  }

  public Auction getAuction() {
    return auction;
  }

  public boolean isAuctionClosed() {
    return auction.isClosed();
  }

  public String getAuctionStatusAsString() {
    return statusName[auction.getAuctionStatus()];
  }

  public int getAuctionStatus() {
    return auction.getAuctionStatus();
  }

  public double getAskPrice() {
    return askPrice;
  }

  public double getBidPrice() {
    return bidPrice;
  }

  public long getNextQuoteTime() {
    return auction.getNextQuoteTime();
  }

  public long getLastQuoteTime() {
    return lastQuoteTime;
  }

  protected void setLastQuoteTime(long lastQuoteTime) {
    this.lastQuoteTime = lastQuoteTime;
  }

  public long getLastClearTime() {
    return lastClearTime;
  }

  public long getFinalClearTime() {
    return finalClearTime;
  }

  protected void setFinalClearTime(long finalClearTime) {
    this.finalClearTime = finalClearTime;
  }

  public int getHQW(User user) {
    int[] hqw = this.hqw;
    if (hqw != null) {
      int aid = user.getID();
      for (int i = 0, n = hqw.length; i < n; i += 3) {
	if (hqw[i] == aid) {
	  return hqw[i + 2];
	}
      }
    }
    return 0;
  }

  protected void setQuoteInfo(double lastAsk, double lastBid) {
    this.askPrice = lastAsk;
    this.bidPrice = lastBid;
  }

  protected void setHQW(int[] hqw) {
    this.hqw = hqw;
  }

  protected void setClearInfo(long lastClearTime, double lastClearPrice,
			      long nextClearTime) {
    this.lastClearTime = lastClearTime;
    this.lastClearPrice = lastClearPrice;
    this.nextClearTime = nextClearTime;
  }

  // Perhaps should be in auction. FIX THIS!!!
  protected StringBuffer toCsv(StringBuffer sb) {
    int[] hqw = this.hqw;
    sb.append(',').append(auction.getID())
      .append(',').append(toString4(askPrice))
      .append(',').append(toString4(bidPrice));
    if (hqw != null) {
      for (int i = 0, n = hqw.length; i < n; i += 3) {
	sb.append(',').append(hqw[i])
	  .append(',').append(hqw[i + 2]);
      }
    }
    return sb;
  }


  /*********************************************************************
   * TAC Message generation support. Move this. FIX THIS!!!
   *********************************************************************/

  public synchronized String generateFields(User user, int bidID) {
    return "<lastAskPrice>" + toString4(askPrice) + "</lastAskPrice>"
      + "<lastBidPrice>" + toString4(bidPrice) + "</lastBidPrice>"
      + "<lastQuoteTime>" + (lastQuoteTime / 1000) + "</lastQuoteTime>"
      + "<nextQuoteTime>" + (auction.getNextQuoteTime() / 1000)
      + "</nextQuoteTime>"
      + "<lastClearPrice>" + toString4(lastClearPrice) + "</lastClearPrice>"
      + "<lastClearTime>" + (lastClearTime / 1000) + "</lastClearTime>"
      + "<finalClearTime>" + (finalClearTime / 1000) + "</finalClearTime>"
      + "<nextClearTime>" + (nextClearTime / 1000) + "</nextClearTime>"
      + getHQW(user, bidID)
      + "<auctionStatus>" + auction.getAuctionStatus() + "</auctionStatus>";
  }

  private String getHQW(User user, int bidID) {
    int[] hqw = this.hqw;
    Bid bid;
    if (hqw != null) {
      int aid = user.getID();
      for (int i = 0, n = hqw.length; i < n; i += 3) {
	if (hqw[i] == aid) {
	  if (bidID == hqw[i + 1]) {
	    return "<hypotheticalQuantityWon>" + hqw[i + 2]
	      + "</hypotheticalQuantityWon>";
	  } else if (((bid = auction.market.getBid(bidID)) != null)
		     && bid.isActive()
		     && (bid.getAuction() == auction)
		     && (bid.getUser() == user)) {
	    // The bid exists in the right auction, has the right
	    // submitter, and is still open. However since it is not
	    // in the hqw it has probably not been processed yet.

	    // One space indicates 'not calculated yet'
	    return "<hypotheticalQuantityWon>" + ' '
	      + "</hypotheticalQuantityWon>";
	  }
	  break;
	}
      }
    }
    return "<hypotheticalQuantityWon> </hypotheticalQuantityWon>";
  }

  private String toString4(double v) {
    return TACFormatter.toString4(v);
  }

} // Quote
