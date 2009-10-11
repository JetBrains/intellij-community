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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnStatusUtil;
import org.jetbrains.idea.svn.SvnVcs;

public class CleanupAction extends BasicAction {
  protected String getActionName(AbstractVcs vcs) {
    return SvnBundle.message("cleanup.action.name");
  }

  protected boolean needsAllFiles() {
    return true;
  }

  protected boolean isEnabled(Project project, SvnVcs vcs, VirtualFile file) {
    return SvnStatusUtil.isUnderControl(project, file);
  }

  protected boolean needsFiles() {
    return true;
  }

  @Override
  protected void execute(final Project project,
                         final SvnVcs activeVcs, final VirtualFile file, final DataContext context, final AbstractVcsHelper helper)
      throws VcsException {
    perform(project, activeVcs, file, context);
  }

  protected void perform(Project project, SvnVcs activeVcs, VirtualFile file, DataContext context)
    throws VcsException {
    new CleanupWorker(new VirtualFile[]{file}, project, "action.Subversion.cleanup.progress.title").execute();
  }

  protected void batchPerform(Project project, SvnVcs activeVcs, VirtualFile[] file, DataContext context)
    throws VcsException {
    throw new VcsException(SvnBundle.message("exception.text.cleanupaction.batchperform.not.implemented"));
  }

  protected boolean isBatchAction() {
    return false;
  }
}
