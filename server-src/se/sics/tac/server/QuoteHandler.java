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
 * QuoteHandler
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-05
 * Updated : $Date: 2004/06/06 14:43:11 $
 *	     $Revision: 1.2 $
 * Purpose :
 *
 */

package se.sics.tac.server;

public class QuoteHandler implements MessageHandler {

  public void registerAt(TACServer server) {
    server.addMessageHandler("getQuote", this, true);
  }

  public void handleMessage(InfoManager infoManager, TACMessage message) {
    int auctionID = -1;
    int bidID = -1;
    Quote quote;
    while (message.nextTag()) {
      if (message.isTag("auctionID")) {
	auctionID = message.getValueAsInt(-1);
      } else if (message.isTag("bidID")) {
	bidID = message.getValueAsInt(-1);
      }
    }

    if (auctionID < 0) {
      message.replyMissingField("auctionID");
    } else if ((quote = infoManager.getMarket().getQuote(auctionID)) == null) {
      message.replyError(TACException.AUCTION_NOT_FOUND);
    } else {
      message.reply(quote.generateFields(message.getUser(), bidID),
		    TACException.NO_ERROR);
    }
  }

} // QuoteHandler
