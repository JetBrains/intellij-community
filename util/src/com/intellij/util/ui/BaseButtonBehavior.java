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
      if (e.getButton() != MouseEvent.BUTTON1) return;

      setPressedByMouse(true);
      myComponent.repaint();
    }

    public void mouseReleased(MouseEvent e) {
      if (e.getButton() != MouseEvent.BUTTON1) return;

      setPressedByMouse(false);

      Point point = e.getPoint();
      if (point.x < 0 || point.x > myComponent.getWidth()) return;
      if (point.y < 0 || point.y > myComponent.getHeight()) return;

      execute(e);
    }
  }

  protected abstract void execute(final MouseEvent e);

}
