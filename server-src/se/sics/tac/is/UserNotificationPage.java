/**
 * SICS TAC Classic Server
 * http://www.sics.se/tac/    tac-dev@sics.se
 *
 * Copyright (c) 2001-2004 SICS AB. All rights reserved.
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
 * UserNotificationPage
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : Fri May 21 15:59:47 2004
 * Updated : $Date: 2004/05/21 16:21:52 $
 *           $Revision: 1.1 $
 */
package se.sics.tac.is;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.mortbay.http.HttpException;
import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import org.mortbay.util.ByteArrayISO8859Writer;

/**
 * This is a user notification listener page when using a central user
 * database for several servers.
 */
public class UserNotificationPage  extends HttpPage {

  private static final Logger log =
    Logger.getLogger(UserNotificationPage.class.getName());

  private final InfoServer infoServer;

  public UserNotificationPage(InfoServer infoServer) {
    this.infoServer = infoServer;
  }

  public void handle(String pathInContext, String pathParams,
		     HttpRequest request, HttpResponse response)
    throws HttpException, IOException
  {
    String name = request.getParameter("id");
    String message = null;
    if (name != null) {
      try {
	int id = Integer.parseInt(name);
	infoServer.updateAgent(id);
	log.info("updated user " + id + " using notification");
	message = "<html><body>User " + id + " has been updated</body></html>";
      } catch (Exception e) {
	log.log(Level.WARNING, "illegal user update '" + name + '\'', e);
      }
    }

    if (message == null) {
      message = "<html><body>Failed to update user "
	+ name + "</body></html>";
    }

    ByteArrayISO8859Writer writer = new ByteArrayISO8859Writer();
    writer.write(message);
    response.setContentType(HttpFields.__TextHtml);
    response.setContentLength(writer.size());
    writer.writeTo(response.getOutputStream());
    response.commit();
  }

} // UserNotificationPage
