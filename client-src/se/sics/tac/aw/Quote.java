/**
 * TAC AgentWare
 * http://www.sics.se/tac        tac-dev@sics.se
 *
 * Copyright (c) 2001-2005 SICS AB. All rights reserved.
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
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 23 April, 2002
 * Updated : $Date: 2005/06/07 19:06:16 $
 *	     $Revision: 1.1 $
 */

package se.sics.tac.aw;

public class Quote {

  public final static int AUCTION_INITIALIZING = 0;
  public final static int AUCTION_INTERMEDIATE_CLEAR = 1;
  public final static int AUCTION_FINAL_CLEAR = 2;
  public final static int AUCTION_CLOSED = 3;

  private final static String[] statusName = new String[] {
    "Initializing", "Intermediate Clear", "Final Clear", "Closed"
  };

  private final int auction;
  private int hqw = -1;
  private int status = AUCTION_INITIALIZING;

  private long nextQuoteTime = -1L;
  private long lastQuoteTime = 0L;

  private float askPrice;
  private float bidPrice;
  private Bid bid;

  Quote(int auctionNo) {
    auction = auctionNo;
  }

  void clearAll() {
    askPrice = 0f;
    bidPrice = 0f;
    nextQuoteTime = -1L;
    lastQuoteTime = 0L;
    bid = null;
    status = AUCTION_INITIALIZING;
    hqw = -1;
  }

  void setAskPrice(float ask) {
    this.askPrice = ask;
  }

  public float getAskPrice() {
    return askPrice;
  }

  void setBidPrice(float bid) {
    this.bidPrice = bid;
  }

  public float getBidPrice() {
    return bidPrice;
  }

  public int getAuction() {
    return auction;
  }

  void setHQW(int hqw) {
    this.hqw = hqw;
  }

  public int getHQW() {
    return hqw;
  }

  public boolean hasHQW(Bid bid) {
    return (this.bid != null && bid == this.bid && hqw >= 0);
  }

  void setAuctionStatus(int status) {
    this.status = status;
  }

  public boolean isAuctionClosed() {
    return status == AUCTION_CLOSED;
  }

  public int getAuctionStatus() {
    return status;
  }

  public String getAuctionStatusAsString() {
    return statusName[status];
  }

  public long getNextQuoteTime() {
    return nextQuoteTime;
  }

  public void setNextQuoteTime(long nextQuoteTime) {
    this.nextQuoteTime = nextQuoteTime;
  }

  public long getLastQuoteTime() {
    return lastQuoteTime;
  }

  public void setLastQuoteTime(long lastQuoteTime) {
    this.lastQuoteTime = lastQuoteTime;
  }

  void setBid(Bid bid) {
    this.bid = bid;
  }

  public Bid getBid() {
    return bid;
  }

}
