// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.


package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.ar;
import static com.intellij.util.containers.ContainerUtil.exists;

public class ResolveAction extends BasicAction {
  @Override
  protected @NotNull String getActionName() {
    return SvnBundle.message("action.name.resolve.conflict");
  }

  @Override
  protected boolean isEnabled(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    FileStatus status = FileStatusManager.getInstance(vcs.getProject()).getStatus(file);

    return file.isDirectory() || FileStatus.MERGED_WITH_CONFLICTS.equals(status) || FileStatus.MERGED_WITH_BOTH_CONFLICTS.equals(status);
  }

  @Override
  protected void perform(@NotNull SvnVcs vcs, @NotNull VirtualFile file, @NotNull DataContext context) {
    batchPerform(vcs, ar(file), context);
  }

  @Override
  protected void batchPerform(@NotNull SvnVcs vcs, VirtualFile @NotNull [] files, @NotNull DataContext context) {
    boolean hasDirs = exists(files, VirtualFile::isDirectory);
    List<VirtualFile> fileList = new ArrayList<>();
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

  @Override
  protected boolean isBatchAction() {
    return true;
  }
}
