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
package com.intellij.designer.designSurface.feedbacks;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class RectangleFeedback extends JComponent {
  private final Color myColor;
  protected final int myLine;

  public RectangleFeedback(Color color, int line) {
    myColor = color;
    myLine = line;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    g.setColor(myColor);

    paintFeedback(g);
  }

  protected void paintFeedback(Graphics g) {
    Dimension size = getSize();
    for (int i = 0; i < myLine; i++) {
      g.drawRect(i, i, size.width - i - i - 1, size.height - i - i - 1);
    }
  }
}