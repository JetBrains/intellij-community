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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import org.jetbrains.idea.maven.facade.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;

import java.util.List;

public class MavenRehighlighter extends SimpleProjectComponent {
  protected MavenRehighlighter(Project project) {
    super(project);
  }

  public void initComponent() {
    MavenProjectsManager m = MavenProjectsManager.getInstance(myProject);
    m.addManagerListener(new MavenProjectsManager.Listener() {
      public void activated() {
        rehighlight(myProject);
      }

      public void scheduledImportsChanged() {
      }
    });
    m.addProjectsTreeListener(new MavenProjectsTree.ListenerAdapter() {
      public void projectsUpdated(List<Pair<MavenProject, MavenProjectChanges>> updated, List<MavenProject> deleted, Object message) {
        rehighlight(myProject);
      }

      public void projectResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                  NativeMavenProjectHolder nativeMavenProject,
                                  Object message) {
        rehighlight(myProject);
      }

      public void pluginsResolved(MavenProject project) {
        rehighlight(myProject);
      }

      public void foldersResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges, Object message) {
        rehighlight(myProject);
      }

      public void artifactsDownloaded(MavenProject project) {
        rehighlight(myProject);
      }
    });
  }

  public static void rehighlight(final Project project) {
    MavenUtil.invokeLater(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            ((PsiModificationTrackerImpl)PsiManager.getInstance(project).getModificationTracker()).incCounter();
            DaemonCodeAnalyzer.getInstance(project).restart();
          }
        });
      }
    });
  }
}
