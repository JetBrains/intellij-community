package org.jetbrains.idea.maven.project.action;

import org.jetbrains.idea.maven.project.MavenProjectsManager;

public class DownloadArtifactsAction extends MavenProjectsManagerAction {
  protected void perform(MavenProjectsManager manager) {
    manager.waitForArtifactsDownloadingCompletion();
  }
}