package org.jetbrains.plugins.ipnb.editor.panels;

import javax.swing.*;

/**
 * @author traff
 */
public class RenderedCell {
  private int myTop;
  private int myBottom;
  private JComponent myComponent;

  public RenderedCell(int top, int bottom) {
    myTop = top;
    myBottom = bottom;
  }

  public RenderedCell(JComponent component) {
    myComponent = component;
  }

  public boolean contains(int y) {
    return y>= getTop() && y<=getBottom();
  }

  public int getTop() {
    return myComponent.getY();
  }

  public int getBottom() {
    return getTop() + getHeight();
  }

  public int getHeight() {
    return myComponent.getHeight();
  }

  public static RenderedCell create(JComponent component) {
    return new RenderedCell(component);
  }
}
