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
 * AgentManager
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-26
 * Updated : $Date: 2004/09/07 14:46:02 $
 *	     $Revision: 1.3 $
 */

package se.sics.tac.server;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.botbox.util.ArrayQueue;
import com.botbox.util.ArrayUtils;

final class AgentManager extends Thread {

  private final static Logger log =
    Logger.getLogger(AgentManager.class.getName());

  private final static int START = 0;
  private final static int STOP = 1;
  private final static int QUOTE = 2;
  private final static int AUCTION_CLOSED = 3;
  private final static int TRANSACTION = 4;

  private final static Integer T_START = new Integer(START);
  private final static Integer T_STOP = new Integer(STOP);
  private final static Integer T_QUOTE = new Integer(QUOTE);
  private final static Integer T_AUCTION_CLOSED = new Integer(AUCTION_CLOSED);
  private final static Integer T_TRANSACTION = new Integer(TRANSACTION);

  // All messages in the form: Type, Argument
  private ArrayQueue queue = new ArrayQueue();
  private BuiltinAgent[] agents;
  private int agentNumber = 0;
  private boolean isRunning = true;

  AgentManager(Market market) {
    super("AgentManager@" + market.getGame().getGameID());
    start();
  }

  synchronized void addBuiltinAgent(BuiltinAgent agent) {
    if (agents == null) {
      agents = new BuiltinAgent[5];
    } else if (agents.length == agentNumber) {
      agents = (BuiltinAgent[]) ArrayUtils.setSize(agents, agentNumber + 5);
    }
    agents[agentNumber++] = agent;
  }


  /*********************************************************************
   * Interface towards the market
   *********************************************************************/

  public synchronized void gameStarted() {
    if (isRunning) {
      queue.add(T_START);
      queue.add(null);
      notify();
    }
  }

  // This method also stops this caller after stopping the agents
  public synchronized void gameStopped() {
    if (isRunning) {
      isRunning = false;
      queue.add(T_STOP);
      queue.add(null);
      notify();
    }
  }

  public synchronized void quoteUpdated(Quote quote) {
    if (isRunning) {
      queue.add(T_QUOTE);
      queue.add(quote);
      notify();
    }
  }

  public synchronized void auctionClosed(Auction auction) {
    if (isRunning) {
      queue.add(T_AUCTION_CLOSED);
      queue.add(auction);
      notify();
    }
  }

  public synchronized void transaction(Transaction transaction) {
    if (isRunning) {
      boolean add = true;
      // Only transactions relevant for at least one of the agents
      // are added
      for (int i = 0; i < agentNumber; i++) {
	if (transaction.isParticipant(agents[i].getUser())) {
	  // Own should be updated as soon as possible
	  agents[i].updateOwn(transaction);
	  // Add message to agent
	  if (add) {
	    queue.add(T_TRANSACTION);
	    queue.add(transaction);
	    notify();
	    add = false;
	  }
	}
      }
    }
  }


  /*********************************************************************
   * Worker thread
   *********************************************************************/

  private String getMessageName(int message) {
    switch (message) {
    case START:
      return "gameStarted";
    case STOP:
      return "gameStopped";
    case QUOTE:
      return "quoteUpdated";
    case AUCTION_CLOSED:
      return "auctionClosed";
    case TRANSACTION:
      return "transaction";
    default:
      return ""+ message;
    }
  }

  public void run() {
    do {
      int message;
      Object parameter = null;
      synchronized (this) {
	while (queue.size() == 0) {
	  try {
	    wait();
	  } catch (InterruptedException e) {
	    log.log(Level.WARNING, "*** interrupted", e);
	  }
	}
	message= ((Integer) queue.remove(0)).intValue();
	parameter = queue.remove(0);
      }

      for (int i = 0; i < agentNumber; i++) {
	BuiltinAgent agent = agents[i];
	try {
	  switch (message) {
	  case START: agent.start();
	    break;
	  case STOP: agent.stop();
	    break;
	  case QUOTE: agent.quoteUpdated((Quote) parameter);
	    break;
	  case AUCTION_CLOSED: agent.auctionClosed((Auction) parameter);
	    break;
	  case TRANSACTION: {
	    Transaction t = (Transaction) parameter;
	    if (t.isParticipant(agent.getUser())) {
	      agent.transaction(t);
	    }
	    break;
	  }
	  default:
	    log.severe("unknown type " + message);
	    // Break the loop
	    i = agentNumber;
	    break;
	  }
	} catch (ThreadDeath e) {
	  log.log(Level.SEVERE, "agent manager " + getName() + " killed", e);
	  isRunning = false;
	  throw e;
	} catch (Throwable e) {
	  log.log(Level.SEVERE, "agent " + agent.getUser().getName()
		  + " could not handle " + getMessageName(message), e);
	}
      }
    } while (isRunning || queue.size() > 0);

    log.finest("AgentManager " + getName() + " is finished");
  }

  // DEBUG FINALIZE REMOVE THIS!!! REMOVE THIS!!!
  protected void finalize() throws Throwable {
    log.finest("AGENTMANAGER " + getName() + " IS BEING GARBAGED");
    super.finalize();
  }

} // AgentManager
