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

package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;

import java.util.List;

public class MavenEditorTabTitleUpdater extends MavenSimpleProjectComponent {
  public MavenEditorTabTitleUpdater(Project project) {
    super(project);
  }

  @Override
  public void initComponent() {
    if (!isNormalProject()) return;

    MavenProjectsManager.getInstance(myProject).addProjectsTreeListener(new MavenProjectsTree.ListenerAdapter() {
      @Override
      public void projectsUpdated(List<Pair<MavenProject, MavenProjectChanges>> updated, List<MavenProject> deleted) {
        updateTabName(MavenUtil.collectFirsts(updated));
      }
    });
  }

  private void updateTabName(final List<MavenProject> projects) {
    MavenUtil.invokeLater(myProject, new Runnable() {
      public void run() {
        for (MavenProject each : projects) {
          FileEditorManagerEx.getInstanceEx(myProject).updateFilePresentation(each.getFile());
        }
      }
    });
  }
}
