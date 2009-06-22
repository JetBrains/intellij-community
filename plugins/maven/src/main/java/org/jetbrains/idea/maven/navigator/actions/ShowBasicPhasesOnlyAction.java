package org.jetbrains.idea.maven.navigator.actions;

import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator;

public class ShowBasicPhasesOnlyAction extends MavenProjectsNavigatorAction {
  @Override
  public boolean isSelected(MavenProjectsNavigator navigator) {
    return navigator.getShowBasicPhasesOnly();
  }

  @Override
  public void setSelected(MavenProjectsNavigator navigator, boolean value) {
    navigator.setShowBasicPhasesOnly(value);
  }
}