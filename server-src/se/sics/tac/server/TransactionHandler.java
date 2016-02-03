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
 * TransactionHandler
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-06
 * Updated : $Date: 2004/06/06 14:43:11 $
 *	     $Revision: 1.2 $
 * Purpose :
 *
 */

package se.sics.tac.server;

public class TransactionHandler implements MessageHandler {

  public void registerAt(TACServer server) {
    server.addMessageHandler("transIDs", this, true);
    server.addMessageHandler("transInfo", this, true);
    server.addMessageHandler("initialLastTransID", this, true);
  }

  public void handleMessage(InfoManager infoManager, TACMessage message) {
    Market market = infoManager.getMarket();
    User user = message.getUser();
    // Allow transaction information retrieval between games
    if (market == null || !market.getGame().isParticipant(user)) {
      message.replyError(TACException.NOT_MEMBER_OF_GAME);
      return;
    }

    String type = message.getType();
    if (type.equals("transIDs")) {
      int transID = -1;
      while (message.nextTag()) {
	if (message.isTag("earliestTransID")) {
	  transID = message.getValueAsInt(-1);
	  break;
	}
      }

      StringBuffer sb = new StringBuffer().append("<ids><list>");
      Transaction transaction;
      while ((transaction = market.getNextTransaction(transID, user)) != null)
	{
	  transID = transaction.getID();
	  sb.append("<transID>").append(transID).append("</transID>");
	}
      sb.append("</list></ids><more>0</more>");
      message.reply(sb.toString(), TACException.NO_ERROR);

    } else if (type.equals("transInfo")) {
      int transID = -1;
      while (message.nextTag()) {
	if (message.isTag("transID")) {
	  transID = message.getValueAsInt(-1);
	  break;
	}
      }

      if (transID < 0) {
	message.replyMissingField("transID");
      } else {
	Transaction transaction = market.getTransaction(transID);
	if ((transaction == null) || !transaction.isParticipant(user)) {
	  // Transaction not found or agent not participating
	  // in the transaction
	  message.replyError(TACException.TRANS_NOT_FOUND);

	} else {
	  message.reply(transaction.generateFields(user),
			TACException.NO_ERROR);
	}
      }

    } else if (type.equals("initialLastTransID")) {
      message.reply("<transID>-1</transID>", TACException.NO_ERROR);
    }
  }

} // TransactionHandler
