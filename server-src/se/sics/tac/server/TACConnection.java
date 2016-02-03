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
 * TACConnection
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-03
 * Updated : $Date: 2004/09/07 14:46:02 $
 *	     $Revision: 1.7 $
 */

package se.sics.tac.server;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.botbox.util.ArrayUtils;
import se.sics.isl.inet.InetConnection;

public class TACConnection extends InetConnection {

  private final static Logger log =
    Logger.getLogger(TACConnection.class.getName());

  private final static String HEADER = "<?xml version=\"1.0\"?>";
  private final static byte EOM = (byte) 0;

  private final static int MAX_BUFFER_SIZE = 40 * 1024;
  private final static int BUFFER_INCREASE = 2048;

  private final TACServer tacServer;

  private InputStreamReader reader;
  private OutputStreamWriter writer;
  private char[] buffer = new char[BUFFER_INCREASE];
  private int bufferLen = 0;

  private long sentChars;
  private long requestedSentChars;
  private long readChars;

  private User user;

  private long lastAliveTime;

  TACConnection(String name, TACServer tacServer, Socket socket) {
    super(name, socket);
    this.tacServer = tacServer;
    setDeliveryBuffered(false);
    setWriteBuffered(false);

    this.lastAliveTime = getConnectTime();
  }


  // -------------------------------------------------------------------
  //
  // -------------------------------------------------------------------

  public User getUser() {
    return user;
  }

  void setUser(User user) {
    this.user = user;
    setUserName(user.getName());
    tacServer.connectionAuthenticated(this);
  }

  public long getLastAliveTime() {
    return lastAliveTime;
  }


  // -------------------------------------------------------------------
  // Connection handling
  // -------------------------------------------------------------------

  protected void connectionOpened() throws IOException {
    reader = new InputStreamReader(getInputStream());
    writer = new OutputStreamWriter(getOutputStream());
  }

  protected void connectionClosed() throws IOException {
    tacServer.removeAgentConnection(this);
    reader.close();
    writer.close();
  }

  protected void doReadMessages() throws IOException {
    while (!isClosed()) {
      if (bufferLen >= buffer.length) {
	if (bufferLen + BUFFER_INCREASE > MAX_BUFFER_SIZE) {
	  // Buffer overflow
	  log.severe("out buffer overflow for connection " + getName()
		     + " from " + getRemoteHost());
	  throw new EOFException();
	}
	buffer = ArrayUtils.setSize(buffer, bufferLen + BUFFER_INCREASE);
      }

      int len = reader.read(buffer, bufferLen, buffer.length - bufferLen);
      if (len < 0) {
	throw new EOFException();
      }
      this.lastAliveTime = System.currentTimeMillis();

      int lastPos = 0;
      for (int i = bufferLen, n = bufferLen + len; i < n; i++) {
	if (buffer[i] == '\0') {
	  // Found end of message
	  deliverMessage(new String(buffer, lastPos, i - lastPos));
	  lastPos = i + 1;
	}
      }
      bufferLen += len;
      if (lastPos > 0) {
	if (lastPos < bufferLen) {
	  System.arraycopy(buffer, lastPos, buffer, 0, bufferLen - lastPos);
	  bufferLen -= lastPos;
	} else {
	  bufferLen = 0;
	}
      }
    }
  }

  protected void doDeliverMessage(Object messageObject) {
    String content = (String) messageObject;
    TACMessage message = new TACMessage(this, content);
    if (message.getType() == null) {
      // No message type was found
      log.warning("XML_IN(" + getName() + "): MALFORMED MESSAGE: "
		  + content);
      message.replyError("malformed message");
    } else {
      log.finest("XML_IN(" + getName() + "): " + content);
      tacServer.deliverMessage(message);
    }

    try {
      message.waitForReply();
    } catch (Exception e) {
      log.log(Level.SEVERE, "connection " + getName()
	      + " interrupted waiting for reply to " + message.getType(), e);
    }
    message.doReply();
  }

  protected void doSendMessage(Object messageObject) throws IOException {
    String message = (String) messageObject;
    log.finest("XML_OUT(" + getName() + "): " + message);
    writer.write(HEADER);
    writer.write(message);
    writer.write('\0');
    writer.flush();
  }

  // DEBUG FINALIZE REMOVE THIS!!! REMOVE THIS!!!
  protected void finalize() throws Throwable {
    log.finest("TAC CONNECTION " + getName() + " FROM "
	       + getRemoteHost() + " IS BEING GARBAGED");
    super.finalize();
  }

} // TACConnection
