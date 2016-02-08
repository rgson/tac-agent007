/**
 * SICS TAC Server - InfoServer
 * http://www.sics.se/tac/	  tac-dev@sics.se
 *
 * Copyright (c) 2001-2005 SICS AB. All rights reserved.
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
 * ArgEnumerator
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 21 March, 2002
 * Updated : $Date: 2005/06/07 19:06:17 $
 *	     $Revision: 1.1 $
 * Purpose : Utility for handling program arguments
 */

package se.sics.tac.util;

public class ArgEnumerator {

  private String[] args;
  private boolean[] used;
  private int index = 0;
  private String usage;

  public ArgEnumerator(String[] args, String usage) {
    this(args, usage, false);
  }

  public ArgEnumerator(String[] args, String usage, boolean checkHelp) {
    if (args == null) {
      args = new String[0];
    }
    this.args = args;
    this.used = new boolean[args.length];
    this.usage = usage;
    if (checkHelp) {
      checkHelp();
    }
  }

  public String getUsage() {
    return usage;
  }

  public void setUsage(String usage) {
    this.usage = usage;
  }

  public void reset() {
    index = 0;
    for (int i = 0, n = used.length; i < n; i++) {
      used[i] = false;
    }
  }

  public boolean hasArgument(String name) {
    for (int i = 0, n = args.length; i < n; i++) {
      if (args[i].equals(name)) {
	return used[i] = true;
      }
    }
    return false;
  }

  public String getArgument(String name) {
    return getArgument(name, null);
  }

  public String getArgument(String name, String defaultValue) {
    for (int i = 0, n = args.length; i < n; i++) {
      if (args[i].equals(name)) {
	used[i++] = true;
	if (i < n) {
	  used[i] = true;
	  return args[i];
	} else {
	  // Missing value for argument 'name'
	  System.err.println("Missing value for argument '" + name + '\'');
	  usage(1);
	}
      }
    }
    return defaultValue;
  }

  public int getArgument(String name, int defaultValue) {
    String value = getArgument(name, null);
    if (value != null) {
      try {
	return Integer.parseInt(value);
      } catch (Exception e) {
	System.err.println("Non-integer value for argument '" + name + '\'');
	usage(1);
      }
    }
    return defaultValue;
  }

  // Standard checking for help
  public void checkHelp() {
    if (hasArgument("-h") || hasArgument("-help") || hasArgument("-?")) {
      usage(0);
    }
  }

  public void checkArguments() {
    for (int i = 0, n = used.length; i < n; i++) {
      if (!used[i]) {
	System.err.println("Unknown argument '" + args[i] + '\'');
	usage(1);
      }
    }
  }



  // -------------------------------------------------------------------
  // Enumeration handling
  // -------------------------------------------------------------------

  public boolean hasNext() {
    return index < args.length;
  }

  public String next() {
    return args[index++];
  }

  public String getString(String name) {
    int currentIndex = index - 1;
    if (currentIndex < 0) {
      // Can not get parameter before first argument
      throw new IllegalStateException("before first argument");
    }
    if (args[currentIndex].length() > name.length()) {
      // Take argument from rest of argument line
      return args[currentIndex].substring(name.length());
    }
    if (index < args.length) {
      // Argument value is the next argument
      return args[index++];
    }
    // Missing value for argument
    System.err.println("Missing value for argument '" + name + '\'');
    usage(1);
    return null;
  }

  public int getInt(String name) {
    String value = getString(name);
    try {
      return Integer.parseInt(value);
    } catch (Exception e) {
      System.err.println("Non-integer value for argument '" + name + '\'');
      usage(1);
    }
    return 0;
  }

  public void usage(int error) {
    if (usage != null) {
      System.out.println(usage);
    }
    System.exit(error);
  }

} // ArgEnumerator
