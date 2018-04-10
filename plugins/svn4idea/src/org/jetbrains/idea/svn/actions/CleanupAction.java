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

  protected boolean isBatchAction() {
    return false;
  }
}
