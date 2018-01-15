package org.jetbrains.plugins.ruby.ruby.actions;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;

class RunAnythingMyAccessibleComponent extends JPanel {
  private Accessible myAccessible;

  public RunAnythingMyAccessibleComponent(LayoutManager layout) {
    super(layout);
    setOpaque(false);
  }

  void setAccessible(Accessible comp) {
    myAccessible = comp;
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    return accessibleContext = (myAccessible != null ? myAccessible.getAccessibleContext() : super.getAccessibleContext());
  }
}
