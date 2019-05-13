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

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class InsertFeedback extends LineInsertFeedback {
  protected boolean myCross;

  public InsertFeedback(Color color) {
    super(color, false);
  }

  @Override
  protected void paintLines(Graphics g) {
    if (myCross) {
      int size = getWidth();
      int size2 = (size - 3) / 2;
      AlphaFeedback.fillRect2(g, 0, size2, size, 3, myColor);
      AlphaFeedback.fillRect2(g, size2, 0, 3, size, myColor);
      g.drawLine(0, size2 + 1, size, size2 + 1);
      g.drawLine(size2 + 1, 0, size2 + 1, size);
    }
    else {
      super.paintLines(g);
    }
  }

  public void cross(int xCenter, int yCenter, int size) {
    myCross = true;
    setBounds(xCenter - size - 1, yCenter - size - 1, 2 * size + 3, 2 * size + 3);
    setVisible(true);
  }

  @Override
  public void horizontal(int x, int y, int width) {
    myCross = false;
    super.horizontal(x, y, width);
  }

  @Override
  public void vertical(int x, int y, int height) {
    myCross = false;
    super.vertical(x, y, height);
  }
}