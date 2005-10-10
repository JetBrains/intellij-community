/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.popup.list;

import javax.swing.*;
import java.awt.*;

/**
 * @author kir
 */
public class ErrorLabel extends JLabel {

  private boolean myUnderline;

  private Color myForeground;
  private String myTooltip;

  public ErrorLabel() {
    this(null, null);
  }

  public ErrorLabel(String text) {
    this(text, null);
  }

  public ErrorLabel(String text, Icon icon) {
    super(text, icon, JLabel.LEFT);
    setOpaque(false);
  }

  public void setErrorText(String text, Color color) {
    boolean newUnderline = text != null;
    myForeground = color;
    if (newUnderline) {
      updateLabelView(newUnderline, text);
    }
    else if (myUnderline) {
      updateLabelView(newUnderline, myTooltip);
    }
  }

  public void setToolTipText(String text) {
    if (myUnderline) {
      myTooltip = text;
    }
    else {
      super.setToolTipText(text);
    }
  }



  private void updateLabelView(boolean newUnderline, String tooltip) {
    super.setToolTipText(tooltip);
    myUnderline = newUnderline;
    repaint();
  }

  protected void paintComponent(Graphics g) {

    super.paintComponent(g);

    if (getText() != null & myUnderline) {
      g.setColor(myForeground);
      int x = 0;

      if (getIcon() != null) {
        x = getIcon().getIconWidth() + getIconTextGap();
      }

      if (getHorizontalAlignment() == CENTER) {
        int w = g.getFontMetrics().stringWidth(getText());
        x += (getWidth() - x - w) >> 1;
      }

      drawWave(this, g, x, getText());
    }
  }

  public static void drawWave(Component c, Graphics g, int x, String text) {
    int y = getTextBaseLine(c);

    y += 2;

    int width = c.getFontMetrics(c.getFont()).stringWidth(text);
    int nLines = (width >> 1) + 1;

    int xCurr = x;
    int yBottom = y + 1;
    int []xx = new int[nLines + 1];
    int []yy = new int[nLines + 1];
    int line = 0;
    for (; line < nLines; line += 2) {
      xx[line] = xCurr;
      yy[line] = yBottom;

      xx[line + 1] = xCurr + 2;
      yy[line + 1] = yBottom - 2;
      xCurr += 4;
    }

    g.drawPolyline(xx, yy, line);
  }

  private static int getTextBaseLine(Component c) {
    FontMetrics fm = c.getFontMetrics(c.getFont());
    return (c.getHeight() >> 1) + ((fm.getHeight() >> 1) - fm.getDescent());
  }
}
