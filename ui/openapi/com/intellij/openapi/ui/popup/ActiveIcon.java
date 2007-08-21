package com.intellij.openapi.ui.popup;

import javax.swing.*;
import java.awt.*;

public class ActiveIcon implements Icon {

  private boolean myActive = true;

  private Icon myRegular;
  private Icon myInactive;

  public ActiveIcon(Icon icon) {
    this(icon, icon);
  }

  public ActiveIcon(final Icon regular, final Icon inactive) {
    myRegular = regular;
    myInactive = inactive;
  }

  public Icon getRegular() {
    return myRegular;
  }

  public Icon getInactive() {
    return myInactive;
  }

  private Icon getIcon() {
    return myActive ? getRegular() : getInactive();
  }

  public void setActive(final boolean active) {
    myActive = active;
  }

  public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
    getIcon().paintIcon(c, g, x, y);
  }

  public int getIconWidth() {
    return getIcon().getIconWidth();
  }

  public int getIconHeight() {
    return getIcon().getIconHeight();
  }
}
