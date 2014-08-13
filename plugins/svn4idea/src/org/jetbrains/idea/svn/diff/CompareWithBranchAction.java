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
import org.jetbrains.idea.svn.*;
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

    public void branchSelected(Project project, SvnBranchConfigurationNew configuration, String url, long revision) {
      ElementWithBranchComparer comparer =
        myVirtualFile.isDirectory()
        ? new DirectoryWithBranchComparer(project, myVirtualFile, url, revision)
        : new FileWithBranchComparer(project, myVirtualFile, url, revision);

      comparer.run();
    }
  }
}
