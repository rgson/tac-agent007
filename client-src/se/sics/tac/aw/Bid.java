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
 * Updated : $Date: 2005/06/07 19:06:15 $
 *	     $Revision: 1.1 $
 */

package se.sics.tac.aw;
import java.util.StringTokenizer;

public class Bid {

  public final static String EMPTY_BID_STRING = "()";

  public final static int NO_ID = -1;

  /** Reject reason */
  public final static int NOT_REJECTED = 0;
  public final static int SELF_TRANSACTION = 5;
  public final static int BUY_NOT_ALLOWED = 7;
  public final static int SELL_NOT_ALLOWED = 8;
  public final static int PRICE_NOT_BEAT = 15;
  public final static int ACTIVE_BID_CHANGED = 20;
  public final static int BID_NOT_IMPROVED = 21;
  public final static int BID_NOT_ACTIVE = 22;

  private final static int INCREMENT = 10;

  private final static String[] rejectName = {
    "not rejected",
    "self transaction",
    "buy not allowed",
    "sell not allowed",
    "price not beat",
    "active bid changed",
    "bid not improved",
    "bid not active"
  };
  private final static int[] rejectCode = {
    NOT_REJECTED,
    SELF_TRANSACTION,
    BUY_NOT_ALLOWED,
    SELL_NOT_ALLOWED,
    PRICE_NOT_BEAT,
    ACTIVE_BID_CHANGED,
    BID_NOT_IMPROVED,
    BID_NOT_ACTIVE
  };

  /** Processing state */
  public final static int UNPROCESSED = 0;
  public final static int REJECTED = 1;
  public final static int VALID = 2;
  public final static int WITHDRAWN = 3;
  public final static int TRANSACTED = 4;
  public final static int REPLACED = 5;
  public final static int EXPIRED = 6;

  private final static String[] stateName = {
    "unprocessed",
    "rejected",
    "valid",
    "withdrawn",
    "transacted",
    "replaced",
    "expired"
  };

  private final int auction;
  private int id = NO_ID;
  private int rejectReason;
  private String bidHash;
  private int processingState = UNPROCESSED;

  private String bidString;

  private long timeProcessed;
  private long timeClosed;

  private int len;
  private int[] quantity;
  private float[] price;

  private Bid replacing;
  private long timeSubmitted = 0L;

  // Transaction clearing
  private int clearID = -1;
  private String clearHash;
  private String clearString;
  private int clearQuantity;

  public Bid(int auction) {
    this.auction = auction;
  }

  public Bid(Bid oldBid) {
    this.auction = oldBid.auction;
  }

  Bid(Bid oldBid, String bidString, String bidHash) {
    this.id = oldBid.id;
    this.auction = oldBid.auction;
    this.bidString = bidString;
    this.bidHash = bidHash;
    this.rejectReason = oldBid.rejectReason;
    this.processingState = oldBid.processingState;
    this.timeProcessed = oldBid.timeProcessed;
    this.timeClosed = timeClosed;
    this.timeSubmitted = timeSubmitted;
    parseBidString(bidString);
  }

  // Should this be public? FIX THIS!!
  private boolean isSubmitted() {
    return timeSubmitted > 0;
  }

  void submitted() {
    if (timeSubmitted > 0) {
      throw new IllegalStateException("Bid already submitted");
    }
    timeSubmitted = System.currentTimeMillis();
  }

  void setID(int bidID) {
    if (id != NO_ID) {
      throw new IllegalStateException("Bid ID already set " + id);
    }
    id = bidID;
  }

  void setRejectReason(int reason) {
    rejectReason = reason;
  }

  // This bid is replacing the included bid
  // It is not neccesary so that this bid is replaced by replaceBid
  // - it can also be a submitBid
  void setReplacing(Bid bid) {
    replacing = bid;
  }

  public Bid getReplacing() {
    return replacing;
  }

  // Only used when recovering bids
  void setBidHash(String hash) {
    bidHash = hash;
  }

  public String getBidHash() {
    return bidHash;
  }

  void setTimeProcessed(long time) {
    timeProcessed = time * 1000;
  }

  public long getTimeProcessed() {
    return timeProcessed;
  }

  void setTimeClosed(long time) {
    timeClosed = time * 1000;
  }

  public long getTimeClosed() {
    return timeClosed;
  }

  void setProcessingState(int state) {
    processingState = state;
  }

  public int getProcessingState() {
    return processingState;
  }

  public String getProcessingStateAsString() {
    int state = this.processingState;
    return (state >= UNPROCESSED) && (state <= stateName.length)
      ? stateName[state]
      : Integer.toString(state);
  }

  public boolean isPreliminary() {
    return (id == NO_ID || processingState == UNPROCESSED);
  }

  public boolean isRejected() {
    return rejectReason != NOT_REJECTED;
  }

  public int getRejectReason() {
    return rejectReason;
  }

  public String getRejectReasonAsString() {
    int reason = this.rejectReason;
    for (int i = 0, n = rejectCode.length; i < n; i++) {
      if (rejectCode[i] == reason) {
	return rejectName[i];
      }
    }
    return Integer.toString(reason);
  }

  public int getAuction() {
    return auction;
  }

  public int getID() {
    return id;
  }

  public synchronized void addBidPoint(int quantity, float unitPrice) {
    if (isSubmitted()) {
      throw new IllegalStateException("Bid already submitted");
    }
    if (unitPrice < 0) {
      throw new IllegalArgumentException("Negative price not allowed");
    }
    // This is a "trick" for checking that this auction allow "sell"
    if (auction < TACAgent.MIN_ENTERTAINMENT && quantity < 0) {
      throw new IllegalArgumentException("Not allowed to sell in auction " +
					 auction);
    }

    realloc();
    this.quantity[len] = quantity;
    this.price[len++] = unitPrice;
    this.bidString = null;
  }

  public int getNoBidPoints() {
    return len;
  }

  public int getQuantity() {
    int len = this.len;
    int[] quant = quantity;
    int q = 0;
    if (quant != null) {
      for (int i = 0; i < len; i++) {
	q += quant[i];
      }
    }
    return q;
  }

  public int getQuantity(int index) {
    if (quantity == null) {
      throw new IndexOutOfBoundsException("Index: " + index
					  + ", Size: " + len);
    }
    return quantity[index];
  }

  public float getPrice(int index) {
    if (price == null) {
      throw new IndexOutOfBoundsException("Index: " + index
					  + ", Size: " + len);
    }
    return price[index];
  }

  public String getBidString() {
    String bidString = this.bidString;
    if (bidString == null){
      StringBuffer bid = new StringBuffer();
      bid.append('(');
      for (int i = 0; i < len; i++) {
	bid.append('(').append(quantity[i]).append(' ').
	  append(price[i]).append(')');
      }
      bid.append(')');
      this.bidString = bidString = bid.toString();
    }
    return bidString;
  }

  // Only used when recovering bids
  void setBidString(String bidString) {
    this.bidString = bidString;
    parseBidString(bidString);
  }

  public boolean same(Bid bid) {
    return this == bid || ((bid != null && id == bid.id) && (id != NO_ID));
  }

  void setBidTransacted(int clearID, String bidHash, String bidString) {
    this.clearID = clearID;
    this.clearHash = bidHash;
    this.clearString = bidString;
  }

  int getClearID() {
    return clearID;
  }

  String getClearString() {
    return clearString;
  }

  String getClearHash() {
    return clearHash;
  }

  public boolean isAwaitingTransactions() {
    return clearID >= 0;
  }

  private synchronized void realloc() {
    if (quantity == null) {
      quantity = new int[INCREMENT];
      price = new float[INCREMENT];
    } else if (len == quantity.length) {
      int[] tmp = new int[len + INCREMENT];
      System.arraycopy(quantity, 0, tmp, 0, len);

      float[] tmp2 = new float[len + INCREMENT];
      System.arraycopy(price, 0, tmp2, 0, len);

      quantity = tmp;
      price = tmp2;
    }
  }

  private void parseBidString(String bidString) {
    StringTokenizer tok = new StringTokenizer(bidString, "() \t\r\n");
    while (tok.hasMoreTokens()) {
      int q = (int) Float.parseFloat(tok.nextToken());
      float p = Float.parseFloat(tok.nextToken());
      addBidPoint(q, p);
    }
  }

  static int mapProcessingState(int state) {
    if (state == 7) {
      return TRANSACTED;
    } else if (state == 9) {
      return UNPROCESSED;
    }
    return state;
  }

  static int mapRejectReason(int state) {
    switch (state) {
    case 1:
      return ACTIVE_BID_CHANGED;
    case 11:
    case 12:
    case 13:
    case 14:
      return BID_NOT_IMPROVED;
    case 15:
    case 16:
      return PRICE_NOT_BEAT;
    default:
      return state;
    }
  }
}
