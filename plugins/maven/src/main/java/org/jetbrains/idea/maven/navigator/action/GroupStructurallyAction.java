package org.jetbrains.idea.maven.navigator.action;

import org.jetbrains.idea.maven.navigator.PomTreeViewSettings;

public class GroupStructurallyAction extends PomTreeViewAction {

  public boolean isSelected(PomTreeViewSettings settings) {
    return settings.groupByDirectory;
  }

  public void setSelected(PomTreeViewSettings settings, boolean state) {
    settings.groupByDirectory = state;
  }
}
