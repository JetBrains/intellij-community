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
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.*;

public class ResolveAction extends BasicAction {
  @NotNull
  @Override
  protected String getActionName() {
    return SvnBundle.message("action.name.resolve.conflict");
  }

  @Override
  protected boolean isEnabled(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    FileStatus status = FileStatusManager.getInstance(vcs.getProject()).getStatus(file);

    return file.isDirectory() || FileStatus.MERGED_WITH_CONFLICTS.equals(status) || FileStatus.MERGED_WITH_BOTH_CONFLICTS.equals(status);
  }

  @Override
  protected void perform(@NotNull SvnVcs vcs, @NotNull VirtualFile file, @NotNull DataContext context) throws VcsException {
    batchPerform(vcs, ar(file), context);
  }

  @Override
  protected void batchPerform(@NotNull SvnVcs vcs, @NotNull VirtualFile[] files, @NotNull DataContext context) {
    boolean hasDirs = exists(files, VirtualFile::isDirectory);
    List<VirtualFile> fileList = newArrayList();
    if (!hasDirs) {
      Collections.addAll(fileList, files);
    }
    else {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
        for (VirtualFile file : files) {
          if (file.isDirectory()) {
            ProjectLevelVcsManager.getInstance(vcs.getProject()).iterateVcsRoot(file, filePath -> {
              ProgressManager.checkCanceled();
              VirtualFile fileOrDir = filePath.getVirtualFile();
              if (fileOrDir != null && !fileOrDir.isDirectory() && isEnabled(vcs, fileOrDir) && !fileList.contains(fileOrDir)) {
                fileList.add(fileOrDir);
              }
              return true;
            });
          }
          else {
            if (!fileList.contains(file)) {
              fileList.add(file);
            }
          }
        }
      }, SvnBundle.message("progress.searching.for.files.with.conflicts"), true, vcs.getProject());
    }
    ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(vcs.getProject()).ensureFilesWritable(fileList);
    fileList.removeAll(Arrays.asList(status.getReadonlyFiles()));
    AbstractVcsHelper.getInstance(vcs.getProject()).showMergeDialog(fileList, new SvnMergeProvider(vcs.getProject()));
  }

  protected boolean isBatchAction() {
    return true;
  }
}
