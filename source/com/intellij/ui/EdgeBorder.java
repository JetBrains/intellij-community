
package com.intellij.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.Border;

public class EdgeBorder implements Border {
  public static final int EDGE_RIGHT = 61440;
  public static final int EDGE_BOTTOM = 3840;
  public static final int EDGE_LEFT = 240;
  public static final int EDGE_TOP = 15;
  public static final int EDGE_ALL = 65535;
  private Insets myInsets;
  private int b;

  public EdgeBorder(int i) {
    myInsets = new Insets(2, 2, 2, 2);
    b = i;
    recalcInsets();
  }

  public boolean isBorderOpaque() {
    return true;
  }

  public Insets getBorderInsets(Component component) {
    return myInsets;
  }

  public void paintBorder(Component component, Graphics g, int x, int y, int width, int height) {
    java.awt.Color color = UIManager.getColor("Separator.shadow");
    java.awt.Color color1 = UIManager.getColor("Separator.highlight");
    java.awt.Color color2 = g.getColor();
    if ((b & 0xf) != 0){
      g.setColor(color);
      g.drawLine(x, y, (x + width) - 1, y);
      g.setColor(color1);
      g.drawLine(x, y + 1, (x + width) - 1, y + 1);
    }
    if ((b & 0xf0) != 0){
      g.setColor(color);
      g.drawLine(x, y, x, (y + height) - 1);
      g.setColor(color1);
      g.drawLine(x + 1, y, x + 1, (y + height) - 1);
    }
    if ((b & 0xf00) != 0){
      g.setColor(color);
      g.drawLine(x, (y + height) - 2, (x + width) - 1, (y + height) - 2);
      g.setColor(color1);
      g.drawLine(x, (y + height) - 1, (x + width) - 1, (y + height) - 1);
    }
    if ((b & 0xf000) != 0){
      g.setColor(color);
      g.drawLine((x + width) - 2, y, (x + width) - 2, (y + height) - 1);
      g.setColor(color1);
      g.drawLine((x + width) - 1, y, (x + width) - 1, (y + height) - 1);
    }
    g.setColor(color2);
  }

  protected void recalcInsets() {
    myInsets.top = (b & 0xf) == 0 ? 0 : 2;
    myInsets.left = (b & 0xf0) == 0 ? 0 : 2;
    myInsets.bottom = (b & 0xf00) == 0 ? 0 : 2;
    myInsets.right = (b & 0xf000) == 0 ? 0 : 2;
  }
}
