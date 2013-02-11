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

import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class LineMarginBorder extends EmptyBorder {
  private final Color myColor;

  public LineMarginBorder(int top, int left, int bottom, int right, Color color) {
    super(top, left, bottom, right);
    myColor = color;
  }

  public LineMarginBorder(int top, int left, int bottom, int right) {
    this(top, left, bottom, right, Color.darkGray);
  }

  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Color oldColor = g.getColor();
    g.setColor(myColor);
    g.drawRect(x, y, width - 1, height - 1);
    g.setColor(oldColor);
  }
}