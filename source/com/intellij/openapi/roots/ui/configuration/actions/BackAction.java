package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;

import javax.swing.*;

public class BackAction extends NavigationAction {

  public BackAction(JComponent c) {
    super(c, "Back");
  }

  protected void doUpdate(final AnActionEvent e) {
    e.getPresentation().setEnabled(getHistory(e).canGoBack());
  }

  public void actionPerformed(final AnActionEvent e) {
    getHistory(e).back();
  }
}
