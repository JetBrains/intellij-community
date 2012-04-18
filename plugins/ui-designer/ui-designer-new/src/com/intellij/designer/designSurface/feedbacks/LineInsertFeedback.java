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
public class LineInsertFeedback extends LineFeedback {
  private static final int SIZE = 2;
  private static final int FULL_SIZE = 6;

  public LineInsertFeedback(Color color, boolean horizontal) {
    super(color, FULL_SIZE, horizontal);
  }

  @Override
  protected void paintHorizontal(Graphics g, Dimension size) {
    for (int i = 0; i < SIZE; i++) {
      g.drawLine(0, i + SIZE, size.width, i + SIZE);
      g.drawLine(i, 0, i, FULL_SIZE);
      g.drawLine(size.width - i - 1, 0, size.width - i - 1, FULL_SIZE);
    }
  }

  @Override
  protected void paintVertical(Graphics g, Dimension size) {
    for (int i = 0; i < SIZE; i++) {
      g.drawLine(i + SIZE, 0, i + SIZE, size.height);
      g.drawLine(0, i, FULL_SIZE, i);
      g.drawLine(0, size.height - i - 1, FULL_SIZE, size.height - i - 1);
    }
  }
}