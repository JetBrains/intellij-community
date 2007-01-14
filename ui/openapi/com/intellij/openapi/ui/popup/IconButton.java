package com.intellij.openapi.ui.popup;

import javax.swing.*;

public class IconButton {

  private String myTooltip;

  private Icon myRegular;
  private Icon myHovered;
  private Icon myInactive;

  public IconButton(final String tooltip, final Icon regular, final Icon hovered, final Icon inactive) {
    myTooltip = tooltip;
    myRegular = regular;
    myHovered = hovered;
    myInactive = inactive;
  }


  public IconButton(final String tooltip, final Icon regular, final Icon hovered) {
    this(tooltip, regular, hovered, regular);
  }

  public IconButton(final String tooltip, final Icon regular) {
    this(tooltip, regular, regular, regular);
  }


  public Icon getRegular() {
    return myRegular;
  }

  public Icon getInactive() {
    return myInactive;
  }

  public Icon getHovered() {
    return myHovered;
  }

  public String getTooltip() {
    return myTooltip;
  }
}
