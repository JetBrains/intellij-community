package org.jetbrains.idea.maven.project.action;

import org.jetbrains.idea.maven.project.MavenProjectsManager;

public class ReimportAction extends MavenProjectsManagerAction {
  @Override
  protected void perform(MavenProjectsManager manager) {
    manager.forceUpdateAllProjectsOrFindAllAvailablePomFiles();
  }
}
