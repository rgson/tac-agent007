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
import java.io.IOException;

public abstract class TACConnection {

  protected TACAgent agent;

  final void init(TACAgent agent) {
    this.agent = agent;
    init();
  }

  protected abstract void init();

  public abstract boolean isConnected();
  public abstract void disconnect();
  public abstract void sendMessage(TACMessage msg) throws IOException;
  public void sendMessage(TACMessage msg, TACMessageReceiver rcv)
    throws IOException {
    msg.setMessageReceiver(rcv);
    sendMessage(msg);
  }
}
