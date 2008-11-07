package org.jetbrains.idea.maven.navigator.action;

import org.jetbrains.idea.maven.navigator.MavenProjectsNavigatorSettings;

public class ShowIgnoredProjectsAction extends MavenProjectsNavigatorAction {
  public boolean isSelected(MavenProjectsNavigatorSettings settings) {
    return settings.showIgnored;
  }

  public void setSelected(MavenProjectsNavigatorSettings settings, boolean state) {
    settings.showIgnored = state;
  }
}