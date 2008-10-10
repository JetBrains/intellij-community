package org.jetbrains.idea.maven.navigator.action;

import org.jetbrains.idea.maven.navigator.PomTreeViewSettings;

public class ShowIgnoredProjectsAction extends MavenTreeViewAction {
  public boolean isSelected(PomTreeViewSettings settings) {
    return settings.showIgnored;
  }

  public void setSelected(PomTreeViewSettings settings, boolean state) {
    settings.showIgnored = state;
  }
}