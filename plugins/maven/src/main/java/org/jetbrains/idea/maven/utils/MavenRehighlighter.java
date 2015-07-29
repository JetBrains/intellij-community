/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;

import java.util.List;

public class MavenRehighlighter {
  private final MergingUpdateQueue queue;

  public MavenRehighlighter(@NotNull Project project) {
    queue = new MergingUpdateQueue(getClass().getSimpleName(), 1000, true, MergingUpdateQueue.ANY_COMPONENT, project, null, true);
    queue.setPassThrough(false);
  }

  private static final class MavenRehighlighterPostStartupActivity implements StartupActivity, DumbAware {
    @Override
    public void runActivity(@NotNull final Project project) {
      MavenProjectsManager mavenProjectManager = MavenProjectsManager.getInstance(project);
      mavenProjectManager.addManagerListener(new MavenProjectsManager.Listener() {
        @Override
        public void activated() {
          rehighlight(project, null);
        }

        @Override
        public void projectsScheduled() {
        }

        @Override
        public void importAndResolveScheduled() {
        }
      });

      mavenProjectManager.addProjectsTreeListener(new MavenProjectsTree.ListenerAdapter() {
        @Override
        public void projectsUpdated(List<Pair<MavenProject, MavenProjectChanges>> updated, List<MavenProject> deleted) {
          for (Pair<MavenProject, MavenProjectChanges> each : updated) {
            rehighlight(project, each.first);
          }
        }

        @Override
        public void projectResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                    NativeMavenProjectHolder nativeMavenProject) {
          rehighlight(project, projectWithChanges.first);
        }

        @Override
        public void pluginsResolved(MavenProject mavenProject) {
          rehighlight(project, mavenProject);
        }

        @Override
        public void foldersResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
          rehighlight(project, projectWithChanges.first);
        }

        @Override
        public void artifactsDownloaded(MavenProject mavenProject) {
          rehighlight(project, mavenProject);
        }
      });
    }
  }

  public static void rehighlight(@NotNull Project project) {
    rehighlight(project, null);
  }

  public static void rehighlight(@NotNull Project project, @Nullable MavenProject mavenProject) {
    AccessToken accessToken = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      if (!project.isDisposed()) {
        ServiceManager.getService(project, MavenRehighlighter.class).queue.queue(new MyUpdate(project, mavenProject));
      }
    }
    finally {
      accessToken.finish();
    }
  }

  private static class MyUpdate extends Update {
    private final Project myProject;
    private final MavenProject myMavenProject;

    public MyUpdate(Project project, MavenProject mavenProject) {
      super(project);
      myProject = project;
      myMavenProject = mavenProject;
    }

    @Override
    public void run() {
      if (myMavenProject == null) {
        for (VirtualFile each : FileEditorManager.getInstance(myProject).getOpenFiles()) {
          doRehighlightMavenFile(each);
        }
      }
      else {
        doRehighlightMavenFile(myMavenProject.getFile());
      }
    }

    private void doRehighlightMavenFile(VirtualFile file) {
      Document doc = FileDocumentManager.getInstance().getCachedDocument(file);
      if (doc == null) return;
      PsiFile psi = PsiDocumentManager.getInstance(myProject).getCachedPsiFile(doc);
      if (psi == null) return;
      if (!MavenDomUtil.isMavenFile(psi)) return;

      DaemonCodeAnalyzer daemon = DaemonCodeAnalyzer.getInstance(myProject);
      daemon.restart(psi);
    }

    @Override
    public boolean canEat(Update update) {
      return myMavenProject == null || myMavenProject == ((MyUpdate)update).myMavenProject;
    }
  }
}
