package com.intellij.util.ui;

import javax.swing.*;
import java.awt.*;

public class EmptyIcon implements Icon {
  private final int myWidth;
  private final int myHeight;

  public EmptyIcon(int size) {
    this(size, size);
  }

  public EmptyIcon(int width, int height) {
    myWidth = width;
    myHeight = height;
  }

  public int getIconWidth() {
    return myWidth;
  }

  public int getIconHeight() {
    return myHeight;
  }

  public void paintIcon(Component component, Graphics g, int i, int j) {
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EmptyIcon)) return false;

    final EmptyIcon icon = (EmptyIcon)o;

    if (myHeight != icon.myHeight) return false;
    if (myWidth != icon.myWidth) return false;

    return true;
  }

  public int hashCode() {
    return myWidth + myHeight;
  }
}
