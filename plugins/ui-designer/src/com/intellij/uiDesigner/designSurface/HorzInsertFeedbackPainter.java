// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.designSurface;

import com.intellij.util.ui.PlatformColors;

import java.awt.*;

/**
 * @author yole
 */
class HorzInsertFeedbackPainter implements FeedbackPainter {
  public static HorzInsertFeedbackPainter INSTANCE = new HorzInsertFeedbackPainter();

  @Override
  public void paintFeedback(Graphics2D g2d, Rectangle rc) {
    g2d.setColor(PlatformColors.BLUE);
    g2d.setStroke(new BasicStroke(1.5f));
    int midY = (int)rc.getCenterY();
    int right = rc.x + rc.width - 1;
    int bottom = rc.y + rc.height - 1;
    g2d.drawLine(rc.x, rc.y, GridInsertLocation.INSERT_ARROW_SIZE, midY);
    g2d.drawLine(rc.x, bottom, GridInsertLocation.INSERT_ARROW_SIZE, midY);
    g2d.drawLine(GridInsertLocation.INSERT_ARROW_SIZE, midY,
                 right - GridInsertLocation.INSERT_ARROW_SIZE, midY);
    g2d.drawLine(right, rc.y,
                 rc.x+rc.width-GridInsertLocation.INSERT_ARROW_SIZE, midY);
    g2d.drawLine(right, bottom,
                 right-GridInsertLocation.INSERT_ARROW_SIZE, midY);
  }
}
