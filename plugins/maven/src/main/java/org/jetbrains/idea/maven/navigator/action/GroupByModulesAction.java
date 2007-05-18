package org.jetbrains.idea.maven.navigator.action;

import org.jetbrains.idea.maven.navigator.PomTreeViewSettings;

public class GroupByModulesAction extends PomTreeViewAction {

  protected boolean isHard() {
    return true;
  }

  public boolean isSelected(PomTreeViewSettings settings) {
    return settings.groupByModule;
  }

  public void setSelected(PomTreeViewSettings settings, boolean state) {
    settings.groupByModule = state;
  }
}