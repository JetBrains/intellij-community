package com.intellij.openapi.diff.impl;

import com.intellij.openapi.diff.DiffRequest;

import javax.swing.*;
import java.awt.*;

public class DiffToolbarComponent extends JPanel {
  private final JComponent myWholeComponent;
  private DiffToolbarImpl myToolbar;

  public DiffToolbarComponent(final JComponent wholeComponent) {
    super(new BorderLayout());
    myWholeComponent = wholeComponent;
  }

  public void resetToolbar(DiffRequest.ToolbarAddons toolBar) {
    if (myToolbar != null) remove(myToolbar.getComponent());
    myToolbar = new DiffToolbarImpl();
    myToolbar.reset(toolBar);
    myToolbar.registerKeyboardActions(myWholeComponent);
    add(myToolbar.getComponent(), BorderLayout.CENTER);
    revalidate();
    repaint();
  }

  public DiffToolbarImpl getToolbar() {
    return myToolbar;
  }
}
