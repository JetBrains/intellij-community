/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
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
import org.jetbrains.idea.svn.api.Revision;
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
    ApplicationManager.getApplication().saveAll();
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

        client.doStatus(path, Revision.UNDEFINED, Depth.INFINITY, false, false, false, false, status -> {
          if (status.getContentsStatus() == StatusType.STATUS_CONFLICTED ||
              status.getPropertiesStatus() == StatusType.STATUS_CONFLICTED) {
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
