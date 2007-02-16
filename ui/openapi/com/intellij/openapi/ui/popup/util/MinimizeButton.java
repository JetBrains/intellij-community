package com.intellij.openapi.ui.popup.util;

import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.IconLoader;

public class MinimizeButton extends IconButton {

  public MinimizeButton(final String tooltip) {
    super(tooltip, IconLoader.getIcon("/general/hideToolWindow.png"),
          IconLoader.getIcon("/general/hideToolWindow.png"),
          IconLoader.getIcon("/general/hideToolWindowInactive.png"));
  }
}
