package com.intellij.openapi.ui.popup;

import javax.swing.*;

public class ActiveIcon {

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
}
