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
  private final int myRadius;

  public RoundRectangleFeedback(Color color, int line, int radius) {
    super(color, line);
    myRadius = radius;
  }

  @Override
  protected void paintFeedback(Graphics g) {
    Dimension size = getSize();
    for (int i = 0; i < myLine; i++) {
      g.drawRoundRect(i, i, size.width - i - i - 1, size.height - i - i - 1, myRadius, myRadius);
    }
  }
}