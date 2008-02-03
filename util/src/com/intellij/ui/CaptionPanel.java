/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author max
 */
public class CaptionPanel extends JPanel {
  private final static Color CNT_COLOR = new Color(240, 240, 240);
  private final static Color BND_COLOR = new Color(240, 240, 240);

  public final static Color CNT_ACTIVE_COLOR = new Color(0xcacaca);
  public final static Color BND_ACTIVE_COLOR = new Color(0xefefef);

  private boolean myActive = false;
  private ActiveComponent myButtonComponent;

  public CaptionPanel() {
    setLayout(new BorderLayout());
    setBorder(new EmptyBorder(0, 4, 0, 4));
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    final Graphics2D g2d = (Graphics2D) g;

    if (myActive) {
      g.setColor(Color.white);
      g.drawLine(0, 0, getWidth(), 0);
      g.setColor(Color.gray);
      g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
      g2d.setPaint(new GradientPaint(0, 0, BND_ACTIVE_COLOR, 0, getHeight(), CNT_ACTIVE_COLOR));
    }
    else {
      g.setColor(Color.white);
      g.drawLine(0, 0, getWidth(), 0);
      g.setColor(Color.lightGray);
      g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
      g2d.setPaint(new GradientPaint(0, 0, BND_COLOR, 0, getHeight(), CNT_COLOR));
    }

    g2d.fillRect(0, 1, getWidth(), getHeight() - 2);
  }

  public void setActive(final boolean active) {
    myActive = active;
    if (myButtonComponent != null) {
      myButtonComponent.setActive(active);
    }
    repaint();
  }

  public void setButtonComponent(ActiveComponent component) {
    if (myButtonComponent != null) {
      remove(myButtonComponent.getComponent());
    }
    add(component.getComponent(), BorderLayout.EAST);
    myButtonComponent = component;
  }

  public boolean isWithinPanel(MouseEvent e) {
    final Point p = SwingUtilities.convertPoint(e.getComponent(), e.getX(), e.getY(), this);
    final Component c = findComponentAt(p);
    return c != null && c != myButtonComponent;
  }

  public static Color getBorderColor(boolean isActive) {
    return isActive ? Color.gray : Color.lightGray;
  }
}
