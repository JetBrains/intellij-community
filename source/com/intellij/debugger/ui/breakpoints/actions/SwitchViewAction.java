/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.breakpoints.actions;

import com.intellij.debugger.ui.breakpoints.BreakpointPanel;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * @author Eugene Zhuravlev
 *         Date: May 25, 2005
 */
public class SwitchViewAction extends BreakpointPanelAction {
  public SwitchViewAction() {
    super("Switch View");
  }

  public void setButton(AbstractButton button) {
    super.setButton(button);
    getButton().setMnemonic('w');
  }

  public void actionPerformed(ActionEvent e) {
    getPanel().switchViews();
  }


  public void update() {
    final AbstractButton button = getButton();
    final BreakpointPanel panel = getPanel();
    button.setText(panel.isTreeShowing()? "List View" : "Tree View");
    button.setEnabled(panel.getBreakpointCount() > 0);
  }
}
