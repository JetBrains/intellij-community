// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.ignore.FileGroupInfo;
import org.jetbrains.idea.svn.ignore.IgnoreGroupHelperAction;
import org.jetbrains.idea.svn.ignore.SvnPropertyService;

import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnBundle.messagePointer;

public class AddToIgnoreListAction extends BasicAction {
  private final boolean myUseCommonExtension;

  public AddToIgnoreListAction(boolean useCommonExtension) {
    myUseCommonExtension = useCommonExtension;
  }

  @Override
  protected VirtualFile @Nullable [] getSelectedFiles(@NotNull AnActionEvent e) {
    return IgnoreGroupHelperAction.getSelectedFiles(e);
  }

  @NotNull
  @Override
  protected String getActionName() {
    return message("action.name.ignore.files");
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    IgnoreGroupHelperAction helper = IgnoreGroupHelperAction.createFor(e);
    if (helper == null || !helper.allCanBeIgnored()) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    FileGroupInfo fileGroupInfo = helper.getFileGroupInfo();
    if (myUseCommonExtension) {
      String actionName = fileGroupInfo.getExtensionMask();
      presentation.setEnabledAndVisible(fileGroupInfo.sameExtension());
      presentation.setText(actionName, false);
      presentation.setDescription(messagePointer("action.Subversion.Ignore.MatchExtension.description", actionName));
    }
    else {
      if (fileGroupInfo.oneFileSelected()) {
        presentation.setText(fileGroupInfo.getFileName(), false);
      }
      else {
        presentation.setText(messagePointer("action.Subversion.Ignore.ExactMatch.text"));
      }
      presentation.setDescription(messagePointer("action.Subversion.Ignore.ExactMatch.description"));
    }
  }

  @Override
  protected void doVcsRefresh(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    VcsDirtyScopeManager vcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(vcs.getProject());
    if (file.getParent() != null) {
      vcsDirtyScopeManager.fileDirty(file.getParent());
    }
  }

  @Override
  protected boolean isEnabled(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    return IgnoreGroupHelperAction.isUnversioned(vcs, file);
  }

  @Override
  protected void perform(@NotNull SvnVcs vcs, @NotNull VirtualFile file, @NotNull DataContext context) {
  }

  @Override
  protected void batchPerform(@NotNull SvnVcs vcs, VirtualFile @NotNull [] files, @NotNull DataContext context) throws VcsException {
    FileGroupInfo groupInfo = new FileGroupInfo();
    for (VirtualFile file : files) {
      groupInfo.onFileEnabled(file);
    }
    SvnPropertyService.doAddToIgnoreProperty(vcs, myUseCommonExtension, files, groupInfo);
  }

  @Override
  protected boolean isBatchAction() {
    return true;
  }
}
