package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.ui.navigation.History;

import javax.swing.*;

abstract class NavigationAction extends AnAction {

  private ShadowAction myShadow;

  protected NavigationAction(JComponent c, final String originalActionID) {
    final AnAction back = ActionManager.getInstance().getAction(originalActionID);
    myShadow = new ShadowAction(this, back, c);
  }

  public final void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(getHistory(e) != null);
    if (e.getPresentation().isEnabled()) {
      e.getPresentation().setIcon(getTemplatePresentation().getIcon());
      doUpdate(e);
    }
  }

  protected abstract void doUpdate(final AnActionEvent e);

  protected final History getHistory(final AnActionEvent e) {
    final ProjectStructureConfigurable config = e.getData(ProjectStructureConfigurable.KEY);
    if (config == null) return null;
    return config.getHistory();
  }

}
