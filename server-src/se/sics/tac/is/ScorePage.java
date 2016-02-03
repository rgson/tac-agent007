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
 * ScorePage
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 15 April, 2002
 * Updated : $Date: 2004/09/14 11:25:01 $
 *	     $Revision: 1.3 $
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
import org.mortbay.util.URI;

public class ScorePage extends HttpPage {

  private static final Logger log =
    Logger.getLogger(ScorePage.class.getName());

  private final InfoServer infoServer;
  private final String gamePath;

  public ScorePage(InfoServer is, String gamePath) throws IOException {
    this.infoServer = is;
    if (gamePath != null && !gamePath.endsWith("/")) {
      gamePath += '/';
    }
    this.gamePath = gamePath;
  }

  public void handle(String pathInContext, String pathParams,
		     HttpRequest request, HttpResponse response)
    throws HttpException, IOException
  {
    int lastGameID = infoServer.getTACStore()
      .getInt(TACStore.LAST_PLAYED_GAME_ID, -1);
    if (lastGameID == -1) {
      String page = "<html><body bgcolor=white link='#204020' vlink='#204020'>"
	+ "<font face='Arial,Helvetica,sans-serif' size='+2'><b>"
	+ "Scores"
	+ "</b></font><p>\r\n"
	+ "<font face='Arial,Helvetica,sans-serif' size='+1'>"
	+ "No games played</font><p>\r\n"
	+ InfoServer.HTTP_FOOTER
	+ "</body></html>\r\n";
      ByteArrayISO8859Writer writer = new ByteArrayISO8859Writer();
      writer.write(page);
      response.setContentType(HttpFields.__TextHtml);
      response.setContentLength(writer.size());
      writer.writeTo(response.getOutputStream());
      response.commit();

    } else {
      Competition competition = infoServer.getTACStore().
	getCurrentCompetition();
      String file;
      if (gamePath != null) {
	file = gamePath;
      } else {
	// This is only possible when standalone. FIX THIS!!!
	StringBuffer buf = request.getRootURL();
	file = URI.addPaths(buf.toString(), "/history/");
      }
      if (competition != null) {
	file = file + "competition/" + competition.getID() + '/';
      } else {
	file = file + "default/";
      }
      response.setField(HttpFields.__Location, file);
      response.setStatus(302);
      request.setHandled(true);
    }
  }

} // ScorePage
