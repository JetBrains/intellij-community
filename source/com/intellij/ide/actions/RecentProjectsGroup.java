package com.intellij.ide.actions;

import com.intellij.ide.RecentProjectsManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;

public class RecentProjectsGroup extends ActionGroup {
  public AnAction[] getChildren(AnActionEvent e) {
    return RecentProjectsManager.getInstance().getRecentProjectsActions();
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(RecentProjectsManager.getInstance().getRecentProjectsActions().length > 0);
  }
}
