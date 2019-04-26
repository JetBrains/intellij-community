// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;

import java.util.List;

final class MavenEditorTabTitleUpdater extends MavenSimpleProjectComponent {
  MavenEditorTabTitleUpdater(Project project) {
    super(project);

    if (!isNormalProject()) {
      return;
    }

    MavenProjectsManager.getInstance(myProject).addProjectsTreeListener(new MavenProjectsTree.Listener() {
      @Override
      public void projectsUpdated(@NotNull List<Pair<MavenProject, MavenProjectChanges>> updated, @NotNull List<MavenProject> deleted) {
        updateTabName(MavenUtil.collectFirsts(updated));
      }
    });
  }

  private void updateTabName(@NotNull List<MavenProject> projects) {
    MavenUtil.invokeLater(myProject, () -> {
      for (MavenProject each : projects) {
        FileEditorManagerEx.getInstanceEx(myProject).updateFilePresentation(each.getFile());
      }
    });
  }
}
