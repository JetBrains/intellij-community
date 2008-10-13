package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Application;

public class ExclusiveBackgroundVcsAction {
  private ExclusiveBackgroundVcsAction() {
  }

  public static void run(final Project project, final Runnable action) {
    final ProjectLevelVcsManager plVcsManager = ProjectLevelVcsManager.getInstance(project);
    plVcsManager.startBackgroundVcsOperation();
    try {
      action.run();
    } finally {
      final Application application = ApplicationManager.getApplication();
      if (application.isDispatchThread()) {
        application.executeOnPooledThread(new Runnable() {
          public void run() {
            plVcsManager.stopBackgroundVcsOperation();
          }
        });
      } else {
        plVcsManager.stopBackgroundVcsOperation();
      }
    }
  }
}
