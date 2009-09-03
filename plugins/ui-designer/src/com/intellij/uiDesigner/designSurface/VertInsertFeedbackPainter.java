/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.designSurface;

import java.awt.*;

/**
 * @author yole
 */
public class VertInsertFeedbackPainter implements FeedbackPainter {
  public static VertInsertFeedbackPainter INSTANCE = new VertInsertFeedbackPainter();

  public void paintFeedback(Graphics2D g2d, Rectangle rc) {
    g2d.setColor(Color.BLUE);
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
