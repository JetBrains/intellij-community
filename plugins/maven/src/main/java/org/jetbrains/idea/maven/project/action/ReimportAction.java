package org.jetbrains.idea.maven.project.action;

import org.jetbrains.idea.maven.project.MavenProjectsManager;

public class ReimportAction extends MavenProjectsManagerAction {
  protected void perform(MavenProjectsManager manager) {
    if (manager.isMavenizedProject()) {
      manager.waitForQuickResolvingCompletionAndImport();
    } else {
      manager.findAndImportAllAvailablePomFiles();
    }
  }
}
