package com.intellij.openapi.ui.popup;

import javax.swing.*;

public class IconButton extends ActiveIcon {

  private String myTooltip;

  private Icon myHovered;

  public IconButton(final String tooltip, final Icon regular, final Icon hovered, final Icon inactive) {
    super(regular, inactive);
    myTooltip = tooltip;
    myHovered = hovered;
  }

  public IconButton(final String tooltip, final Icon regular, final Icon hovered) {
    this(tooltip, regular, hovered, regular);
  }

  public IconButton(final String tooltip, final Icon regular) {
    this(tooltip, regular, regular, regular);
  }


  public Icon getHovered() {
    return myHovered;
  }

  public String getTooltip() {
    return myTooltip;
  }
}
