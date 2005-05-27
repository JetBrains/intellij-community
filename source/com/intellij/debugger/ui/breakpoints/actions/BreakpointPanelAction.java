/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.breakpoints.actions;

import com.intellij.debugger.ui.breakpoints.BreakpointPanel;

import javax.swing.*;
import java.awt.event.ActionListener;

/**
 * @author Eugene Zhuravlev
 *         Date: May 25, 2005
 */
public abstract class BreakpointPanelAction implements ActionListener {
  private final String myName;
  private AbstractButton myButton;
  private BreakpointPanel myPanel;

  protected BreakpointPanelAction(String name) {
    myName = name;
  }

  public final String getName() {
    return myName;
  }

  public boolean isStateAction() {
    return false;
  }

  public void setPanel(BreakpointPanel panel) {
    myPanel = panel;
  }

  public final BreakpointPanel getPanel() {
    return myPanel;
  }

  public void setButton(AbstractButton button) {
    myButton = button;
  }

  public final AbstractButton getButton() {
    return myButton;
  }

  public abstract void update();
}
