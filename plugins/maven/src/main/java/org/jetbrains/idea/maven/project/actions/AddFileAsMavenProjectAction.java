// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.util.Collections;

import static org.jetbrains.idea.maven.utils.actions.MavenActionUtil.getProject;

public class AddFileAsMavenProjectAction extends MavenAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DataContext context = e.getDataContext();
    Project project = getProject(context);
    MavenProjectsManager manager = MavenActionUtil.getProjectsManager(context);
    if (project != null && manager != null) {
      MavenUtil.isProjectTrustedEnoughToImport(project, true);
      manager.addManagedFilesOrUnignore(Collections.singletonList(getSelectedFile(context)));
    }
  }

  @Override
  protected boolean isAvailable(@NotNull AnActionEvent e) {
    final DataContext context = e.getDataContext();
    VirtualFile file = getSelectedFile(context);
    return super.isAvailable(e)
           && MavenActionUtil.isMavenProjectFile(file)
           && !isExistingProjectFile(context, file);
  }

  @Override
  protected boolean isVisible(@NotNull AnActionEvent e) {
    return super.isVisible(e) && isAvailable(e);
  }

  private static boolean isExistingProjectFile(DataContext context, VirtualFile file) {
    MavenProjectsManager manager = MavenActionUtil.getProjectsManager(context);
    return manager != null && manager.findProject(file) != null;
  }

  @Nullable
  private static VirtualFile getSelectedFile(DataContext context) {
    return CommonDataKeys.VIRTUAL_FILE.getData(context);
  }
}
