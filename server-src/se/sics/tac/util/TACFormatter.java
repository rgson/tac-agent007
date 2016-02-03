/**
 * SICS TAC Classic Server
 * http://www.sics.se/tac/    tac-dev@sics.se
 *
 * Copyright (c) 2001-2004 SICS AB. All rights reserved.
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
 * TACFormatter
 *
 * Author  : Joakim Eriksson, Niclas Finne, Sverker Janson
 * Created : Thu Apr 29 13:20:58 2004
 * Updated : $Date: 2004/06/14 09:23:14 $
 *           $Revision: 1.4 $
 */
package se.sics.tac.util;

/**
 */
public class TACFormatter {

  private TACFormatter() {
  }


  // -------------------------------------------------------------------
  // Time formatting utitities
  // -------------------------------------------------------------------

  public static String formatServerTime(long td) {
    return formatServerTime(new StringBuffer(), td).toString();
  }

  public static StringBuffer formatServerTime(StringBuffer sb, long td) {
    td /= 1000;
    long sek = td % 60;
    long minutes = (td / 60) % 60;
    long hours = (td / 3600) % 24;
    if (hours < 10) sb.append('0');
    sb.append(hours).append(':');
    if (minutes < 10) sb.append('0');
    sb.append(minutes).append(':');
    if (sek < 10) sb.append('0');
    sb.append(sek);
    return sb;
  }

  public static String formatDelayAsHtml(int delay) {
    return formatDelayAsHtml(new StringBuffer(), delay).toString();
  }

  public static StringBuffer formatDelayAsHtml(StringBuffer sb,
					       int milliseconds) {
    int length = milliseconds / 1000;
    int minutes = length / 60;
    int seconds = length % 60;
    sb.append(minutes).append("&nbsp;min");
    if (seconds > 0) {
      sb.append("&nbsp;").append(seconds).append("&nbsp;sec");
    }
    return sb;
  }


  // -------------------------------------------------------------------
  // Float value formatting utilities
  // -------------------------------------------------------------------

  public static String toString(double dValue) {
    long iValue = (long) (dValue * 100 + 0.5);
    long h = iValue / 100L;
    if (iValue < 0) {
      iValue = -iValue;
    }
    long dec = iValue % 100L;
    return "" + h + '.' + (dec < 10 ? "0" : "") + dec;
  }

  public static String toString4(double dValue) {
    long iValue = (long) (dValue * 10000 + 0.5);
    long h = iValue / 10000L;
    if (iValue < 0) {
      iValue = -iValue;
    }
    long dec = iValue % 10000L;
    if (dec < 10) {
      return "" + h + ".000" + dec;
    } else if (dec < 100) {
      return "" + h + ".00" + dec;
    } else if (dec < 1000) {
      return "" + h + ".0" + dec;
    } else {
      return "" + h + '.' + dec;
    }
  }


  // -------------------------------------------------------------------
  // String formatting utilities
  // -------------------------------------------------------------------

  /**
   * Formats a string according to the format string and the variables
   * (names of %x:es) and the data (replaces a %x).<p>
   *
   * Example:<br><pre>
   * format("The file %f contained %p\\% data", "fp", new String[] { "test.txt", "20" })
   * returns "The file test.txt contained 20% data"
   * </pre>
   *
   * @param format The string to format
   * @param variables The variables to replace. Each character
   * corresponds to one variable
   * @param data The values to substitute the variables with
   * @return the formatted string
   */
  public static String format(String format, String variables, String[] data) {
    if (format == null) {
      return null;
    }

    int flen = format.length();
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < flen; i++) {
      char c = format.charAt(i);
      if (c == '\\' && ((i + 1) < flen)) {
	sb.append(format.charAt(i + 1));
	i++;

      } else if (c == '%' && ((i + 1) < flen)) {
	char c2 = format.charAt(i + 1);
	int index = variables.indexOf(c2);
	if (index >= 0) {
	  sb.append(data[index]);
	  i++;
	} else {
	  sb.append('%');
	}
      } else {
	sb.append(c);
      }
    }
    return sb.toString();
  }

} // TACFormatter
