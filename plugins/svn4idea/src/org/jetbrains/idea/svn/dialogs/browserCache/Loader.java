package org.jetbrains.idea.svn.dialogs.browserCache;

import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorMessage;

import java.util.List;

public abstract class Loader {
  protected final SvnRepositoryCache myCache;

  protected Loader(final SvnRepositoryCache cache) {
    myCache = cache;
  }

  public abstract void load(final RepositoryTreeNode node, final Expander afterRefreshExpander);
  public abstract void forceRefresh(final String repositoryRootUrl);
  protected abstract NodeLoadState getNodeLoadState();

  protected void refreshNodeError(final RepositoryTreeNode node, final SVNErrorMessage text) {
    if (node.isDisposed()) {
      return;
    }
    final RepositoryTreeNode existingNode = node.getNodeWithSamePathUnderModelRoot();
    if (existingNode == null) {
      return;
    }
    if (existingNode.isDisposed()) {
      return;
    }

    existingNode.setErrorNode(text, getNodeLoadState());
  }

  protected void refreshNode(final RepositoryTreeNode node, final List<SVNDirEntry> data, final Expander expander) {
    if (node.isDisposed()) {
      return;
    }
    final RepositoryTreeNode existingNode = node.getNodeWithSamePathUnderModelRoot();
    if (existingNode == null) {
      return;
    }

    if (existingNode.isDisposed()) {
      return;
    }
    expander.onBeforeRefresh(existingNode);
    existingNode.setChildren(data, getNodeLoadState());
    expander.onAfterRefresh(existingNode);
  }
}
