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
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNInfo;

import java.util.LinkedList;
import java.util.List;

public class ForNestedRootChecker {

  @NotNull private final SvnVcs myVcs;
  @NotNull private final VcsRootIterator myRootIterator;

  public ForNestedRootChecker(@NotNull SvnVcs vcs) {
    myVcs = vcs;
    myRootIterator = new VcsRootIterator(vcs.getProject(), vcs);
  }

  @Nullable
  public Node resolveVcsElement(@NotNull final VirtualFile file) {
    final SVNInfo info = myVcs.getInfo(file);
    if (info == null || info.getRepositoryRootURL() == null || info.getURL() == null) {
      return null;
    }
    return new Node(file, info.getURL(), info.getRepositoryRootURL());
  }

  public List<Node> getAllNestedWorkingCopies(@NotNull final VirtualFile root, final boolean goIntoNested) {
    final LinkedList<WorkItem> workItems = new LinkedList<WorkItem>();
    final LinkedList<Node> result = new LinkedList<Node>();

    workItems.add(new WorkItem(root));
    while (!workItems.isEmpty()) {
      final WorkItem item = workItems.removeFirst();
      checkCancelled();

      // check self
      final Node vcsElement = resolveVcsElement(item.file);
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
}
