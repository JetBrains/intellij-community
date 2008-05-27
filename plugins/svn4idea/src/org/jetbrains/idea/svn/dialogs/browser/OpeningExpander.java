package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserComponent;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;
import org.jetbrains.idea.svn.dialogs.browserCache.Expander;
import org.jetbrains.idea.svn.dialogs.browserCache.KeepingExpandedExpander;

import javax.swing.tree.TreeNode;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

public class OpeningExpander implements Expander {
  private final List<String> pathElements;
  private final String myLongest;

  private final RepositoryBrowserComponent myBrowser;
  private final KeepingExpandedExpander myKeepingExpander;

  private final String mySelectionPath;

  public OpeningExpander(final TreeNode[] path, final RepositoryBrowserComponent browser, final RepositoryTreeNode selectionPath) {
    myBrowser = browser;
    pathElements = new LinkedList<String>();

    for (TreeNode aPath : path) {
      RepositoryTreeNode node = (RepositoryTreeNode)aPath;
      pathElements.add(node.getURL().toString());
    }
    myLongest = pathElements.get(pathElements.size() - 1);

    myKeepingExpander = new KeepingExpandedExpander(browser);
    mySelectionPath = selectionPath.getURL().toString();
  }

  public void onBeforeRefresh(final RepositoryTreeNode node) {
    myKeepingExpander.onBeforeRefresh(node);
  }

  public void onAfterRefresh(final RepositoryTreeNode node) {
    myKeepingExpander.onAfterRefresh(node);

    if (node.isLeaf()) {
      return;
    }

    final String myUrl = node.getURL().toString();
    if (pathElements.contains(myUrl)) {
      myBrowser.expandNode(node);
      if (myLongest.equals(myUrl)) {
        removeSelf();
        return;
      }

      final Enumeration children = node.children();
      while (children.hasMoreElements()) {
        final TreeNode treeNode = (TreeNode) children.nextElement();
        if (treeNode instanceof RepositoryTreeNode) {
          final RepositoryTreeNode repositoryTreeNode = (RepositoryTreeNode) treeNode;
          final String childUrl = repositoryTreeNode.getURL().toString();
          if (pathElements.contains(childUrl)) {
            if ((mySelectionPath != null) && (mySelectionPath.equals(childUrl))) {
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

  public static class Factory implements NotNullFunction<RepositoryBrowserComponent, Expander> {
    private final TreeNode[] myPath;
    private final RepositoryTreeNode mySelectionPath;

    public Factory(final TreeNode[] path, final RepositoryTreeNode selectionPath) {
      myPath = path;
      mySelectionPath = selectionPath;
    }

    @NotNull
    public Expander fun(final RepositoryBrowserComponent repositoryBrowserComponent) {
      return new OpeningExpander(myPath, repositoryBrowserComponent, mySelectionPath);
    }
  }

  private void removeSelf() {
    myBrowser.setLazyLoadingExpander(new KeepingExpandedExpander.Factory());
  }
}
