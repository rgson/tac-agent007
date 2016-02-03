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
 * BidList
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-13
 * Updated : $Date: 2004/06/04 11:36:20 $
 *	     $Revision: 1.2 $
 */

package se.sics.tac.server;
import java.util.StringTokenizer;
import java.util.logging.Logger;
import se.sics.tac.util.TACFormatter;

public class BidList {

  private static final Logger log =
    Logger.getLogger(BidList.class.getName());

  protected final static int INCREMENT = 10;

  protected String bidString;

  protected int bidListLength;
  protected int[] quantity;
  protected double[] price;

  public BidList() {
  }

  public String getBidString() {
    String bidString = this.bidString;
    if (bidString == null){
      StringBuffer bid = new StringBuffer();
      bid.append('(');
      for (int i = 0; i < bidListLength; i++) {
	bid.append('(').append(quantity[i]).append(' ').
	  append(toString4(price[i])).append(')');
      }
      bid.append(')');
      this.bidString = bidString = bid.toString();
    }
    return bidString;
  }

  public synchronized boolean setBidString(String bidString) {
    try {
      StringTokenizer tok = new StringTokenizer(bidString, "() \t\r\n");
      // Clear the old bid string
      this.bidListLength = 0;
      while (tok.hasMoreTokens()) {
	double fq = Double.parseDouble(tok.nextToken());
	int q = (int) fq;
	if (q != fq) {
	  throw new IllegalArgumentException("quantity '" + fq
					     + "' is not an integer");
	}
	double p = Double.parseDouble(tok.nextToken());
	add(q, p);
      }
      this.bidString = null;
      return true;
    } catch (Exception e) {
      log.warning("could not parse bid string '" + bidString + "': " + e);
      return false;
    }
  }

  public synchronized void addBidPoint(int quantity, double unitPrice) {
    add(quantity, unitPrice);
    this.bidString = null;
  }

  // Note: MAY ONLY BE CALLED SYNCHRONIZED ON THIS OBJECT
  private void add(int quantity, double unitPrice) {
    if (unitPrice < 0) {
      throw new IllegalArgumentException("negative price not allowed: "
					 + unitPrice);
    }
    // Ignore 0 quantities
    if (quantity != 0) {
      realloc();
      this.quantity[bidListLength] = quantity;
      this.price[bidListLength++] = unitPrice;
    }
  }

  // Note: MAY ONLY BE CALLED SYNCHRONIZED ON THIS OBJECT
  private void realloc() {
    if (quantity == null) {
      quantity = new int[INCREMENT];
      price = new double[INCREMENT];
    } else if (bidListLength == quantity.length) {
      int[] tmp = new int[bidListLength + INCREMENT];
      System.arraycopy(quantity, 0, tmp, 0, bidListLength);

      double[] tmp2 = new double[bidListLength + INCREMENT];
      System.arraycopy(price, 0, tmp2, 0, bidListLength);

      quantity = tmp;
      price = tmp2;
    }
  }

  public boolean hasSellPoints() {
    for (int i = 0; i < bidListLength; i++) {
      if (quantity[i] < 0) {
	return true;
      }
    }
    return false;
  }

  public boolean hasBuyPoints() {
    for (int i = 0; i < bidListLength; i++) {
      if (quantity[i] > 0) {
	return true;
      }
    }
    return false;
  }

  public boolean isSelfTransacting() {
    double maxSell = -1f;
    double maxBuy = -1f;

    for (int i = 0; i < bidListLength; i++) {
      if (quantity[i] < 0) {
	// Sell
	if (price[i] > maxSell) {
	  maxSell = price[i];
	}
      } else if (price[i] > maxBuy) {
	maxBuy = price[i];
      }
    }

    return (maxSell >= 0) && (maxBuy >= maxSell);
  }

  public int removeBuyPoints(double price) {
    return removeBuyPoints(Integer.MAX_VALUE, price);
  }

  public synchronized int removeBuyPoints(int quant, double buyPrice) {
    int noRemoved = 0;
    do {
      int index = getLowestBuyPoint(buyPrice);
      if (index < 0) {
	// No more buy points
	break;
      } else if (quantity[index] > quant) {
	quantity[index] -= quant;
	noRemoved += quant;
	break;
      } else {
	quant -= quantity[index];
	noRemoved += quantity[index];
	removeBidPointAt(index);
// 	bidListLength--;
// 	quantity[index] = quantity[bidListLength];
// 	price[index] = price[bidListLength];
      }
    } while (quant > 0);

    // Must regenerate the bid string if some bid points have been removed
    if (noRemoved > 0) {
      this.bidString = null;
    }
    return noRemoved;
  }

  public int getLowestBuyPoint(double buyPrice) {
    int index = -1;
    double minPrice = Double.MAX_VALUE;
    for (int i = 0; i < bidListLength; i++) {
      if (price[i] < minPrice && price[i] >= buyPrice && quantity[i] > 0) {
	index = i;
	minPrice = price[i];
      }
    }
    return index;
  }

  public int removeSellPoints(double price) {
    return removeSellPoints(Integer.MAX_VALUE, price);
  }

  public synchronized int removeSellPoints(int quant, double sellPrice) {
    int noRemoved = 0;
    do {
      int index = getHighestSellPoint(sellPrice);
      if (index < 0) {
	// No more buy points
	break;
      } else if (-quantity[index] > quant) {
	quantity[index] += quant;
	noRemoved += quant;
	break;
      } else {
	// quantity[index] is negative because it is a sell point
	quant += quantity[index];
	noRemoved -= quantity[index];
	removeBidPointAt(index);
// 	bidListLength--;
// 	quantity[index] = quantity[bidListLength];
// 	price[index] = price[bidListLength];
      }
    } while (quant > 0);

    // Must regenerate the bid string if some bid points have been removed
    if (noRemoved > 0) {
      this.bidString = null;
    }
    return noRemoved;
  }

  public int getHighestSellPoint(double sellPrice) {
    int index = -1;
    double maxPrice = -1;
    for (int i = 0; i < bidListLength; i++) {
      if (price[i] > maxPrice && price[i] <= sellPrice && quantity[i] < 0) {
	index = i;
	maxPrice = price[i];
      }
    }
    return index;
  }

  public int size() {
    return bidListLength;
  }

  public int getQuantity() {
    int q = 0;
    for (int i = 0; i < bidListLength; i++) {
      q += quantity[i];
    }
    return q;
  }

  public int getQuantityAt(int index) {
    if (index >= bidListLength) {
      throw new IndexOutOfBoundsException("Index: " + index
					  + ", Size: " + bidListLength);
    }
    return quantity[index];
  }

  public double getPriceAt(int index) {
    if (index >= bidListLength) {
      throw new IndexOutOfBoundsException("Index: " + index
					  + ", Size: " + bidListLength);
    }
    return price[index];
  }

  public synchronized void removeBidPointAt(int index) {
    if (index < 0 || index >= bidListLength) {
      throw new IndexOutOfBoundsException("Index: " + index
					  + ", Size: " + bidListLength);
    }
    bidListLength--;
    if (index < bidListLength) {
      for (int i = index; i < bidListLength; i++) {
	quantity[i] = quantity[i + 1];
	price[i] = price[i + 1];
      }
    }
  }

  public StringBuffer toCsv(StringBuffer sb) {
    for (int i = 0; i < bidListLength; i++) {
      sb.append(',').append(quantity[i]).append(',')
	.append(toString4(price[i]));
    }
    return sb;
  }

  public String toString() {
    StringBuffer sb = new StringBuffer().append("BidList[")
      .append(quantity == null ? 0 : quantity.length);
    return toCsv(sb).append(']').toString();
  }

  private String toString4(double v) {
    return TACFormatter.toString4(v);
  }

} // BidList
