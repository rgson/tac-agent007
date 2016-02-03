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
 * BidHandler
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-11
 * Updated : $Date: 2004/06/06 14:43:11 $
 *	     $Revision: 1.3 $
 */

package se.sics.tac.server;

public class BidHandler implements MessageHandler {

  public void registerAt(TACServer server) {
    server.addMessageHandler("bidInfo", this, true);
    server.addMessageHandler("submitBid", this, true);
    server.addMessageHandler("replaceBid", this, true);
    server.addMessageHandler("withdrawBid", this, true);
    server.addMessageHandler("recoverBidIDs", this, true);
    server.addMessageHandler("recoverStaticBidInfo", this, true);
  }

  public void handleMessage(InfoManager infoManager, TACMessage message)
    throws TACException
  {
    String type = message.getType();
    if ("bidInfo".equals(type)) {
      Market market = infoManager.getMarket();
      int bidID = getInt(message, "bidID", -1);
      Bid bid;
      if (bidID < 0) {
	message.replyMissingField("bidID");
      } else if ((market == null)
		 || ((bid = market.getBid(bidID)) == null)
		 || (bid.getUser() != message.getUser())) {
	message.replyError(TACException.BID_NOT_FOUND);
      } else {
	message.reply(bid.generateFields(false), TACException.NO_ERROR);
      }

    } else if ("submitBid".equals(type)) {
      submitBid(infoManager, message, false);

    } else if ("replaceBid".equals(type)) {
      submitBid(infoManager, message, true);

    } else if ("withdrawBid".equals(type)) {
      Market market = infoManager.getMarket();
      int auctionID = getInt(message, "auctionID", -1);
      Auction auction;
      if (auctionID < 0) {
	message.replyMissingField("auctionID");
      } else if ((market == null)
		 || ((auction = market.getAuction(auctionID)) == null)) {
	message.replyError(TACException.AUCTION_NOT_FOUND);
      } else if (auction.isClosed()) {
	message.replyError(TACException.AUCTION_CLOSED);
      } else if (auction.withdraw(message.getUser())) {
	message.reply("", TACException.NO_ERROR);
      } else {
	message.replyError(TACException.CANNOT_WITHDRAW_BID);
      }

    } else if ("recoverBidIDs".equals(type)) {
      Market market = infoManager.getMarket();
      StringBuffer sb = new StringBuffer()
	.append("<auctionBidIDs><list>");
      if (market != null) {
	User user = message.getUser();
	Bid bid;
	int id = -1;
	while ((bid = market.getNextBid(id, user)) != null) {
	  id = bid.getBidID();
	  sb.append("<auctionBidIDsTuple><auctionID>")
	    .append(bid.getAuctionID())
	    .append("</auctionID><bidID>").append(id)
	    .append("</bidID></auctionBidIDsTuple>");
	}
      }
      sb.append("</list></auctionBidIDs><more>0</more>");
      message.reply(sb.toString(), TACException.NO_ERROR);

    } else if ("recoverStaticBidInfo".equals(type)) {
      Market market = infoManager.getMarket();
      int bidID = getInt(message, "bidID", -1);
      Bid bid;
      if (bidID < 0) {
	message.replyMissingField("bidID");
      } else if ((market == null)
		 || ((bid = market.getBid(bidID)) == null)
		 || (bid.getUser() != message.getUser())) {
	message.replyError(TACException.BID_NOT_FOUND);
      } else {
	message.reply(bid.generateFields(true), TACException.NO_ERROR);
      }
    }
  }

  private void submitBid(InfoManager infoManager, TACMessage message,
			 boolean replaceBid) throws TACException {
    Market market = infoManager.getMarket();
    User user = message.getUser();
    int auctionID = -1;
    String bidString = null;
    BidList bidList;
    int bidID = -1;
    String oldBidHash = null;
    Auction auction;

    while (message.nextTag()) {
      if (message.isTag("auctionID")) {
	auctionID = message.getValueAsInt(-1);
      } else if (message.isTag("bidString")) {
	bidString = message.getValue();
      } else if (message.isTag("bidHash")) {
	oldBidHash = message.getValue();
      } else if (message.isTag("bidID")) {
	bidID = message.getValueAsInt(-1);
      }
      // Ignore the others for now
    }

    if (auctionID < 0) {
      message.replyMissingField("auctionID");
    } else if (bidString == null) {
      message.replyMissingField("bidString");
    } else if (replaceBid && oldBidHash == null) {
      message.replyMissingField("bidHash");
    } else if (replaceBid && bidID < 0) {
      message.replyMissingField("bidID");
    } else if ((market == null)
	       || ((auction = market.getAuction(auctionID)) == null)) {
      message.replyError(TACException.AUCTION_NOT_FOUND);
    } else if (!(bidList = new BidList()).setBidString(bidString)) {
      // Could not parse bid string
      throw new TACException(TACException.BAD_BIDSTRING_FORMAT);
    } else {
      Bid bid = replaceBid
	? auction.replace(user, bidList, bidID, oldBidHash)
	: auction.submit(user, bidList);
      message.reply("<bidID>" + bid.getBidID() + "</bidID>"
		    + "<bidHash>" + bid.getOriginalBidHash() + "</bidHash>"
		    + "<rejectReason>" + bid.getRejectReason()
		    + "</rejectReason>", TACException.NO_ERROR);
    }
  }

  private int getInt(TACMessage message, String param, int defaultValue) {
    while (message.nextTag()) {
      if (message.isTag(param)) {
	return message.getValueAsInt(defaultValue);
      }
    }
    return defaultValue;
  }

} // BidHandler
