// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.idea.svn.diff;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.branchConfig.SelectBranchPopup;

import static org.jetbrains.idea.svn.SvnBundle.message;

public class CompareWithBranchAction extends DumbAwareAction {

  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VirtualFile file = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE);

    SelectBranchPopup.show(project, file, (p, configuration, url, revision) -> {
      ElementWithBranchComparer comparer = file.isDirectory()
                                           ? new DirectoryWithBranchComparer(project, file, url, revision)
                                           : new FileWithBranchComparer(project, file, url, revision);

      comparer.run();
    }, message("compare.with.branch.popup.title"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

    e.getPresentation().setEnabled(project != null && file != null && isEnabled(FileStatusManager.getInstance(project).getStatus(file)));
  }

  private static boolean isEnabled(FileStatus fileStatus) {
    return fileStatus != FileStatus.UNKNOWN && fileStatus != FileStatus.ADDED && fileStatus != FileStatus.IGNORED;
  }
}
