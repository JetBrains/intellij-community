package com.intellij.ui;

import javax.swing.*;
import java.awt.*;

public class StrikeoutLabel extends JLabel{
  private boolean myStrikeout = false;

  public StrikeoutLabel(String text, int horizontalAlignment) {
    super(text, horizontalAlignment);
  }

  public void setStrikeout(boolean strikeout) {
    myStrikeout = strikeout;
  }

  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (myStrikeout){
      Dimension size = getSize();
      Dimension prefSize = getPreferredSize();
      int width = Math.min(size.width, prefSize.width);
      int iconWidth = 0;
      Icon icon = StrikeoutLabel.this.getIcon();
      if (icon != null){
        iconWidth = icon.getIconWidth();
        iconWidth += getIconTextGap();
      }
      g.setColor(this.getForeground());
      g.drawLine(iconWidth, size.height / 2, width, size.height / 2);
    }
  }
}