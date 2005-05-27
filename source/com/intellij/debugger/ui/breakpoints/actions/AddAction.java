/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.breakpoints.actions;

import com.intellij.debugger.ui.breakpoints.BreakpointPanel;

import javax.swing.*;
import java.awt.event.KeyEvent;

/**
 * @author Eugene Zhuravlev
 *         Date: May 25, 2005
 */public abstract class AddAction extends BreakpointPanelAction {
  protected AddAction() {
    super("Add...");
  }

  public void setButton(AbstractButton button) {
    super.setButton(button);
    getButton().setMnemonic('A');
  }

  public void setPanel(BreakpointPanel panel) {
    super.setPanel(panel);
    getPanel().getTable().registerKeyboardAction(this, KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  public void update() {
  }
}
