/**
 * SICS TAC Server
 * http://www.sics.se/tac/    tac-dev@sics.se
 *
 * Copyright (c) 2001, 2002 SICS AB. All rights reserved.
 * -----------------------------------------------------------------
 *
 * PageHandler
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : Sat Jun 21 20:38:34 2003
 * Updated : $Date: 2004/05/04 15:48:17 $
 *           $Revision: 1.1 $
 */
package se.sics.tac.is;
import java.io.IOException;
import java.util.Map;

import org.mortbay.http.HttpException;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;
import org.mortbay.http.PathMap;
import org.mortbay.http.handler.AbstractHttpHandler;
import org.mortbay.util.Code;

/**
 */
public class PageHandler extends AbstractHttpHandler {

  private PathMap pathMap = new PathMap();

  public PageHandler() {
  }

  public void addPage(String pathSpec, HttpPage page) {
    if (!pathSpec.startsWith("/") && !pathSpec.startsWith("*")) {
      Code.warning("pathSpec should start with '/' or '*' : " + pathSpec);
      pathSpec = "/" + pathSpec;
    }

    pathMap.put(pathSpec, page);
  }

  public Map.Entry getPageEntry(String pathInContext) {
    return pathMap.getMatch(pathInContext);
  }

  public void handle(String pathInContext,
		     String pathParams,
		     HttpRequest request,
		     HttpResponse response)
    throws HttpException, IOException
  {
    if (!isStarted()) {
      return;
    }

    Map.Entry pageEntry = getPageEntry(pathInContext);
    HttpPage page = pageEntry == null ? null : (HttpPage) pageEntry.getValue();
    if (page != null) {
      page.handle(pathInContext, pathParams, request, response);
    }
  }

} // PageHandler
