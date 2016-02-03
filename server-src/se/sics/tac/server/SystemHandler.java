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
 * SystemHandler
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-17
 * Updated : $Date: 2004/06/06 14:43:11 $
 *	     $Revision: 1.3 $
 */

package se.sics.tac.server;
import java.util.logging.Logger;

public class SystemHandler implements MessageHandler {

  public void registerAt(TACServer server) {
    server.addMessageHandler("auth", this, false);
    server.addMessageHandler("serverTime", this, false);
    server.addMessageHandler("quit", this, false);
  }

  public void handleMessage(InfoManager infoManager, TACMessage message) {
    String type = message.getType();
    if ("auth".equals(type)) {
      handleLogin(infoManager, message);
    } else if ("serverTime".equals(type)) {
      message.reply("<time>" + infoManager.getServerTimeSeconds()
		    + "</time>", TACException.NO_ERROR);
    } else if ("quit".equals(type)) {
      message.reply("", TACException.NO_ERROR);
      message.getConnection().close();
    }
  }

  private void handleLogin(InfoManager infoManager, TACMessage message) {
    String userName = null;
    String password = null;
    User user;

    while (message.nextTag()) {
      if (message.isTag("userName")) {
	userName = message.getValue();
      } else if (message.isTag("userPW")) {
	password = message.getValue();
      }
    }

    if (userName == null) {
      message.replyMissingField("userName");
    } else if (password == null) {
      message.replyMissingField("userPW");
    } else if (((user = infoManager.getUser(userName)) == null)
	       || !password.equals(user.getPassword())) {
      Logger.global.warning("AuthHandler: agent " + userName
			    + " failed to login");
      message.replyError(TACException.AGENT_NOT_AUTH);

      // Request a user update in case the user information has changed
      infoManager.requestUserUpdate(userName);
    } else {
      message.setUser(user);
      message.reply("<userID>" + user.getID() + "</userID>",
		    TACException.NO_ERROR);
    }
  }

} // SystemHandler
