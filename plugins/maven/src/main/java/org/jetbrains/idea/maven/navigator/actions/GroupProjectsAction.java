package org.jetbrains.idea.maven.navigator.actions;

import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator;

public class GroupProjectsAction extends MavenProjectsNavigatorAction {
  @Override
  public boolean isSelected(MavenProjectsNavigator navigator) {
    return navigator.getGroupModules();
  }

  @Override
  public void setSelected(MavenProjectsNavigator navigator, boolean value) {
    navigator.setGroupModules(value);
  }
}
