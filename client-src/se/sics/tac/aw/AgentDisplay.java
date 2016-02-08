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
 * Updated : $Date: 2005/06/07 19:06:15 $
 *	     $Revision: 1.1 $
 */

package se.sics.tac.aw;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.table.TableModel;

public class AgentDisplay implements ActionListener, WindowListener {

  private JFrame window;
  private JTable agentTable;
  private TACAgent agent;
  private JLabel status;
  private Timer timer;
  private boolean isVisible = false;
  private boolean isClosing = false;

  public AgentDisplay(TableModel tableModel, TACAgent agent) {
    this.agent = agent;
    window = new JFrame("Agent Display (TAC AgentWare "
			+ TACAgent.VERSION + ')');
    window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    window.addWindowListener(this);
    window.setSize(800, 520);
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    agentTable = new JTable(tableModel);
    panel.add(new JScrollPane(agentTable), BorderLayout.CENTER);
    panel.add(status = new JLabel(" - "), BorderLayout.SOUTH);
    window.getContentPane().add(panel);
    timer = new Timer(1000, this);
  }

  public void setVisible(boolean visible) {
    if (visible != isVisible) {
      this.isVisible = visible;
      window.setVisible(visible);
      if (visible) {
	isClosing = false;
	timer.start();
      } else {
	window.dispose();
	timer.stop();
      }
    }
  }

  public void setGameStatus(String status) {
    window.setTitle(status);
  }



  // -------------------------------------------------------------------
  // Action Listener
  // -------------------------------------------------------------------

  public void actionPerformed(ActionEvent ae) {
    Object source = ae.getSource();
    if (source == timer) {
      if (isClosing) {
	isClosing = false;
	setVisible(false);
      } else {
	status.setText("Messages sent: " + TACMessage.getMessageCount() +
		       "  Avg. response time: " +
		       TACMessage.getAverageResponseTime() + " msek" +
		       "  Time left: " + agent.getGameTimeLeftAsString());
      }
    }
  }



  // -------------------------------------------------------------------
  // WindowListener
  // -------------------------------------------------------------------

  public void windowOpened(WindowEvent e) {
  }

  public void windowClosing(WindowEvent e) {
    if (e.getSource() == window) {
      isClosing = true;
    }
  }

  public void windowClosed(WindowEvent e) {
  }

  public void windowIconified(WindowEvent e) {
  }

  public void windowDeiconified(WindowEvent e) {
  }

  public void windowActivated(WindowEvent e) {
  }

  public void windowDeactivated(WindowEvent e) {
  }
}
