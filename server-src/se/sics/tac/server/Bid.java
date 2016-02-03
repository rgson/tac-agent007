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
 * Bid
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-05
 * Updated : $Date: 2004/09/14 11:31:15 $
 *	     $Revision: 1.2 $
 * Purpose :
 *
 */

package se.sics.tac.server;

public class Bid {

  /** Bid update types */
  public final static char SUBMIT_TYPE = 's';
  public final static char REPLACE_TYPE = 'r';
  public final static char REJECT_TYPE = 'c';
  public final static char WITHDRAW_TYPE = 'w';
  public final static char TRANSACT_TYPE = 't';
  public final static char EXPIRE_TYPE = 'e';

  /** Reject reason */
  public final static int NOT_REJECTED = 0;
  public final static int SELF_TRANSACTION = 5;
  public final static int BUY_NOT_ALLOWED = 7;
  public final static int SELL_NOT_ALLOWED = 8;
  public final static int PRICE_NOT_BEAT = 15;
  public final static int ACTIVE_BID_CHANGED = 20;
  public final static int BID_NOT_IMPROVED = 21;
  public final static int BID_NOT_ACTIVE = 22;

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

  protected final Auction auction;
  protected final User user;
  protected final int bidID;

  private int rejectReason = NOT_REJECTED;
  private int processingState = UNPROCESSED;

  private long timeProcessed = 0L;
  private long timeClosed = 0L;

  private String originalBidString;
  private BidList bidList;

  public Bid(Auction auction, User user, int bidID, BidList list) {
    if (auction == null || list == null || user == null) {
      throw new NullPointerException(auction == null ? "auction"
				     : (user == null ? "user" : "bidList"));
    }
    this.bidID = bidID;
    this.auction = auction;
    this.user = user;
    this.bidList = list;
    this.originalBidString = list.getBidString();
  }

  public int getAuctionID() {
    return auction.getID();
  }

  public Auction getAuction() {
    return auction;
  }

  public User getUser() {
    return user;
  }

  public int getBidID() {
    return bidID;
  }

  public String getBidHash() {
    // This should be fixed to allow each auction to use its own type
    // of bid hash (identifier). FIX THIS!!!
    return bidList.getBidString(); // bidHash;
  }

  public String getOriginalBidHash() {
    // This should be fixed to allow each auction to use its own
    // type of bid hash (identifier). FIX THIS!!!
    return originalBidString;
  }

  public String getOriginalBidString() {
    return originalBidString;
  }

//   void setBidHash(String hash) {
//     bidHash = hash;
//   }

  public BidList getBidList() {
    if (bidList == null) {
      return bidList = new BidList();
    } else {
      return bidList;
    }
  }

  public long getTimeProcessed() {
    return timeProcessed;
  }

  public long getTimeClosed() {
    return timeClosed;
  }

  public boolean isActive() {
    return timeClosed == 0l && processingState != UNPROCESSED;
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

  public void setValid(long timeProcessed) {
    setState(SUBMIT_TYPE, VALID, NOT_REJECTED, timeProcessed, 0L);
  }

  public void setExpired(long timeClosed) {
    setState(EXPIRE_TYPE, EXPIRED, NOT_REJECTED, timeClosed, timeClosed);
  }

  public void setTransacted(long time) {
    if (bidList.size() == 0) {
      // The bid has been completely transacted
      setState(TRANSACT_TYPE, TRANSACTED, NOT_REJECTED, time, time);
    } else {
      setState(TRANSACT_TYPE, TRANSACTED, NOT_REJECTED, timeProcessed, 0L);
    }
  }

  public void setReplaced(long timeReplaced) {
    setState(REPLACE_TYPE, REPLACED, NOT_REJECTED, timeReplaced, timeReplaced);
  }

  public void setWithdrawn(long timeWithdraw) {
    setState(WITHDRAW_TYPE, WITHDRAWN, NOT_REJECTED,
	     timeWithdraw, timeWithdraw);
  }

  public void setRejected(int reason, long timeClosed) {
    setState(REJECT_TYPE, REJECTED, reason, timeClosed, timeClosed);
  }

  private void setState(char type, int processingState, int rejectReason,
			long timeProcessed, long timeClosed) {
    this.processingState = processingState;
    this.rejectReason = rejectReason;
    this.timeClosed = timeClosed;
    if (this.timeProcessed <= 0) {
      this.timeProcessed = timeProcessed;
    }
    // Do not publish expired bids for now (because all active bids will
    // expire when the auctions close)
    if (type != EXPIRE_TYPE) {
      auction.getMarket().bidUpdated(this, type);
    }
  }


  /*********************************************************************
   * TAC Message generation support. Move this. FIX THIS!!!
   *********************************************************************/

  public String generateFields(boolean recoverBid) {
    if (recoverBid) {
      return "<auctionID>" + auction.getID() + "</auctionID>"
	+ "<bidString>" + originalBidString + "</bidString>"
	+ "<expireMode>0</expireMode>"
	+ "<expireTime>0</expireTime>"
	+ "<divisible>0</divisible>"
	+ "<rejectReason>" + rejectReason + "</rejectReason>";

    } else {
      long w = processingState == WITHDRAWN ? (timeClosed / 1000) : 0L;
      return "<bidString>" + bidList.getBidString() + "</bidString>"
	+ "<bidHash>" + getBidHash() + "</bidHash>"
	+ "<rejectReason>" + rejectReason + "</rejectReason>"
	+ "<processingState>" + processingState + "</processingState>"
	+ "<timeProcessed>" + (timeProcessed / 1000) + "</timeProcessed>"
	+ "<timeClosed>" + (timeClosed / 1000) + "</timeClosed>"
	+ "<withdrawState>0</withdrawState>"
	+ "<timeWithdrawRequest>" + w + "</timeWithdrawRequest>"
	+ "<timeWithdraw>" + w + "</timeWithdraw>";
    }
  }

} // Bid
