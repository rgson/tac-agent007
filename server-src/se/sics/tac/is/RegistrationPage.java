/**
 * SICS TAC Server - InfoServer
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
 * RegistrationPage
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 10 April, 2002
 * Updated : $Date: 2004/05/25 13:04:30 $
 *	     $Revision: 1.7 $
 */

package se.sics.tac.is;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.botbox.html.HtmlWriter;
import org.mortbay.http.HttpException;
import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import org.mortbay.util.ByteArrayISO8859Writer;

public class RegistrationPage extends HttpPage {

  private static final Logger log =
    Logger.getLogger(RegistrationPage.class.getName());

  private InfoServer infoServer;
  private URL[] notificationTargets;

  public RegistrationPage(InfoServer is, String notification) {
    this.infoServer = is;

    // User Notification
    if (notification != null) {
      StringTokenizer tok = new StringTokenizer(notification, ", \t");
      int len = tok.countTokens();
      if (len > 0) {
	try {
	  URL[] n = new URL[len];
	  for (int i = 0; i < len; i++) {
	    n[i] = new URL(tok.nextToken());
	  }
	  this.notificationTargets = n;
	  for (int i = 0; i < len; i++) {
	    log.info("user registration notification to " + n[i]);
	  }
	} catch (Exception e) {
	  log.log(Level.WARNING, "could not handle notifications "
		  + notification, e);
	}
      }
    }
  }

  public void handle(String pathInContext, String pathParams,
		     HttpRequest req, HttpResponse response)
    throws HttpException, IOException
  {
    String message = null;
    boolean created = false;
    String name = null;
    String email = null;

    if (HttpRequest.__POST.equals(req.getMethod())) {
      String pw1 = trim(req.getParameter("p1"));
      String pw2 = trim(req.getParameter("p2"));
      name = trim(req.getParameter("name"));
      email = trim(req.getParameter("email"));

      if (name != null) {
	if (pw1 == null || pw1.length() < 4) {
	  message = "No password (please use at least 4 characters)";
	} else if (pw1.equals(pw2)) {
	  try {
	    int userID = infoServer.registerAgent(name, pw1, email);
	    message = "User " + name + " has been registered";
	    created = true;
	    callNotification(userID);
	  } catch (TACException e) {
	    message = "<font color=red>Error: " + e.getMessage() + "</font>";
	  }
	} else {
	  message = "Passwords do not match";
	}
      } else {
	message = "You must enter a user name";
      }
    }

    HtmlWriter page = new HtmlWriter();
    page.pageStart("Agent/User Registration");
    if (created) {
      page.h2(message);
    } else {
      if (message != null) {
	page.tag("font", "color=red").h3(message).tagEnd("font")
	  .p().tag("hr").p();
      }

      page.h2("Register new Agent/User")
	.form("", "POST")
	.table(HtmlWriter.BORDERED).attr("cellpadding", 2)
	.td("Agent/User Name")
	.td("<input name=name type=text length=22");
      if (name != null) {
	page.text(" value='").text(name).text('\'');
      }
      page.text('>').tr()
	.td("Email")
	.td("<input name=email type=text length=22");
      if (email != null) {
	page.text(" value='").text(email).text('\'');
      }
      page.text('>').tr().td("Password")
	.td("<input name=p1 type=password length=22>")
	.tr().td("Password (retype)")
	.td("<input name=p2 type=password length=22>")
	.tableEnd()
	.text("<input type=submit value='Register'>")
	.formEnd();
    }
    page.close();

    ByteArrayISO8859Writer writer = new ByteArrayISO8859Writer();
    page.write(writer);
    response.setContentType(HttpFields.__TextHtml);
    response.setContentLength(writer.size());
    writer.writeTo(response.getOutputStream());
    response.commit();
  }

  private String trim(String text) {
    return (text != null) && ((text = text.trim()).length() > 0)
      ? text
      : null;
  }


  // -------------------------------------------------------------------
  // User Notification!!!
  // -------------------------------------------------------------------

  private void callNotification(final int userID) {
    if (notificationTargets != null) {
      (new Thread("usernotify." + userID) {
	public void run() {
	  for (int i = 0, n = notificationTargets.length; i < n; i++) {
	    try {
	      URL url = new URL(notificationTargets[i], "?id=" + userID);
	      URLConnection conn = url.openConnection();
	      int length = conn.getContentLength();
	      conn.getInputStream().close();
	    } catch (Exception e) {
	      log.log(Level.WARNING, "could not notify "
		      + notificationTargets[i], e);
	    }
	  }
	}
      }).start();
    }
  }

} // RegistrationPage
