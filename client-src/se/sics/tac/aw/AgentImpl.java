/**
 * TAC AgentWare
 * http://www.sics.se/tac        tac-dev@sics.se
 *
 * Copyright (c) 2001-2006 SICS AB. All rights reserved.
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
 * Updated : $Date: 2006/05/02 13:06:47 $
 *	     $Revision: 1.2 $
 */

package se.sics.tac.aw;
import se.sics.tac.util.ArgEnumerator;

public abstract class AgentImpl {

  protected TACAgent agent;

  final void init(TACAgent agent, ArgEnumerator args) {
    this.agent = agent;

    init(args);
  }

  /**
   * This method is used to initialize the agent implementation.
   *
   * @param args an <code>ArgEnumerator</code> value containing any
   *	arguments for the agent implementation
   */
  protected abstract void init(ArgEnumerator args);

  /**
   * This function returns a human readable argument description for
   * the agent implementation or <code>null</code> if the agent
   * implementation takes no arguments.  Arguments can only be
   * specified at runtime and overrides configuration parameters.
   *
   * This description is shown to the user as usage information when
   * requested.  The default implementation returns <code>null</code>.
   *
   * @return a <code>String</code> value describing all arguments for
   *	the agent implementation or <code>null</code> if the agent
   *	implementation does not utilize any arguments.
   */
  protected String getUsage() {
    return null;
  }

  public void quoteUpdated(Quote quote) {
  }

  public void quoteUpdated(int auctionCategory) {
  }

  public abstract void bidUpdated(Bid bid);
  public abstract void bidRejected(Bid bid);
  public abstract void bidError(Bid bid, int error);

  public abstract void gameStarted();

  public abstract void gameStopped();

  public abstract void auctionClosed(int auction);

  public void transaction(Transaction transaction) {
  }

  /**
   * Called to notify the agent that a TAC Error message has been received.
   * The default behaviour is to exit the agent with an error code but
   * agent implementations might override this with their own behaviour.
   * Note however if a tac error is returned for some messages such as
   * requests for transactions or bid information the agent state will be
   * uncertain and the server connection should at least be reset.
   *
   * @param msg the message to which a tac error was returned from the server
   */
  protected void tacerrorReceived(TACMessage msg) {
    agent.fatalError("tacerror for " + msg.getType() + ": " + msg.getValue(),
		     15000);
  }

} // AgentImpl
