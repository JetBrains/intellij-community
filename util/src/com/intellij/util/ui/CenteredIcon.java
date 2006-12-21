package com.intellij.util.ui;

import javax.swing.*;
import java.awt.*;

public class CenteredIcon implements Icon {

  private Icon myIcon;

  private int myWidth;
  private int myHight;

  public CenteredIcon(Icon icon) {
    this(icon, icon.getIconWidth(), icon.getIconHeight());
  }

  public CenteredIcon(Icon icon, int width, int hight) {
    myIcon = icon;
    myWidth = width;
    myHight = hight;
  }

  public void paintIcon(Component c, Graphics g, int x, int y) {
    final Dimension size = c.getSize();

    int actualX = size.width / 2 - myIcon.getIconWidth() /2;
    int actualY = size.height / 2 - myIcon.getIconHeight() /2;

    myIcon.paintIcon(c, g, x + actualX, y + actualY);
  }

  public int getIconWidth() {
    return myWidth;
  }

  public int getIconHeight() {
    return myHight;
  }
}