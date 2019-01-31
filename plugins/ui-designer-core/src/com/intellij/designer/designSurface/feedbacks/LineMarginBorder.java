// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.designer.designSurface.feedbacks;

import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class LineMarginBorder extends EmptyBorder {
  private final Color myColor;

  public LineMarginBorder(int top, int left, int bottom, int right, Color color) {
    super(top, left, bottom, right);
    myColor = color;
  }

  public LineMarginBorder(int top, int left, int bottom, int right) {
    this(top, left, bottom, right, Color.darkGray);
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Color oldColor = g.getColor();
    g.setColor(myColor);
    g.drawRect(x, y, width - 1, height - 1);
    g.setColor(oldColor);
  }
}