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

  protected boolean isBatchAction() {
    return true;
  }
}
