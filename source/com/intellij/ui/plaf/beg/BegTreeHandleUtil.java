
package com.intellij.ui.plaf.beg;

import java.awt.*;

public class BegTreeHandleUtil {
  public static void a(Graphics g, int i, int j, int k, int l) {
    g.translate(i, j);
    boolean flag = false;
    for(int i1 = 0; i1 < k; i1++){
      // beg: unknown start value for j1
      for(int j1 = 0; j1 < l; j1 += 2){
        g.drawLine(i1, j1, i1, j1);
      }
      flag = !flag;
    }
    g.translate(-i, -j);
  }

  /**
   * @param g
   * @param x top left X coordinate.
   * @param y top left Y coordinate.
   * @param x1 right bottom X coordinate.
   * @param y1 right bottom Y coordinate.
   */
  public static void drawDottedRectangle(Graphics g, int x, int y, int x1, int y1) {
    int i1;
    for(i1 = x; i1 <= x1; i1 += 2){
      g.drawLine(i1, y, i1, y);
    }

    for(i1 = i1 != x1 + 1 ? y + 2 : y + 1; i1 <= y1; i1 += 2){
      g.drawLine(x1, i1, x1, i1);
    }

    for(i1 = i1 != y1 + 1 ? x1 - 2 : x1 - 1; i1 >= x; i1 -= 2){
      g.drawLine(i1, y1, i1, y1);
    }

    for(i1 = i1 != x - 1 ? y1 - 2 : y1 - 1; i1 >= y; i1 -= 2){
      g.drawLine(x, i1, x, i1);
    }
  }
}
