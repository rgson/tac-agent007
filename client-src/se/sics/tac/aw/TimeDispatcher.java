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
 * TimeDispatcher
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : 25 June, 2002
 * Updated : $Date: 2005/06/07 19:06:17 $
 *	     $Revision: 1.1 $
 * Purpose :
 *   The TimeDispatcher is used to schedule tasks at specified times.
 *
 *   A TimeDispatcher object is obtained by calling
 *   TimeDispatcher.getDefault().
 */

package se.sics.tac.aw;
import java.util.ArrayList;

public class TimeDispatcher extends Thread {

  private static TimeDispatcher dispatcher;

  public static TimeDispatcher getDefault() {
    if (dispatcher == null) {
      synchronized (TimeDispatcher.class) {
	if (dispatcher == null) {
	  dispatcher = new TimeDispatcher();
	}
      }
    }
    return dispatcher;
  }

  private ArrayList list = new ArrayList();
  private long timeDiff;

  private TimeDispatcher() {
    super("timer");
    start();
  }

  public void setTimeDiff(long timeDiff) {
    this.timeDiff = timeDiff;
  }

  public synchronized
    void addTask(long time, Object key, Object value, Task task) {
    list.add(new TaskHolder(time, key, value, task));
    notify();
  }

  public synchronized void cancelTask(Object key, Task task) {
    for (int i = 0, n = list.size(); i < n; i++) {
      TaskHolder h = (TaskHolder) list.get(i);
      if (h.key == key && h.task == task) {
	list.remove(i);
	i--; n--;
      }
    }
  }

  private synchronized TaskHolder nextTask() {
    do {
      long currentTime = System.currentTimeMillis() - timeDiff;
      for (int i = 0, n = list.size(); i < n; i++) {
	TaskHolder h = (TaskHolder) list.get(i);
	if (h.time <= currentTime) {
	  list.remove(i);
	  return h;
	}
      }
      try {
	wait(1000);
      } catch (Exception e) {
	e.printStackTrace();
      }
    } while (true);
  }

  public void run() {
    do {
      TaskHolder h = nextTask();
      try {
	h.task.performWork(h.time, h.key, h.value);
      } catch (Exception e) {
	e.printStackTrace();
      }
    } while (true);
  }


  private static class TaskHolder {
    public long time;
    public Object key;
    public Object value;
    public Task task;

    public TaskHolder(long time, Object key, Object value, Task task) {
      this.time = time;
      this.key = key;
      this.value = value;
      this.task = task;
    }
  }

} // TimeDispatcher
