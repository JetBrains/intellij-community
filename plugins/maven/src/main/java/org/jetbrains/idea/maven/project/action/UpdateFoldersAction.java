package org.jetbrains.idea.maven.project.action;

import org.jetbrains.idea.maven.project.MavenProjectsManager;

public class UpdateFoldersAction extends MavenProjectsManagerAction {
  @Override
  protected void perform(MavenProjectsManager manager) {
    manager.scheduleFoldersResolvingForAllProjects();
  }
}