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
package org.jetbrains.idea.svn;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.vcs.impl.VcsRootIterator;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.info.Info;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class ForNestedRootChecker {

  @NotNull private final SvnVcs myVcs;
  @NotNull private final VcsRootIterator myRootIterator;

  public ForNestedRootChecker(@NotNull SvnVcs vcs) {
    myVcs = vcs;
    myRootIterator = new VcsRootIterator(vcs.getProject(), vcs);
  }

  @NotNull
  public List<Node> getAllNestedWorkingCopies(@NotNull VirtualFile root) {
    LinkedList<Node> result = ContainerUtil.newLinkedList();
    LinkedList<VirtualFile> workItems = ContainerUtil.newLinkedList();

    workItems.add(root);
    while (!workItems.isEmpty()) {
      VirtualFile item = workItems.removeFirst();
      checkCancelled();

      final Node vcsElement = new VcsFileResolver(myVcs, item, root).resolve();
      if (vcsElement != null) {
        result.add(vcsElement);
      }
      else {
        for (VirtualFile child : item.getChildren()) {
          checkCancelled();

          if (child.isDirectory() && myRootIterator.acceptFolderUnderVcs(root, child)) {
            workItems.add(child);
          }
        }
      }
    }
    return result;
  }

  private void checkCancelled() {
    if (myVcs.getProject().isDisposed()) {
      throw new ProcessCanceledException();
    }
  }

  private static class VcsFileResolver {

    @NotNull private final SvnVcs myVcs;
    @NotNull private final VirtualFile myFile;
    @NotNull private final File myIoFile;
    @NotNull private final VirtualFile myRoot;
    @Nullable private Info myInfo;
    @Nullable private SvnBindException myError;

    private VcsFileResolver(@NotNull SvnVcs vcs, @NotNull VirtualFile file, @NotNull VirtualFile root) {
      myVcs = vcs;
      myFile = file;
      myIoFile = VfsUtilCore.virtualToIoFile(file);
      myRoot = root;
    }

    @Nullable
    public Node resolve() {
      runInfo();

      return processInfo();
    }

    private void runInfo() {
      if (isRoot() || hasChildAdminDirectory()) {
        try {
          myInfo = myVcs.getFactory(myIoFile, false).createInfoClient().doInfo(myIoFile, SVNRevision.UNDEFINED);
        }
        catch (SvnBindException e) {
          myError = e;
        }
      }
    }

    @SuppressWarnings("UseVirtualFileEquals")
    private boolean isRoot() {
      return myRoot == myFile;
    }

    private boolean hasChildAdminDirectory() {
      return myFile.findChild(SvnUtil.SVN_ADMIN_DIR_NAME) != null;
    }

    @Nullable
    private Node processInfo() {
      Node result = null;

      if (myError != null) {
        if (!SvnUtil.isUnversionedOrNotFound(myError)) {
          // error code does not indicate that myFile is unversioned or path is invalid => create result, but indicate error
          result = new Node(myFile, getFakeUrl(), getFakeUrl(), myError);
        }
      }
      else if (myInfo != null && myInfo.getRepositoryRootURL() != null && myInfo.getURL() != null) {
        result = new Node(myFile, myInfo.getURL(), myInfo.getRepositoryRootURL());
      }

      return result;
    }

    // TODO: This should be updated when fully removing SVNKit object model from code
    private SVNURL getFakeUrl() {
      // do not have constants like SVNURL.EMPTY - use fake url - it should be never used in any real logic
      try {
        return SVNURL.fromFile(myIoFile);
      }
      catch (SVNException e) {
        // should not occur
        throw SvnUtil.createIllegalArgument(e);
      }
    }
  }
}
