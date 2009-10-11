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
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnStatusUtil;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;

import java.io.File;

public class UnlockAction extends BasicAction {
  protected String getActionName(AbstractVcs vcs) {
    return SvnBundle.message("action.Subversion.Unlock.description");
  }

  protected boolean needsAllFiles() {
    return true;
  }

  protected boolean isEnabled(Project project, SvnVcs vcs, VirtualFile file) {
    if (file == null || file.isDirectory()) {
      return false;
    }
    return SvnStatusUtil.isExplicitlyLocked(project, file);
  }

  protected boolean needsFiles() {
    return true;
  }

  protected void perform(Project project, SvnVcs activeVcs, VirtualFile file, DataContext context)
    throws VcsException {
    batchPerform(project, activeVcs, new VirtualFile[]{file}, context);
  }

  protected void batchPerform(Project project, SvnVcs activeVcs, VirtualFile[] files, DataContext context)
    throws VcsException {
    File[] ioFiles = new File[files.length];
    for (int i = 0; i < files.length; i++) {
      VirtualFile virtualFile = files[i];
      ioFiles[i] = new File(virtualFile.getPath());
    }
    SvnUtil.doUnlockFiles(project, activeVcs, ioFiles);
  }

  protected boolean isBatchAction() {
    return true;
  }
}
