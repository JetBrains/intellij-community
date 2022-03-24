// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.actions;

import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.dialogs.ImportDialog;

public class ImportToRepositoryAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    e.getPresentation().setEnabled(
      project == null ||
      TrustedProjects.isTrusted(project) && !ProjectLevelVcsManager.getInstance(project).isBackgroundVcsOperationRunning()
    );
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    ImportDialog dialog = new ImportDialog(project);
    dialog.show();
  }
}
