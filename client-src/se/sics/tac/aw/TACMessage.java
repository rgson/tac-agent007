/**
 * TAC AgentWare
 * http://www.sics.se/tac        tac-dev@sics.se
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
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 23 April, 2002
 * Updated : $Date: 2005/06/07 19:06:16 $
 *	     $Revision: 1.1 $
 */

package se.sics.tac.aw;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TACMessage {

  private static final Logger log =
    Logger.getLogger(TACMessage.class.getName());

  private static long msgCount = 0;
  private static long totalResponseTime = 0;

  private final String type;
  private ArrayList msg;

  private String sentMessage;
  private long timeSent;
  private String receivedMessage;
  private int pos = 0;
  private long responseTime;
  private boolean isTACError = false;

  private TACMessageReceiver receiver;
  private Object userData;

  public TACMessage(String type) {
    this.type = type;
    msg = new ArrayList();
  }

  public String getType() {
    return type;
  }

  public boolean isTACError() {
    return isTACError;
  }

  void setUserData(Object obj) {
    userData = obj;
  }

  Object getUserData() {
    return userData;
  }

  void setMessageReceiver(TACMessageReceiver receiver) {
    this.receiver = receiver;
  }

  void deliverMessage() {
    if (receiver != null) {
      try {
	receiver.messageReceived(this);
      } catch (ThreadDeath e) {
	log.log(Level.SEVERE, "receiver could not handle message: " +
		this, e);
	throw e;
      } catch (Throwable e) {
	log.log(Level.SEVERE, "receiver could not handle message: " +
		this, e);
      }
    }
  }

  public long getResponseTime() {
    return responseTime;
  }

  void setReceivedMessage(String receivedMessage) {
    if (this.receivedMessage != null) {
      throw new IllegalStateException("Message alredy received: " +
				      this.receivedMessage);
    }
    if (timeSent > 0) {
      responseTime = System.currentTimeMillis() - timeSent;
    }
    this.receivedMessage = receivedMessage;
    totalResponseTime += responseTime;
    msgCount++;

    // Check if extra information should be displayed
    if ("getQuote".equals(type)) {
      Object data = userData;
      int auction = -1;
      if (data instanceof Quote) {
	auction = ((Quote) data).getAuction();
      } else if (data instanceof Bid) {
	auction = ((Bid) data).getAuction();
      }
      if (auction >= 0) {
	log.finest("requesting quotes for auction " + auction + " ("
		   + TACAgent.getAuctionTypeAsString(auction) + ')');
      }
    } else if ("submitBid".equals(type) || "replaceBid".equals(type)) {
      Object data = userData;
      if (data instanceof Bid) {
	int auction = ((Bid) data).getAuction();
	log.finest("submitting bid (" + type
		   + ") to auction " + auction + " ("
		   + TACAgent.getAuctionTypeAsString(auction) + ')');
      }
    }

    log.finest("XML out: '" + sentMessage + '\'');
    log.finest("XML in: '" + receivedMessage + "' responseTime: " +
	       getResponseTime() + " avg: " +
	       getAverageResponseTime() + " count: " +
	       getMessageCount());

    pos = 0;
    if (nextTag() && (!isDeclaration() || nextTag())) {
      if (isTag(type)) {
	pos = 0;
      } else if (isTag("tacerror")) {
	isTACError = true;
      } else {
	throw new IllegalStateException("Message not expected: " +
					type + " -> " + getTag());
      }
    } else {
      throw new IllegalArgumentException("Malformed message: " +
					 receivedMessage);
    }
  }

  public void setParameter(String name, String value) {
    msg.add(name);
    msg.add(value);
    sentMessage = null;
  }

  public void setParameter(String name, int value) {
    msg.add(name);
    msg.add(Integer.toString(value));
    sentMessage = null;
  }

  public void setParameter(String name, float value) {
    msg.add(name);
    msg.add(Float.toString(value));
    sentMessage = null;
  }

  public String getParameter(String name) {
    for (int i = 0, n = msg.size(); i < n; i += 2) {
      if (msg.get(i).equals(name))
	return (String) msg.get(i + 1);
    }
    return null;
  }

  public boolean nextTag() {
    if (pos < receivedMessage.length()) {
      int nextPos = receivedMessage.indexOf('<', pos);
      if (nextPos >= 0) {
	pos = nextPos + 1;
	return true;
      }
    }
    return false;
  }

  public String getValue() {
    int start = receivedMessage.indexOf('>', pos);
    int end = receivedMessage.indexOf('<', start);
    if (start > 0 && end > 0) {
      return receivedMessage.substring(start + 1, end);
    }
    return null;
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

  public String getTag() {
    int end = receivedMessage.indexOf('>', pos);
    if (end > 0) {
      return receivedMessage.substring(pos, end);
    }
    return null;
  }

  public boolean isDeclaration() {
    return pos < receivedMessage.length()
      && receivedMessage.charAt(pos) == '?';
  }

  public boolean isTag(String name) {
    name = name + '>';
    int len = name.length();
    return receivedMessage.regionMatches(pos, name, 0, len);
  }

//   public void reset() {
//     pos = 0;
//   }

  public String getMessageString() {
    String message = this.sentMessage;
    if (message == null) {
      StringBuffer sb = new StringBuffer();
      sb.append('<').append(type).append('>');

      for (int i = 0, n = msg.size(); i < n; i += 2) {
	sb.append('<').append(msg.get(i)).append('>');
	sb.append(msg.get(i+1));
	sb.append("</").append(msg.get(i)).append('>');
      }
      sb.append("</").append(type).append('>');
      this.sentMessage = sb.toString();
      // Timestamp the generation of this message (when it was sent)
      this.timeSent = System.currentTimeMillis();
      return sb.append('\0').toString();
    } else {
      // Timestamp the generation of this message (when it was sent)
      this.timeSent = System.currentTimeMillis();
      return message + '\0';
    }
  }

  public static long getMessageCount() {
    return msgCount;
  }

  public static float getAverageResponseTime() {
    if (msgCount == 0) {
      return 0f;
    }
    return (float) (totalResponseTime / msgCount);
  }

  public static void resetResponseTime() {
    totalResponseTime = 0;
    msgCount = 0;
  }
}
