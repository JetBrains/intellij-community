package org.jetbrains.idea.svn.dialogs.browser;

import org.jetbrains.idea.svn.dialogs.ReloadListener;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserComponent;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;

import javax.swing.tree.TreeNode;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

public class Expander implements ReloadListener {
  private final List<String> pathElements;
  private final RepositoryBrowserComponent myBrowser;

  public Expander(final TreeNode[] path, final RepositoryBrowserComponent browser) {
    myBrowser = browser;
    pathElements = new LinkedList<String>();
    // starting from 1st level child, not root (root is already defined/found)
    for (int i = 1; i < path.length; i++) {
      TreeNode node = path[i];
      pathElements.add(node.toString());
    }
  }

  public void onAfterReload(final RepositoryTreeNode node) {
    if (node.isLeaf()) {
      return;
    }

    myBrowser.expandNode(node);

    if (pathElements.isEmpty()) {
      return;
    }

    final String nextKey = pathElements.remove(0);

    final Enumeration children = node.children();
    while ((nextKey != null) && children.hasMoreElements()) {
      final TreeNode treeNode = (TreeNode) children.nextElement();

      if (treeNode instanceof RepositoryTreeNode) {
        final RepositoryTreeNode repositoryTreeNode = (RepositoryTreeNode) treeNode;
        // toString() is sufficient here -> user objects do not define equals()
        if (nextKey.equals(treeNode.toString())) {
          repositoryTreeNode.registerReloadListener(this);
          break;
        }
      }
    }
  }
}
