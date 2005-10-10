/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.popup;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import java.awt.*;

public class PopupIcons {

  public static Icon HAS_NEXT_ICON = IconLoader.getIcon("/icons/ide/nextStep.png");
  public static Icon HAS_NEXT_ICON_GRAYED = IconLoader.getIcon("/icons/ide/nextStepGrayed.png");
  public static Icon EMPTY_ICON = new EmptyIcon();

  private static class EmptyIcon implements Icon {
    public int getIconHeight() {
      return HAS_NEXT_ICON.getIconHeight();
    }

    public int getIconWidth() {
      return HAS_NEXT_ICON.getIconWidth();
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {

    }
  }
}
