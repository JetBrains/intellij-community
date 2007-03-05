package com.intellij.openapi.ui;

import java.awt.*;

public interface NullableComponent {

  boolean isNull();

  class Check {

    private Check() {
    }

    public static boolean isNull(Component c) {
      if (c == null) return true;
      if (c instanceof NullableComponent) return ((NullableComponent)c).isNull();
      return false;
    }

    public static boolean isNullOrHidden(Component c) {
      if (c != null && !c.isShowing()) return true;
      return isNull(c);
    }

    public static boolean isNotNullAndVisible(Component c) {
      return !isNull(c) && c.isVisible();
    }
  }

}
