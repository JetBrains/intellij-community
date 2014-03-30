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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNInfo;
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

  public List<Node> getAllNestedWorkingCopies(@NotNull final VirtualFile root, final boolean goIntoNested) {
    final LinkedList<WorkItem> workItems = new LinkedList<WorkItem>();
    final LinkedList<Node> result = new LinkedList<Node>();

    workItems.add(new WorkItem(root));
    while (!workItems.isEmpty()) {
      final WorkItem item = workItems.removeFirst();
      checkCancelled();

      // check self
      final Node vcsElement = new VcsFileResolver(myVcs, item.file).resolve();
      // TODO: actually goIntoNested = false always => item.url will be always null when this line is reached
      if (vcsElement != null && (item.url == null || vcsElement.onUrl(item.url))) {
        result.add(vcsElement);
        if (!goIntoNested) {
          continue;
        }
      }

      // for next step
      final VirtualFile file = item.file;
      if (file.isDirectory() && (! SvnUtil.isAdminDirectory(file))) {
        // TODO: Only directory children should be checked.
        for (VirtualFile child : file.getChildren()) {
          checkCancelled();

          if (myRootIterator.acceptFolderUnderVcs(root, child)) {
            // TODO: actually goIntoNested = false always => we could reach this line only when vcsElement is null
            workItems.add(WorkItem.create(vcsElement, child));
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

  private static class WorkItem {

    @NotNull private final VirtualFile file;
    @Nullable private final SVNURL url;

    private WorkItem(@NotNull VirtualFile file) {
      this(file, null);
    }

    private WorkItem(@NotNull VirtualFile file, @Nullable SVNURL url) {
      this.file = file;
      this.url = url;
    }

    @NotNull
    private static WorkItem create(@Nullable Node node, @NotNull VirtualFile child) {
      return node == null ? new WorkItem(child) : new WorkItem(child, SvnUtil.append(node.getUrl(), child.getName()));
    }
  }

  private static class VcsFileResolver {

    @NotNull private final SvnVcs myVcs;
    @NotNull private final VirtualFile myFile;
    @NotNull private final File myIoFile;
    @Nullable private SVNInfo myInfo;
    @Nullable private SVNException myError;

    private VcsFileResolver(@NotNull SvnVcs vcs, @NotNull VirtualFile file) {
      myVcs = vcs;
      myFile = file;
      myIoFile = VfsUtilCore.virtualToIoFile(file);
    }

    @Nullable
    public Node resolve() {
      runInfo();

      return processInfo();
    }

    private void runInfo() {
      try {
        myInfo = myVcs.getFactory(myIoFile, false).createInfoClient().doInfo(myIoFile, SVNRevision.UNDEFINED);
      }
      catch (SVNException e) {
        myError = e;
      }
    }

    @Nullable
    private Node processInfo() {
      Node result = null;

      if (myError != null) {
        SVNErrorCode errorCode = myError.getErrorMessage().getErrorCode();

        if (!SvnUtil.isUnversionedOrNotFound(errorCode)) {
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
