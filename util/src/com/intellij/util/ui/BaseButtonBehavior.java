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

package com.intellij.util.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public abstract class BaseButtonBehavior {

  private JComponent myComponent;

  private boolean myHovered;
  private boolean myPressedByMouse;
  private boolean mySelected;


  public BaseButtonBehavior(JComponent component) {
    myComponent = component;
    myComponent.addMouseListener(new MyMouseListener());
  }

  public boolean isHovered() {
    return myHovered;
  }

  public void setHovered(boolean hovered) {
    myHovered = hovered;
    myComponent.repaint();
  }

  public boolean isPressedByMouse() {
    return myPressedByMouse;
  }

  public void setPressedByMouse(boolean pressedByMouse) {
    myPressedByMouse = pressedByMouse;
    myComponent.repaint();
  }

  public boolean isSelected() {
    return mySelected;
  }

  public void setSelected(boolean selected) {
    mySelected = selected;
  }

  public boolean isPressed() {
    return isSelected() || isPressedByMouse();
  }


  private class MyMouseListener extends MouseAdapter {
    public void mouseEntered(MouseEvent e) {
      setHovered(true);
      myComponent.repaint();
    }

    public void mouseExited(MouseEvent e) {
      setHovered(false);
      myComponent.repaint();
    }

    public void mousePressed(MouseEvent e) {
      if (!UIUtil.isActionClick(e)) {
        pass(e);
        return;
      }

      setPressedByMouse(true);
      myComponent.repaint();
    }

    public void mouseReleased(MouseEvent e) {
      if (!UIUtil.isActionClick(e)) {
        pass(e);
        return;
      }

      setPressedByMouse(false);

      Point point = e.getPoint();
      if (point.x < 0 || point.x > myComponent.getWidth()) return;
      if (point.y < 0 || point.y > myComponent.getHeight()) return;

      execute(e);
    }
  }

  protected abstract void execute(final MouseEvent e);

  protected void pass(MouseEvent e) {

  }

}
