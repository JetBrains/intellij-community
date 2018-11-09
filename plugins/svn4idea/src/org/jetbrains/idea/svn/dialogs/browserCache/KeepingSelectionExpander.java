// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs.browserCache;

import org.jetbrains.idea.svn.dialogs.RepositoryBrowserComponent;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.Enumeration;

public class KeepingSelectionExpander implements Expander {
  private final JTree myTree;
  private TreePath mySelectionPath;

  public KeepingSelectionExpander(final RepositoryBrowserComponent browser) {
    myTree = browser.getRepositoryTree();
  }

  @Override
  public void onBeforeRefresh(final RepositoryTreeNode node) {
    mySelectionPath = myTree.getSelectionPath();
  }

  @Override
  public void onAfterRefresh(final RepositoryTreeNode node) {
    if (mySelectionPath == null) {
      return;
    }

    setSelection(mySelectionPath.getPath(), 1, (TreeNode) myTree.getModel().getRoot());
  }

  private boolean setSelection(final Object[] oldSelectionPath, final int idx, final TreeNode node) {
    if (idx >= oldSelectionPath.length) {
      return false;
    }
    if ((oldSelectionPath[idx] != null) && (oldSelectionPath[idx].toString() != null)) {
      final Enumeration children = node.children();
      while (children.hasMoreElements()) {
        final TreeNode child = (TreeNode) children.nextElement();
        if (oldSelectionPath[idx].toString().equals(child.toString())) {
          final boolean childStatus = setSelection(oldSelectionPath, idx + 1, child);
          if (! childStatus) {
            myTree.setSelectionPath(new TreePath(((DefaultTreeModel) myTree.getModel()).getPathToRoot(child)));
          }
          return true;
        }
      }
    }
    myTree.setSelectionPath(new TreePath(((DefaultTreeModel) myTree.getModel()).getPathToRoot(node)));
    return true;
  }
}
