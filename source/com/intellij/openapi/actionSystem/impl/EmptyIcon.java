
package com.intellij.openapi.actionSystem.impl;

import javax.swing.*;
import java.awt.*;

public class EmptyIcon implements Icon {
  private final int myWidth;
  private final int myHeight;

  private EmptyIcon(int width, int height) {
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

  public static EmptyIcon create(int width, int height) {
    return new EmptyIcon(width, height);
  }
}
