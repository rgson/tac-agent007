/**
 * SICS TAC Server
 * http://www.sics.se/tac/    tac-dev@sics.se
 *
 * Copyright (c) 2001, 2002 SICS AB. All rights reserved.
 * -----------------------------------------------------------------
 *
 * AgentRealm
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : Sat Jun 21 20:12:37 2003
 * Updated : $Date: 2004/05/21 17:47:34 $
 *           $Revision: 1.4 $
 * Purpose :
 *
 */
package se.sics.tac.is;
import org.mortbay.http.UserPrincipal;
import org.mortbay.http.HashUserRealm;
import org.mortbay.http.HttpRequest;

/**
 */
public class AgentRealm extends HashUserRealm {

  public static final String ADMIN_ROLE = "admin";

  private InfoServer infoServer;

  public AgentRealm(InfoServer infoServer, String realmName) {
    super(realmName);
    this.infoServer = infoServer;
  }

  void setAdminUser(String name, String password) {
    put(name, password);
    addUserToRole(name, ADMIN_ROLE);
  }

  public UserPrincipal authenticate(String username,
				    Object credentials,
				    HttpRequest request) {
    boolean userExists = get(username) != null;
    if (!userExists) {
      loadUser(username);
    }

    UserPrincipal user = super.authenticate(username, credentials, request);
    if ((user == null || !user.isAuthenticated()) && userExists) {
      infoServer.updateAgent(username);
      loadUser(username);
      user = super.authenticate(username, credentials, request);
    }
    return user;
  }

  private void loadUser(String name) {
    TACStore store = infoServer.getTACStore();
    TACUser user = store.getUser(name);
    if (user != null) {
      String password = user.getPassword();
      if (password != null) {
	put(name, password);
      }
    }
  }

} // AgentRealm
