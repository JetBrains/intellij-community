/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
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
