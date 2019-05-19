// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


package org.jetbrains.idea.svn.actions;

import com.intellij.configurationStore.StoreUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnStatusUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.conflict.ConflictClient;
import org.jetbrains.idea.svn.dialogs.SelectFilesDialog;
import org.jetbrains.idea.svn.status.StatusClient;
import org.jetbrains.idea.svn.status.StatusType;

import java.io.File;
import java.util.Collection;

import static com.intellij.util.containers.ContainerUtil.ar;
import static com.intellij.util.containers.ContainerUtil.newTreeSet;
import static org.jetbrains.idea.svn.SvnBundle.message;

public class MarkResolvedAction extends BasicAction {
  private static final Logger LOG = Logger.getInstance(MarkResolvedAction.class);

  @NotNull
  @Override
  protected String getActionName() {
    return message("action.name.mark.resolved");
  }

  @Override
  protected boolean needsAllFiles() {
    return false;
  }

  @Override
  protected boolean isEnabled(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
    FileStatus status = FileStatusManager.getInstance(vcs.getProject()).getStatus(file);

    return file.isDirectory()
           ? SvnStatusUtil.isUnderControl(vcs, file)
           : FileStatus.MERGED_WITH_CONFLICTS.equals(status) ||
             FileStatus.MERGED_WITH_BOTH_CONFLICTS.equals(status) ||
             FileStatus.MERGED_WITH_PROPERTY_CONFLICTS.equals(status);
  }

  @Override
  protected void perform(@NotNull SvnVcs vcs, @NotNull VirtualFile file, @NotNull DataContext context) throws VcsException {
    batchPerform(vcs, ar(file), context);
  }

  @Override
  protected void batchPerform(@NotNull SvnVcs vcs, @NotNull VirtualFile[] files, @NotNull DataContext context) throws VcsException {
    StoreUtil.saveDocumentsAndProjectSettings(vcs.getProject());
    Collection<String> paths = collectResolvablePaths(vcs, files);
    if (paths.isEmpty()) {
      Messages.showInfoMessage(vcs.getProject(), message("message.text.no.conflicts.found"), message("message.title.no.conflicts.found"));
      return;
    }
    String[] pathsArray = ArrayUtil.toStringArray(paths);
    SelectFilesDialog dialog = new SelectFilesDialog(vcs.getProject(), message("label.select.files.and.directories.to.mark.resolved"),
                                                     message("dialog.title.mark.resolved"), message("action.name.mark.resolved"),
                                                     pathsArray, "vcs.subversion.resolve");
    if (!dialog.showAndGet()) {
      return;
    }
    pathsArray = dialog.getSelectedPaths();
    try {
      for (String path : pathsArray) {
        File ioFile = new File(path);
        ConflictClient client = vcs.getFactory(ioFile).createConflictClient();

        // TODO: Probably false should be passed to "resolveTree", but previous logic used true implicitly
        client.resolve(ioFile, Depth.EMPTY, true, true, true);
      }
    }
    finally {
      for (VirtualFile file : files) {
        VcsDirtyScopeManager.getInstance(vcs.getProject()).fileDirty(file);
        file.refresh(true, false);
        if (file.getParent() != null) {
          file.getParent().refresh(true, false);
        }
      }
    }
  }

  @Override
  protected boolean isBatchAction() {
    return true;
  }

  @NotNull
  private static Collection<String> collectResolvablePaths(@NotNull SvnVcs vcs, @NotNull VirtualFile[] files) {
    Collection<String> result = newTreeSet();

    for (VirtualFile file : files) {
      try {
        File path = VfsUtilCore.virtualToIoFile(file);
        StatusClient client = vcs.getFactory(path).createStatusClient();

        client.doStatus(path, Depth.INFINITY, false, false, false, false, status -> {
          if (status.is(StatusType.STATUS_CONFLICTED) || status.isProperty(StatusType.STATUS_CONFLICTED)) {
            result.add(status.getFile().getAbsolutePath());
          }
        });
      }
      catch (SvnBindException e) {
        LOG.warn(e);
      }
    }

    return result;
  }
}
