// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.application.ApplicationManager.getApplication;

public final class ExclusiveBackgroundVcsAction {
  private ExclusiveBackgroundVcsAction() {
  }

  public static void run(@NotNull Project project, @NotNull Runnable action) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    vcsManager.startBackgroundVcsOperation();
    try {
      action.run();
    }
    finally {
      if (getApplication().isDispatchThread()) {
        getApplication().executeOnPooledThread(() -> vcsManager.stopBackgroundVcsOperation());
      }
      else {
        vcsManager.stopBackgroundVcsOperation();
      }
    }
  }
}
