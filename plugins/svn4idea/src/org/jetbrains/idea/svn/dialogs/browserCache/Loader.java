// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.dialogs.browserCache;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.browse.DirectoryEntry;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;

import java.util.List;

public abstract class Loader {

  protected final @NotNull SvnRepositoryCache myCache;

  protected Loader(@NotNull SvnRepositoryCache cache) {
    myCache = cache;
  }

  public abstract void load(@NotNull RepositoryTreeNode node, @NotNull Expander afterRefreshExpander);

  protected abstract @NotNull NodeLoadState getNodeLoadState();

  protected void refreshNodeError(@NotNull RepositoryTreeNode node, @NotNull VcsException error) {
    RepositoryTreeNode existingNode = findExistingNode(node);

    if (existingNode != null) {
      existingNode.setErrorNode(error.getMessage());
    }
  }

  protected void refreshNode(@NotNull RepositoryTreeNode node, @NotNull List<DirectoryEntry> data, @NotNull Expander expander) {
    RepositoryTreeNode existingNode = findExistingNode(node);

    if (existingNode != null) {
      expander.onBeforeRefresh(existingNode);
      existingNode.setChildren(data, getNodeLoadState());
      expander.onAfterRefresh(existingNode);
    }
  }

  private static @Nullable RepositoryTreeNode findExistingNode(@NotNull RepositoryTreeNode node) {
    RepositoryTreeNode result = null;

    if (!node.isDisposed()) {
      result = node.getNodeWithSamePathUnderModelRoot();
    }

    return result == null || result.isDisposed() ? null : result;
  }
}
