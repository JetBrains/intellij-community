package com.intellij.openapi.diff.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.DiffToolbar;

import javax.swing.*;

public class DiffToolbarImpl implements DiffToolbar {
  private final DefaultActionGroup myActionGroup  = new DefaultActionGroup();
  private ActionToolbar myActionToolbar;

  public void registerKeyboardActions(JComponent registerActionsTo) {
    AnAction[] actions = getAllActions();
    for (AnAction action : actions) {
      action.registerCustomShortcutSet(action.getShortcutSet(), registerActionsTo);
    }
  }

  public AnAction[] getAllActions() {
    return myActionGroup.getChildren(null);
  }

  public boolean removeActionById(String actionId) {
    AnAction[] allActions = getAllActions();
    for (AnAction action : allActions) {
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
      myActionToolbar = ActionManager.getInstance().
        createActionToolbar(ActionPlaces.UNKNOWN, myActionGroup, true);
    return myActionToolbar.getComponent();
  }

  public void addAction(AnAction action) {
    myActionGroup.add(action);
    updateToolbar();
  }

  private void updateToolbar() {
    if (myActionToolbar != null) myActionToolbar.updateActionsImmediately();
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
