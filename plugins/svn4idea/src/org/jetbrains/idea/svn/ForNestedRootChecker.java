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
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.impl.VcsRootIterator;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNInfo;

import java.util.*;

public class ForNestedRootChecker {

  @NotNull private final SvnVcs myVcs;
  @NotNull private final VcsRootIterator myRootIterator;

  public ForNestedRootChecker(@NotNull SvnVcs vcs) {
    myVcs = vcs;
    myRootIterator = new VcsRootIterator(vcs.getProject(), vcs);
  }

  @Nullable
  public Node createReal(final VirtualFile file) {
    final SVNInfo info = myVcs.getInfo(file);
    if (info == null || info.getRepositoryRootURL() == null || info.getURL() == null) {
      return null;
    }
    return new Node(file, info.getURL(), info.getRepositoryRootURL());
  }

  public Node createReplaceable(final VirtualFile file) {
    return new Node(file, null);
  }

  public Node createSupposed(final Node parent, final VirtualFile child) {
    return new Node(child, getForChild(parent.getUrl(), child.getName()));
  }

  public boolean replaceWithReal(final Node real, final Node supposed) {
    return supposed.getUrl() == null || ((supposed.getUrl() != null) && (! supposed.getUrl().equals(real.getUrl())));
  }

  public SVNURL getForChild(@Nullable final SVNURL parent, @NotNull final String childName) {
    return parent != null ? SvnUtil.append(parent, childName) : null;
  }

  public List<Node> getAllNestedWorkingCopies(final VirtualFile root, final boolean goIntoNested, final Getter<Boolean> cancelledGetter) {
    final LinkedList<Node> queue = new LinkedList<Node>();
    final LinkedList<Node> result = new LinkedList<Node>();

    queue.add(createReplaceable(root));
    while (! queue.isEmpty()) {
      final Node node = queue.removeFirst();
      if (Boolean.TRUE.equals(cancelledGetter.get())) throw new ProcessCanceledException();

      // check self
      final Node real = createReal(node.getFile());
      if (real != null) {
        if (replaceWithReal(real, node)) {
          result.add(real);
          if (!goIntoNested) continue;
        }
      }

      // for next step
      final VirtualFile file = node.getFile();
      if (file.isDirectory() && (! SvnUtil.isAdminDirectory(file))) {
        for (VirtualFile child : file.getChildren()) {
          if (Boolean.TRUE.equals(cancelledGetter.get())) throw new ProcessCanceledException();
          if (myRootIterator.acceptFolderUnderVcs(root, child)) {
            if (real == null) {
              queue.add(createReplaceable(child));
            } else {
              queue.add(createSupposed(real, child));
            }
          }
        }
      }
    }
    return result;
  }
}
