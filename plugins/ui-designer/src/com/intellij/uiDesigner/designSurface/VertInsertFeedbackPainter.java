/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.uiDesigner.designSurface;

import com.intellij.util.ui.PlatformColors;

import java.awt.*;

/**
 * @author yole
 */
public class VertInsertFeedbackPainter implements FeedbackPainter {
  public static VertInsertFeedbackPainter INSTANCE = new VertInsertFeedbackPainter();

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
