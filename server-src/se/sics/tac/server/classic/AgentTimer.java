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
 * DummyAgent
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-25
 * Updated : $Date: 2004/05/04 15:48:18 $
 *	     $Revision: 1.1 $
 */

package se.sics.tac.server.classic;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;
import java.util.logging.Level;

public class AgentTimer extends TimerTask {

  private final static Logger log =
    Logger.getLogger(AgentTimer.class.getName());

  private final static Timer timer = new Timer();

  private final DummyAgent agent;
  private final Object attachment;

  public AgentTimer(DummyAgent agent, int delay) {
    this(agent, delay, null);
  }

  public AgentTimer(DummyAgent agent, int delay, Object attachment) {
    if (agent == null) {
      throw new NullPointerException();
    }
    this.agent = agent;
    this.attachment = attachment;
    timer.schedule(this, delay);
  }

  public Object attachment() {
    return attachment;
  }

  public void run() {
    // By some strange design reason the Java Timer does not catch exceptions
    // which means we must do this here!!!
    try {
      agent.wakeup(this);
    } catch (ThreadDeath e) {
      log.log(Level.SEVERE, "agent timer was killed", e);
      throw e;
    } catch (Throwable e) {
      log.log(Level.SEVERE, "agent " + agent.getUser().getName()
	      + " could not handle wakeup call", e);
    }
  }

} // AgentTimer
