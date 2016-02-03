/*
 * @(#)HtmlUtils.java	Created date: 03-04-22
 * $Revision: 1.1 $, $Date: 2003/04/30 15:53:35 $
 *
 * Copyright (c) 2001 BotBox AB.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * BotBox AB. ("Confidential Information").  You shall not disclose
 * such Confidential Information and shall use it only in accordance
 * with the terms of the license agreement you entered into with
 * BotBox AB.
 */
package com.botbox.html;

import java.awt.Color;



/**
 * Provides simpler generation of HTML.
 *
 * @author  Joakim Eriksson	(joakim.eriksson@botbox.com)
 * @author  Niclas Finne	(niclas.finne@botbox.com)
 * @author  Sverker Janson	(sverker.janson@botbox.com)
 * @version $Revision: 1.1 $, $Date: 2003/04/30 15:53:35 $
 */
public class HtmlUtils {

  private static final String loColor = "#40ff40";
  private static final String miColor = "yellow";
  private static final String hiColor = "red";

  // Prevent instances of this class
  private HtmlUtils() {
  }

  public static void progress(HtmlWriter out, int width, int height,
			      int lo, int mi, int hi) {
    out.tag("table").attr("border=0 cellpadding=0 cellspacing=1 bgcolor=black")
      .attr("width", width).attr("height", 8)
      .text("<tr>");
    if (lo > 0) {
      out.text("<td bgcolor='").text(loColor).text("' width='")
	.text(lo).text("%'></td>").newLine();
    }
    if (mi > 0) {
      out.text("<td bgcolor='").text(miColor).text("' width='")
	.text(mi).text("%'></td>").newLine();
    }
    if (hi > 0) {
      out.text("<td bgcolor='").text(hiColor).text("' width='")
	.text(hi).text("%'></td>").newLine();
    }
    out.text("</tr>");
    out.tagEnd("table");
  }

} // HtmlUtils
