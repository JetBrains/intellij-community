/*
 * Class MultiLineTooltipUI
 * @author Jeka
 */
package com.intellij.ui;

import com.intellij.openapi.util.text.LineTokenizer;

import javax.swing.*;
import javax.swing.plaf.metal.MetalToolTipUI;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MultiLineTooltipUI extends MetalToolTipUI {
  private List myLines = new ArrayList();

  public void paint(Graphics g, JComponent c) {
    FontMetrics metrics = g.getFontMetrics(g.getFont());
    Dimension size = c.getSize();
    g.setColor(c.getBackground());
    g.fillRect(0, 0, size.width, size.height);
    g.setColor(c.getForeground());
    int idx = 0;
    for (Iterator it = myLines.iterator(); it.hasNext(); idx++) {
      String line = (String)it.next();
      g.drawString(line, 3, (metrics.getHeight()) * (idx + 1));
    }
  }

  public Dimension getPreferredSize(JComponent c) {
    FontMetrics metrics = c.getFontMetrics(c.getFont());
    String tipText = ((JToolTip)c).getTipText();
    if (tipText == null) {
      tipText = "";
    }
    int maxWidth = 0;
    myLines.clear();

    final String[] lines = LineTokenizer.tokenize(tipText.toCharArray(), false);
    for (String line : lines) {
      myLines.add(line);
      int width = SwingUtilities.computeStringWidth(metrics, line);
      if (width > maxWidth) {
        maxWidth = width;
      }
    }

    int height = metrics.getHeight() * ((lines.length < 1)? 1 : lines.length);
    return new Dimension(maxWidth + 6, height + 4);
  }
}
