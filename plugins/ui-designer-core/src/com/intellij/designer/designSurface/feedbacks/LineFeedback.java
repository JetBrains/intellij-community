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
public class LineFeedback extends JComponent {
  protected final Color myColor;
  private final int myLine;
  protected boolean myHorizontal;

  public LineFeedback(Color color, int line, boolean horizontal) {
    myColor = color;
    myLine = line;
    myHorizontal = horizontal;
  }

  public void size(int width, int height) {
    if (myHorizontal) {
      setSize(width, myLine);
    }
    else {
      setSize(myLine, height);
    }
  }

  public void horizontal(int x, int y, int width) {
    myHorizontal = true;
    setBounds(x, y, width, myLine);
    setVisible(true);
  }

  public void vertical(int x, int y, int height) {
    myHorizontal = false;
    setBounds(x, y, myLine, height);
    setVisible(true);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    g.setColor(myColor);
    paintLines(g);
  }

  protected void paintLines(Graphics g) {
    Dimension size = getSize();
    if (myHorizontal) {
      paintHorizontal(g, size);
    }
    else {
      paintVertical(g, size);
    }
  }

  protected void paintHorizontal(Graphics g, Dimension size) {
    for (int i = 0; i < myLine; i++) {
      g.drawLine(0, i, size.width, i);
    }
  }

  protected void paintVertical(Graphics g, Dimension size) {
    for (int i = 0; i < myLine; i++) {
      g.drawLine(i, 0, i, size.height);
    }
  }
}