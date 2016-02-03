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
 * DoubleContinuousAuction
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-14
 * Updated : $Date: 2004/06/29 15:12:49 $
 *	     $Revision: 1.3 $
 */

package se.sics.tac.server.classic;
import se.sics.tac.server.*;

public class DoubleContinuousAuction extends Auction {

  private int quotePeriod = 30000;
  private Order[] buyOrders = new Order[16];
  private int buyNumber = 0;
  private Order[] sellOrders = new Order[16];
  private int sellNumber = 0;

  public DoubleContinuousAuction(Market market, int type, int day,
				 long closeTime) {
    super(market, type, day, ALLOW_BUY | ALLOW_SELL | ALLOW_WITHDRAW);
    setCloseTime(closeTime);
    setFinalClearTime(closeTime);
  }

  public long getNextQuoteTime() {
    // Overrides the default next quote time to always return a
    // time in the future (only to update the nextQuoteTime in case
    // agents use it).
    return getServerTime() + quotePeriod;
  }

  protected void openAuction(long openTime) {
    // ask, bid
    setQuoteInfo(0, 0);
  }

  protected synchronized void closeAuction(long time) {
    // All bids not completely transacted are now expired
    expireBids(buyOrders, buyNumber, time);
    buyNumber = 0;
    expireBids(sellOrders, sellNumber, time);
    sellNumber = 0;
  }

  protected synchronized boolean isBidActive(Bid bid) {
    return containsBid(buyOrders, buyNumber, bid)
      || containsBid(sellOrders, sellNumber, bid);
  }

  protected synchronized Bid getActiveBid(User user) {
    Bid bid = getBidForUser(buyOrders, buyNumber, user);
    if (bid == null) {
      bid = getBidForUser(sellOrders, sellNumber, user);
    }
    return bid;
  }

  protected synchronized void submitBid(Bid bid) {
    long time = getServerTime();
    Bid oldBid = getActiveBid(bid.getUser());
    if (oldBid != null) {
      removeBid(oldBid);
      oldBid.setReplaced(time);
    }

    // The bid is now valid
    bid.setValid(time);

    BidList list = bid.getBidList();
    if (list.size() == 0) {
      // The bid contained no bid points (quantities of 0 are never
      // included in the bid list) and will be considered transacted.
      bid.setTransacted(time);

    } else {
      // Match current sell points with the new bid
      buyNumber = match(buyOrders, buyNumber, bid, time, true);
      // Match current buy points with the new bid
      sellNumber = match(sellOrders, sellNumber, bid, time, false);

      // Add the remaining bid points
      for (int i = 0, n = list.size(); i < n; i++) {
	int quantity = list.getQuantityAt(i);
	double price = list.getPriceAt(i);
	if (quantity < 0) {
	  addSellOrder(new Order(-quantity, price, bid));
	} else {
	  addBuyOrder(new Order(quantity, price, bid));
	}
      }
    }

    // Update the quote if needed (even if the new bid did not contain
    // any bid points, a replaced bid might have changed the quote)
    maybeUpdateQuote(time);
  }

  protected synchronized void withdrawBid(Bid bid) {
    long time = getServerTime();
    removeBid(bid);
    bid.setWithdrawn(time);
    maybeUpdateQuote(time);
  }

  protected void updateAuctionQuote(long time) {
  }

  // Note: MAY ONLY BE CALLED SYNCHRONIZED
  private void maybeUpdateQuote(long time) {
    double askPrice = sellNumber > 0 ? sellOrders[0].price : 0.0;
    double bidPrice = buyNumber > 0 ? buyOrders[0].price : 0.0;
    // Only update the quote if needed
    if ((askPrice != quote.getAskPrice())
	|| (bidPrice != quote.getBidPrice())) {
      setQuoteInfo(askPrice, bidPrice);
      quoteUpdated(time);
    }
  }

  private void expireBids(Order[] orders, int len, long time) {
    for (int i = 0; i < len; i++) {
      Bid bid = orders[i].bid;
      if (bid.getTimeClosed() == 0L) {
	bid.setExpired(time);
      }
      // Allow the orders to be garbaged
      orders[i] = null;
    }
  }


  /*********************************************************************
   * Utility
   *********************************************************************/

  // NOTE: may only be called synchronized on this object
  private void addBuyOrder(Order order) {
    // Find the right index
    int index = buyNumber;
    for (int i = 0; i < buyNumber; i++) {
      if (order.price > buyOrders[i].price) {
	index = i;
	break;
      }
    }

    if (buyNumber == buyOrders.length) {
      Order[] tmp = new Order[buyNumber + 16];
      System.arraycopy(buyOrders, 0, tmp, 0, buyNumber);
      buyOrders = tmp;
    }

    if (index < buyNumber) {
      System.arraycopy(buyOrders, index, buyOrders, index + 1,
		       buyNumber - index);
    }
    buyNumber++;
    buyOrders[index] = order;
  }

  // NOTE: may only be called synchronized on this object
  private void addSellOrder(Order order) {
    // Find the right index
    int index = sellNumber;
    for (int i = 0; i < sellNumber; i++) {
      if (order.price < sellOrders[i].price) {
	index = i;
	break;
      }
    }

    if (sellNumber == sellOrders.length) {
      Order[] tmp = new Order[sellNumber + 16];
      System.arraycopy(sellOrders, 0, tmp, 0, sellNumber);
      sellOrders = tmp;
    }

    if (index < sellNumber) {
      System.arraycopy(sellOrders, index, sellOrders, index + 1,
		       sellNumber - index);
    }
    sellNumber++;
    sellOrders[index] = order;
  }

  // NOTE: may only be called synchronized on this object
  private int match(Order[] orders, int number, Bid bid,
		    long time, boolean matchSell) {
    BidList list = bid.getBidList();
    while (number > 0) {
      Order order = orders[0];
      double price = order.price;
      int matched = matchSell
	? list.removeSellPoints(order.quantity, price)
	: list.removeBuyPoints(order.quantity, price);

      if (matched > 0) {
	// A transaction can be made
	if (matchSell) {
	  order.bid.getBidList().removeBuyPoints(matched, price);
	  market.createTransaction(this, order.bid, bid,
				   matched, price, time);
	} else {
	  order.bid.getBidList().removeSellPoints(matched, price);
	  market.createTransaction(this, bid, order.bid,
				   matched, price, time);
	}
	order.bid.setTransacted(time);
	bid.setTransacted(time);
	setClearInfo(time, price, -1L);
	order.quantity -= matched;
	if (order.quantity == 0) {
	  // Remove first order because it is now empty
	  number--;
	  if (number > 0) {
	    System.arraycopy(orders, 1, orders, 0, number);
	  }
	  orders[number] = null;
	} else {
	  break;
	}
      } else {
	break;
      }
    }
    return number;
  }

  private boolean containsBid(Order[] orders, int len, Bid bid) {
    for (int i = 0; i < len; i++) {
      if (orders[i].bid == bid) {
	return true;
      }
    }
    return false;
  }

  private Bid getBidForUser(Order[] orders, int len, User user) {
    for (int i = 0; i < len; i++) {
      if (orders[i].bid.getUser() == user) {
	return orders[i].bid;
      }
    }
    return null;
  }

  // NOTE: may only be called synchronized on this object
  private void removeBid(Bid bid) {
    buyNumber = removeBid(buyOrders, buyNumber, bid);
    sellNumber = removeBid(sellOrders, sellNumber, bid);
  }

  // NOTE: may only be called synchronized on this object
  private int removeBid(Order[] orders, int number, Bid bid) {
    for (int i = number - 1; i >= 0; i--) {
      if (orders[i].bid == bid) {
	number--;
	if (i < number) {
	  System.arraycopy(orders, i + 1, orders, i, number - i);
	}
	orders[number] = null;
      }
    }
    return number;
  }


  /*********************************************************************
   * Data container for orders
   *********************************************************************/

  private static class Order {
    public int quantity;
    public final double price;
    public final Bid bid;

    public Order(int quantity, double price, Bid bid) {
      this.quantity = quantity;
      this.price = price;
      this.bid = bid;
    }
  }

} // DoubleContinuousAuction
