package org.jetbrains.idea.maven.navigator.action;

import org.jetbrains.idea.maven.navigator.PomTreeViewSettings;

public class ShowIgnoredAction extends PomTreeViewAction {

  protected boolean isHard() {
    return true;
  }

  public boolean isSelected(PomTreeViewSettings settings) {
    return settings.showIgnored;
  }

  public void setSelected(PomTreeViewSettings settings, boolean state) {
    settings.showIgnored = state;
  }
}