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
 * ClassicMarket
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 02-9-14
 * Updated : $Date: 2004/09/07 15:22:04 $
 *	     $Revision: 1.2 $
 */

package se.sics.tac.server.classic;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import se.sics.isl.util.LogFormatter;
import se.sics.tac.server.Auction;
import se.sics.tac.server.Game;
import se.sics.tac.server.InfoManager;
import se.sics.tac.server.Market;
import se.sics.tac.server.User;
import se.sics.tac.util.TACFormatter;

public class ClassicMarket extends Market {

  private final static Logger log =
    Logger.getLogger(ClassicMarket.class.getName());

  public static final int DEFAULT_GAME_LENGTH = 9 * 60000;

  /** Client preferences types */
  public final static int ARRIVAL = 0;
  public final static int DEPARTURE = 1;
  public final static int HOTEL_VALUE = 2;
  public final static int E1 = 3;
  public final static int E2 = 4;
  public final static int E3 = 5;

  private final static int[][] closeData = {
    { 1, 8}, { 2, 4}, { 1, 2, 2, 3}, { 4, 2},
    { 2, 1, 3, 2}, { 4, 1, 2, 2}, { 6, 1, 1, 2}
  };

  private final static int ENT_TYPE = 3;

  protected Auction[] auctionsByType;

  protected int[][][] clientPreferences;
  protected String[] clientPreferencesInfo;

  protected String gameConstantMessage;
  protected String gameAuctionIDsMessage;
  protected String[] gameParameterMessages;


  /*********************************************************************
   * Setup handling
   *********************************************************************/

  public ClassicMarket(InfoManager infoManager, Game game) {
    super(infoManager, game);
  }

  protected void setupGame() {
    setupAuctions();
    setupClients();
    this.gameConstantMessage =
      "<gameType>" + game.getGameType() + "</gameType>"
      + "<numClients>8</numClients>"
      + "<numAgents>8</numAgents>"
      + "<gameLength>" + (game.getGameLength() / 1000) + "</gameLength>";
  }

  protected void startGame() {
  }

  protected void stopGame() {
  }

  /*********************************************************************
   * Information retrieval
   *********************************************************************/

  public Auction getAuction(int type, int day) {
    int auction = type * 4 + day - 1;
    // Outflights are on day 2-5 while the rest is on day 1-4
    if (type == 1) {
      auction--;
    }
    return auctionsByType[auction];
  }

  public int[][] getGamePreferences(User user) {
    if (clientPreferences == null) {
      return null;
    }
    int index = indexOfParticipant(user);
    return clientPreferences[indexOfParticipant(user)];
  }

  protected String[] getGamePreferencesInfo() {
    return clientPreferencesInfo;
  }

  protected String getGamePreferencesInfo(User user) {
    int index = indexOfParticipant(user);
    return clientPreferencesInfo[index];
  }


  /*********************************************************************
   * Support for XML message generation for the message handlers
   *********************************************************************/

  public String generateGameParams(User user) {
    int index = indexOfParticipant(user);
    return gameParameterMessages[index];
  }

  public String generateGameAuctionIDs(User user) {
    // This information is common for all agents
    return gameAuctionIDsMessage;
  }

  public String generateGameConstants(User user) {
    return gameConstantMessage;
  }



  /*********************************************************************
   * Initialization
   *********************************************************************/

  protected void setupAuctions() {
    Random random = getRandom();
    long startTime = game.getStartTime();
    long endTime = game.getEndTime();
    int minutes = game.getGameLength() / 60000;
    StringBuffer auctionParams = new StringBuffer()
      .append("<startTime>").append(startTime / 1000)
      .append("</startTime><auctionIDs><list>");

    // Setup the auctions
    Auction auction;
    String category;
    char type;
    int closeNumber = 0;
    int[] closeTimes = new int[8];
    Auction[] auctionsByType = new Auction[28];

    // Generate the close times for the 8 hotel auctions
    // > 8 => 8*1
    //   8 => 6*1, 1*2
    //   7 => 4*1, 2*2
    //   6 => 2*1, 3*2
    //   5 => 0*1, 4*2
    //   4 => 1*2, 2*3
    //   3 => 2*4
    //   2 => 1*8
    //   1 => 0*8
    if (minutes > 8) {
      for (int i = 0; i < 8; i++) {
	closeTimes[i] = i + 1;
      }
      closeNumber = 8;
    } else {
      if (minutes < 2) {
	minutes = 2;
      }
      // we now know that 2 <= minutes <= 8
      int[] d = closeData[minutes - 2];
      int min = minutes - 1;
      for (int i = 0, n = d.length; i < n; i += 2) {
	int c = d[i + 1];
	for (int j = 0, m = d[i]; j < m; j++) {
	  for (int p = 0, pm = c; p < pm; p++) {
	    closeTimes[closeNumber++] = min;
	  }
	  min--;
	}
      }
    }

    for (int a = 0; a < 7; a++) {
      if (a < 2) {
	category = "flight";
	// 1 => inflight, 0 => outflight
	type = a == 0 ? '1' : '0';
      } else if (a < 4) {
	category = "hotel";
	//	type = (char) (a - 2 + '0');
	type = a == 2 ? '1' : '0';
      } else {
	category = "entertainment";
	type = (char) (a - 3 + '0');
      }

      for (int day = 1; day < 5; day++) {
	// Outflights are on day 2-5 while the rest is on day 1-4
	int auctionDay = a == 1 ? day + 1 : day;
	if (a < 2) {
	  OnesideContinuousAuction2 au =
	    new OnesideContinuousAuction2(this, a, auctionDay, endTime);
	  auction = au;

	} else if (a < 4) {
	  int close = random.nextInt(closeNumber--);
	  EngAscAuction au =
	    new EngAscAuction(this, a, auctionDay,
			      endTime - closeTimes[close] * 60000);
	  // Move the last close time to the used position to have
	  // all unused close times in the beginning
	  closeTimes[close] = closeTimes[closeNumber];
// 	  au.setQuotePeriod(60);
	  auction = au;

	} else {
	  DoubleContinuousAuction au =
	    new DoubleContinuousAuction(this, a, auctionDay, endTime);
// 	  au.setQuotePeriod(30);
	  auction = au;
	}
	auctionsByType[4 * a + day - 1] = auction;

	auctionParams.append("<TACAuctionTuple><category>")
	  .append(category)
	  .append("</category><type>").append(type)
	  .append("</type><day>")
	  .append(auctionDay)
	  .append("</day><ID>").append(auction.getID())
	  .append("</ID></TACAuctionTuple>");
      }
    }

    this.auctionsByType = auctionsByType;
    this.gameAuctionIDsMessage =
      auctionParams.append("</list></auctionIDs>").toString();
  }

  private void setupClients() {
    Random random = getRandom();
    String prefPrefix = "c," + game.getGameID() + ',';
    int participants = game.getNumberOfParticipants();
    String[] params = new String[participants];
    String[] info = new String[participants];
    int[][] endowments = generateEndowments(random);
    int endowNumber = endowments.length;
    long startTime = game.getStartTime();
    int[][][] prefs = new int[participants][8][6];
    for (int i = 0; i < participants; i++) {
      User u = game.getParticipant(i);
      StringBuffer p = new StringBuffer()
	.append("<clientPreferences><list>");
      StringBuffer c = new StringBuffer()
        .append(prefPrefix).append(u.getID());
       // 8 clients per agent in this type of game.
      for (int j = 0; j < 8; j++) {
	createClient(random, j + 1, p, c, prefs[i][j]);
      }
      p.append("</list></clientPreferences>");

      int e = random.nextInt(endowNumber--);
      int[] endow = endowments[e];

      // One less endowment assignment to pick from
      endowments[e] = endowments[endowNumber];

      p.append("<ticketEndowments><list>");
      appendEndowment(endow, p);
      p.append("</list></ticketEndowments>");

      // Setup endowments as transactions
      for (int type = 0, n = endow.length; type < n; type++) {
	if (endow[type] > 0) {
	  Auction auction = auctionsByType[type + 16];
	  createEndowment(auction, u, endow[type], 0f, startTime);
	}
      }
      params[i] = p.toString();
      info[i] = c.toString();
    }
    this.gameParameterMessages = params;
    this.clientPreferencesInfo = info;
    this.clientPreferences = prefs;
  }

  private void createClient(Random random, int client, StringBuffer msg,
			    StringBuffer info, int[] prefs) {
    int inday, outday;
    int hotel = 50 + random.nextInt(101);
    int e1 = random.nextInt(201);
    int e2 = random.nextInt(201);
    int e3 = random.nextInt(201);
    switch (random.nextInt(10)) {
    case 0: inday = 1; outday = 2; break;
    case 1: inday = 1; outday = 3; break;
    case 2: inday = 1; outday = 4; break;
    case 3: inday = 1; outday = 5; break;
    case 4: inday = 2; outday = 3; break;
    case 5: inday = 2; outday = 4; break;
    case 6: inday = 2; outday = 5; break;
    case 7: inday = 3; outday = 4; break;
    case 8: inday = 3; outday = 5; break;
    case 9: inday = 4; outday = 5; break;
    default:
      // This can never happen but is required by the compiler
      inday = 3; outday = 4;
    }
    msg.append("<clientPrefTuple><client>" + client
	       + "</client><arrival>" + inday
	       + "</arrival><departure>" + outday
	       + "</departure><hotel>" + toString(hotel)
	       + "</hotel><ticketPreferences><list>"
	       + "<typePriceTuple><type>1</type><price>" + toString(e1)
	       + "</price></typePriceTuple>"
	       + "<typePriceTuple><type>2</type><price>" + toString(e2)
	       + "</price></typePriceTuple>"
	       + "<typePriceTuple><type>3</type><price>" + toString(e3)
	       + "</price></typePriceTuple>"
	       + "</list></ticketPreferences></clientPrefTuple>");
    info.append("" + ',' + inday + ',' + outday + ',' + hotel + ','
		+ e1 + ',' + e2 + ',' + e3);
    prefs[0] = inday;
    prefs[1] = outday;
    prefs[2] = hotel;
    prefs[3] = e1;
    prefs[4] = e2;
    prefs[5] = e3;
  }

  private StringBuffer appendEndowment(int[] endowments, StringBuffer p) {
    for (int type = 0; type < ENT_TYPE; type++) {
      for (int day = 0; day < 4; day++) {
	p.append("<ticketEndowmentTuple><type>")
	  .append(type + 1)
	  .append("</type><day>")
	  .append(day + 1)
	  .append("</day><quantity>")
	  .append(endowments[type * 4 + day])
	  .append("</quantity>"
		  + "</ticketEndowmentTuple>");
      }
    }
    return p;
  }

  /**
   * Generates an endowment assignment for all participants in a TAC
   * classic game.
   *
   * The return value is a matris composed as:
   * <pre>
   *  Participant 1: #Type 1 at Day 1, ..., #Type 1 at Day 4,
   *                 #Type 2 at Day 1, ..., #Type 2 at Day 4,
   *                 #Type 3 at Day 1, ..., #Type 3 at Day 4
   *  Participant 2: #Type 1 at Day 1, ..., #Type 1 at Day 4
   *		...
   * </pre>
   *
   * @param random the <code>Random</code> instance to use for random numbers
   * @param participants the number of participants in the game
   * @return an <code>int[][]</code> value describing the endowment assigment
   */
  protected int[][] generateEndowments(Random random) {
    final int PARTICIPANTS = 8;
    // Day A      Day B
    //  4, 4, 4,   4, 4, 4,
    //  4, 2, 2,   2, 4, 2,
    //  0, 2, 2,   2, 0, 2

    // 1. Day A 4 split type X
    // 2. Day B 4 split type Y
    // 3. One 4 per agent randomly
    // 4. Pick 2 from the other type with most 2
    // 5. Do once more for other day
    int[][] endowments = new int[PARTICIPANTS][ENT_TYPE * 4];

    // Pick endownents for Day 1 and 4
    int[][] endowPack1 = createEndowPack(random, 100);
    // Pick endownents for Day 2 and 3
    int[][] endowPack2 = createEndowPack(random, 100);

    // Generate an actual "random" allocation of endowmwnts and participants
    int[] pmap1 = createMap(random, PARTICIPANTS);
    int[] pmap2 = createMap(random, PARTICIPANTS);
    int[] tmap1 = createMap(random, ENT_TYPE);
    int[] tmap2 = createMap(random, ENT_TYPE);
    for (int i = 0; i < PARTICIPANTS; i++) {
      int[] day14 = endowPack1[pmap1[i]];
      int[] day23 = endowPack2[pmap2[i]];
      int[] endows = endowments[i];
      for (int j = 0; j < ENT_TYPE; j++) {
	int t1 = tmap1[j];
	int t2 = tmap2[j];
	endows[t1 * 4 + 0] = day14[t1 * 2 + 0];
	endows[t2 * 4 + 1] = day23[t2 * 2 + 0];
	endows[t2 * 4 + 2] = day23[t2 * 2 + 1];
	endows[t1 * 4 + 3] = day14[t1 * 2 + 1];
      }
    }
    return endowments;
  }

  protected int[][] createEndowPack(Random random, int retry) {
    final int PARTICIPANTS = 8;
    int[][] endowPack = new int[PARTICIPANTS][ENT_TYPE * 2];
    int[] endows = new int[ENT_TYPE * 2];

    // Pick the first 6 bundles of 4 tickets.
    for (int i = 0; i < 6; i++) {
      endowPack[i][(i % ENT_TYPE) * 2 + i / ENT_TYPE] = 4;
    }
    // Assign the number of endowments left
    for (int i = 0, n = endows.length; i < n; i++) {
      endows[i] = 4;
    }

    // Pick another two bundles of 4 tickets (for the last 2 agents)
    int type = random.nextInt(ENT_TYPE);
    endows[type * 2] -= 4;
    endowPack[6][type * 2] = 4;

    type = random.nextInt(ENT_TYPE);
    endows[type * 2 + 1] -= 4;
    endowPack[7][type * 2 + 1] = 4;

    // Pick the bundles of 2 tickes for each agent
    for (int i = 0; i < PARTICIPANTS; i++) {
      int[] pack = endowPack[i];
      int currType;
      if (pack[0] > 0 || pack[1] > 0) {
	currType = 0;
      } else if (pack[1 * 2] > 0 || pack[1 * 2 + 1] > 0) {
	currType = 1;
      } else {
	currType = 2;
      }
      type = getNextPack(random, endows, currType);
      endows[type] -= 2;
      pack[type] += 2;
      if (endows[type] < 0) {
	// It seems like we have done a wrong choice and entered an
	// illegal state.  We should backtrack and try another path
	// but since the probability to arrive in illegal states is so
	// small, we simply restart the endowment pack assignment. The
	// retry number is only to ensure that we never enters a live
	// lock.  This should be fixed nicer. FIX THIS!!!
	if (retry > 0) {
	  return createEndowPack(random, retry - 1);
	} else {
	  // Potential live-lock detected
	  LogFormatter.separator(log, Level.SEVERE,
				 "Endowment underflow for type " + type
				 + " (" + endows[type]
				 + " items left) for participant " + (i + 1));
	}
      }
    }
    return endowPack;
  }

  private int getNextPack(Random random, int[] endows, int currType) {
    int max = -1;
    int maxIndex = 0;
    for (int i = 0, n = endows.length; i < n; i++) {
      if ((i / 2) != currType) {
	if (endows[i] > max || (endows[i] == max && random.nextInt(2) > 0)) {
	  max = endows[i];
	  maxIndex = i;
	}
      }
    }
    return maxIndex;
  }

  private int[] createMap(Random random, int len) {
    int[] map = new int[len];
    int[] nmap = new int[len];
    int number = len;
    for (int i = 0; i < len; i++) {
      map[i] = i;
    }
    for (int i = 0; i < len; i++) {
      int index = random.nextInt(number);
      nmap[i] = map[index];
      number--;
      map[index] = map[number];
    }
    return nmap;
  }

  private String toString(double v) {
    return TACFormatter.toString(v);
  }

} // ClassicMarket
