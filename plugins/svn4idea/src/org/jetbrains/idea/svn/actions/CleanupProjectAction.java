// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;

import java.util.List;

import static org.jetbrains.idea.svn.SvnBundle.message;

public class CleanupProjectAction extends AnAction implements DumbAware {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    SvnVcs vcs = SvnVcs.getInstance(project);
    VirtualFile[] roots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs);

    new CleanupWorker(vcs, List.of(roots), message("progress.title.cleanup.project")).execute();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();

    e.getPresentation()
      .setEnabledAndVisible(project != null && ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(SvnVcs.VCS_NAME));
  }
}
