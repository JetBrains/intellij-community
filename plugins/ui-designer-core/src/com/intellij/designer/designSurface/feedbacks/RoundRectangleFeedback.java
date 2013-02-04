/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.designer.designSurface.feedbacks;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class RoundRectangleFeedback extends RectangleFeedback {
  private static final BasicStroke STROKE = new BasicStroke(2);

  private final int myRadius;

  public RoundRectangleFeedback(Color color, int line, int radius) {
    super(color, line);
    myRadius = radius;
  }

  @Override
  protected void paintFeedback(Graphics g) {
    Graphics2D g2d = (Graphics2D)g;

    Stroke oldStroke = g2d.getStroke();
    g2d.setStroke(STROKE);
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    Dimension size = getSize();
    g.drawRoundRect(0, 0, size.width - 1, size.height - 1, myRadius, myRadius);

    g2d.setStroke(oldStroke);
  }
}