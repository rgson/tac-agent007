/**
 * SICS TAC Classic Server
 * http://www.sics.se/tac/    tac-dev@sics.se
 *
 * Copyright (c) 2001-2004 SICS AB. All rights reserved.
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
 * LineServer
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : Fri Apr 30 11:17:35 2004
 * Updated : $Date: 2004/05/04 15:48:18 $
 *           $Revision: 1.1 $
 */
package se.sics.tac.server;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.logging.Logger;

import com.botbox.util.ThreadPool;
import se.sics.isl.inet.InetServer;
import se.sics.isl.util.AMonitor;
import se.sics.tac.line.LineConnection;
import se.sics.tac.line.LineListener;
import se.sics.isl.util.AdminMonitor;

/**
 */
public class LineServer extends InetServer implements LineListener, AMonitor {

  private static final Logger log =
    Logger.getLogger(LineServer.class.getName());

  private static final String STATUS_NAME = "Info";

  private final InfoServer infoServer;
  private ThreadPool threadPool;

  private LineConnection connection;
  private int connectionID = 0;

  public LineServer(InfoServer infoServer, String host, int port)
    throws IOException
  {
    super("info", host, port);
    this.infoServer = infoServer;

    this.threadPool = ThreadPool.getThreadPool("info");
    this.threadPool.setMinThreads(1);
    this.threadPool.setMaxThreads(2);
    this.threadPool.setMaxIdleThreads(2);
    this.threadPool.setInterruptThreadsAfter(120000);

    AdminMonitor adminMonitor = AdminMonitor.getDefault();
    if (adminMonitor != null) {
      adminMonitor.addMonitor(STATUS_NAME, this);
    }
  }

  // -------------------------------------------------------------------
  //  Inet Server
  // -------------------------------------------------------------------

  protected void serverStarted() {
    log.info("info server started at " + getBindAddress());
  }

  protected void serverShutdown() {
    closeConnection();

    log.severe("info server has closed");
    infoServer.serverClosed(this);
  }

  protected void newConnection(Socket socket) throws IOException {
    if (this.connection != null) {
      // Already connected: deny new connection
      InetAddress remoteAddress = socket.getInetAddress();
      log.warning("info connection already open: denying access from "
		  + remoteAddress.getHostAddress());
      socket.close();

    } else {
      LineConnection channel =
	new LineConnection(getName() + '-' + (++connectionID),
			   socket, this, false);
      channel.setThreadPool(threadPool);
      this.connection = channel;
      channel.start();
      log.fine("new info connection: " + channel.getName());
      infoServer.connectionOpened(this);
    }
  }

  public synchronized void lineRead(LineConnection source, String line) {
    if (source != this.connection) {
      if (line != null) {
	// Wrong connection
	log.severe("wrong info connection: " + source + "<=>"
		   + this.connection);
      }
      source.close();

    } else if (line == null) {
      // connection has closed
      log.fine("info connection " + source.getName() + " has closed");
      this.connection = null;
      infoServer.connectionClosed(this);

    } else if (line.length() > 0) {
      infoServer.deliverMessage(line);

    } else {
      // Empty line => ignore
    }
  }


  // -------------------------------------------------------------------
  // Interface towards InfoServer
  // -------------------------------------------------------------------

  public void send(String message) {
    LineConnection connection = this.connection;
    if (connection == null) {
      log.severe("no connection to InfoServer when sending: " + message);
    } else {
      connection.write(message);
    }
  }

  public void closeConnection() {
    LineConnection connection = this.connection;
    this.connection = null;

    if (connection != null) {
      connection.close();
    }
  }


  // -------------------------------------------------------------------
  // AMonitor API
  // -------------------------------------------------------------------

  public String getStatus(String propertyName) {
    if (propertyName != STATUS_NAME) {
      return null;
    }

    StringBuffer sb = new StringBuffer();
    sb.append("--- Info Connections ---");

    LineConnection connection = this.connection;
    if (connection != null) {
      sb.append('\n')
	.append(connection.getName())
	.append(" (")
	.append(connection.getRemoteHost())
	.append(':').append(connection.getRemotePort())
	.append(')');
    } else {
      sb.append("\n<no connection>");
    }
    return sb.toString();
  }

} // LineServer
