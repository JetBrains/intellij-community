// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.treeConflict;

import com.intellij.history.LocalHistory;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusClient;
import org.jetbrains.idea.svn.status.StatusType;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class SvnTreeConflictResolver {

  @NotNull private final SvnVcs myVcs;
  @NotNull private final FilePath myPath;
  @Nullable private final FilePath myRevertPath;
  @NotNull private final VcsDirtyScopeManager myDirtyScopeManager;

  public SvnTreeConflictResolver(@NotNull SvnVcs vcs, @NotNull FilePath path, @Nullable FilePath revertPath) {
    myVcs = vcs;
    myPath = path;
    myRevertPath = revertPath;
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myVcs.getProject());
  }

  public void resolveSelectTheirsFull() throws VcsException {
    final LocalHistory localHistory = LocalHistory.getInstance();
    String pathPresentation = TreeConflictRefreshablePanel.filePath(myPath);

    localHistory.putSystemLabel(myVcs.getProject(), "Before accepting theirs for " + pathPresentation);
    try {
      updateToTheirsFull();
      pathDirty(myPath);
      revertAdditional();
    } finally {
      localHistory.putSystemLabel(myVcs.getProject(), "After accepting theirs for " + pathPresentation);
    }
  }

  private void pathDirty(@NotNull FilePath path) {
    VirtualFile validParent = ChangesUtil.findValidParentAccurately(path);

    if (validParent != null) {
      validParent.refresh(false, true);

      if (path.isDirectory()) {
        myDirtyScopeManager.dirDirtyRecursively(path);
      }
      else {
        myDirtyScopeManager.fileDirty(path);
      }
    }
  }

  private void revertAdditional() throws VcsException {
    if (myRevertPath != null) {
      final File ioFile = myRevertPath.getIOFile();
      final Status status = myVcs.getFactory(ioFile).createStatusClient().doStatus(ioFile, false);

      revert(ioFile);
      if (status.is(StatusType.STATUS_ADDED)) {
        FileUtil.delete(ioFile);
      }
      pathDirty(myRevertPath);
    }
  }

  public void resolveSelectMineFull() throws VcsException {
    final File ioFile = myPath.getIOFile();

    myVcs.getFactory(ioFile).createConflictClient().resolve(ioFile, Depth.INFINITY, true, true, true);
    pathDirty(myPath);
  }

  private void updateToTheirsFull() throws VcsException {
    final File ioFile = myPath.getIOFile();
    Status status = myVcs.getFactory(ioFile).createStatusClient().doStatus(ioFile, false);

    if (status == null || status.is(StatusType.STATUS_UNVERSIONED)) {
      revert(ioFile);
      updateFile(ioFile, Revision.HEAD);
    }
    else if (status.is(StatusType.STATUS_ADDED)) {
      revert(ioFile);
      updateFile(ioFile, Revision.HEAD);
      FileUtil.delete(ioFile);
    } else {
      Set<File> usedToBeAdded = myPath.isDirectory() ? getDescendantsWithAddedStatus(ioFile) : ContainerUtil.newHashSet();

      revert(ioFile);
      for (File wasAdded : usedToBeAdded) {
        FileUtil.delete(wasAdded);
      }
      updateFile(ioFile, Revision.HEAD);
    }
  }

  @NotNull
  private Set<File> getDescendantsWithAddedStatus(@NotNull File ioFile) throws SvnBindException {
    final Set<File> result = ContainerUtil.newHashSet();
    StatusClient statusClient = myVcs.getFactory(ioFile).createStatusClient();

    statusClient.doStatus(ioFile, Depth.INFINITY, false, false, false, false, status -> {
      if (status != null && status.is(StatusType.STATUS_ADDED)) {
        result.add(status.getFile());
      }
    });

    return result;
  }

  private void revert(@NotNull File file) throws VcsException {
    myVcs.getFactory(file).createRevertClient().revert(Collections.singletonList(file), Depth.INFINITY, null);
  }

  private void updateFile(@NotNull File file, @NotNull Revision revision) throws SvnBindException {
    boolean useParentAsTarget = !file.exists();
    File target = useParentAsTarget ? file.getParentFile() : file;

    myVcs.getFactory(target).createUpdateClient().doUpdate(target, revision, Depth.INFINITY, useParentAsTarget, false);
  }
}
