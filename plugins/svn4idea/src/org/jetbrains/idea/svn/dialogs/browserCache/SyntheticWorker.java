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
package org.jetbrains.idea.svn.dialogs.browserCache;

import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.browse.DirectoryEntry;
import org.jetbrains.idea.svn.checkin.CommitInfo;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import java.util.*;

public class SyntheticWorker {
  private final SvnRepositoryCache myCache;
  private final SVNURL myUrl;

  public SyntheticWorker(final SVNURL url) {
    myCache = SvnRepositoryCache.getInstance();
    myUrl = url;
  }

  public void removeSelf() {
    final String parentUrl;
    try {
      parentUrl = myUrl.removePathTail().toString();
    }
    catch (SVNException e) {
      return;
    }

    final List<DirectoryEntry> children = myCache.getChildren(parentUrl);
    if (children == null) {
      return;
    }
    for (Iterator<DirectoryEntry> iterator = children.iterator(); iterator.hasNext(); ) {
      final DirectoryEntry entry = iterator.next();
      if (myUrl.equals(entry.getUrl())) {
        iterator.remove();
      }
    }
    myCache.put(parentUrl, children);
  }

  public void addSyntheticChildToSelf(final SVNURL newUrl, final SVNURL repositoryUrl, final String name, final boolean isDir) {
    final String currentUrlAsString = myUrl.toString();

    final List<DirectoryEntry> children = myCache.getChildren(currentUrlAsString);
    if (children == null) {
      return;
    }
    children.add(createSyntheticEntry(newUrl, repositoryUrl, name, isDir));

    // TODO: Seems that just Set instead of List could be used in cache - so no sort() after add() will be required
    Collections.sort(children);
    myCache.put(currentUrlAsString, children);
  }

  public void copyTreeToSelf(final RepositoryTreeNode node) {
    try {
      node.doOnSubtree(new Adder(node.getURL().removePathTail().toString().length(), myUrl));
    }
    catch (SVNException e) {
      //
    }
  }

  public static void removeTreeOf(final RepositoryTreeNode node) {
    node.doOnSubtree(new Remover());
  }

  public static DirectoryEntry createSyntheticEntry(final SVNURL newUrl, final SVNURL repositoryUrl, final String name, final boolean isDir) {
    return new DirectoryEntry(newUrl, repositoryUrl, name, NodeKind.from(isDir), CommitInfo.EMPTY, null);
  }

  private static class Remover implements NotNullFunction<RepositoryTreeNode, Object> {
    private final SvnRepositoryCache myCache = SvnRepositoryCache.getInstance();

    @NotNull
    public Object fun(final RepositoryTreeNode repositoryTreeNode) {
      myCache.remove(repositoryTreeNode.getURL().toString());
      return Boolean.FALSE;
    }
  }

  private class Adder implements NotNullFunction<RepositoryTreeNode, Object> {
    private final int myOldPrefixLen;
    private final SVNURL myNewParentUrl;

    private Adder(final int oldPrefixLen, final SVNURL newParentUrl) {
      myOldPrefixLen = oldPrefixLen;
      myNewParentUrl = newParentUrl;
    }

    @NotNull
    public Object fun(final RepositoryTreeNode repositoryTreeNode) {
      final List<DirectoryEntry> children = myCache.getChildren(repositoryTreeNode.getURL().toString());
      if (children == null) {
        return Boolean.FALSE;
      }
      final List<DirectoryEntry> newChildren = new ArrayList<>(children.size());

      try {
        for (DirectoryEntry child : children) {
          newChildren.add(createSyntheticEntry(convertUrl(child.getUrl()), child.getRepositoryRoot(), child.getName(), child.isDirectory()));
        }
        myCache.put(convertUrl(repositoryTreeNode.getURL()).toString(), newChildren);
      }
      catch (SVNException e) {
        //
      }
      return Boolean.FALSE;
    }

    private SVNURL convertUrl(final SVNURL currentUrl) throws SVNException {
      return myNewParentUrl.appendPath(currentUrl.toString().substring(myOldPrefixLen), true);
    }
  }
}
