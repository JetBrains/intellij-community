package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;

/**
 * @author max
 */
public class QuickChangeSchemesAction extends QuickSwitchSchemeAction {
  protected void fillActions(Project project, DefaultActionGroup group) {
    final AnAction[] actions = getGroup().getChildren(null);
    for (int i = 0; i < actions.length; i++) {
      group.add(actions[i]);
    }
  }

  protected boolean isEnabled() {
    return true;
  }

  private DefaultActionGroup getGroup() {
    return (DefaultActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_CHANGE_SCHEME);
  }
}
