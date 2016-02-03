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
 * TACGameInfo
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 8 April, 2002
 * Updated : $Date: 2004/07/07 15:28:33 $
 *	     $Revision: 1.3 $
 */

package se.sics.tac.log;
import com.botbox.util.ArrayUtils;

public class TACGameInfo {

  public final static String IGNORE_QUOTES = "quotesIgnore";
  public final static String IGNORE_BIDS = "bidsIgnore";

  public final static int INFLIGHT = 0;
  public final static int OUTFLIGHT = 1;
  public final static int HOTEL = 2;
  public final static int ALLIGATOR_WRESTLING = 3;
  public final static int AMUSEMENT_PARK = 4;
  public final static int MUSEUM = 5;

  public final static int ITEM_INFLIGHT = 0;
  public final static int ITEM_OUTFLIGHT = 1;
  public final static int ITEM_GOOD_HOTEL = 2;
  public final static int ITEM_CHEAP_HOTEL = 3;
  public final static int ITEM_ALLIGATOR_WRESTLING = 4;
  public final static int ITEM_AMUSEMENT_PARK = 5;
  public final static int ITEM_MUSEUM = 6;

  private final static String[] ITEM_NAMES = {
    "InFlight", "OutFlight",
    "TampaTowersHotel", "ShorelineShantyHotel",
    "AlligatorWrestling", "AmusementPark", "Museum"
  };
  private final static int AUCTION_NUMBER = 7 * 5;

  /** Information about current game */
  private String serverVersion;
  private String serverName;
  private int uniqID;
  private int gameID;
  private String gameType = "tacClassic";
  private long startTime;
  private long endTime;

  /** Auction information */
  // 5 * 7 resources -> 5 * 7 auction ids
  private int[] auctions = new int[AUCTION_NUMBER];
  /** Time the auction closed (is 0L if the auction is still opened)  */
  private long[] auctionClosed = new long[AUCTION_NUMBER];
  /** Quotes for the auctions */
  private TACQuote[][] auctionQuotes = new TACQuote[AUCTION_NUMBER][];

  private String[] agentNames = new String[] {
    "A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8"
  };
  private int[] agentIDs = new int[8];
  private float[] cost = new float[8];
  private float[] score = new float[8];
  private long[] calculationTime = new long[8];
  private int[] penalty = new int[8];
  private int[] utility = new int[8];
  private int[] agentPositions = null;
  private boolean[] hasScore = new boolean[8];
  private int numberOfAgents = 0;

  /** Goods: 8 agents, 5 days, with (In,Out,H1,H2,E1,E2,E3) */
  private int[][][] goods = new int[8][5][7];
  private float[][][] goodsCost = new float[8][5][7];
  private int[][][] usedGoods = new int[8][5][7];

  private boolean isFinished = false;

  // Infl, outfl, hval, e1val, e2val, e3val
  private int[][][] clientPrefs = new int[8][8][6];
  private int[] clientNo = new int[8];

  /** Endowments: 8 agents, 4 days, with (E1,E2,E3) */
  private int[][][] endowments = new int[8][4][3];

  /** Allocation */
  private int[][][] allocation = new int[8][8][6];

  /** Transactions */
  private Transaction[] transactions = new Transaction[384];
  private int numberOfTransactions = 0;

  private boolean ignoreQuotes = false;
  private boolean ignoreBids = false;

  public TACGameInfo() {
  }

  public void gameData(ISTokenizer tokenizer) {
    switch (tokenizer.getCommand()) {
    case ISTokenizer.VERSION: {
      if (tokenizer.hasMoreTokens()) {
	serverVersion = tokenizer.nextToken();
	if (tokenizer.hasMoreTokens()) {
	  serverName = tokenizer.nextToken();
	}
      }
      break;
    }
    case ISTokenizer.GAME_STARTED:
      gameID = tokenizer.nextInt();
      startTime = tokenizer.nextTimeMillis();
      endTime = tokenizer.nextTimeMillis();
      if (tokenizer.hasMoreTokens()) {
	uniqID = tokenizer.nextInt();
	if (tokenizer.hasMoreTokens()) {
	  gameType = tokenizer.nextToken();
	  // Ignore the rest for now. FIX THIS!!
	}
      } else {
	uniqID = gameID;
      }
      break;
    case ISTokenizer.GAME_ENDED: {
      // Make sure all auctions have been closed
      long time = tokenizer.getServerTimeSeconds() * 1000;
      for (int i = 0; i < AUCTION_NUMBER; i++) {
	if (auctionClosed[i] == 0) {
	  closeAuction(i, time);
	}
      }
      break;
    }
    case ISTokenizer.AUCTION:
      while (tokenizer.hasMoreTokens()) {
	int auctionID = tokenizer.nextInt();
	int resource = tokenizer.nextInt();
	int day = tokenizer.nextInt() - 1;
	// Normalize the day for out flights
	if (resource == 1) {
	  day--;
	}
	auctions[day + resource * 4] = auctionID;
      }
      break;
    case ISTokenizer.AUCTION_CLOSED:
      {
	int auctionID = tokenizer.nextInt();
	int index = getAuctionIndex(auctionID);
	if (index >= 0) {
	  closeAuction(index, tokenizer.getServerTimeSeconds() * 1000);
	}
      }
      break;
    case ISTokenizer.AGENT:
      {
	String agentName = tokenizer.nextToken();
	int agentID = tokenizer.nextAgentID();
	if (numberOfAgents < 8) {
	  agentNames[numberOfAgents] = agentName;
	  agentIDs[numberOfAgents++] = agentID;
	}
      }
      break;
    case ISTokenizer.CLIENT:
      if (gameID == tokenizer.nextInt()) {
	int agentID = tokenizer.nextAgentID();
	int index = getAgentIndex(agentID);
	if (index >= 0) {
	  while (tokenizer.hasMoreTokens()) {
	    if (clientNo[index] < 8) {
	      int[] tmp = clientPrefs[index][clientNo[index]];
	      for (int i = 0; i < 6; i++) {
		tmp[i] = tokenizer.nextInt();
	      }
	      clientNo[index]++;
	    } else {
	      break;
	    }
	  }
	}
      }
      break;

    case ISTokenizer.QUOTE:
      if (!ignoreQuotes) {
	int auctionID = tokenizer.nextInt();
	int auctionIndex = getAuctionIndex(auctionID);
	if (auctionIndex >= 0) {
	  TACQuote[] quotes = auctionQuotes[auctionIndex];
	  float ask = tokenizer.nextFloat();
	  float bid = tokenizer.nextFloat();
	  long lastUpdated = tokenizer.getServerTimeSeconds() * 1000;
	  int quoteEnd;
	  int[] hqw = null;
	  if (tokenizer.hasMoreTokens()) {
	    // Quote contains HQW
	    int hqwCount = 0;
	    hqw = new int[32];
	    do {
	      int agentID = tokenizer.nextInt();
	      int agentHQW = tokenizer.nextInt();
	      int agentIndex = getAgentIndex(agentID);
	      if (agentIndex >= 0) {
		if ((hqwCount + 2) >= hqw.length) {
		  hqw = ArrayUtils.setSize(hqw, hqw.length + 6);
		}
		hqw[hqwCount++] = agentIndex;
		hqw[hqwCount++] = agentHQW;
	      }
	    } while (tokenizer.hasMoreTokens());
	    // Trim the hqw to right size
	    if (hqwCount < hqw.length) {
	      hqw = ArrayUtils.setSize(hqw, hqwCount);
	    }
	  }
	  if (quotes == null) {
	    quotes = new TACQuote[20];
	    quoteEnd = 0;
	  } else if ((quoteEnd = getLastQuoteIndex(quotes)) == quotes.length) {
	    quotes = (TACQuote[])
	      ArrayUtils.setSize(quotes, quotes.length + 20);
	  }
	  quotes[quoteEnd] = new TACQuote(auctionIndex, ask, bid, hqw,
					  lastUpdated);
	  auctionQuotes[auctionIndex] = quotes;
	}
      }
      break;
    case ISTokenizer.TRANSACTION:
      {
	int buyer = getAgentIndex(tokenizer.nextAgentID());
	int seller = getAgentIndex(tokenizer.nextAgentID());
	int auction = getAuctionIndex(tokenizer.nextInt());
	int quantity = tokenizer.nextInt();
	float price = tokenizer.nextFloat();
	int transID = tokenizer.hasMoreTokens()
	  ? tokenizer.nextInt()
	  : -1;
	long time = tokenizer.getServerTimeSeconds() * 1000;
	updateOwn(buyer, auction, quantity, price);
	if (seller == Integer.MIN_VALUE) {
	  // Auction is seller
	  int auctionType = getAuctionType(auction);
	  if (auctionType >= ITEM_ALLIGATOR_WRESTLING) {
	    // Auction sold entertainment => endowment
	    endowments[buyer][getAuctionDay(auction) - 1]
	      [auctionType - ITEM_ALLIGATOR_WRESTLING] += quantity;
	    quantity = 0;
	  }
	} else {
	  updateOwn(seller, auction, -quantity, price);
	}
	if (quantity != 0) {
	  if (numberOfTransactions == transactions.length) {
	    transactions = (Transaction[])
	      ArrayUtils.setSize(transactions, numberOfTransactions + 10);
	  }
	  transactions[numberOfTransactions++] =
	    new Transaction(buyer, seller, auction, quantity, price, time,
			    transID);
	}
      }
      break;
    case ISTokenizer.SCORE:
      if (gameID == tokenizer.nextInt()) {
	int agentID = tokenizer.nextAgentID();
	int index = getAgentIndex(agentID);
	if (index >= 0) {
	  score[index] = tokenizer.nextFloat();
	  // Penalty
	  penalty[index] = tokenizer.nextInt();
	  // Utility
	  utility[index] = tokenizer.nextInt();
	  cost[index] = utility[index] - penalty[index] - score[index];
	  hasScore[index] = true;

	  if (tokenizer.hasMoreTokens()) {
	    calculationTime[index] = tokenizer.nextLong();
	  }

	  // Check if scores has been received for all agents
	  if (numberOfAgents == 8) {
	    isFinished = true;
	    for (int i = 7; i >= 0; i--) {
	      if (!hasScore[index]) {
		isFinished = false;
		break;
	      }
	    }
	  }
	}
      }
      break;
//     case ISTokenizer.OWN: // only for solver
//       break;
    case ISTokenizer.ALLOCATION:
      if (gameID == tokenizer.nextInt()) {
	int agentID = tokenizer.nextAgentID();
	int index = getAgentIndex(agentID);
	if (index >= 0) {
	  int[] tmp;
	  for (int i = 0; i < 8; i++) {
	    tmp = allocation[index][i];
	    int inflight = tmp[INFLIGHT] = tokenizer.nextInt();
	    int outflight = tmp[OUTFLIGHT] = tokenizer.nextInt();
	    int hotel = tmp[HOTEL] = tokenizer.nextInt();
	    int alligatorWrestling = tmp[ALLIGATOR_WRESTLING] =
	      tokenizer.nextInt();
	    int amusementPark = tmp[AMUSEMENT_PARK] = tokenizer.nextInt();
	    int museum = tmp[MUSEUM] = tokenizer.nextInt();

	    // Client only goes if inflight day > 0
	    if (inflight > 0) {
	      int hotelType = hotel > 0 ? ITEM_GOOD_HOTEL : ITEM_CHEAP_HOTEL;
	      usedGoods[index][inflight - 1][ITEM_INFLIGHT]++;
	      usedGoods[index][outflight - 1][ITEM_OUTFLIGHT]++;

	      // Client uses one hotel for each day he stays
	      for (int j = inflight; j < outflight; j++) {
		usedGoods[index][j - 1][hotelType]++;
	      }
	      if (alligatorWrestling > 0) {
		usedGoods[index][alligatorWrestling - 1]
		  [ITEM_ALLIGATOR_WRESTLING]++;
	      }
	      if (amusementPark > 0) {
		usedGoods[index][amusementPark - 1][ITEM_AMUSEMENT_PARK]++;
	      }
	      if (museum > 0) {
		usedGoods[index][museum - 1][ITEM_MUSEUM]++;
	      }
	    }
	  }
	}
      }
      break;
    }
  }

  private void updateOwn(int agentIndex, int auctionIndex,
			 int quantity, float unitPrice) {
    int day = getAuctionDay(auctionIndex) - 1;
    int type = getAuctionType(auctionIndex);
//     System.out.println("updateown: ag=" + agentIndex
// 		       + "  " + auctionIndex + " - " + day + " - " + type);
    goods[agentIndex][day][type] += quantity;
    goodsCost[agentIndex][day][type] += quantity * unitPrice;
  }

  private int getAuctionIndex(int ID) {
    for (int i = 0; i < AUCTION_NUMBER; i++) {
      if (auctions[i] == ID) {
	return i;
      }
    }
    return -1;
  }

  private void closeAuction(int index, long time) {
    auctionClosed[index] = time;

    TACQuote[] quotes = auctionQuotes[index];
    if (quotes != null) {
      int lastIndex = getLastQuoteIndex(quotes);
      if (lastIndex > 0) {
	quotes[lastIndex - 1].setAuctionClosed();
      }
    }
  }

  private int getLastQuoteIndex(TACQuote[] quotes) {
    for (int i = quotes.length - 1; i >= 0; i--) {
      if (quotes[i] != null) {
	return i + 1;
      }
    }
    return 0;
  }


  /*********************************************************************
   * Properties
   *********************************************************************/

  public String getProperty(String name) {
    if (IGNORE_QUOTES.equals(name)) {
      return ignoreQuotes ? "true" : "false";
    } else if (IGNORE_BIDS.equals(name)) {
      return ignoreBids ? "true" : "false";
    }
    return null;
  }

  public void setProperty(String name, String value) {
    if (IGNORE_QUOTES.equals(name)) {
      ignoreQuotes = "true".equals(value);
    } else if (IGNORE_BIDS.equals(name)) {
      ignoreBids = "true".equals(value);
    }
  }


  /*********************************************************************
   * Information access API
   *********************************************************************/

  public String getServerName() {
    return serverName;
  }

  public String getServerVersion() {
    return serverVersion;
  }

  public int getID() {
    return uniqID;
  }

  public int getGameID() {
    return gameID;
  }

  public String getGameType() {
    return gameType;
  }

  public long getStartTime() {
    return startTime;
  }

  public long getEndTime() {
    return endTime;
  }

  public int getGameLength() {
    return (int) (endTime - startTime);
  }

  // Return true if all information has been received
  public boolean isFinished() {
    return isFinished;
  }

  public boolean isScratched() {
    return startTime == endTime;
  }

  /** Agent information */

  public int getNumberOfAgents() {
    return numberOfAgents;
  }

  public int getAgentID(int index) {
    return index == Integer.MIN_VALUE ? Integer.MIN_VALUE : agentIDs[index];
  }

  public int getAgentIndex(int ID) {
    if (ID == Integer.MIN_VALUE) {
      return ID;
    }

    for (int i = 0; i < numberOfAgents; i++) {
      if (agentIDs[i] == ID) {
	return i;
      }
    }
    return -1;
  }

  public int getAgentIndex(String name) {
    if (name.equals("auction")) {
      return Integer.MIN_VALUE;
    }

    for (int i = 0; i < numberOfAgents; i++) {
      if (name.equals(agentNames[i])) {
	return i;
      }
    }
    return -1;
  }

  public boolean isBuiltinAgent(int index) {
    return index >= 0 && agentIDs[index] < 0;
  }

  public String getAgentName(int index) {
    if (index == Integer.MIN_VALUE) {
      return "auction";
    }
    return agentNames[index];
//     int index;
//     if (agentID == Integer.MIN_VALUE) {
//       return "auction";
//     } else if ((index = getAgentIndex(agentID)) < 0) {
//       // Agent not found
//       return "unknown";
//     } else {
//       return agentNames[index];
//     }
  }

  public int getClientPreferences(int agentIndex, int clientIndex, int type) {
    return clientPrefs[agentIndex][clientIndex][type];
  }

  // Returns the agents position starting from 0. Highest scored agent
  // has lowest position.
  public int getAgentPosition(int index) {
    if (!isFinished) {
      return index;
    }

    int[] tmp = agentPositions;
    if (tmp == null) {
      tmp = new int[numberOfAgents];

      // Slow sorting but since there are only 8 agents...
      for (int i = 0; i < numberOfAgents; i++) {
	tmp[i] = i;
      }
      for (int i = 1; i < numberOfAgents; i++) {
	for (int j = i; j > 0 && (score[tmp[j - 1]] < score[tmp[j]]); j--) {
	  int t = tmp[j];
	  tmp[j] = tmp[j - 1];
	  tmp[j - 1] = t;
	}
      }
      agentPositions = tmp;
    }
    return tmp[index];
  }

  public float getAgentCost(int index) {
    return cost[index];
  }

  public float getAgentScore(int index) {
    return score[index];
  }

  public int getAgentUtility(int index) {
    return utility[index];
  }

  public int getAgentPenalty(int index) {
    return penalty[index];
  }

  public long getAgentUtilityCalculationTime(int index) {
    return calculationTime[index];
  }

  public boolean hasAgentScore(int index) {
    return hasScore[index];
  }

  /** Allocation information */

  /**
   * Returns the day for the specified type of goods for the
   * specified client.
   *
   * @param agentIndex the index of the agent handling the client
   * @param clientIndex the index of the client
   * @param type the type of goods
   * @return the day for which the goods is allocated to the client
   *	or 0 if the client has not been allocated to such goods.
   */
  public int getAllocatedDay(int agentIndex, int clientIndex, int type) {
    return allocation[agentIndex][clientIndex][type];
  }

  public boolean hasGoodHotel(int agentIndex, int clientIndex) {
    return allocation[agentIndex][clientIndex][HOTEL] > 0;
  }

  // Uses ITEM type
  public static String getItemName(int type) {
    return ITEM_NAMES[type];
  }

  // Uses ITEM type
  public int getEndowments(int agentIndex, int type, int day) {
    return endowments[agentIndex][day][type - 4];
  }

  // Uses ITEM type
  public int getOwn(int agentIndex, int type, int day) {
    return goods[agentIndex][day][type];
  }

  // Uses ITEM type
  public float getUnitCost(int agentIndex, int type, int day) {
//     int quantity = usedGoods[agentIndex][day][type];
    int quantity = goods[agentIndex][day][type];
    if (quantity <= 0) {
      return 0.0f;
    } else {
      return goodsCost[agentIndex][day][type] / quantity;
    }
  }

  // Get used goods (uses ITEM type)
  public int getUsed(int agentIndex, int type, int day) {
    return usedGoods[agentIndex][day][type];
  }

  /** Transaction information */
  public Transaction[] getTransactions() {
    if (numberOfTransactions != transactions.length) {
      transactions = (Transaction[])
	ArrayUtils.setSize(transactions, numberOfTransactions);
    }
    return transactions;
  }

  /** Auction information */
  public int getAuctionID(int index) {
    return auctions[index];
  }

  // Returns ITEM type
  public static int getAuctionType(int index) {
    return index / 4;
  }

  public static int getAuctionDay(int index) {
    int type = index / 4;
    int day = index % 4;
    if (type == 1) {
      day++;
    }
    return day + 1;
  }

  public long getAuctionCloseTime(int index) {
    return auctionClosed[index];
  }

  // Note: might return NULL
  public TACQuote[] getAuctionQuotes(int auctionIndex) {
    TACQuote[] quotes = auctionQuotes[auctionIndex];
    if (quotes != null) {
      // Make sure the quote array has the right size before it is returned
      int quoteEnd = getLastQuoteIndex(quotes);
      if (quoteEnd < quotes.length) {
	auctionQuotes[auctionIndex] = quotes = (TACQuote[])
	  ArrayUtils.setSize(quotes, quoteEnd);
      }
    }
    return quotes;
  }


  /**
   * Returns the first quote after the specified number of seconds in
   * game for the specified auction. If no quote was issued after
   * the specified time, the last quote before the time is returned.
   *
   * @param gameTimeSeconds the earliest update time for the quote
   * @param auctionIndex the auction for the quote
   * @return the quote or NULL if no suitable quote was found
   */
  public TACQuote getAuctionQuote(int gameTimeSeconds, int auctionIndex) {
    TACQuote[] quotes = auctionQuotes[auctionIndex];
    if (quotes != null) {
      long time = startTime + gameTimeSeconds * 1000;
      for (int i = 0, n = quotes.length; i < n; i++) {
	TACQuote q = quotes[i];
	if (q == null) {
	  // End of quotes was found
	  break;
	} else if (q.getLastUpdated() >= time) {
	  return q;
	}
      }
      // Return the last quote if no quote was found after the specified time
      for (int i = quotes.length - 1; i >= 0; i--) {
	if (quotes[i] != null) {
	  return quotes[i];
	}
      }
    }
    return null;
  }



  // -------------------------------------------------------------------
  // Utilities
  // -------------------------------------------------------------------

  public static int indexOf(TACGameInfo[] games, int start, int end, int id) {
    for (int i = start; i < end; i++) {
      if (games[i].gameID == id) {
	return i;
      }
    }
    return -1;
  }

} // TACGameInfo
