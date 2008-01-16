/*
 * User: anna
 * Date: 16-Jan-2008
 */
package com.intellij.ide.util.scopeChooser;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.util.IconLoader;
import com.intellij.packageDependencies.DependencyUISettings;

public final class GroupByScopeTypeAction extends ToggleAction {
  private final Runnable myUpdate;

  public GroupByScopeTypeAction(Runnable update) {
    super(IdeBundle.message("action.group.by.scope.type"),
          IdeBundle.message("action.description.group.by.scope"), IconLoader.getIcon("/nodes/testSourceFolder.png"));
    myUpdate = update;
  }

  public boolean isSelected(AnActionEvent event) {
    return DependencyUISettings.getInstance().UI_GROUP_BY_SCOPE_TYPE;
  }

  public void setSelected(AnActionEvent event, boolean flag) {
    DependencyUISettings.getInstance().UI_GROUP_BY_SCOPE_TYPE = flag;
    myUpdate.run();
  }
}