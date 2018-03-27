// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs.browser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserComponent;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;
import org.jetbrains.idea.svn.dialogs.browserCache.Expander;
import org.jetbrains.idea.svn.dialogs.browserCache.KeepingExpandedExpander;

import javax.swing.tree.TreeNode;
import java.util.Enumeration;

public abstract class AbstractOpeningExpander implements Expander {
  private final RepositoryBrowserComponent myBrowser;
  private final KeepingExpandedExpander myKeepingExpander;
  @NotNull private final Url mySelectionPath;

  protected AbstractOpeningExpander(final RepositoryBrowserComponent browser, @NotNull Url selectionPath) {
    myBrowser = browser;
    myKeepingExpander = new KeepingExpandedExpander(browser);
    mySelectionPath = selectionPath;
  }

  public void onBeforeRefresh(final RepositoryTreeNode node) {
    myKeepingExpander.onBeforeRefresh(node);
  }

  protected enum ExpandVariants {
    DO_NOTHING,
    EXPAND_AND_EXIT,
    EXPAND_CONTINUE
  }

  protected abstract ExpandVariants expandNode(@NotNull Url url);

  protected abstract boolean checkChild(@NotNull Url childUrl);

  public void onAfterRefresh(final RepositoryTreeNode node) {
    myKeepingExpander.onAfterRefresh(node);

    if (node.isLeaf()) {
      return;
    }

    final ExpandVariants expandVariant = expandNode(node.getURL());

    if (ExpandVariants.DO_NOTHING.equals(expandVariant)) {
      return;
    }

    // then expanded
    myBrowser.expandNode(node);

    if (ExpandVariants.EXPAND_AND_EXIT.equals(expandVariant)) {
      removeSelf();
    } else {
      final Enumeration children = node.children();
      while (children.hasMoreElements()) {
        final TreeNode treeNode = (TreeNode) children.nextElement();
        if (treeNode instanceof RepositoryTreeNode) {
          final RepositoryTreeNode repositoryTreeNode = (RepositoryTreeNode) treeNode;
          Url childUrl = repositoryTreeNode.getURL();
          if (checkChild(childUrl)) {
            if (mySelectionPath.equals(childUrl)) {
              myBrowser.setSelectedNode(repositoryTreeNode);
            }
              repositoryTreeNode.reload(this, false);
            return;
          }
        }
      }
      removeSelf();
    }
  }

  private void removeSelf() {
    myBrowser.setLazyLoadingExpander(new KeepingExpandedExpander.Factory());
  }
}
