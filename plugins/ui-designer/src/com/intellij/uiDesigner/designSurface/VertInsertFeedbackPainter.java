// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.designSurface;

import com.intellij.util.ui.PlatformColors;

import java.awt.*;

/**
 * @author yole
 */
public class VertInsertFeedbackPainter implements FeedbackPainter {
  public static VertInsertFeedbackPainter INSTANCE = new VertInsertFeedbackPainter();

  @Override
  public void paintFeedback(Graphics2D g2d, Rectangle rc) {
    g2d.setColor(PlatformColors.BLUE);
    g2d.setStroke(new BasicStroke(1.5f));
    int right = rc.x + rc.width - 1;
    int bottom = rc.y + rc.height - 1;
    int midX = (int) rc.getCenterX();
    g2d.drawLine(rc.x, rc.y, midX, rc.y+GridInsertLocation.INSERT_ARROW_SIZE);
    g2d.drawLine(right, rc.y, midX, rc.y+GridInsertLocation.INSERT_ARROW_SIZE);
    g2d.drawLine(midX, rc.y+GridInsertLocation.INSERT_ARROW_SIZE,
                 midX, bottom-GridInsertLocation.INSERT_ARROW_SIZE);
    g2d.drawLine(rc.x, bottom, midX, bottom-GridInsertLocation.INSERT_ARROW_SIZE);
    g2d.drawLine(right, bottom, midX, bottom-GridInsertLocation.INSERT_ARROW_SIZE);
  }
}
