// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs.browserCache;

import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.browse.DirectoryEntry;
import org.jetbrains.idea.svn.checkin.CommitInfo;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static java.util.Collections.sort;
import static org.jetbrains.idea.svn.SvnUtil.append;
import static org.jetbrains.idea.svn.SvnUtil.removePathTail;

public class SyntheticWorker {
  private final SvnRepositoryCache myCache;
  private final Url myUrl;

  public SyntheticWorker(final Url url) {
    myCache = SvnRepositoryCache.getInstance();
    myUrl = url;
  }

  public void removeSelf() {
    final String parentUrl;
    try {
      parentUrl = removePathTail(myUrl).toString();
    }
    catch (SvnBindException e) {
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

  public void addSyntheticChildToSelf(final Url newUrl, final Url repositoryUrl, final String name, final boolean isDir) {
    final String currentUrlAsString = myUrl.toString();

    final List<DirectoryEntry> children = myCache.getChildren(currentUrlAsString);
    if (children == null) {
      return;
    }
    children.add(createSyntheticEntry(newUrl, repositoryUrl, name, isDir));

    sort(children, DirectoryEntry.CASE_INSENSITIVE_ORDER);
    myCache.put(currentUrlAsString, children);
  }

  public void copyTreeToSelf(final RepositoryTreeNode node) {
    try {
      node.doOnSubtree(new Adder(removePathTail(node.getURL()).toString().length(), myUrl));
    }
    catch (SvnBindException ignored) {
    }
  }

  public static void removeTreeOf(final RepositoryTreeNode node) {
    node.doOnSubtree(new Remover());
  }

  public static DirectoryEntry createSyntheticEntry(final Url newUrl, final Url repositoryUrl, final String name, final boolean isDir) {
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
    private final Url myNewParentUrl;

    private Adder(final int oldPrefixLen, final Url newParentUrl) {
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
      catch (SvnBindException ignored) {
      }
      return Boolean.FALSE;
    }

    @NotNull
    private Url convertUrl(@NotNull Url currentUrl) throws SvnBindException {
      return append(myNewParentUrl, currentUrl.toString().substring(myOldPrefixLen), true);
    }
  }
}
