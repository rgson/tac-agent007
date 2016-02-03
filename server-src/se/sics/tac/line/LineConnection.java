/**
 * SICS ISL Java Utilities
 * http://www.sics.se/tac/    tac-dev@sics.se
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
 * LineConnection
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : Tue Jun 17 15:01:21 2003
 * Updated : $Date: 2004/06/01 11:28:15 $
 *           $Revision: 1.2 $
 */
package se.sics.tac.line;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.botbox.util.ArrayQueue;
import com.botbox.util.JobStatus;
import com.botbox.util.ThreadPool;

/**
 *
 * - One thread for reading
 * - Threadpool threads for delivering lines (configurable),
 *   and for writing
 */
public class LineConnection {

  private static final Logger log =
    Logger.getLogger(LineConnection.class.getName());

  // Maximum - 1 megabyte of data in messages!!!
  private static final int MAX_BUFFER_SIZE = 1024 * 1024;

  private String name;
  private String fullName;
  private String userName;
  private LineListener listener;

  private Socket socket;
  private String remoteHost;
  private int remotePort;
  private BufferedReader reader;
  private OutputStreamWriter writer;

  private int maxBuffer = MAX_BUFFER_SIZE;

  private long sentChars;
  private long requestedSentChars;
  private long readChars;

  private boolean bufferDelivery = false;
  private boolean deliveryRunning = false;
  private boolean writerRunning = false;
  private boolean isOpen = false;
  private boolean isClosed = true;

  private boolean isActive = false;

  private ArrayQueue inBuffer;
  private ArrayQueue outBuffer;

  private ThreadPool threadPool;

  private LineWriter lineWriter;
  private LineDeliverer lineDeliverer;
  private LineReader lineReader;

  public LineConnection(String name, Socket socket, LineListener listener,
			boolean bufferDelivery) {
    this.name = name;
    this.fullName = name;
    this.socket = socket;
    this.listener = listener;
    this.bufferDelivery = bufferDelivery;
  }

  public LineConnection(String name, String host, int port,
			LineListener listener, boolean bufferDelivery) {
    this.name = name;
    this.fullName = name;
    this.remoteHost = host;
    this.remotePort = port;
    this.listener = listener;
    this.bufferDelivery = bufferDelivery;
  }

  public String getName() {
    return fullName;
  }

  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    if (userName == null) {
      throw new NullPointerException();
    }
    // User name can only be set once
    this.fullName = userName + '@' + this.name;
    this.userName = userName;
  }

  public boolean isActive() {
    return isActive;
  }

  public void setActive(boolean isActive) {
    this.isActive = isActive;
  }

  public String getRemoteHost() {
    return remoteHost;
  }

  public int getRemotePort() {
    return remotePort;
  }

  public int getMaxBuffer() {
    return maxBuffer;
  }

  public void setMaxBuffer(int maxBuffer) {
    this.maxBuffer = maxBuffer;
  }

  public ThreadPool getThreadPool() {
    ThreadPool pool = this.threadPool;
    if (pool == null) {
      pool = this.threadPool = ThreadPool.getDefaultThreadPool();
    }
    return pool;
  }

  public void setThreadPool(ThreadPool threadPool) {
    this.threadPool = threadPool;
  }

  public final void start() throws IOException {
    if (reader != null) {
      return;
    }

    if (socket == null) {
      socket = new Socket(remoteHost, remotePort);
    } else {
      InetAddress remoteAddress = socket.getInetAddress();
      this.remoteHost = remoteAddress.getHostAddress();
      this.remotePort = socket.getPort();
    }

    reader =
      new BufferedReader(new InputStreamReader(socket.getInputStream()));
    writer =
      new OutputStreamWriter(socket.getOutputStream());

    if (bufferDelivery) {
      inBuffer = new ArrayQueue();
    }
    outBuffer = new ArrayQueue();
    isClosed = false;
    isOpen = true;

    lineReader = new LineReader(name, this);
    lineReader.start();

    connectionOpened();
  }

  protected void connectionOpened() {
  }

  public boolean isClosed() {
    return !isOpen;
  }

  public void close() {
    if (isOpen) {
      isOpen = false;
      log.finest(fullName + ": connection from " + remoteHost
		 + " requested to close");
      addOutBuffer(null);
    }
  }

  public void closeImmediately() {
    closeImmediately(true);
  }

  private void closeImmediately(boolean useThread) {
    if (!isClosed) {
      isOpen = false;
      isClosed = true;
      deliverLine(null);
      if (useThread) {
	getThreadPool().invokeLater(new LineCloser(this));
      } else {
	doClose();
      }
    }
  }

  private void doClose() {
    try {
      log.finest(fullName + ": connection closed from " + remoteHost);
      lineReader.interrupt();
      reader.close();
      writer.close();
      socket.close();
    } catch (Exception e) {
      // Ignore errors when closing connection
    } finally {
      connectionClosed();
    }
  }

  protected void connectionClosed() {
  }


  // -------------------------------------------------------------------
  // Data sending
  // -------------------------------------------------------------------

  public void write(String line) {
    if (isOpen && line != null) {
      requestedSentChars += line.length();
      if ((requestedSentChars - sentChars) > maxBuffer) {
	log.log(Level.SEVERE, fullName + ": could not send data",
		new IOException("out buffer overflow: "
				+ (requestedSentChars - sentChars)));
	closeImmediately();
      } else {
	addOutBuffer(line);
      }
    }
  }

  private void addOutBuffer(String line) {
    synchronized (outBuffer) {
      outBuffer.add(line);
      if (!writerRunning) {
	if (lineWriter == null) {
	  lineWriter = new LineWriter(this);
	}
	writerRunning = true;
	getThreadPool().invokeLater(lineWriter);
      } else {
	outBuffer.notify();
      }
    }
  }



  // -------------------------------------------------------------------
  // Data reception
  // -------------------------------------------------------------------

  private void deliverLine(String line) {
    if (line != null) {
      readChars += line.length();
    }
    if (bufferDelivery) {
      addInBuffer(line);
    } else {
      doDeliver(line);
    }
  }

  private void addInBuffer(String line) {
    synchronized (inBuffer) {
      inBuffer.add(line);
      if (!deliveryRunning) {
	if (lineDeliverer == null) {
	  lineDeliverer = new LineDeliverer(this);
	}
	deliveryRunning = true;
	getThreadPool().invokeLater(lineDeliverer);
      } else {
	inBuffer.notify();
      }
    }
  }

  private void doDeliver(String line) {
    try {
      listener.lineRead(this, line);
    } catch (Throwable e) {
      log.log(Level.SEVERE, fullName + ": could not deliver line: " + line, e);
    }
  }



  // -------------------------------------------------------------------
  // LineDeliverer
  // -------------------------------------------------------------------

  private static class LineDeliverer implements Runnable {

    private LineConnection connection;

    public LineDeliverer(LineConnection connection) {
      this.connection = connection;
    }

    public void run() {
      String line = null;
      boolean ok = false;
      JobStatus jobStatus = ThreadPool.getJobStatus();
      ArrayQueue inBuffer = connection.inBuffer;
      try {
	while (true) {
	  synchronized (inBuffer) {
	    if (inBuffer.isEmpty()) {
	      // Wait a short time for more data because data is often
	      // written in short intervals
	      try {
		connection.outBuffer.wait(800);
	      } catch (Exception e) {
	      }
	    }

	    if (!inBuffer.isEmpty()) {
	      line = (String) inBuffer.remove(0);
	    } else {
	      connection.deliveryRunning = false;
	      ok = true;
	      return;
	    }
	  }
	  if (jobStatus != null) {
	    jobStatus.stillAlive();
	  }
	  connection.doDeliver(line);
	}

      } catch (Throwable e) {
	log.log(Level.SEVERE, connection.fullName
		+ ": could not deliver " + line, e);
      } finally {
	if (!ok) {
	  synchronized(inBuffer) {
	    if (!inBuffer.isEmpty()) {
	      log.warning("reinvoking deliverer for " + connection.fullName);
	      connection.getThreadPool().invokeLater(this);
	    } else {
	      log.warning("deliverer for " + connection.fullName + " exiting");
	      connection.deliveryRunning = false;
	    }
	  }
	}
      }
    }

    public String toString() {
      return "LineDeliverer[" + connection.fullName + ','
	+ connection.inBuffer.size()
	+ ',' + connection.remoteHost + ']';
    }

  }



  // -------------------------------------------------------------------
  // LineWriter
  // -------------------------------------------------------------------

  private static class LineWriter implements Runnable {

    private LineConnection connection;

    LineWriter(LineConnection connection) {
      this.connection = connection;
    }

    public void run() {
      String line = null;
      boolean ok = false;
      JobStatus jobStatus = ThreadPool.getJobStatus();
      try {
	// Only write if not closed!!!
	while (!connection.isClosed) {
	  synchronized (connection.outBuffer) {
	    if (connection.outBuffer.isEmpty()) {
	      // Wait a short time for more data because data is often
	      // written in short intervals
	      try {
		connection.outBuffer.wait(800);
	      } catch (Exception e) {
	      }
	    }

	    if (!connection.outBuffer.isEmpty()) {
	      line = (String) connection.outBuffer.remove(0);
	    } else {
	      connection.writerRunning = false;
	      ok = true;
	      return;
	    }
	  }
	  if (line == null) {
	    // Time to close the connection
	    connection.closeImmediately(false);
	    break;
	  }
	  if (jobStatus != null) {
	    jobStatus.stillAlive();
	  }
	  connection.writer.write(line);
 	  connection.writer.write("\r\n");
	  connection.writer.flush();
	  connection.sentChars += line.length() + 2;
	}

	// The connection will never write again so we never clear
	// the writer running flag
// 	 connection.writerRunning = false;
	ok = true;

      } catch (Throwable e) {
	log.log(Level.SEVERE, connection.fullName + ": could not send "
		+ line, e);
	// Since it was not possible to send the complete data the
	// connection is in an unknown state and the best thing to do
	// is to close it.
	connection.closeImmediately(false);

	if (e instanceof ThreadDeath) {
	  throw (ThreadDeath) e;
	}
      } finally {
	if (!ok) {
	  synchronized(connection.outBuffer) {
	    if (!connection.outBuffer.isEmpty() && !connection.isClosed) {
	      log.warning("reinvoking writer for " + connection.fullName);
	      connection.getThreadPool().invokeLater(this);
	    } else {
	      log.warning("writer for " + connection.fullName + " exiting");
	      connection.writerRunning = false;
	    }
	  }
	}
      }
    }

    public String toString() {
      return "LineWriter[" + connection.fullName + ','
	+ connection.outBuffer.size()
	+ ',' + connection.remoteHost + ']';
    }

  }


  // -------------------------------------------------------------------
  // Line Reader
  // -------------------------------------------------------------------

  private static class LineReader extends Thread {

    private LineConnection connection;

    LineReader(String name, LineConnection connection) {
      super(name);
      this.connection = connection;
    }

    public void run() {
      String line;
      try {
	while (connection.isOpen
	       && (line = connection.reader.readLine()) != null) {
	  connection.deliverLine(line);
	}
      } catch (Throwable e) {
	if (connection.isOpen) {
	  log.log(Level.SEVERE, connection.fullName
		  + ": line reader error", e);
	}
      } finally {
	if (connection.isOpen) {
	  log.warning(connection.fullName + ": "
		      + connection.remoteHost + " closed connection");
	  connection.closeImmediately(false);
	}
      }
    }
  }


  // -------------------------------------------------------------------
  // LineCloser
  // -------------------------------------------------------------------

  private static class LineCloser implements Runnable {

    private LineConnection connection;

    LineCloser(LineConnection connection) {
      this.connection = connection;
    }

    public void run() {
      connection.doClose();
    }

    public String toString() {
      return "LineCloser[" + connection.fullName + ','
	+ connection.outBuffer.size()
	+ ',' + connection.remoteHost + ']';
    }

  }

} // LineConnection
