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
 * User
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-05
 * Updated : $Date: 2004/05/04 15:48:18 $
 *	     $Revision: 1.1 $
 * Purpose :
 *
 */

package se.sics.tac.server;

public class User {

  protected final int id;
  protected String name;
  protected String password;

  protected final User parent;
  protected User[] childs;

  User(int id, String name, String password) {
    this.id = id;
    this.name = name;
    this.password = password;
    this.parent = null;
  }

  private User(int id, User parent) {
    this.id = id;
    this.name = parent.name + ((char) (id - parent.id - 1 + '0'));
    this.password = parent.password;
    this.parent = parent;
  }

  public int getID() {
    return id;
  }

  public boolean isDummyUser() {
    return id < 0;
  }

  public String getName() {
    return name;
  }

  public String getPassword() {
    return password;
  }

  void setUserInfo(String name, String password) {
    if (parent != null) {
      parent.setUserInfo(name, password);
    } else {
      this.name = name;
      this.password = password;

      if (childs != null) {
	synchronized (this) {
	  User child;
	  for (int i = 0, n = childs.length; i < n; i++) {
	    if ((child = childs[i]) != null) {
	      child.name = name + ((char) (i + '0'));
	      child.password = password;
	    }
	  }
	}
      }
    }
  }

  public User getChild(int id) {
    if (parent != null) {
      return parent.getChild(id);
    }

    int childNumber = id - this.id - 1;
    if ((id <= this.id) || (childNumber > 9)) {
      // Not a valid child id
      return null;
    }

    if (childs == null || childs[childNumber] == null) {
      synchronized (this) {
	if (childs == null) {
	  childs = new User[10];
	}
	if (childs[childNumber] == null) {
	  childs[childNumber] = new User(id, this);
	}
      }
    }
    return childs[childNumber];
  }

} // User
