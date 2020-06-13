// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;

import java.util.List;

public class MavenRehighlighter implements Disposable {
  private final MergingUpdateQueue queue;

  public MavenRehighlighter(@NotNull Project project) {
    queue = new MergingUpdateQueue(getClass().getSimpleName(), 1000, true, MergingUpdateQueue.ANY_COMPONENT, this, null, true);
  }

  @Override
  public void dispose() {
  }

  public static void install(@NotNull Project project, @NotNull MavenProjectsManager projectsManager) {
    projectsManager.addManagerListener(new MavenProjectsManager.Listener() {
      @Override
      public void activated() {
        rehighlight(project, null);
      }
    });
    projectsManager.addProjectsTreeListener(new MavenProjectsTree.Listener() {
      @Override
      public void projectsUpdated(@NotNull List<Pair<MavenProject, MavenProjectChanges>> updated, @NotNull List<MavenProject> deleted) {
        for (Pair<MavenProject, MavenProjectChanges> each : updated) {
          rehighlight(project, each.first);
        }
      }

      @Override
      public void projectResolved(@NotNull Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                  NativeMavenProjectHolder nativeMavenProject) {
        rehighlight(project, projectWithChanges.first);
      }

      @Override
      public void pluginsResolved(@NotNull MavenProject mavenProject) {
        rehighlight(project, mavenProject);
      }

      @Override
      public void foldersResolved(@NotNull Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
        rehighlight(project, projectWithChanges.first);
      }

      @Override
      public void artifactsDownloaded(@NotNull MavenProject mavenProject) {
        rehighlight(project, mavenProject);
      }
    });
  }

  public static void rehighlight(@NotNull Project project) {
    rehighlight(project, null);
  }

  public static void rehighlight(@NotNull Project project, @Nullable MavenProject mavenProject) {
    ApplicationManager.getApplication().runReadAction(() -> {
      if (!project.isDisposed()) {
        ServiceManager.getService(project, MavenRehighlighter.class).queue.queue(new MyUpdate(project, mavenProject));
      }
    });
  }

  private static class MyUpdate extends Update {
    private final Project myProject;
    private final MavenProject myMavenProject;

    MyUpdate(Project project, MavenProject mavenProject) {
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
