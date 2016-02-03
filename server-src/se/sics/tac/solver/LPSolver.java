/**
 * LPSolver.java
 *
 *
 * Created: Tue May 25 22:46:13 2004
 *
 * @Author  :
 * @version 1.0
 */
package se.sics.tac.solver;

import java.io.PrintStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;


public class LPSolver implements Solver {

  public static final int NO_PRICES = 0;
  public static final int LINEAR_PRICES = 1;

  private int priceMode = 0;

  public LPSolver() {
  } // LPSolver constructor

  // in,out, G=1, e1,e2,e3
  private int[][] alloc = new int[8][6];
  private long elapsedTime;

  private int preferences[][] = new int[][] {{1,2,72,77,78,37},
					    {2,2,55,193,180,111},
					    {0,1,112,67,149,177},
					    {1,1,87,74,72,167},
					    {2,2,110,68,193,148},
					    {1,1,69,87,142,189},
					    {1,2,67,78,154,67},
					    {0,2,140,141,3,23}
  };

  private int own[][] = new int[][] {{2,4,2,0}, // Inflight?
				    {0,3,5,0}, // Outflight?
				    {7,3,2,0}, // Hotel 1
				    {9,3,3,0}, // Hotel 2
				    {1,1,2,2},
				    {1,0,0,4},
				    {1,1,4,0}
  };

  private int prices[][] = new int[7][4];

  private static String[] pack = new String[20]; // All package namnes
  private static String[] ent = new String[12]; // All ent names

  static {
    for (int i = 0, n = 20; i < n; i++) {
      int infl = getInFlight(i) + 1;
      int outfl = getOutFlight(i) + 2;
      pack[i] = "f" + infl + "_" + outfl + (i >= 10 ? "g" : "c");
    }

    for (int i = 0, n = 12; i < n; i++) {
      int eType = (i / 4) + 1;
      int eDay = (i % 4) + 1;
      ent[i] = "t" + eType + "d" + eDay;
    }
  }

  private int packageValue(int client,int pack) {
    int pArrival = preferences[client][0];
    int pDeparture = preferences[client][1] + 1;
    int pHotel = preferences[client][2];
    int arrival = 0;
    int departure = 0;
    int hotel = 0;
    // pack = 0 -> arrival 1, dep 2
    // pack = 1 -> arrival 1, dep 3...
    if (pack >= 10) {
      pack -= 10;
      hotel = 1;
    }
    if (pack < 4) {
      arrival = 0;
      departure = 1 + pack;
    } else if (pack < 7) {
      arrival = 1;
      departure = 2 + (pack - 4);
    } else if (pack < 9) {
      arrival = 2;
      departure = 3 + (pack - 7);
    } else {
      arrival = 3;
      departure = 4;
    }
    return 1000 - 100 * Math.abs(pArrival - arrival) -
      100 * Math.abs(pDeparture - departure) + hotel * pHotel;
  }


  private boolean isStaying(int pack, int day) {
    if (pack >= 10) pack -= 10;
    return useHotel(pack, day, false);
  }

  private boolean useHotel(int pack, int day, boolean good) {
    int arrival = 0;
    int departure = 0;
    boolean goodHotel = false;

    if (pack >= 10) {
      pack -= 10;
      goodHotel = true;
    }

    if (good != goodHotel) return false;

    if (pack < 4) {
      arrival = 0;
      departure = 1 + pack;
    } else if (pack < 7) {
      arrival = 1;
      departure = 2 + (pack - 4);
    } else if (pack < 9) {
      arrival = 2;
      departure = 3 + (pack - 7);
    } else {
      arrival = 3;
      departure = 4;
    }

    return (arrival <= day) && (departure > day);
  }

  private static int getInFlight(int pack) {
    if (pack >= 10) pack -= 10;

    if (pack < 4) {
      return 0;
    } else if (pack < 7) {
      return 1;
    } else if (pack < 9) {
      return 2;
    } else {
      return 3;
    }
  }

  private static int getOutFlight(int pack) {
    if (pack >= 10) pack -= 10;
    if (pack < 4) {
      return 0 + pack;
    } else if (pack < 7) {
      return 1 + (pack - 4);
    } else if (pack < 9) {
      return 2 + (pack - 7);
    } else {
      return 3;
    }
  }

  private int performSolve() {
    int score = 0;
    try {
      PrintStream out;

      Process solver = Runtime.getRuntime().exec("e:/lpwin/lp_solve.exe -B1");
      out = new PrintStream(solver.getOutputStream());

      // The optimization formula
      out.println("max: ");

      // Flight + Hotel utility
      for (int i = 1, n = 8; i <= n; i++) {
	for (int j = 1, m = 20; j <= m; j++) {
	  out.print(packageValue(i - 1, j - 1) + " p" + i + pack[j - 1]);
	  if (j < 20) out.print(" + ");
	  else out.println(" +\n");
	}
      }

      // Entertainment utility
      for (int i = 1, n = 8; i <= n; i++) {
	for (int j = 0, m = 12; j < m; j++) {
	  int eType = j / 4;
	  int eDay = j % 4;
	  out.print(preferences[i - 1][3 + eType] + " e" + i + ent[j]);
	  if (j < 11) out.print(" + ");
	  else if (i < 8) out.println(" +\n");
	}
      }

      if (priceMode == LINEAR_PRICES) {
	System.out.println();
	for (int i = 0, n = 4; i < n; i++) {
	  for (int j = 0, m = 4; j < m; j++) {
	    out.print(" - ");
	    out.print(prices[i][j] + " b" + i + "d" + j);
	  }
	}
      }

      out.println(";");
      out.println();
      out.println();

      // Constraints (possible flight packages for each customer)
      // One package per customer (Max)
      for (int i = 1, n = 8; i <= n; i++) {
	for (int j = 1, m = 20; j <= m; j++) {
	  out.print("p" + i + pack[j-1]);
	  if (j < 20) out.print(" + ");
	  else out.println(" <= 1;");
	}
      }


      // One entertainment ticket per day
      out.println("\n/* One entertainment ticket per day / client */");
      for (int j = 1, m = 8; j <= m; j++) {
	for (int i = 0, n = 4; i < n; i++) {
	  out.println("e" + j + ent[i] +
		      " + e" + j + ent[i + 4] +
		      " + e" + j + ent[i + 8] + " <= 1;");
	}
      }

      out.println("\n/* One entertainment ticket per type / client */");
      for (int j = 1, m = 8; j <= m; j++) {
	for (int i = 0, n = 3; i < n; i++) {
	  out.println("e" + j + ent[i * 4] +
		      " + e" + j + ent[i * 4 + 1] +
		      " + e" + j + ent[i * 4 + 2] +
		      " + e" + j + ent[i * 4 + 3] +
		      " <= 1;");
	}
      }

      out.println("\n/* Only entertainment on stay days */");
      for (int i = 0, n = 8; i < n; i++) {
	for (int j = 0, m = 12; j < m; j++) {
	  int outNo = 0;
	  for (int p = 0; p < 20; p++) {
	    if (isStaying(p, j % 4)) {
	      if (outNo > 0) out.print(" + ");
	      out.print("p" + (i + 1) + pack[p]);
	      outNo++;
	    }
	  }
	  if (outNo > 0) out.println(" >= e" + (i + 1) + ent[j] + ';');
	}
      }

      // What we own and the maximum usage of that...

      out.println("/* Ownership constraints */");
      for (int i = 0, n = 4; i < n; i++) {
	int noOut = 0;
	for (int j = 0, m = 20; j < m; j++) {
	  if (getInFlight(j) == i) {
	    if (noOut == 0) {
	      out.println("\n/* Inflight day " + (i + 1) + " */");
	    }
	    for (int z = 1; z <= 8; z++) {
	      if (noOut > 0) {
		out.print(" + ");
	      }
	      out.print("p" + z + pack[j]);
	      noOut++;
	    }
	  }
	}
	if (priceMode == LINEAR_PRICES) {
	  out.println(" <= " + own[0][i] + " + b0d" + i + ";");
	} else {
	  out.println(" <= " + own[0][i] + ";");
	}
      }

      // Outflight
      for (int i = 0, n = 4; i < n; i++) {
	int noOut = 0;
	for (int j = 0, m = 20; j < m; j++) {
	  if (getOutFlight(j) == i) {
	    if (noOut == 0) {
	      out.println("\n/* Outflight day " + (i + 2) + " */");
	    }
	    for (int z = 1; z <= 8; z++) {
	      if (noOut > 0) {
		out.print(" + ");
	      }
	      out.print("p" + z + pack[j]);
	      noOut++;
	    }
	  }
	}
	if (priceMode == LINEAR_PRICES) {
	  out.println(" <= " + own[1][i] + " + b1d" + i + ";");
	} else {
	  out.println(" <= " + own[1][i] + ";");
	}
      }

      // Good Hotel
      for (int i = 0, n = 4; i < n; i++) {
	int noOut = 0;
	for (int j = 0, m = 20; j < m; j++) {
	  if (useHotel(j, i, true)) {
	    if (noOut == 0) {
	      out.println("\n/* Good Hotel day " + (i + 1) + " */");
	    }
	    for (int z = 1; z <= 8; z++) {
	      if (noOut > 0) {
		out.print(" + ");
	      }
	      out.print("p" + z + pack[j]);
	      noOut++;
	    }
	  }
	}
	if (priceMode == LINEAR_PRICES) {
	  out.println(" <= " + own[2][i] + " + b2d" + i + ";");
	} else {
	  out.println(" <= " + own[2][i] + ";");
	}
      }

      // Cheap Hotel
      for (int i = 0, n = 4; i < n; i++) {
	int noOut = 0;
	for (int j = 0, m = 20; j < m; j++) {
	  if (useHotel(j, i, false)) {
	    if (noOut == 0) {
	      out.println("\n/* Cheap Hotel day " + (i + 1) + " */");
	    }
	    for (int z = 1; z <= 8; z++) {
	      if (noOut > 0) {
		out.print(" + ");
	      }
	      out.print("p" + z + pack[j]);
	      noOut++;
	    }
	  }
	}
	if (priceMode == LINEAR_PRICES) {
	  out.println(" <= " + own[3][i] + " + b3d" + i + ";");
	} else {
	  out.println(" <= " + own[3][i] + ";");
	}
      }

      // Entertainment
      out.println("\n/* Entertainment tickets */");
      for (int i = 0, n = 12; i < n; i++) {
	for (int j = 1, m = 8; j <= m; j++) {
	  if (j > 1) out.print(" + ");
	  out.print("e" + j + ent[i]);
	}
	out.println(" <= " + own[4 + (i / 4)][i % 4] + ';');
      }

      //     for (int i = 0, n = own.length; i < n; i++) {
      //       for (int j = 0, m = 4; j < m; j++) {
      // 	int day = j;
      // 	if (i == 1) day += 2;
      // 	else day++;
      // 	out.println(ownStr[i] + day + " = " + own[i][j] + ";");
      //       }
      //     }

      out.println();
      out.println();
      out.print("int");
      for (int i = 1, n = 8; i <= n; i++) {
	for (int j = 1, m = 20; j <= m; j++) {
	  out.print(" p" + i + pack[j - 1]);
	}
      }
      for (int i = 1, n = 8; i <= n; i++) {
	for (int j = 0, m = 12; j < m; j++) {
	  out.print(" e" + i + ent[j]);
	}
      }
      for (int i = 0, n = 4; i < n; i++) {
	for (int j = 0, m = 4; j < m; j++) {
	  out.print(" b" + i + "d" + j);
	}
      }
      out.println(";");
      out.close();


      // Read input

      BufferedReader reader =
	new BufferedReader(new InputStreamReader(solver.getInputStream()));
      String line;
      while((line = reader.readLine()) != null) {
	// System.out.println(line);
	if (line.length() > 0) {
	  char c0 = line.charAt(0);
	  if (c0 == 'e') {
	    int index = line.indexOf('1', 8);
	    if (index > 0) {
	      int client = line.charAt(1) - '1';
	      int type = line.charAt(3) - '1';
	      int day = line.charAt(5) - '0';
	      alloc[client][3 + type] = day;
	    }
	  } else if (c0 == 'p') {
	    int index = line.indexOf('1', 8);
	    if (index > 0) {
	      int client = line.charAt(1) - '1';
	      int inday = line.charAt(3) - '0';
	      int outday = line.charAt(5) - '0';
	      int hotel = line.charAt(6) == 'g' ? 1 : 0;
	      alloc[client][0] = inday;
	      alloc[client][1] = outday;
	      alloc[client][2] = hotel;
// 	      System.out.println("Client: " + (client + 1) +
// 				 " " + inday + "-" + outday +
// 				 " " + hotel);
	    }
	  } else if (c0 == 'V') {
	    int index = line.indexOf(':');
	    if (index > 0) {
	      score = Integer.parseInt(line.substring(index + 1).trim());
	    }
	  }
	}
      }
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }
    return score;
  }


  private void generateAllocation(int[][] preferences,
				  int[][] alloc) {
    StringBuffer sb = new StringBuffer();
    int total = 0;
    for (int i = 0, n = alloc.length; i < n; i++) {
      int[] clientAlloc = alloc[i];
      int[] clientPref = preferences[i];
      if (clientAlloc[0] > 0) {
	int inf = clientAlloc[0];
     int outf = clientAlloc[1];
     int utility = 1000 - Math.abs(inf - 1 - (clientPref[0])) * 100 +
       - Math.abs(outf - 2 - (clientPref[1])) * 100;
     sb.append("Client ").append(i + 1).append(" stays ")
       .append(inf).append(" - ").append(outf);
     if (clientAlloc[2] > 0) {
       sb.append(" on good hotel  ");
       utility += clientPref[2];
     } else {
       sb.append(" on cheap hotel ");
     }
     if (clientAlloc[3] > 0) {
       sb.append(" E1 day ").append(clientAlloc[3]);
       utility += clientPref[3];
     } else {
       sb.append("         ");
     }
     if (clientAlloc[4] > 0) {
       sb.append(" E2 day ").append(clientAlloc[4]);
       utility += clientPref[4];
     } else {
       sb.append("         ");
     }
     if (clientAlloc[5] > 0) {
       sb.append(" E3 day ").append(clientAlloc[5]);
       utility += clientPref[5];
     } else {
       sb.append("         ");
     }
     sb.append(" utility = ").append(utility);
     total += utility;

      } else {
     sb.append("Client ").append(i + 1).append(" does not go, utility = 0");

      }
      sb.append('\n');
    }
    sb.append("Total utility = ").append(total).append('\n');

    System.out.println(sb.toString());
  }

  // -------------------------------------------------------------------
  //
  // -------------------------------------------------------------------


  public void setClientData(int prefs[][], int owns[][]) {
    for (int c = 0; c < 8; c++) {
      // Modify Flight data
      preferences[c][0] = prefs[c][0] - 1;
      preferences[c][1] = prefs[c][1] - 2;
      for (int i = 2; i < 6; i++)
	preferences[c][i] = prefs[c][i];
    }
    for (int day = 0; day < 4; day++) {
      own[0][day] = max15(owns[day][0]);
      own[1][day] = max15(owns[day + 1][1]);
      for (int j = 2; j < 7; j++)
	own[j][day] = max15(owns[day][j]);
    }
  }

  private int max15(int own) {
    return own < 15 ? (own < 0 ? 0 : own) : 15;
  }

  public void setPriceMode(int mode) {
    priceMode = mode;
  }

  // Linear prices - only price for flight and hotels
  public void setPrices(int[][] prices) {
    for (int i = 0, n = 4; i < n; i++) {
      for (int j = 0, m = 4; j < m; j++) {
	this.prices[i][j] = prices[i][j];
      }
    }
  }

  public int solve() {
    for (int i = 0, n = alloc.length; i < n; i++) {
      for (int j = 0, m = alloc[i].length; j < m; j++) {
	alloc[i][j] = 0;
      }
    }

    long time = System.currentTimeMillis();
    int score = performSolve();
    elapsedTime = System.currentTimeMillis() - time;
    //    generateAllocation(preferences, alloc);
    return score;
  }

  public int[][] getLatestAllocation() {
    return alloc;
  }

  public long getCalculationTime() {
    return elapsedTime;
  }


  public static void main(String[] args) {
    LPSolver solver = new LPSolver();
    solver.setPriceMode(LINEAR_PRICES);
    solver.setPrices(new int[][] {
      {300,300,300,300},
      {300,300,300,300},
      {150,150,150,150},
      {100,100,100,100}
    });
    System.out.println("Score: " + solver.solve() + " elapsed time: " +
		       solver.getCalculationTime());
    solver.generateAllocation(solver.preferences, solver.alloc);
  }


} // LPSolver
