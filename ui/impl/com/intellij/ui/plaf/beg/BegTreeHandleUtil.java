
package com.intellij.ui.plaf.beg;

import com.intellij.util.ui.UIUtil;

import java.awt.*;

public class BegTreeHandleUtil {
  public static void a(Graphics g, int i, int j, int k, int l) {
    g.translate(i, j);
    boolean flag = false;
    for(int i1 = 0; i1 < k; i1++){
      // beg: unknown start value for j1
      for(int j1 = 0; j1 < l; j1 += 2){
        UIUtil.drawLine(g, i1, j1, i1, j1);
      }
      flag = !flag;
    }
    g.translate(-i, -j);
  }

}
