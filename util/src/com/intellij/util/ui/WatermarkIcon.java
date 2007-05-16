package com.intellij.util.ui;


import javax.swing.*;
import java.awt.*;

public class WatermarkIcon implements Icon {

  private Icon myIcon;
  private float myAlpha;

  public WatermarkIcon(Icon icon, float alpha) {
    myIcon = icon;
    myAlpha = alpha;
  }

  public void paintIcon(Component c, Graphics g, int x, int y) {
    ((Graphics2D)g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, myAlpha));
    myIcon.paintIcon(c, g, x, y);
  }

  public int getIconWidth() {
    return myIcon.getIconWidth();
  }

  public int getIconHeight() {
    return myIcon.getIconHeight();
  }

}