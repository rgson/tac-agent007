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
 * BuiltinAgent
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-25
 * Updated : $Date: 2004/09/07 14:46:02 $
 *	     $Revision: 1.6 $
 */

package se.sics.tac.server;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.botbox.util.ArrayUtils;

public abstract class BuiltinAgent {

  private final Logger log =
    Logger.getLogger(getClass().getName());

  protected final Market market;
  protected final User user;

  // Auction and ownership information
//   private Bid[] bids = new Bid[28];
  private int[] owns = new int[28];
  private double[] costs = new double[28];

  private int[] allocate = new int[28];

  private boolean isRunning = false;

  protected BuiltinAgent(Market market, User user) {
    if (market == null) {
      throw new NullPointerException("market");
    }
    if (user == null) {
      throw new NullPointerException("user");
    }

    this.market = market;
    this.user = user;
  }

  final void start() {
    isRunning = true;

    // Retrieve the endowments
    Transaction[] transactions = market.getTransactions();
    if (transactions != null) {
      for (int i = 0, n = transactions.length; i < n; i++) {
	Transaction t = transactions[i];
	if (t != null && t.isEndowment() && t.getBuyer() == user) {
	  updateOwn(t);
	}
      }
    }

    gameStarted();
  }

  final void stop() {
    isRunning = false;
    gameStopped();
  }


  /*********************************************************************
   * Information retrieval
   *********************************************************************/

  public User getUser() {
    return user;
  }

  public boolean isRunning() {
    return isRunning;
  }

  public int getAuctionIndex(Auction auction) {
    int type = auction.getType();
    int day = auction.getDay();
    // Outflights are on day 2-5 while the rest is on day 1-4
    return type * 4 + day - (type == 1 ? 2 : 1);
  }

  public Auction getAuction(int auction) {
    int type = auction / 4;
    int day = (auction % 4) + 1;
    if (type == 1) {
      day++;
    }
    return market.getAuction(type, day);
  }

  public int getOwn(int auction) {
    return auction >= owns.length ? 0 : owns[auction];
  }

//   public Bid getBid(int auction) {
//     return auction >= bids.length ? null : bids[auction];
//   }

  public int getAllocation(int auction) {
    return auction >= allocate.length ? 0 : allocate[auction];
  }

  public void setAllocation(int auction, int alloc) {
    if (allocate.length <= auction) {
      allocate = ArrayUtils.setSize(allocate, auction + 5);
    }
    allocate[auction] = alloc;
  }

  public void clearAllocation() {
    for (int i = 0, n = allocate.length; i < n; i++) {
      allocate[i] = 0;
    }
  }


  /*********************************************************************
   * General Information
   *********************************************************************/

  protected Random getRandom() {
    return market.getRandom();
  }

  protected long getServerTime() {
    return market.getServerTime();
  }

  protected long getGameTime() {
    return market.getGameTime();
  }

  protected long getGameTimeLeft() {
    return market.getGameTimeLeft();
  }


  /*********************************************************************
   * Bidding
   *********************************************************************/

  public boolean submitBid(Auction auction, BidList list) {
    if (auction.isClosed()) {
      return false;
    }
    String bidString = list.getBidString();
    try {
      Bid bid = auction.submit(user, list);
      if (bid.getRejectReason() == Bid.NOT_REJECTED) {
	// Bid was submitted
	log.info("agent " + user.getName()// DEBUG
		 + " placed bid " + bidString
		 + " at auction "
		 + auction.getType()
		 + " day " + auction.getDay());
	return true;
      }
    } catch (TACException e) {
      log.log(Level.WARNING, "agent " + user.getName()
	      + " could not submit bid " + bidString
	      + " at auction " + auction.getType() + " day "
	      + auction.getDay(), e);
    }
    return false;
  }

//   public void replaceBid(Bid oldBid, Bid bid) {
//     if (getGameID() < 0) {
//       throw new IllegalStateException("No game playing");
//     }
//     int auction = bid.getAuction();
//     int oldAuction = oldBid.getAuction();
//     if (oldBid.isPreliminary()) {
//       throw new IllegalStateException("Old bid is still preliminary");
//     }
//     if (auction != oldAuction) {
//       throw new IllegalArgumentException("Bids do not have same AuctionID");
//     }
//     bid.submitted();
//     if (oldBid != bids[auction]) {
//       bid.setRejectReason(Bid.ACTIVE_BID_CHANGED);
//       bid.setProcessingState(Bid.REJECTED);
//       try {
// 	agent.bidRejected(bid);
//       } catch (Exception e) {
// 	log.log(Level.SEVERE, "agent could not handle bidRejected", e);
//       }
//     } else {
//       TACMessage msg = new TACMessage("replaceBid");
//       msg.setParameter("bidID", oldBid.getID());
//       msg.setParameter("bidHash", oldBid.getBidHash());

//       prepareBidMsg(msg, bid);
//       sendMessage(msg, this);
//       updateBid(bid);
//     }
//   }



  /*********************************************************************
   * Agent methods
   *********************************************************************/

  protected abstract void gameStarted();

  protected abstract void gameStopped();

//   protected void bidUpdated(Bid bid) {
//   }

//   protected void bidError(Bid bid, int error) {
//   }


  protected void quoteUpdated(Quote quote) {
  }

  protected void auctionClosed(Auction auction) {
  }

  final void updateOwn(Transaction transaction) {
    Auction auction = transaction.getAuction();
    int a = getAuctionIndex(auction);
    int quantity = 0;
    if (transaction.getBuyer() == user) {
      quantity = transaction.getQuantity();
    } else if (transaction.getSeller() == user) {
      quantity = -transaction.getQuantity();
    }
    if (quantity != 0) {
      if (a >= owns.length) {
	owns = ArrayUtils.setSize(owns, a + 5);
	costs = ArrayUtils.setSize(costs, a + 5);
      }
      owns[a] += quantity;
      costs[a] += quantity * transaction.getPrice();
    }
  }

  protected void transaction(Transaction transaction) {
  }

  // DEBUG FINALIZE REMOVE THIS!!! REMOVE THIS!!!
  protected void finalize() throws Throwable {
    log.finest("BUILTIN AGENT " + user.getName() + " (" + user.getID()
	       + ") IN GAME " + market.getGame().getGameID()
	       + " IS BEING GARBAGED");
    super.finalize();
  }

} // BuiltinAgent
