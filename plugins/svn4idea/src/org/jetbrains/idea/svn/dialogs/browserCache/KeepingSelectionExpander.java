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

  public void onBeforeRefresh(final RepositoryTreeNode node) {
    mySelectionPath = myTree.getSelectionPath();
  }

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
