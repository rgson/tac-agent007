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
 * Updated : $Date: 2006/05/02 11:44:12 $
 *	     $Revision: 1.2 $
 */

package se.sics.tac.aw;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.botbox.util.ArrayQueue;

public class TACReader extends TACConnection implements Runnable {

  private static final Logger log =
    Logger.getLogger(TACReader.class.getName());

  private InputStream input;
  private OutputStreamWriter output;
  private Socket socket;

  private ArrayQueue queue = new ArrayQueue();
  private boolean disconnected = true;

  protected void init() {
    doConnect();
  }

  public boolean isConnected() {
    return !disconnected;
  }

  public void disconnect() {
    disconnected = true;
    if (socket != null) {
      try {
	output.close();
	input.close();
	socket.close();
      } catch (Exception e) {
	log.log(Level.SEVERE,"could not close connection:", e);
      } finally {
	socket = null;
      }
    }
  }

  public synchronized void sendMessage(TACMessage msg) throws IOException {
    if (disconnected) {
      throw new IOException("Disconnected from server");
    }

    String msgStr = msg.getMessageString();
    addMessage(msg);
    output.write(msgStr);
    output.flush();
  }

  public void run() {
    try {
      byte[] buffer = new byte[1024];
      StringBuffer lastMessage = new StringBuffer();
      int len;
      int lastPos;

      while (!disconnected && (len = input.read(buffer)) != -1) {
	lastPos = 0;
	for (int i = 0; i < len; i++) {
	  if (buffer[i] == 0) {
	    String msg = new String(buffer, lastPos, i - lastPos);
	    lastMessage.append(msg);
	    handleMessage(lastMessage.toString());
	    lastMessage = new StringBuffer();
	    lastPos = i + 1;
	    /* To test if the server handles agents that does not read
	       if (msg.indexOf("bidInfo") > 0) {
	       System.out.println("Reader sleeping...");
	       Thread.sleep(200000);
	       }
	    */
	  }
	}
	if (lastPos < len) {
	  lastMessage.append(new String(buffer, lastPos, len - lastPos));
	}
      }
    } catch (Throwable e) {
      log.log(Level.SEVERE, "could not read:", e);
    } finally {
      agent.reset(0, this);
    }
  }

  // Synchronized to ensure that no one else sends a message before
  // authentication of this connection!
  private synchronized void doConnect() {
    try {
      String host = agent.getHost();
      int port = agent.getPort();
      log.fine("Connecting to server " + host + ':' + port);
      socket = new Socket(host, port);
      input = socket.getInputStream();
      output = new OutputStreamWriter(socket.getOutputStream());
      disconnected = false;
      new Thread(this).start();

      // Automatically login! -> give an auth to the agent...
      TACMessage msg = new TACMessage("auth");
      msg.setParameter("userName", agent.getUser());
      msg.setParameter("userPW", agent.getPassword());
      msg.setMessageReceiver(agent);
      sendMessage(msg);

    } catch (Exception e) {
      disconnected = true;
      log.log(Level.SEVERE, "connection to server failed:", e);
      socket = null;
    }
  }

  private synchronized void addMessage(TACMessage msg) {
    queue.add(msg);
  }

  private synchronized TACMessage getMessage() {
    if (queue.isEmpty()) {
      return null;
    }
    return (TACMessage) queue.remove(0);
  }

  private void handleMessage(String msg) {
    TACMessage tacMsg = getMessage();
    if (tacMsg == null) {
      ///??? ILLEGAL STATE!!!
      throw new IllegalStateException("received unexpected message: "
				      + msg);
    }
    if (!disconnected) {
      tacMsg.setReceivedMessage(msg);
      tacMsg.deliverMessage();
    }
  }
}
