/**
 * SICS TAC Server - InfoServer
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
 * TACQuote
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 2 July, 2002
 * Updated : $Date: 2004/05/04 15:48:17 $
 *	     $Revision: 1.1 $
 */
package se.sics.tac.log;

public class TACQuote {

  private final int auctionIndex;
  private final float ask;
  private final float bid;
  private final long lastUpdated;
  private final int[] hqw;
  private boolean isClosed = false;

  public TACQuote(int auctionIndex, float ask, float bid, int[] hqw,
		  long lastUpdated) {
    this.auctionIndex = auctionIndex;
    this.ask = ask;
    this.bid = bid;
    this.hqw = hqw;
    this.lastUpdated = lastUpdated;
  }

  public int getAuction() {
    return auctionIndex;
  }

  public float getAsk() {
    return ask;
  }

  public float getBid() {
    return bid;
  }

  public int getAgentHQW(int agentIndex) {
    if (hqw != null) {
      for (int i = 0, n = hqw.length; i < n; i += 2) {
	if (hqw[i] == agentIndex) {
	  return hqw[i + 1];
	}
      }
    }
    return 0;
  }

  public long getLastUpdated() {
    return lastUpdated;
  }

  public boolean isAuctionClosed() {
    return isClosed;
  }

  void setAuctionClosed() {
    isClosed = true;
  }

} // TACQuote
