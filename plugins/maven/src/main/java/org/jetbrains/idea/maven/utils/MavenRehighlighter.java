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
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.facade.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;

import java.util.List;

public class MavenRehighlighter extends SimpleProjectComponent {
  private MergingUpdateQueue myQueue;

  protected MavenRehighlighter(Project project) {
    super(project);
  }

  public void initComponent() {
    myQueue = new MergingUpdateQueue(getClass().getSimpleName(), 1000, true, MergingUpdateQueue.ANY_COMPONENT, myProject);

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
        for (Pair<MavenProject, MavenProjectChanges> each : updated) {
          rehighlight(myProject, each.first);
        }
      }

      public void projectResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                  NativeMavenProjectHolder nativeMavenProject,
                                  Object message) {
        rehighlight(myProject, projectWithChanges.first);
      }

      public void pluginsResolved(MavenProject project) {
        rehighlight(myProject, project);
      }

      public void foldersResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges, Object message) {
        rehighlight(myProject, projectWithChanges.first);
      }

      public void artifactsDownloaded(MavenProject project) {
        rehighlight(myProject, project);
      }
    });
  }

  public static void rehighlight(final Project project) {
    rehighlight(project, null);
  }

  public static void rehighlight(final Project project, final MavenProject mavenProject) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        if (project.isDisposed()) return;
        ServiceManager.getService(project, MavenRehighlighter.class).myQueue.queue(new MyUpdate(project, mavenProject));
      }
    });
  }

  private static class MyUpdate extends Update {
    private final Project myProject;
    private final MavenProject myMavenProject;

    public MyUpdate(Project project, MavenProject mavenProject) {
      super(project);
      myProject = project;
      myMavenProject = mavenProject;
    }

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

      DaemonCodeAnalyzerImpl daemon = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
      daemon.getFileStatusMap().markFileScopeDirtyDefensively(psi);
      daemon.stopProcess(true);
    }

    @Override
    public boolean canEat(Update update) {
      if (myMavenProject == null) return true;
      if (myMavenProject == ((MyUpdate)update).myMavenProject) return true;
      return false;
    }
  }
}
