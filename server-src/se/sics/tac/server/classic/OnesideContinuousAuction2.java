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
 * OnesideContinuousAuction2
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-14
 * Updated : $Date: 2004/05/21 15:29:21 $
 *	     $Revision: 1.2 $
 */

package se.sics.tac.server.classic;
import java.util.Random;

import com.botbox.util.ArrayUtils;
import se.sics.tac.server.Auction;
import se.sics.tac.server.Bid;
import se.sics.tac.server.BidList;
import se.sics.tac.server.Game;
import se.sics.tac.server.Market;
import se.sics.tac.server.User;

public class OnesideContinuousAuction2 extends Auction {

  private int minDelay = 10;
  private int randomDelay = 0;

  private Bid[] activeBids = new Bid[16];
  private int bidNumber = 0;

  private int upperBound;

  public OnesideContinuousAuction2(Market market, int type, int day,
				   long closeTime) {
    super(market, type, day, ALLOW_BUY | ALLOW_WITHDRAW);
    setCloseTime(closeTime);
    setFinalClearTime(closeTime);
  }

  // Set quote period information in seconds
//   void setQuotePeriod(int minDelay, int randomDelay) {
//     this.minDelay = minDelay;
//     this.randomDelay = randomDelay;
//   }

  protected void openAuction(long openTime) {
    Random random = getRandom();
    int delay = minDelay + (randomDelay > 0 ? random.nextInt(randomDelay) : 0);
    long nextUpdate = openTime + delay * 1000;
    double startPrice = 250.0 + random.nextInt(151);
    this.upperBound = -10 + random.nextInt(41);
    // ask, bid, nextQuoteTime
    setQuoteInfo(startPrice, 0, nextUpdate);
    quoteUpdated(openTime);
  }

  protected synchronized void closeAuction(long time) {
    // All bids not completely transacted are now expired
    for (int i = 0; i < bidNumber; i++) {
      if (activeBids[i].getTimeClosed() == 0) {
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
    User user = bid.getUser();
    long time = getServerTime();

    for (int i = 0; i < bidNumber; i++) {
      if (activeBids[i].getUser() == user) {
	activeBids[i].setReplaced(time);
	bidNumber--;
	// Order does not matter
	activeBids[i] = activeBids[bidNumber];
	activeBids[bidNumber] = null;
	// Only one bid per user is possible
	break;
      }
    }

    bid.setValid(time);

    BidList list = bid.getBidList();
    if (list.size() == 0) {
      // The bid contained no bid points (quantities of 0 are never
      // included in the bid list) and will be considered transacted.
      bid.setTransacted(time);

    } else {
      // See if some transactions can be performed
      double price = quote.getAskPrice();
      int q = list.removeBuyPoints(price);
      if (q > 0) {
	// Some items could be sold
	market.createTransaction(this, bid, null, q, price, time);
	bid.setTransacted(time);
	setClearInfo(time, price, -1L);
      }

      if (bid.isActive()) {
	// Bid is still active
	if (bidNumber == activeBids.length) {
	  activeBids = (Bid[]) ArrayUtils.setSize(activeBids, bidNumber + 16);
	}
	activeBids[bidNumber++] = bid;
      }
    }
  }

  protected synchronized void withdrawBid(Bid bid) {
    int index = ArrayUtils.indexOf(activeBids, 0, bidNumber, bid);
    if (index >= 0) {
      bidNumber--;
      // Order does not matter in this auction
      activeBids[index] = activeBids[bidNumber];
      activeBids[bidNumber] = null;
      bid.setWithdrawn(getServerTime());
    }
  }

  protected synchronized void updateAuctionQuote(long time) {
    Random random = getRandom();
    Game game = market.getGame();
    long startTime = game.getStartTime();
    int gameLength = game.getGameLength();
    long timeInGame = time - startTime;
    double price = quote.getAskPrice();
    if (gameLength <= 0) {
      gameLength = ClassicMarket.DEFAULT_GAME_LENGTH;
    }

    // Initiate next quote update
    int delay = minDelay + (randomDelay > 0 ? random.nextInt(randomDelay) : 0);
    long nextUpdate = time + delay * 1000;

    // Update price
    double xt = 10 + (((double) timeInGame / gameLength) * (upperBound - 10));
    int xti = (int) (xt + 0.5);
    if (xt < 0.0) {
      price += random.nextInt(-xti + 11) + xti;
    } else if (xt > 0.0) {
      price += -10 + random.nextInt(xti + 11);
    } else {
      price += -10 + random.nextInt(11);
    }
    // Price constraints
    if (price > 800) {
      price = 800;
    } else if (price < 150) {
      price = 150;
    }

//     java.util.logging.Logger.global.finest("UPDATE flight auction " + getID() + " x=" + x + " bound=" + upperBound + " ask=" + price + " delay=" + delay);

    // ask, bid, nextQuoteTime
    setQuoteInfo(price, 0, nextUpdate);

    double lastPrice = -1.0;
    for (int i = bidNumber - 1; i >= 0; i--) {
      Bid bid = activeBids[i];
      BidList list = bid.getBidList();
      boolean bidTransacted = false;

      // Since the bid was standing the transaction price is the bid
      // price which means we must traverse the bid list and create a
      // new transaction for each bid point in case they have
      // different prices.
      for (int j = 0; j < list.size(); j++) {
	int q = list.getQuantityAt(j);
	double bidPrice = list.getPriceAt(j);
	if ((q > 0) && (bidPrice >= price)) {
	  // Some items could be sold
	  list.removeBidPointAt(j);
	  // Redo this index because its previous bid point was removed
	  j--;

	  market.createTransaction(this, bid, null, q, bidPrice, time);
	  bidTransacted = true;
	  // Remember the last transaction price for the quote clearing info
	  lastPrice = bidPrice;
	}
      }

      if (bidTransacted) {
	bid.setTransacted(time);

	if (bid.getTimeClosed() > 0) {
	  // Bid was fully transacted
	  bidNumber--;
	  activeBids[i] = activeBids[bidNumber];
	  activeBids[bidNumber] = null;
	}
      }
    }

    // Set the quote clearing info if a transaction occurred
    if (lastPrice >= 0.0) {
      setClearInfo(time, lastPrice, -1L);
    }
  }

} // OnesideContinuousAuction2
