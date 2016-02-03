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
 * FileTACStore
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 9 April, 2002
 * Updated : $Date: 2004/07/05 11:54:23 $
 *	     $Revision: 1.5 $
 */

package se.sics.tac.is;
import java.io.*;
import java.util.logging.*;
import se.sics.tac.log.TACGameInfo;

public class FileTACStore extends TACStore {

  private static final Logger log =
    Logger.getLogger(FileTACStore.class.getName());

  private final String ATTR_FILE;
  private final String USER_FILE;
  private final String GAMES_FILE;
  private final String COMPETITION_FILE;

  public FileTACStore() {
    File fp = new File("state");
    if ((!fp.exists() && !fp.mkdir()) || !fp.isDirectory()) {
      throw new IllegalStateException("coul not create 'state' directory");
    } else {
      String prefix = "state" + File.separatorChar;
      ATTR_FILE = prefix + "state";
      USER_FILE = prefix + "users";
      GAMES_FILE = prefix + "games";
      COMPETITION_FILE = prefix + "competitions";
    }

    loadState(ATTR_FILE, true);
    loadState(USER_FILE, true);
    loadState(GAMES_FILE, true);
    loadState(COMPETITION_FILE, true);
  }

  protected boolean hasGameResults(int gameID) {
    return false;
  }

  protected void gameStopped(TACGameInfo game) {
  }

  public TACGameResult getLatestGameResult(int agentID,
					   Competition competition,
					   int maxNumberOfGames) {
    return new TACGameResult(agentID);
  }

  public TACGameResult getLatestGameResult(int agentID, int lowestGameID,
					   int maxNumberOfGames) {
    return new TACGameResult(agentID);
  }


  protected void setScore(Competition competition,
			  int gameID, TACUser agent, float score,
			  int penalty, int util, float weight, int flags) {
    if (competition != null) {
      saveState(COMPETITION_FILE);
    } else {
      saveState(USER_FILE);
    }
  }

  /**
   * Updates the user and returns the updated user if such was
   * found. Returns <code>null</code> if the user was not found or if
   * it did not need updating.
   */
  // All users are in memory and never needs to be updated
  public TACUser updateUser(int userID) {
    return null;
  }

  // All users are in memory and never needs to be updated
  public TACUser updateUser(String userName) {
    return null;
  }

  protected void attributeChanged(String name, int val, int operation) {
    saveState(ATTR_FILE);
  }

  protected void userChanged(TACUser user, int operation) {
    saveState(USER_FILE);
  }

  protected void gameChanged(TACGame game, int operation) {
    saveState(GAMES_FILE);
  }

  protected void competitionChanged(Competition competition, int operation) {
    // Do not need to save competitions when they are started
    if (operation != STARTED) {
      saveState(COMPETITION_FILE);
    }
  }


  /*********************************************************************
   * State handling
   *********************************************************************/

  private void loadState(String name, boolean revert) {
    try {
      InputStream in = getInputStream(name);
      loadState(name, in);
    } catch (FileNotFoundException e) {
      // No saved state
    } catch (Exception e) {
      log.log(Level.SEVERE, "could not load state " + name, e);
      if (revert && revertFile(name)) {
	loadState(name, false);
      }
    }
  }

  private void loadState(String name, InputStream in)
    throws ClassNotFoundException,
	   IOException
  {
    ObjectInputStream oin = null;
    try {
      oin = new ObjectInputStream(in);
      if (name == ATTR_FILE) {
	intNames = (String[]) oin.readObject();
	intValues = (int[]) oin.readObject();
      } else if (name == USER_FILE) {
	setUsers((TACUser[]) oin.readObject());
      } else if (name == COMPETITION_FILE) {
	comingCompetitions = (Competition[]) oin.readObject();
	currentCompetition = (Competition) oin.readObject();
      } else {  // GAMES_FILE
	comingGames = (TACGame[]) oin.readObject();
      }
    } finally {
      if (oin != null) {
	oin.close();
      } else {
	in.close();
      }
    }
  }

  private void saveState(String name) {
    OutputStream out = null;
    ObjectOutputStream oout = null;
    try {
      out = getOutputStream(name);
      oout = new ObjectOutputStream(out);
      if (name == ATTR_FILE) {
	oout.writeObject(intNames);
	oout.writeObject(intValues);
      } else if (name == USER_FILE) {
	oout.writeObject(getUsers());
      } else if (name == COMPETITION_FILE) {
	oout.writeObject(comingCompetitions);
	oout.writeObject(currentCompetition);
      } else {  // GAMES_FILE
	oout.writeObject(comingGames);
      }
    } catch (Exception e) {
      log.log(Level.SEVERE, "could not save state " + name, e);
    } finally {
      try {
	if (oout != null) {
	  oout.close();
	} else if (out != null) {
	  out.close();
	}
      } catch (IOException e) {}
    }
  }

  private InputStream getInputStream(String name) throws IOException {
    String filename = name + ".ser";
    try {
      return new FileInputStream(filename);
    } catch (FileNotFoundException e) {
      String bakName = name + ".bak";
      if (new File(bakName).renameTo(new File(filename))) {
	return new FileInputStream(filename);
      }
      throw e;
    }
  }

  private OutputStream getOutputStream(String name) throws IOException {
    String filename = name + ".ser";
    String bakName = name + ".bak";
    File fp = new File(filename);
    if (fp.exists()) {
      File bakFp = new File(bakName);
      bakFp.delete();
      fp.renameTo(bakFp);
    }
    return new FileOutputStream(filename);
  }

  private boolean revertFile(String name) {
    File bakFp = new File(name + ".bak");
    File fp = new File(name + ".ser");
    if (fp.exists() && !fp.delete()) {
      log.severe("could not remove old file " + fp
		 + " when reverting attribute (will retry)" + bakFp);
      // Remove the file we began saving because the save failed
      // but wait a short while because otherwise it might not
      // be possible to remove the newly created file.
      Runtime.getRuntime().gc();
      // Wait a small time because sometime just edited files
      // can not be removed
      try {
	Thread.sleep(2000);
      } catch (Exception e) {
      }
      Runtime.getRuntime().gc();
      if (!fp.delete()) {
	log.severe("could not remove old file " + fp
		   + " when reverting attribute " + bakFp);

	File rFp = new File(name + ".ser~");
	int nr = 0;
	// Loop until we find an empty remove file that we could
	// use to move the old broken file to (but NOT forever!)
	// We could of course use Java's unique temporary file creation
	// and then use a garbage collect scheme to erase the files. FIX THIS!!
	while (rFp.exists() && !rFp.delete() && (++nr < 10)) {
	  log.severe("could not remove old removed file "
		     + rFp + " when reverting attribute");
	  rFp = new File(name + '-' + nr + ".ser~");
	}
	if (!fp.renameTo(rFp)) {
	  log.severe("could not rename old file "
		     + fp + " to " + rFp + " when reverting attribute");
	}
      }
    }
    return bakFp.renameTo(fp);
  }

} // FileTACStore
