// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.designer.designSurface.feedbacks;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class AlphaFeedback extends JComponent {
  protected static final AlphaComposite myComposite1 = AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.3f);
  protected static final AlphaComposite myComposite2 = AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.6f);

  private final Color myColor;
  private final Color myBorderColor;

  public AlphaFeedback(Color color) {
    this(color, color);
  }

  public AlphaFeedback(Color color, Color borderColor) {
    myColor = color;
    myBorderColor = borderColor;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2d = (Graphics2D)g;

    g2d.setColor(myColor);
    g2d.setComposite(myComposite1);
    paintOther1(g2d);

    g2d.setColor(myBorderColor);
    g2d.setComposite(myComposite2);
    paintOther2(g2d);
  }

  protected void paintOther1(Graphics2D g2d) {
    g2d.fillRect(0, 0, getWidth(), getHeight());
  }

  protected void paintOther2(Graphics2D g2d) {
    g2d.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Utils
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public static void fillRect1(Graphics g, int x, int y, int width, int height, Color color) {
    fillRect(g, x, y, width, height, color, myComposite1);
  }

  public static void fillRect2(Graphics g, int x, int y, int width, int height, Color color) {
    fillRect(g, x, y, width, height, color, myComposite2);
  }

  public static void fillRect(Graphics g, int x, int y, int width, int height, Color color, Composite composite) {
    Graphics2D g2d = (Graphics2D)g;
    Composite oldComposite = g2d.getComposite();
    Color oldColor = g.getColor();

    g2d.setColor(color);
    g2d.setComposite(composite);

    g2d.fillRect(x, y, width, height);

    g2d.setComposite(oldComposite);
    g2d.setColor(oldColor);
  }
}