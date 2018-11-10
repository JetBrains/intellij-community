// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;

import static com.intellij.util.containers.ContainerUtil.ar;
import static org.jetbrains.idea.svn.SvnStatusUtil.*;
import static org.jetbrains.idea.svn.SvnUtil.toIoFiles;

public class LockAction extends BasicAction {
  @NotNull
  @Override
  protected String getActionName() {
    return SvnBundle.message("action.Subversion.Lock.description");
  }

  @Override
  protected boolean isEnabled(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    return !file.isDirectory() && isUnderControl(vcs, file) && !isAdded(vcs, file) && !isExplicitlyLocked(vcs, file);
  }

  @Override
  protected void perform(@NotNull SvnVcs vcs, @NotNull VirtualFile file, @NotNull DataContext context) throws VcsException {
    batchPerform(vcs, ar(file), context);
  }

  @Override
  protected void batchPerform(@NotNull SvnVcs vcs, @NotNull VirtualFile[] files, @NotNull DataContext context) throws VcsException {
    SvnUtil.doLockFiles(vcs.getProject(), vcs, toIoFiles(files));
  }

  @Override
  protected boolean isBatchAction() {
    return true;
  }
}
