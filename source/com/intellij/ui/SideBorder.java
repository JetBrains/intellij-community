package com.intellij.ui;

import javax.swing.border.LineBorder;
import java.awt.*;

public class SideBorder extends LineBorder {
  public static final int LEFT = 0x01;
  public static final int TOP = 0x02;
  public static final int RIGHT = 0x04;
  public static final int BOTTOM = 0x08;
  public static final int ALL = LEFT | TOP | RIGHT | BOTTOM;
  private int mySideMask;

  public SideBorder(Color color, int mask) {
    super(color, 1);
    mySideMask = mask;
  }

  public Insets getBorderInsets(Component component) {
    return new Insets(
      (mySideMask & TOP) != 0 ? getThickness() : 0,
      (mySideMask & LEFT) != 0 ? getThickness() : 0,
      (mySideMask & BOTTOM) != 0 ? getThickness() : 0,
      (mySideMask & RIGHT) != 0 ? getThickness() : 0
    );
  }

  public Insets getBorderInsets(Component component, Insets insets) {
    insets.top = (mySideMask & TOP) != 0 ? getThickness() : 0;
    insets.left = (mySideMask & LEFT) != 0 ? getThickness() : 0;
    insets.bottom = (mySideMask & BOTTOM) != 0 ? getThickness() : 0;
    insets.right = (mySideMask & RIGHT) != 0 ? getThickness() : 0;
    return insets;
  }

  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Color oldColor = g.getColor();
    int i;

    g.setColor(getLineColor());
    for(i = 0; i < getThickness(); i++){
      if ((mySideMask & LEFT) != 0){
        g.drawLine(x + i, y + i, x + i, height - i - i - 1);
      }
      if ((mySideMask & TOP) != 0){
        g.drawLine(x + i, y + i, width - i - i - 1, y + i);
      }
      if ((mySideMask & RIGHT) != 0){
        g.drawLine(width - i - i - 1, y + i, width - i - i - 1, height - i - i - 1);
      }
      if ((mySideMask & BOTTOM) != 0){
        g.drawLine(x + i, height - i - i - 1, width - i - i - 1, height - i - i - 1);
      }
    }
    g.setColor(oldColor);
  }

  public void setLineColor(Color color) {
    lineColor = color;
  }
}