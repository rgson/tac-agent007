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
 * Transaction
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-06
 * Updated : $Date: 2004/05/13 14:08:18 $
 *	     $Revision: 1.2 $
 */

package se.sics.tac.server;
import se.sics.tac.util.TACFormatter;

public class Transaction {

  protected final Auction auction;
  protected final int transactionID;
  protected final User buyer;
  protected final User seller;
  protected final Bid buyBid;
  protected final Bid sellBid;
  protected final int quantity;
  protected final double price;
  protected final long clearTime;
  protected final boolean isEndowment;

  Transaction(Auction auction, int transactionID,
	      User buyer, User seller, int quantity, double price,
	      long clearTime, boolean isEndowment) {
    this(auction, transactionID, buyer, seller, null, null,
	 quantity, price, clearTime, isEndowment);
  }

  Transaction(Auction auction, int transactionID,
	      Bid buyBid, Bid sellBid, int quantity, double price,
	      long clearTime) {
    this(auction, transactionID,
	 buyBid != null ? buyBid.getUser() : null,
	 sellBid != null ? sellBid.getUser() : null,
	 buyBid, sellBid,
	 quantity, price, clearTime, false);
  }

  private Transaction(Auction auction, int transactionID,
		      User buyer, User seller,
		      Bid buyBid, Bid sellBid, int quantity, double price,
		      long clearTime, boolean isEndowment) {
    if (auction == null) {
      throw new NullPointerException();
    }
    this.auction = auction;
    this.transactionID = transactionID;
    this.buyer = buyer;
    this.buyBid = buyBid;
    this.seller = seller;
    this.sellBid = sellBid;
    this.quantity = quantity;
    this.price = price;
    this.clearTime = clearTime;
    this.isEndowment = isEndowment;
  }

  public int getAuctionID() {
    return auction.getID();
  }

  public Auction getAuction() {
    return auction;
  }

  public int getID() {
    return transactionID;
  }

  public boolean isEndowment() {
    return isEndowment;
  }

  public User getBuyer() {
    return buyer;
  }

  public User getSeller() {
    return seller;
  }

  public boolean isParticipant(User user) {
    return buyer == user || seller == user;
  }

  public Bid getBuyBid() {
    return buyBid;
  }

  public Bid getSellBid() {
    return sellBid;
  }

  public long getClearTime() {
    return clearTime;
  }

  public int getQuantity() {
    return quantity;
  }

  public double getPrice() {
    return price;
  }

  public StringBuffer toCsv(StringBuffer sb) {
    sb.append(',').append(getID(buyer))
      .append(',').append(getID(seller))
      .append(',').append(auction.getID())
      .append(',').append(quantity)
      .append(',').append(TACFormatter.toString4(price))
      .append(',').append(transactionID);
    return sb;
  }

  /*********************************************************************
   * TAC Message generation support
   *********************************************************************/

  public synchronized String generateFields(User user) {
    boolean isBuying = buyer == user;
    return "<auctionID>" + auction.getID() + "</auctionID>"
      + "<clearTime>" + (clearTime / 1000) + "</clearTime>"
      + "<tradePartyID>" + (isBuying ? getID(seller) : getID(buyer))
      + "</tradePartyID>"
      + "<price>" + TACFormatter.toString4(price) + "</price>"
      + "<quantity>" + (isBuying ? quantity : -quantity) + ".0</quantity>";
  }

  private String getID(User user) {
    return user != null ? Integer.toString(user.getID()) : "auction";
  }

} // Transaction
