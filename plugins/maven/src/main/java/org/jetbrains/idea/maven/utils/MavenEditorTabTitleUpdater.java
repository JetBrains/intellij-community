// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;

import java.util.List;

final class MavenEditorTabTitleUpdater implements ApplicationInitializedListener {
  @Override
  public void componentsInitialized() {
    Application app = ApplicationManager.getApplication();
    if (app.isUnitTestMode() || app.isHeadlessEnvironment()) {
      return;
    }

    app.getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        MavenProjectsManager.getInstance(project).addProjectsTreeListener(new MavenProjectsTree.Listener() {
          @Override
          public void projectsUpdated(@NotNull List<Pair<MavenProject, MavenProjectChanges>> updated, @NotNull List<MavenProject> deleted) {
            updateTabName(MavenUtil.collectFirsts(updated), project);
          }
        });
      }
    });
  }

  private static void updateTabName(@NotNull List<MavenProject> projects, @NotNull Project project) {
    MavenUtil.invokeLater(project, () -> {
      for (MavenProject each : projects) {
        FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(each.getFile());
      }
    });
  }
}
