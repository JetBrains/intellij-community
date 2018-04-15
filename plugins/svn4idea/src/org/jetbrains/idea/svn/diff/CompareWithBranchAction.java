// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.idea.svn.diff;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.branchConfig.SelectBranchPopup;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;

/**
 * @author yole
 */
public class CompareWithBranchAction extends AnAction implements DumbAware {

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    assert project != null;
    final VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);

    SelectBranchPopup
      .show(project, virtualFile, new MyBranchSelectedCallback(virtualFile), SvnBundle.message("compare.with.branch.popup.title"));
  }

  @Override
  public void update(final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    e.getPresentation().setEnabled(isEnabled(project, virtualFile));
  }

  private static boolean isEnabled(final Project project, final VirtualFile virtualFile) {
    if (project == null || virtualFile == null) {
      return false;
    }
    final FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(virtualFile);
    if (fileStatus == FileStatus.UNKNOWN || fileStatus == FileStatus.ADDED || fileStatus == FileStatus.IGNORED) {
      return false;
    }
    return true;
  }

  private static class MyBranchSelectedCallback implements SelectBranchPopup.BranchSelectedCallback {

    @NotNull private final VirtualFile myVirtualFile;

    public MyBranchSelectedCallback(@NotNull VirtualFile virtualFile) {
      myVirtualFile = virtualFile;
    }

    public void branchSelected(Project project, SvnBranchConfigurationNew configuration, @NotNull Url url, long revision) {
      ElementWithBranchComparer comparer =
        myVirtualFile.isDirectory()
        ? new DirectoryWithBranchComparer(project, myVirtualFile, url, revision)
        : new FileWithBranchComparer(project, myVirtualFile, url, revision);

      comparer.run();
    }
  }
}
