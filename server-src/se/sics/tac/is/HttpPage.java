/**
 * SICS TAC Server
 * http://www.sics.se/tac/    tac-dev@sics.se
 *
 * Copyright (c) 2001, 2002 SICS AB. All rights reserved.
 * -----------------------------------------------------------------
 *
 * HttpPage
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : Sat Jun 21 20:27:46 2003
 * Updated : $Date: 2004/05/04 15:48:16 $
 *           $Revision: 1.1 $
 * Purpose :
 *
 */
package se.sics.tac.is;
import java.io.IOException;
import org.mortbay.http.HttpException;
import org.mortbay.http.HttpRequest;
import org.mortbay.http.HttpResponse;

/**
 */
public abstract class HttpPage {

  public abstract void handle(String pathInContext, String pathParams,
			      HttpRequest request, HttpResponse response)
    throws HttpException, IOException;

} // HttpPage
