/**
 * SICS TAC Server
 * http://www.sics.se/tac/    tac-dev@sics.se
 *
 * Copyright (c) 2001, 2002 SICS AB. All rights reserved.
 * -----------------------------------------------------------------
 *
 * StaticPage
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : Sat Jun 21 20:27:17 2003
 * Updated : $Date: 2004/05/04 15:48:17 $
 *           $Revision: 1.1 $
 */
package se.sics.tac.is;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.botbox.html.HtmlWriter;
import org.mortbay.http.HttpException;
import org.mortbay.http.HttpFields;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import org.mortbay.util.ByteArrayISO8859Writer;

/**
 */
public class StaticPage extends HttpPage {

  private static final Logger log =
    Logger.getLogger(StaticPage.class.getName());

  private String path;
  private byte[] pageData;
  private String contentType = HttpFields.__TextHtml;

  private StaticPage(String path) {
    if (path == null) {
      throw new NullPointerException();
    }
    this.path = path;
  }

  public StaticPage(String path, String page) {
    this(path);
    setPage(page);
  }

  public StaticPage(String path, HtmlWriter writer) {
    this(path);
    setPage(writer);
  }

  public void setPage(String page) {
    try {
      ByteArrayISO8859Writer writer =
	new ByteArrayISO8859Writer();
      writer.write(page);
      this.pageData = writer.getByteArray();
    } catch (Exception e) {
      log.log(Level.SEVERE, "could not set page data for " + path, e);
    }
  }

  public void setPage(HtmlWriter page) {
    try {
      ByteArrayISO8859Writer writer = new ByteArrayISO8859Writer();
      page.close();
      page.write(writer);
      this.pageData = writer.getByteArray();
    } catch (Exception e) {
      log.log(Level.SEVERE, "could not set page data for " + path, e);
    }
  }

  public void handle(String pathInContext, String pathParams,
		     HttpRequest request, HttpResponse response)
    throws HttpException, IOException
  {
    if (path.equals(pathInContext) && pageData != null) {
      response.setContentType(contentType);
      response.setContentLength(pageData.length);
      response.getOutputStream().write(pageData);
      response.commit();
    }
  }

  public String toString() {
    return "StaticPage[" + path + ','
      + (pageData == null ? 0 : pageData.length) + ']';
  }

} // StaticPage
