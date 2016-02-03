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
 * AppletPage
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 12 April, 2002
 * Updated : $Date: 2004/05/04 15:48:16 $
 *	     $Revision: 1.1 $
 * Purpose :
 *
 */

package se.sics.tac.is;
import java.io.IOException;
import java.util.logging.Logger;

import org.mortbay.http.HttpException;
import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import org.mortbay.util.ByteArrayISO8859Writer;

public class AppletPage extends HttpPage {

  private final String page;
  private final String endPage = "'>\r\n"
    + "<font color=white>"
    + "You must use a browser with Java support and have Java turned on "
    + "to use the TAC game viewer.</font>\r\n"
    + "</applet></td></tr></table>\r\n"
    + "<font size=-1><em>Note that you can see the client preferences by clicking on an agent name during a game.</em></font>"
    + "</body></html>\r\n";

  public AppletPage(InfoServer is, int appletPort) {
    this.page =
      "<html><head><title>TAC Classic - Game Information</title>"
      + "</head><body>\r\n"
      + "<table bgcolor=black cellpadding=1 cellspacing=0><tr><td>\r\n"
      + "<applet code='se.sics.tac.applet.TACApplet' "
      + "width=620 height=610 archive='/code/tacapplet.jar'>\r\n"
      + "<param name=port value='" + appletPort + "'>\r\n"
      + "<param name=agent value='";
  }

  public void handle(String pathInContext, String pathParams,
		     HttpRequest request, HttpResponse response)
    throws HttpException, IOException
  {
    String userName = request.getAuthUser();

    ByteArrayISO8859Writer writer = new ByteArrayISO8859Writer();
    writer.write(page);
    writer.write(userName);
    writer.write(endPage);
    response.setContentType(HttpFields.__TextHtml);
    response.setContentLength(writer.size());
    writer.writeTo(response.getOutputStream());
    response.commit();
  }

} // AppletPage
