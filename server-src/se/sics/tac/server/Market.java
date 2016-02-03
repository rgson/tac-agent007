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
 * Market
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-05
 * Updated : $Date: 2004/09/07 14:46:02 $
 *	     $Revision: 1.7 $
 */

package se.sics.tac.server;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.botbox.util.ArrayUtils;
import se.sics.tac.log.ISTokenizer;

public abstract class Market {

  private final static Logger log =
    Logger.getLogger(Market.class.getName());


  /*********************************************************************
   * Handling of generating unique and persistent ids of various types *
   * (Perhaps should be moved to InfoManager, FIX THIS!!!)
   *********************************************************************/

  private static int lastGameUnique;
  private static int lastGameID;
  private static int lastAuctionID;
  private static int lastTransactionID;
  private static int lastBidID;

  static int getLastGameUnique() {
    return lastGameUnique;
  }

  synchronized static int getNextGameUnique() {
    return ++lastGameUnique;
  }

  synchronized static int reserveGameUnique(int number) {
    int id = lastGameUnique + 1;
    lastGameUnique += number;
    return id;
  }

  static int getLastGameID() {
    return lastGameID;
  }

  synchronized static int getNextGameID() {
    return ++lastGameID;
  }

  static int getLastAuctionID() {
    return lastAuctionID;
  }

  private synchronized static int getNextAuctionID() {
    return ++lastAuctionID;
  }

  static int getLastTransactionID() {
    return lastTransactionID;
  }

  private synchronized static int getNextTransactionID() {
    return ++lastTransactionID;
  }

  static int getLastBidID() {
    return lastBidID;
  }

  private synchronized static int getNextBidID() {
    return ++lastBidID;
  }

  synchronized static void initState(int lastGameUnique,
				     int lastGameID,
				     int lastAuctionID,
				     int lastTransactionID,
				     int lastBidID) {
    if (Market.lastGameUnique < lastGameUnique) {
      Market.lastGameUnique = lastGameUnique;
    }

    if (Market.lastGameID < lastGameID) {
      Market.lastGameID = lastGameID;
    }
    if (Market.lastAuctionID < lastAuctionID) {
      Market.lastAuctionID = lastAuctionID;
    }
    if (Market.lastTransactionID < lastTransactionID) {
      Market.lastTransactionID = lastTransactionID;
    }
    if (Market.lastBidID < lastBidID) {
      Market.lastBidID = lastBidID;
    }
  }


  /*********************************************************************
   * Common market information
   *********************************************************************/

  protected final InfoManager infoManager;
  protected final Game game;
  private boolean isRunning = false;
  private Ticker ticker;

  private int oversellPenalty = 200;

  private Auction[] auctions;
  private int auctionStartID;
  private int auctionNumber;

  private Bid[] bids;
  private int bidStartID;
  private int bidNumber;

  private Transaction[] transactions;
  private int transactionStartID;
  private int transactionNumber;

  // Cache for agent information
  private int[][] agentOwn; // [Agent][OwnInAuction]
  private double[] agentCost;
  private int[] agentPenalty;
  private boolean hasOwn = false;

  /** Manager for builtin agents */
  private AgentManager agentManager;

  /** Internal use only */
  private int solveCounter = 0;
  private PrintWriter gameLog;

  protected Market(InfoManager infoManager, Game game) {
    if (infoManager == null) {
      throw new NullPointerException();
    }
    if (game == null) {
      throw new NullPointerException("game");
    }
    if (game.getGameID() < 0) {
      throw new IllegalArgumentException("no game id");
    }
    this.infoManager = infoManager;
    this.game = game;
  }

  PrintWriter getGameLog() {
    return gameLog;
  }

  void setGameLog(PrintWriter log) {
    this.gameLog = log;
  }

  // Keeps track of how many participants have been solved
  boolean isSolved() {
    return solveCounter >= game.getNumberOfParticipants();
  }

  void solveReport() {
    solveCounter++;
  }

  protected int getOversellPenalty() {
    return oversellPenalty;
  }

  protected synchronized void setOversellPenalty(int penalty) {
    this.oversellPenalty = penalty;
    this.agentPenalty = null;
  }

  public synchronized boolean addBuiltinAgent(BuiltinAgent agent) {
    if (game.isParticipant(agent.getUser())) {
      if (agentManager == null) {
	agentManager = new AgentManager(this);
      }
      agentManager.addBuiltinAgent(agent);
      return true;
    } else {
      return false;
    }
  }


  /*********************************************************************
   * Information access
   *********************************************************************/

  public Game getGame() {
    return game;
  }

  public int indexOfParticipant(User user) {
    int index = game.indexOfParticipant(user);
    if (index < 0) {
      throw new IllegalArgumentException(user.getName()
					 + " is not a participant in game "
					 + game.getGameID());
    }
    return index;
  }

  public boolean isRunning() {
    return isRunning;
  }


  /*********************************************************************
   * Utilities
   *********************************************************************/

  protected Random getRandom() {
    return infoManager.getRandom();
  }

  protected long getServerTime() {
    return infoManager.getServerTime();
  }

  protected long getGameTime() {
    return infoManager.getServerTime() - game.getStartTime();
  }

  protected long getGameTimeLeft() {
    long time = game.getEndTime() - getServerTime();
    return time > 0L ? time : 0L;
  }


  /*********************************************************************
   * Information access (might be unused in some markets)
   *********************************************************************/

  public Auction getAuction(int auctionID) {
    auctionID -= auctionStartID;
    return (auctionID >= 0 && auctionID < auctionNumber)
      ? auctions[auctionID]
      : null;
  }

  public Auction getAuction(int type, int day) {
    return null;
  }

  // Note: may only be called from Auction
  synchronized int addAuction(Auction auction) {
    int auctionID = getNextAuctionID();
    int index;
    if (auctions == null) {
      auctions = new Auction[32];
      // First added auction
      auctionStartID = auctionID;
      index = 0;
    } else {
      index = auctionID - auctionStartID;
      if (auctions.length <= index) {
	auctions = (Auction[]) ArrayUtils.setSize(auctions, index + 32);
      }
    }

    auctions[index] = auction;
    auctionNumber = index + 1;
    hasOwn = false;
    return auctionID;
  }

  // Used by InfoServer when sending auction information to game viewer
  // and game log files.
  // Note: this array might be NULL, may NOT be changed, and it might
  // contain some elements that are NULL.
  protected Auction[] getAuctions() {
    return auctions;
  }

  public Bid getBid(int bidID) {
    // The bids only increases so this should be ok without synchronizing
    bidID -= bidStartID;
    return (bidID >= 0 && bidID < bidNumber)
      ? bids[bidID]
      : null;
  }

  /**
   * Returns the next unprocessed or active bid for the specified user
   *
   * @param lastID the previous bid id
   * @param user the owner of the bid
   * @return the next bid or <CODE>null</CODE> if no more bids exists
   */
  public Bid getNextBid(int lastID, User user) {
    lastID -= bidStartID;
    if (lastID < bidNumber) {
      if (lastID < 0) {
	lastID = -1;
      }
      // Since bidNumber is updated after the bid array
      // this can be done without synchronization.
      for (int i = lastID + 1; i < bidNumber; i++) {
	Bid bid = bids[i];
	if ((bid != null) && (bid.getUser() == user)
	    && (bid.getTimeClosed() == 0L)) {
	  return bid;
	}
      }
    }
    return null;
  }

  public synchronized Bid createBid(Auction auction, User user,
				    BidList bidList) {
    int bidID = getNextBidID();
    Bid bid = new Bid(auction, user, bidID, bidList);
    int index;
    if (bids == null) {
      bids = new Bid[256];
      bidStartID = bidID;
      index = 0;
    } else {
      index = bidID - bidStartID;
      if (bids.length <= index) {
	bids = (Bid[]) ArrayUtils.setSize(bids, index + 256);
      }
    }
    bids[index] = bid;
    bidNumber = index + 1;
    return bid;
  }

  public Transaction getTransaction(int transactionID) {
    // The transactions only increases so this should be ok without
    // synchronizing
    transactionID -= transactionStartID;
    return (transactionID >= 0 && transactionID < transactionNumber)
      ? transactions[transactionID]
      : null;
  }

  // Note: this method will not return endowments
  public Transaction getNextTransaction(int lastID, User user) {
    lastID -= transactionStartID;
    if (lastID < transactionNumber) {
      if (lastID < 0) {
	lastID = -1;
      }
      // Since transactionNumber is updated after the transaction array
      // this can be done without synchronization.
      for (int i = lastID + 1; i < transactionNumber; i++) {
	Transaction t = transactions[i];
	if ((t != null) && t.isParticipant(user) && !t.isEndowment()) {
	  return t;
	}
      }
    }
    return null;
  }

//   // Note: this method will only return endowments
//   public Transaction getNextEndowment(int lastID, User user) {
//     lastID -= transactionStartID;
//     if (lastID < transactionNumber) {
//       if (lastID < 0) {
// 	lastID = -1;
//       }
//       // Since transactionNumber is updated after the transaction array
//       // this can be done without synchronization.
//       for (int i = lastID + 1; i < transactionNumber; i++) {
// 	Transaction t = transactions[i];
// 	if ((t != null) && t.isParticipant(user) && t.isEndowment()) {
// 	  return t;
// 	}
//       }
//     }
//     return null;
//   }

  // Note: this array might be NULL, may NOT be changed, and it might
  // contain some elements that are NULL
  protected Transaction[] getTransactions() {
    return transactions;
  }

  public synchronized Transaction createTransaction(Auction auction,
						    Bid buyBid,
						    Bid sellBid,
						    int quantity,
						    double price,
						    long clearTime) {
    Transaction transaction =
      new Transaction(auction, getNextTransactionID(),
		      buyBid, sellBid, quantity, price, clearTime);
    addTransaction(transaction);

    // Notify the InfoManager about the transaction
    infoManager.transaction(transaction);
    if (agentManager != null) {
      agentManager.transaction(transaction);
    }
    return transaction;
  }

  // Endowments are items given to the agents at startup and it will
  // not be used during games.
  protected synchronized Transaction createEndowment(Auction auction,
						     User agent,
						     int quantity,
						     double price,
						     long clearTime) {
    Transaction transaction =
      new Transaction(auction, getNextTransactionID(),
		      agent, null, quantity, price, clearTime, true);
    addTransaction(transaction);
    return transaction;
  }

  // NOTE: may only be called synchronized on this object
  private void addTransaction(Transaction transaction) {
    int index;
    int transactionID = transaction.getID();
    if (transactions == null) {
      transactions = new Transaction[256];
      transactionStartID = transactionID;
      index = 0;
    } else {
      index = transactionID - transactionStartID;
      if (transactions.length <= index) {
	transactions = (Transaction[])
	  ArrayUtils.setSize(transactions, index + 256);
      }
    }
    transactions[index] = transaction;
    transactionNumber = index + 1;
    hasOwn = false;
  }

  public Quote getQuote(int auctionID) {
    Auction auction = getAuction(auctionID);
    return auction != null ? auction.getQuote() : null;
  }


  /*********************************************************************
   * Market startup/stop handling
   *********************************************************************/

  final void setup() {
    setupGame();
  }

  protected abstract void setupGame();

  final void start() {
    long startTime = game.getStartTime();
    startGame();
    isRunning = true;

    // Open any auctions that should be open at start
    int auctionNumber = this.auctionNumber;
    Auction[] auctions = this.auctions;
    Auction a;
    long openTime;
    for (int i = 0; i < auctionNumber; i++) {
      if (((a = auctions[i]) != null)
	  && !a.isOpen()
	  && (((openTime = a.getOpenTime()) <= 0L)
	      || (startTime >= openTime))) {
	a.open(startTime);
      }
    }

    ticker = new Ticker(this);
    infoManager.getTimer()
      .schedule(ticker, new Date(startTime), 1000);

    if (agentManager != null) {
      agentManager.gameStarted();
    }
  }

  protected abstract void startGame();

  final void stop() {
    isRunning = false;
    if (ticker != null) {
      ticker.cancel();
      ticker = null;
    }

    // Close all auctions not already closed
    int auctionNumber = this.auctionNumber;
    Auction[] auctions = this.auctions;
    long endTime = game.getEndTime();
    for (int i = 0; i < auctionNumber; i++) {
      Auction a = auctions[i];
      if ((a != null) && !a.isClosed()) {
	a.close(endTime);
      }
    }

    try {
      stopGame();
    } catch (Exception e) {
      log.log(Level.SEVERE, "could not stop market for game "
	      + game.getGameID(), e);
    }

    // All active bids should now be expired
    int bidNumber = this.bidNumber;
    Bid[] bids = this.bids;
    for (int i = 0; i < bidNumber; i++) {
      Bid b = bids[i];
      if ((b != null) && b.isActive()) {
	b.setExpired(0);
      }
    }

    if (agentManager != null) {
      agentManager.gameStopped();
    }
  }

  protected abstract void stopGame();


  /*********************************************************************
   * Support for XML message generation for the message handlers
   *********************************************************************/

  public abstract String generateGameParams(User user);

  public abstract String generateGameAuctionIDs(User user);

  public abstract String generateGameConstants(User user);


  /*********************************************************************
   * Game information
   *********************************************************************/

  public abstract int[][] getGamePreferences(User user);

  // An list of number of own items per auction. Might be null if the
  // agent does not own anything. The auctions are ordered in creation order.
  // Note: should not be called often during game because they can trigger
  // some calculation
  protected int[] getAgentOwn(User user) {
    int index = indexOfParticipant(user);
    if (!hasOwn) {
      calculateOwn();
    }
    return agentOwn[index];
  }

  // Note: should not be called often during game because they can trigger
  // some calculation
  protected double getAgentCost(User user) {
    int index = indexOfParticipant(user);
    if (!hasOwn) {
      calculateOwn();
    }
    return agentCost[index];
  }

  // Note: should not be called often during game because they can trigger
  // some calculation
  protected int getAgentPenalty(User user) {
    int index = indexOfParticipant(user);
    if (!hasOwn) {
      calculateOwn();
    }
    return agentPenalty[index];
  }

  protected abstract String[] getGamePreferencesInfo();

  protected abstract String getGamePreferencesInfo(User user);

  /**
   * Returns the goods the specified participant owns.
   *
   * Default is to return an OWN message with list of what the agents owns
   * in each auction which is extracted from the registered transactions.
   * The auctions are ordered in creation order.
   *
   * @param user the participant
   * @return an IS message describing what the participant owns
   */
  protected String getAgentOwnInfo(User user) {
    int[] own = getAgentOwn(user);
    StringBuffer sb = new StringBuffer()
      .append("o,").append(game.getGameID())
      .append(',').append(user.getID());
    if (own != null) {
      for (int i = 0, n = own.length; i < n; i++) {
	sb.append(',').append(own[i]);
      }
    }
    return sb.toString();
  }

  protected synchronized void calculateOwn() {
    int number = game.getNumberOfParticipants();
    int[][] own = new int[number][];
    int[] penalty = new int[number];
    double[] cost = new double[number];
    for (int i = 0; i < transactionNumber; i++) {
      Transaction t = transactions[i];
      if (t != null) {
	Auction a = t.getAuction();
	int aindex = a.getID() - auctionStartID;
	if (aindex >= 0 && aindex < auctionNumber) {
	  User buyer = t.getBuyer();
	  User seller = t.getSeller();
	  int q = t.getQuantity();
	  double p = q * t.getPrice();
	  if (buyer != null) {
	    int index = indexOfParticipant(buyer);
	    if (own[index] == null) {
	      own[index] = new int[auctionNumber];
	    }
	    own[index][aindex] += q;
	    cost[index] += p;
	  }
	  if (seller != null) {
	    int index = indexOfParticipant(seller);
	    if (own[index] == null) {
	      own[index] = new int[auctionNumber];
	    }
	    own[index][aindex] -= q;
	    cost[index] -= p;
	  }
	}
      }
    }

    for (int i = 0; i < number; i++) {
      int[] o = own[i];
      if (o != null) {
	for (int j = 0; j < auctionNumber; j++) {
	  if (o[j] < 0) {
	    penalty[i] -= o[j] * oversellPenalty;
	  }
	}
      }
    }
    this.agentPenalty = penalty;
    this.agentCost = cost;
    this.agentOwn = own;
    this.hasOwn = true;
  }

  // Returns an IS solve request for this game
  protected String[] getSolveRequestInfo() {
    ArrayList list = new ArrayList();
    String reqPrefix = "sr," + game.getGameID() + ',';
    for (int i = 0, n = game.getNumberOfParticipants(); i < n; i++) {
      User user = game.getParticipant(i);
      String prefs = getGamePreferencesInfo(user);
      String own = getAgentOwnInfo(user);
      list.add(reqPrefix + user.getID());
      if (prefs != null) {
	list.add(prefs);
      }
      if (own != null) {
	list.add(own);
      }
    }
    return (String[]) list.toArray(new String[list.size()]);
  }

  // Convert an allocation report to a IS allocation message
  protected String getAllocationInfo(User user, ISTokenizer tokenizer) {
    StringBuffer sb = new StringBuffer()
      .append("l,").append(game.getGameID()).append(',').append(user.getID());
    while (tokenizer.hasMoreTokens()) {
      sb.append(',').append(tokenizer.nextInt());
    }
    return sb.toString();
  }


  /*********************************************************************
   * Interface towards the market ticker
   **********************************************************************/

  // Perform tick.  InfoManager will notify the market when it is time
  // to stop so we do not need to do it here.
  long tickPerformed(long currentTime) {
    long nextTick = Long.MAX_VALUE;
    if (isRunning) {
      for (int i = 0; i < auctionNumber; i++) {
	Auction auction = auctions[i];
	if (auction != null) {
	  long time = auction.tickPerformed(currentTime);
	  if (time < nextTick) {
	    nextTick = time;
	  }
	}
      }
    }
    return nextTick == Long.MAX_VALUE ? currentTime + 30000 : nextTick;
  }


  /*********************************************************************
   * Interface towards the auctions and bids
   *********************************************************************/

  final void bidUpdated(Bid bid, char type) {
    infoManager.bidUpdated(bid, type);
  }

  final void quoteUpdated(Quote quote) {
    infoManager.quoteUpdated(quote);
    if (agentManager != null) {
      agentManager.quoteUpdated(quote);
    }
  }

  final void auctionClosed(Auction auction) {
    infoManager.auctionClosed(auction);
    if (agentManager != null) {
      agentManager.auctionClosed(auction);
    }
  }

  // DEBUG FINALIZE REMOVE THIS!!! REMOVE THIS!!!
  protected void finalize() throws Throwable {
    log.finest("MARKET " + game.getGameID() + " IS BEING GARBAGED");
    super.finalize();
  }

} // Market
