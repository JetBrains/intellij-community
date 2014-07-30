package org.jetbrains.plugins.ipnb.editor.panels;

import javax.swing.*;
import java.awt.*;

public abstract class IpnbPanel<T extends JComponent> extends JPanel {
  private boolean myEditing;
  protected T myViewPanel;

  public IpnbPanel() {
    super(new CardLayout());
  }

  public boolean contains(int y) {
    return y>= getTop() && y<=getBottom();
  }

  public int getTop() {
    return getY();
  }

  public int getBottom() {
    return getTop() + getHeight();
  }

  public boolean isEditing() {
    return myEditing;
  }

  public void setEditing(boolean editing) {
    myEditing = editing;
  }

  protected abstract T createViewPanel();

  public void switchToEditing() {
    setEditing(true);
    getParent().repaint();
  }

  public void runCell() {
    setEditing(false);
  }

  public void updateCellView() { // TODO: make abstract
  }
}
