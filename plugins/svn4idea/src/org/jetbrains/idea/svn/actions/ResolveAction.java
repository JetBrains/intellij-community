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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ResolveAction extends BasicAction {
  protected String getActionName(AbstractVcs vcs) {
    return SvnBundle.message("action.name.resolve.conflict");
  }

  protected boolean needsAllFiles() {
    return true;
  }

  protected boolean isEnabled(Project project, SvnVcs vcs, VirtualFile file) {
    if (file.isDirectory()) return true;
    final FileStatus fStatus = FileStatusManager.getInstance(project).getStatus(file);
    return FileStatus.MERGED_WITH_CONFLICTS.equals(fStatus) || FileStatus.MERGED_WITH_BOTH_CONFLICTS.equals(fStatus);
  }

  protected boolean needsFiles() {
    return true;
  }

  protected void perform(Project project, SvnVcs activeVcs, VirtualFile file, DataContext context) throws VcsException {
    batchPerform(project, activeVcs, new VirtualFile[]{file}, context);
  }

  protected void batchPerform(final Project project, final SvnVcs activeVcs, final VirtualFile[] files, DataContext context) throws VcsException {
    boolean hasDirs = false;
    for(VirtualFile file: files) {
      if (file.isDirectory()) {
        hasDirs = true;
      }
    }
    final List<VirtualFile> fileList = new ArrayList<>();
    if (!hasDirs) {
      Collections.addAll(fileList, files);
    }
    else {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          for (VirtualFile file: files) {
            if (file.isDirectory()) {
              ProjectLevelVcsManager.getInstance(project).iterateVcsRoot(file, new Processor<FilePath>() {
                public boolean process(final FilePath filePath) {
                  ProgressManager.checkCanceled();
                  VirtualFile fileOrDir = filePath.getVirtualFile();
                  if (fileOrDir != null && !fileOrDir.isDirectory() && isEnabled(project, activeVcs, fileOrDir) && !fileList.contains(fileOrDir)) {
                    fileList.add(fileOrDir);
                  }
                  return true;
                }
              });
            }
            else {
              if (!fileList.contains(file)) {
                fileList.add(file);
              }
            }
          }
        }
      }, SvnBundle.message("progress.searching.for.files.with.conflicts"), true, project);
    }
    final ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(fileList);
    fileList.removeAll(Arrays.asList(status.getReadonlyFiles()));
    AbstractVcsHelper.getInstance(project).showMergeDialog(fileList, new SvnMergeProvider(project));
  }

  protected boolean isBatchAction() {
    return true;
  }
}
