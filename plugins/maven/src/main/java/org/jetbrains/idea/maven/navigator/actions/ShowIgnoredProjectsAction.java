package org.jetbrains.idea.maven.navigator.actions;

import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator;

public class ShowIgnoredProjectsAction extends MavenProjectsNavigatorAction {
  @Override
  public boolean isSelected(MavenProjectsNavigator navigator) {
    return navigator.getShowIgnored();
  }

  @Override
  public void setSelected(MavenProjectsNavigator navigator, boolean value) {
    navigator.setShowIgnored(value);
  }
}