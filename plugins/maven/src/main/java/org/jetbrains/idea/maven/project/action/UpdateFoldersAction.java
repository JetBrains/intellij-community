package org.jetbrains.idea.maven.project.action;

import org.jetbrains.idea.maven.project.MavenProjectsManager;

public class UpdateFoldersAction extends MavenProjectsManagerAction {
  protected void perform(MavenProjectsManager manager) {
    manager.waitForFoldersUpdatingCompletionAndImport();
  }
}