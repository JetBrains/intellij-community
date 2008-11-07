package org.jetbrains.idea.maven.navigator.action;

import org.jetbrains.idea.maven.navigator.MavenProjectsNavigatorSettings;

public class GroupStructurallyAction extends MavenProjectsNavigatorAction {
  public boolean isSelected(MavenProjectsNavigatorSettings settings) {
    return settings.groupStructurally;
  }

  public void setSelected(MavenProjectsNavigatorSettings settings, boolean state) {
    settings.groupStructurally = state;
  }
}
