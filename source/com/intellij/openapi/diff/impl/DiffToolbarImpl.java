package com.intellij.openapi.diff.impl;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffToolbar;
import com.intellij.openapi.wm.ex.ActionToolbarEx;

import javax.swing.*;

public class DiffToolbarImpl implements DiffToolbar {
  private final DefaultActionGroup myActionGroup  = new DefaultActionGroup();
  private ActionToolbarEx myActionToolbar;

  public void registerKeyboardActions(JComponent registerActionsTo) {
    AnAction[] actions = getAllActions();
    for (int i = 0; i < actions.length; i++) {
      AnAction action = actions[i];
      action.registerCustomShortcutSet(action.getShortcutSet(), registerActionsTo);
    }
  }

  public AnAction[] getAllActions() {
    return myActionGroup.getChildren(null);
  }

  public boolean removeActionById(String actionId) {
    AnAction[] allActions = getAllActions();
    for (int i = 0; i < allActions.length; i++) {
      AnAction action = allActions[i];
      if (actionId.equals(ActionManager.getInstance().getId(action))) {
        removeAction(action);
        return true;
      }
    }
    return false;
  }

  public void removeAction(AnAction action) {
    myActionGroup.remove(action);
    updateToolbar();
  }

  public JComponent getComponent() {
    if (myActionToolbar == null)
      myActionToolbar = (ActionToolbarEx)ActionManager.getInstance().
        createActionToolbar(ActionPlaces.UNKNOWN, myActionGroup, true);
    return myActionToolbar.getComponent();
  }

  public void addAction(AnAction action) {
    myActionGroup.add(action);
    updateToolbar();
  }

  private void updateToolbar() {
    if (myActionToolbar != null) myActionToolbar.updateActions();
  }

  public void addSeparator() {
    myActionGroup.addSeparator();
    updateToolbar();
  }

  public void reset(DiffRequest.ToolbarAddons toolBar) {
    myActionGroup.removeAll();
    toolBar.customize(this);
  }
}
