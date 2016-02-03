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
 * GameArchiver
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 15 May, 2002
 * Updated : $Date: 2004/09/07 14:46:02 $
 *	     $Revision: 1.8 $
 */

package se.sics.tac.is;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

import com.botbox.util.ArrayQueue;
import se.sics.tac.log.GameResultCreator;
import se.sics.tac.log.ISTokenizer;
import se.sics.tac.log.TACGameInfo;
import se.sics.tac.util.TACFormatter;

public class GameArchiver extends Thread implements FilenameFilter,
						    Comparator {

  private static final Logger log =
    Logger.getLogger(GameArchiver.class.getName());

  private final static String GAME_DATA_NAME = "gameinfo.dat";
  private final static String LOG_NAME = "applet.log";

  private final InfoServer infoServer;
  private final TACStore store;
  private final String backupPrefix;
  private final String gamePath;
  private final String gameURLPath;
  private final String gameDataFile;
  private final String defaultPath;

  private final String runAfterGame;

  private ArrayQueue queue = new ArrayQueue();

  private GameResultCreator resultCreator;
  private ScoreGenerator defaultScoreGenerator;

  public GameArchiver(InfoServer infoServer, String gamePath,
		      String gameURLPath, String backupPrefix,
		      String runAfterGame) {
    super("GameArchive");
    if (infoServer == null) {
      throw new NullPointerException();
    }
    this.infoServer = infoServer;
    this.store = infoServer.getTACStore();
    this.backupPrefix = backupPrefix;
    if (gamePath.endsWith(File.separator)) {
      gamePath = gamePath.substring(0, gamePath.length() - 1);
    }
    this.gamePath = gamePath;
    if (gameURLPath != null && !gameURLPath.endsWith("/")) {
      gameURLPath += '/';
    }
    this.gameURLPath = gameURLPath;
    this.gameDataFile = gamePath + File.separatorChar + GAME_DATA_NAME;
    this.defaultPath = gamePath + File.separatorChar + "default";
    this.runAfterGame = runAfterGame;

    checkGames();
    start();
  }

  private void checkGames() {
    File fp = new File(".");
    File[] logs = fp.listFiles(this);
    if (logs != null && logs.length > 0) {
      Arrays.sort(logs, this);
      for (int i = 0, n = logs.length; i < n; i++) {
	int gameID = fileToGameID(logs[i]);
	if (gameID > 0) {
	  log.info("adding game " + gameID + " to be generated");
	  gameFinished(gameID);
	}
      }
    }
  }

//   private void checkGames() {
//     // Check if any games are ready to be generated
//     int gid = store.getInt(TACStore.LAST_GAME_ID, 0) + 1;
//     for (int i = lastArchivedGameID; i < gid; i++) {
//       File fp = new File(gamePath + File.separatorChar + i);
//       if (fp.exists() && fp.isDirectory()) {
// 	// The game directory exists
// 	fp = new File(gamePath + File.separatorChar + i
// 		      + File.separatorChar + "index.html");
// 	if (fp.exists()) {
// 	  lastArchivedGameID = i;
// 	} else if ((new File(gamePath + File.separatorChar + i
// 			     + File.separatorChar + LOG_NAME)).exists()) {
// 	  log.info("adding game " + i + " to be generated");
// 	  gameFinished(i);
// 	} else {
// 	  log.warning("no applet log for game " + i);
// 	}
//       }
//     }
//   }

  public void prepareCompetition(int gameID, Competition competition) {
    String path = gamePath + File.separatorChar + "competition"
      + File.separatorChar + competition.getID();
    File fp = new File(path);
    if (!fp.exists() && !fp.mkdirs()) {
      log.severe("could not create score directory " + path);
    } else {
      generateScorePage(store, path, competition, gameID, false);
    }
  }

  public synchronized void gameFinished(int gameID) {
    Integer game = new Integer(gameID);
    // Only add the game to be generated if not already added
    if (!queue.contains(game)) {
      queue.add(game);
      notify();
    }
  }

  private synchronized int nextGame() {
    while (queue.isEmpty()) {
      try {
	wait();
      } catch (Exception e) {
	log.log(Level.WARNING, "wait interrupted", e);
      }
    }
    return ((Integer) queue.remove(0)).intValue();
  }

  public void run() {
    // Let the rest of the system startup before starting to generate
    // game summaries

    // Generate initial score page if no score page already exists
    try {
      File fp = new File(defaultPath);
      if (!fp.exists() && !fp.mkdirs()) {
	log.severe("could not create score directory " + defaultPath);
      } else {
	generateScorePage(store, defaultPath, null, 0, false);
      }
    } catch (Exception e) {
      log.log(Level.SEVERE, "could not generate empty default score page", e);
    }

    // Generate initial score page if no score page already exists
    try {
      Competition comp = store.getCurrentCompetition();
      if (comp != null) {
	prepareCompetition(0, comp);
      }
    } catch (Exception e) {
      log.log(Level.SEVERE, "could not generate empty default score page", e);
    }

    try {
      Thread.sleep(30000);
    } catch (Exception e) {
      e.printStackTrace();
    }

    do {
      int gameID = nextGame();

      // Read the game file
      try {
	String gameFile = "applet" + gameID + ".log";
	String targetFile = gamePath + File.separatorChar
	  + gameID + File.separatorChar + LOG_NAME + ".gz";
	String gameDirectory = gamePath + File.separatorChar + gameID;
	File directoryFp = new File(gameDirectory);
	if (!directoryFp.exists() && !directoryFp.mkdirs()) {
	  log.severe("could not create game directory '"
		     + gameDirectory + '\'');
	} else {
	  // Read and copy the game data. We can not simply rename
	  // the game data file to its new position because it might
	  // be on another disk + the file is gzipped to save space
	  TACGameInfo game = readGame(gameID, gameFile, targetFile);
	  // Minor optimization: the quotes and bids are not needed
	  // and there is no reason to remember them.
	  game.setProperty(TACGameInfo.IGNORE_QUOTES, "true");
	  game.setProperty(TACGameInfo.IGNORE_BIDS, "true");
	  if (game.isFinished() || game.isScratched()) {
	    store.addGameResults(game);

	    if (resultCreator == null) {
	      resultCreator = new GameResultCreator();
	    }
	    // Generate result for this game if it has not been scratched.
	    // However the moving of the game file must be done even
	    // if the game was scratched.
	    if (game.isScratched()
		|| resultCreator.generate(gamePath, game, true)) {
	      // Move the game data file to its right location
	      String bakGameFile = backupPrefix + gameFile;
	      File fp = new File(gameFile);
	      File newFp = new File(bakGameFile);
	      if (!fp.renameTo(newFp)) {
		log.severe("could not move game data '"
			   + gameFile + " to '" + bakGameFile + '\'');
	      }

	      // Storage of last game id lets the PHP scripts and others
	      // know how many game results exists
	      setLastGame(gameID);
	    } else {
	      // The game result create must already have shown an error
	      // if this execution point was reached.
	    }

	    // Only generate score if game has not been scratched
	    if (!game.isScratched()) {
	      generateScore(gameID, game);
	    }

	    if (runAfterGame != null) {
	      try {
		String command =
		  TACFormatter.format(runAfterGame, "g",
				      new String[] {
					Integer.toString(gameID)
				      });
		if (command != null) {
		  log.fine("running '" + command + '\'');
		  Runtime.getRuntime().exec(command);
		}
	      } catch (Throwable e) {
		log.log(Level.SEVERE, "could not run '"
			+ runAfterGame + '\'', e);
	      }
	    }
	  } else {
	    log.severe("game " + gameID + " was not (yet?) finished!");
	  }
	}

      } catch (Exception e) {
	log.log(Level.SEVERE, "could not generate result for game "
		+ gameID, e);
      }
    } while (true);
  }

  void generateScore(int gameID) {
    generateScore(gameID, null);
  }

  private void generateScore(int gameID, TACGameInfo game) {
    Competition competition = store.getCompetitionByID(gameID);
    String path = competition == null
      ? defaultPath
      : (gamePath + File.separatorChar + "competition"
	 + File.separatorChar + competition.getID());

    File fp = new File(path);
    if (!fp.exists() && !fp.mkdirs()) {
      log.severe("could not create score directory " + path);
    } else {
      if (game != null) {
	// Can only generate game statistics if a game has been specified
	for (int i = 0, n = game.getNumberOfAgents(); i < n; i++) {
	  int id = game.getAgentID(i);
	  TACUser user = competition == null
	    ? store.getUser(id)
	    : competition.getParticipant(id);
	  // Only generate statistics for main agents (not sub agents
	  // like pelle1, pelle2, etc)
	  if (user != null && id == user.getID()) {
	    Statistics.generateStatisticsPage(store, path, gameURLPath,
					      competition, user, true);
	  }
	}
      } else if (competition != null && competition.hasGameID()) {
	TACUser[] participants = competition.getParticipants();
	if (participants != null) {
	  for (int i = 0, n = participants.length; i < n; i++) {
	    Statistics.generateStatisticsPage(store, path, gameURLPath,
					      competition, participants[i],
					      true);
	  }
	}
      }

      generateScorePage(store, path, competition, gameID, true);
    }
  }

  private boolean generateScorePage(TACStore store, String compPath,
				    Competition comp, int gid,
				    boolean update) {
    ScoreGenerator generator = comp != null
      ? comp.getScoreGenerator()
      : null;
    if (generator == null) {
      if (defaultScoreGenerator == null) {
	defaultScoreGenerator = new DefaultScoreGenerator();
      }
      generator = defaultScoreGenerator;
    }
    generator.setServerInfo(infoServer.getServerName(),
			    InfoServer.FULL_VERSION);
    return generator.generateScorePage(store, compPath, comp, gid, update);
  }

  private TACGameInfo readGame(int gameID, String gameFile,
			       String targetFile)
    throws IOException
  {
    log.fine("Reading game " + gameID + " from " + gameFile
	     + " copying to " + targetFile);
    BufferedReader reader = new BufferedReader(new FileReader(gameFile));
    BufferedWriter writer =
      new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(targetFile))));
    int lineNumber = 0;
    try {
      String line;
      TACGameInfo game = new TACGameInfo();
      while ((line = reader.readLine()) != null) {
	lineNumber++;
	if (line.length() > 0) {
	  game.gameData(new ISTokenizer(line));
	  writer.write(line);
	  writer.newLine();
	}
      }
      return game;
    } catch (Exception e) {
      throw (IOException) new IOException("could not parse line " + lineNumber)
	.initCause(e);
    } finally {
      writer.close();
      reader.close();
    }
  }

  private void setLastGame(int gameID) {
    try {
      FileWriter out = new FileWriter(gameDataFile);
      out.write(Integer.toString(gameID));
      out.close();
    } catch (Exception e) {
      log.log(Level.SEVERE, "could not update game data file for game "
	      + gameID, e);
    }
  }


  /*********************************************************************
   * FilenameFilter
   *********************************************************************/

  public boolean accept(File fp, String name) {
    return name.startsWith("applet");
  }


  /*********************************************************************
   * Comparator
   *********************************************************************/

  public int compare(Object o1, Object o2) {
    File f1 = (File) o1;
    File f2 = (File) o2;
    int v1, v2;

    // Special sorting of directories consisting of only digits
    if (((v1 = fileToGameID(f1)) >= 0)
	&& ((v2 = fileToGameID(f2)) >= 0)) {
      return v1 - v2;
    }
    return f1.compareTo(f2);
  }

  private int fileToGameID(File fp) {
    String name = fp.getName();
    if (name.startsWith("applet")) {
      char c;
      int value = 0;
      for (int i = 6, n = name.length(); i < n; i++) {
	c = name.charAt(i);
	if (c >= '0' && c <= '9') {
	  value = value * 10 + c - '0';
	} else if ((i + 4 == n) && name.regionMatches(i, ".log", 0, 4)) {
	  return value;
	} else {
	  return -1;
	}
      }
    }
    return -1;
  }

} // GameArchiver
