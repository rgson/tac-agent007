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
 * TACMessage
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-04
 * Updated : $Date: 2004/06/07 08:42:33 $
 *	     $Revision: 1.3 $
 */

package se.sics.tac.server;

public class TACMessage {

  private final TACConnection connection;
  private final String message;
  private final int messageLen;
  private final String type;
  private int pos = 0;
  private int tagEnd = -1;
  private int tagNameEnd = -1;

  private String replyMessage = null;
  private boolean hasReplied = false;

  TACMessage(TACConnection connection, String message) {
    if (connection == null) {
      throw new NullPointerException();
    }
    this.connection = connection;
    this.message = message;
    this.messageLen = message.length();
    this.type = nextTag() ? getTag() : null;
  }

  public String getName() {
    return type + " from " + connection.getName();
  }

  public String getType() {
    return type;
  }

  public User getUser() {
    return connection.getUser();
  }

  void setUser(User user) {
    connection.setUser(user);
  }

  TACConnection getConnection() {
    return connection;
  }


  // -------------------------------------------------------------------
  // Message parsing
  // -------------------------------------------------------------------

  public boolean nextTag() {
    if (pos >= messageLen) {
      tagEnd = -1;
      return false;
    }

    int nextPos = message.indexOf('<', tagEnd > 0 ? (tagEnd + 1) : pos);
    if (nextPos >= 0) {
      pos = nextPos = wss(nextPos + 1);
      tagEnd = message.indexOf('>', pos);

      // Find tag name end (endTag will be -1 if nextPos >= messageLen)
      if (tagEnd > pos) {
	char c = message.charAt(nextPos);
	if (c == '?') {
	  // XML Header: ignore for now
	  pos = tagEnd + 1;
	  return nextTag();
	} else {
	  if (c == '/') {
	    // Tag beginning with '/' is an end tag and the name must
	    // contain the '/'
	    nextPos++;
	  }
	  // If the name ends with '/' it is an open/end tag such as
	  // <tag/> and the '/' should not be included in the tag name
	  while ((nextPos < tagEnd)
		 && ((c = message.charAt(nextPos)) > 32)
		 && (c != '>')
		 && (c != '/')) nextPos++;
	  tagNameEnd = nextPos;
	  return true;
	}
      } else {
	// Malformed XML: FIX THIS!!!
// 	log.warning("malformed URL from "
// 		    + (connection != null
// 		       ? connection.getName()
// 		       : "<no connection>"
// 		       ) + ": '" + message + '\'');
	return false;
      }
    }
    return false;
  }

  public String getValue() {
    return getValue(null);
  }

  public String getValue(String defaultValue) {
    if (tagEnd < 0) {
      return defaultValue;
    }
    int start = wss(tagEnd + 1);
    if (start < messageLen) {
      int end = message.indexOf('<', start);
      while (end > start && message.charAt(end) <= 32) {
	end--;
      }
      return (end > start)
	? message.substring(start, end)
	: defaultValue;
    }
    return defaultValue;
  }

  public int getValueAsInt(int def) {
    String val = getValue();
    if (val != null) {
      try {
	return Integer.parseInt(val);
      } catch (Exception e) {
      }
    }
    return def;
  }

  public long getValueAsLong(long def) {
    String val = getValue();
    if (val != null) {
      try {
	return Long.parseLong(val);
      } catch (Exception e) {
      }
    }
    return def;
  }

  public float getValueAsFloat(float def) {
    String val = getValue();
    if (val != null) {
      try {
	return Float.parseFloat(val);
      } catch (Exception e) {
      }
    }
    return def;
  }

  public double getValueAsDouble(double def) {
    String val = getValue();
    if (val != null) {
      try {
	return Double.parseDouble(val);
      } catch (Exception e) {
      }
    }
    return def;
  }

  public String getTag() {
    return tagEnd < 0
      ? null
      : message.substring(pos, tagNameEnd);
  }

  public boolean isTag(String name) {
    int len = name.length();
    return (tagEnd > 0)
      && ((pos + len) == tagNameEnd)
      && message.regionMatches(pos, name, 0, len);
  }

//   public void reset() {
//     pos = 0;
//     tagEnd = -1;
//   }

  // Skips any whitespace
  private int wss(int index) {
    while (index < messageLen && message.charAt(index) <= 32) {
      index++;
    }
    return index;
  }



  // -------------------------------------------------------------------
  // Message Replying
  // -------------------------------------------------------------------

  public boolean hasReply() {
    return replyMessage != null;
  }

  public boolean hasReplied() {
    return hasReplied;
  }

  public void replyError(int statusCommand) {
    reply("", statusCommand);
  }

  public void replyError(String tacerror) {
    reply("<tacerror>" + tacerror + "</tacerror>");
  }

  public void replyMissingField(String fieldName) {
    reply("<tacerror>missing field " + fieldName + "</tacerror>");
  }

  public void reply(String content, int statusCommand) {
    reply('<' + type + '>' + content
	  + "<commandStatus>" + statusCommand
	  + "</commandStatus></" + type + '>');
  }

  private synchronized void reply(String message) {
    if (this.replyMessage != null) {
      throw new IllegalStateException("message " + type + " from "
				      + connection.getName()
				      + " already replied");
    }
    this.replyMessage = message;
    notify();
  }

  synchronized void waitForReply() throws InterruptedException {
    while (this.replyMessage == null) {
      wait(10000);
    }
  }

  // Called by TACConnection when it is time to reply
  void doReply() {
    if (hasReplied) {
      throw new IllegalStateException("message " + type + " from "
				      + connection.getName()
				      + " already replied");
    }
    if (replyMessage == null) {
      throw new IllegalStateException("message " + type + " from "
				      + connection.getName()
				      + " has no reply");
    }

    hasReplied = true;
    connection.sendMessage(replyMessage);
  }

} // TACMessage
