/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.treeConflict;

import com.intellij.history.LocalHistory;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.portable.SvnStatusClientI;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
* Created with IntelliJ IDEA.
* User: Irina.Chernushina
* Date: 5/2/12
* Time: 1:03 PM
*/
public class SvnTreeConflictResolver {
  private final SvnVcs myVcs;
  private final FilePath myPath;
  private VcsRevisionNumber myCommittedRevision;
  private final FilePath myRevertPath;
  private final VcsDirtyScopeManager myDirtyScopeManager;

  public SvnTreeConflictResolver(SvnVcs vcs, FilePath path, VcsRevisionNumber committedRevision, final @Nullable FilePath revertPath) {
    myVcs = vcs;
    myPath = path;
    myCommittedRevision = committedRevision;
    myRevertPath = revertPath;
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myVcs.getProject());
  }

  public void resolveSelectTheirsFull(SVNTreeConflictDescription d) throws VcsException {
    final LocalHistory localHistory = LocalHistory.getInstance();
    localHistory.putSystemLabel(myVcs.getProject(), "Before accepting theirs for " + TreeConflictRefreshablePanel.filePath(myPath));
    try {
      updatetoTheirsFull();
      pathDirty(myPath);
      revertAdditional();
    } finally {
      localHistory.putSystemLabel(myVcs.getProject(), "After accepting theirs for " + TreeConflictRefreshablePanel.filePath(myPath));
    }
  }

  private void pathDirty(final FilePath path) {
    final VirtualFile validParent = ChangesUtil.findValidParentAccurately(path);
    if (validParent == null) return;
    validParent.refresh(false, true);
    if (path.isDirectory()) {
      myDirtyScopeManager.dirDirtyRecursively(path);
    }
    else {
      myDirtyScopeManager.fileDirty(path);
    }
  }

  private void revertAdditional() throws VcsException {
    if (myRevertPath == null) return;
    final File ioFile = myRevertPath.getIOFile();
    try {
      final SVNStatus status = myVcs.getFactory(ioFile).createStatusClient().doStatus(ioFile, false);
      myVcs.getFactory(ioFile).createRevertClient().revert(new File[]{ioFile}, SVNDepth.INFINITY, null);
      if (SVNStatusType.STATUS_ADDED.equals(status.getNodeStatus())) {
        FileUtil.delete(ioFile);
      }
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
    pathDirty(myRevertPath);
  }

  public void resolveSelectMineFull(SVNTreeConflictDescription d) throws VcsException {
    final File ioFile = myPath.getIOFile();

    myVcs.getFactory(ioFile).createConflictClient().resolve(ioFile, SVNDepth.INFINITY, true, true, true);
    pathDirty(myPath);
  }

  private void updatetoTheirsFull() throws VcsException {
    try {
      final File ioFile = myPath.getIOFile();
      SVNStatus status = myVcs.getFactory(ioFile).createStatusClient().doStatus(ioFile, false);
      if (myCommittedRevision == null) {
        myCommittedRevision = new SvnRevisionNumber(status.getCommittedRevision());
      }
      if (status == null || SVNStatusType.STATUS_UNVERSIONED.equals(status.getNodeStatus())) {
        myVcs.getFactory(ioFile).createRevertClient().revert(new File[]{ioFile}, SVNDepth.INFINITY, null);
        updateIoFile(ioFile, SVNRevision.HEAD);
        return;
      } else if (SVNStatusType.STATUS_ADDED.equals(status.getNodeStatus())) {
        myVcs.getFactory(ioFile).createRevertClient().revert(new File[]{ioFile}, SVNDepth.INFINITY, null);
        updateIoFile(ioFile, SVNRevision.HEAD);
        FileUtil.delete(ioFile);
        return;
      } else {
        final Set<File> usedToBeAdded = new HashSet<File>();
        if (myPath.isDirectory()) {
          SvnStatusClientI statusClient = myVcs.getFactory(ioFile).createStatusClient();
          statusClient.doStatus(ioFile, SVNRevision.UNDEFINED, SVNDepth.INFINITY, false, false, false, false,
                                new ISVNStatusHandler() {
                                  @Override
                                  public void handleStatus(SVNStatus status) throws SVNException {
                                    if (status != null && SVNStatusType.STATUS_ADDED.equals(status.getNodeStatus())) {
                                      usedToBeAdded.add(status.getFile());
                                    }
                                  }
                                }, null);
        }
        myVcs.getFactory(ioFile).createRevertClient().revert(new File[]{ioFile}, SVNDepth.INFINITY, null);
        for (File wasAdded : usedToBeAdded) {
          FileUtil.delete(wasAdded);
        }
        updateIoFile(ioFile, SVNRevision.HEAD);
      }
    }
    catch (SVNException e1) {
      throw new VcsException(e1);
    }
  }

  private void updateIoFile(@NotNull File ioFile, @NotNull final SVNRevision revision) throws SVNException {
    if (! ioFile.exists()) {
      File parent = ioFile.getParentFile();
      myVcs.getFactory(parent).createUpdateClient().doUpdate(parent, revision, SVNDepth.INFINITY, true, false);
    } else {
      myVcs.getFactory(ioFile).createUpdateClient().doUpdate(ioFile, revision, SVNDepth.INFINITY, false, false);
    }
  }
}
