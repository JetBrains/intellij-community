package org.jetbrains.idea.svn.dialogs.browserCache;

import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;

public interface Expander {
  void onBeforeRefresh(final RepositoryTreeNode node);
  void onAfterRefresh(final RepositoryTreeNode node);
}
