/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

public class MavenEditorTabTitleUpdater extends DummyProjectComponent {
  private final Project myProject;

  public MavenEditorTabTitleUpdater(Project project) {
    myProject = project;
  }

  @Override
  public void initComponent() {
    if (ApplicationManager.getApplication().isUnitTestMode()
        || ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return;
    }

    MavenProjectsManager.getInstance(myProject).addListener(new MavenProjectsManager.ListenerAdapter() {
      public void projectUpdated(final MavenProjectModel project) {
        MavenUtil.invokeLater(myProject, new Runnable() {
          public void run() {
            FileEditorManagerEx.getInstanceEx(myProject).updateFilePresentation(project.getFile());
          }
        });
      }
    });
  }
}
