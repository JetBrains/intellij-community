package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;

import javax.swing.*;

public class ForwardAction extends NavigationAction {

  public ForwardAction(JComponent c) {
    super(c, "Forward");
  }

  protected void doUpdate(final AnActionEvent e) {
    e.getPresentation().setEnabled(getHistory(e).canGoForward());
  }

  public void actionPerformed(final AnActionEvent e) {
    getHistory(e).forward();
  }
}