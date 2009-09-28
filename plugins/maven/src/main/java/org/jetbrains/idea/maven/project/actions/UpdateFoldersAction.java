package org.jetbrains.idea.maven.project.actions;

import org.jetbrains.idea.maven.project.MavenProjectsManager;

public class UpdateFoldersAction extends MavenProjectsManagerAction {
  @Override
  protected void perform(MavenProjectsManager manager) {
    manager.scheduleFoldersResolvingForAllProjects();
  }
}