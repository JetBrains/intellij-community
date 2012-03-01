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
public class AlphaComponent extends JComponent {
  private static final AlphaComposite myComposite1 = AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.3f);
  private static final AlphaComposite myComposite2 = AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.6f);

  private final Color myColor;
  private final Color myBorderColor;

  public AlphaComponent(Color color) {
    this(color, color);
  }

  public AlphaComponent(Color color, Color borderColor) {
    myColor = color;
    myBorderColor = borderColor;
  }

  protected void paintComponent(Graphics g) {
    Graphics2D g2d = (Graphics2D)g;
    super.paintComponent(g);
    Composite oldComposite = g2d.getComposite();
    Color oldColor = g2d.getColor();

    g2d.setColor(myColor);
    g2d.setComposite(myComposite1);
    g2d.fillRect(0, 0, getWidth(), getHeight());

    g2d.setColor(myBorderColor);
    g2d.setComposite(myComposite2);
    g2d.drawRect(0, 0, getWidth() - 1, getHeight() - 1);

    g2d.setColor(oldColor);
    g2d.setComposite(oldComposite);
  }
}