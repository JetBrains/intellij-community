package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;

public class ExclusiveBackgroundVcsAction {
  private ExclusiveBackgroundVcsAction() {
  }

  public static void run(final Project project, final Runnable action) {
    final ProjectLevelVcsManager plVcsManager = ProjectLevelVcsManager.getInstance(project);
    plVcsManager.startBackgroundVcsOperation();
    try {
      action.run();
    } finally {
      plVcsManager.stopBackgroundVcsOperation();
    }
  }
}
