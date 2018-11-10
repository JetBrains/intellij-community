// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnStatusUtil;
import org.jetbrains.idea.svn.SvnVcs;

import static java.util.Collections.singletonList;

public class CleanupAction extends BasicAction {
  @NotNull
  @Override
  protected String getActionName() {
    return SvnBundle.message("cleanup.action.name");
  }

  @Override
  protected boolean isEnabled(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    return SvnStatusUtil.isUnderControl(vcs, file);
  }

  @Override
  protected void execute(@NotNull SvnVcs vcs, @NotNull VirtualFile file, @NotNull DataContext context) throws VcsException {
    perform(vcs, file, context);
  }

  @Override
  protected void perform(@NotNull SvnVcs vcs, @NotNull VirtualFile file, @NotNull DataContext context) {
    new CleanupWorker(vcs, singletonList(file)).execute();
  }

  @Override
  protected void batchPerform(@NotNull SvnVcs vcs, @NotNull VirtualFile[] files, @NotNull DataContext context) throws VcsException {
    throw new VcsException(SvnBundle.message("exception.text.cleanupaction.batchperform.not.implemented"));
  }

  @Override
  protected boolean isBatchAction() {
    return false;
  }
}
