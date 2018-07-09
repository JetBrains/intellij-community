// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.dialogs.browserCache.*;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.util.Enumeration;
import java.util.List;

public class RepositoryTreeModel extends DefaultTreeModel implements Disposable {
  private boolean myIsDisposed;
  private final SvnVcs myVCS;
  private boolean myIsShowFiles;

  private final Loader myCacheLoader;
  private final RepositoryBrowserComponent myBrowser;
  private NotNullFunction<RepositoryBrowserComponent, Expander> myDefaultExpanderFactory;

  public RepositoryTreeModel(@NotNull SvnVcs vcs, boolean showFiles, final RepositoryBrowserComponent browser) {
    super(null);
    myVCS = vcs;
    myIsShowFiles = showFiles;
    myBrowser = browser;

    myCacheLoader = CacheLoader.getInstance();

    myDefaultExpanderFactory = new KeepingExpandedExpander.Factory();
  }

  public boolean isShowFiles() {
    return myIsShowFiles;
  }

  public void setShowFiles(boolean showFiles) {
    myIsShowFiles = showFiles;
  }

  public void setRoots(Url[] urls) {
    final RepositoryTreeRootNode rootNode = new RepositoryTreeRootNode(this, urls);
    Disposer.register(this, rootNode);
    setRoot(rootNode);
  }

  public void setSingleRoot(Url url) {
    final RepositoryTreeNode rootNode = new RepositoryTreeNode(this, null, url, url);
    Disposer.register(this, rootNode);
    setRoot(rootNode);
  }

  private boolean hasRoot(Url url) {
    if (getRoot()instanceof RepositoryTreeNode) {
      return ((RepositoryTreeNode) getRoot()).getUserObject().equals(url);
    }
    RepositoryTreeRootNode root = (RepositoryTreeRootNode) getRoot();
    for (int i = 0; i < root.getChildCount(); i++) {
      RepositoryTreeNode node = (RepositoryTreeNode) root.getChildAt(i);
      if (node.getUserObject().equals(url)) {
        return true;
      }
    }
    return false;
  }

  public TreeNode[] getPathToSubRoot(final TreeNode node) {
    final TreeNode[] path = getPathToRoot(node);
    final TreeNode[] result = new TreeNode[path.length - 1];
    System.arraycopy(path, 1, result, 0, path.length - 1);
    return result;
  }

  public void addRoot(Url url) {
    if (!hasRoot(url)) {
      ((RepositoryTreeRootNode) getRoot()).addRoot(url);
    }
  }

  public void removeRoot(Url url) {
    RepositoryTreeRootNode root = (RepositoryTreeRootNode) getRoot();
    for (int i = 0; i < root.getChildCount(); i++) {
      RepositoryTreeNode node = (RepositoryTreeNode) root.getChildAt(i);
      if (node.getUserObject().equals(url)) {
        root.remove(node);
      }
    }
  }

  public SvnVcs getVCS() {
    return myVCS;
  }

  public void dispose() {
    myIsDisposed = true;
  }

  public boolean isDisposed() {
    return myIsDisposed;
  }

  public Loader getCacheLoader() {
    return myCacheLoader;
  }

  @NotNull
  public Expander getLazyLoadingExpander() {
    return myDefaultExpanderFactory.fun(myBrowser);
  }

  @NotNull
  public Expander getSelectionKeepingExpander() {
    return new KeepingSelectionExpander(myBrowser);
  }

  public void setDefaultExpanderFactory(final NotNullFunction<RepositoryBrowserComponent, Expander> defaultExpanderFactory) {
    myDefaultExpanderFactory = defaultExpanderFactory;
  }

  @Nullable
  public RepositoryTreeNode findByUrl(final RepositoryTreeNode oldNode) {
    if (oldNode.getParent() == null) {
      return oldNode;
    }

    TreeNode[] oldPath = getPathToRoot(oldNode);
    if (! (oldPath[0] instanceof RepositoryTreeNode)) {
      final TreeNode[] result = new TreeNode[oldPath.length - 1];
      System.arraycopy(oldPath, 1, result, 0, oldPath.length - 1);
      oldPath = result;
    }

    TreeNode root = (TreeNode) getRoot();
    if (! (root instanceof RepositoryTreeNode)) {
      final Enumeration children = root.children();
      root = null;
      while (children.hasMoreElements()) {
        TreeNode node = (TreeNode) children.nextElement();
        if ((node instanceof RepositoryTreeNode) && (((RepositoryTreeNode) node).getURL().equals(((RepositoryTreeNode) oldPath[0]).getURL()))) {
          root = node;
          break;
        }
      }
    } else {
      if ((root == null) || (! ((RepositoryTreeNode) root).getURL().equals(((RepositoryTreeNode) oldPath[0]).getURL()))) {
        return null;
      }
    }
    if (root == null) {
      return null;
    }

    for (int i = 1; i < oldPath.length; i++) {
      final TreeNode treeNode = oldPath[i];
      if (root == null) {
        return null;
      }
      root = getChild((RepositoryTreeNode) root, ((RepositoryTreeNode) treeNode).getURL());
    }

    return (RepositoryTreeNode) root;
  }

  @Nullable
  private RepositoryTreeNode getChild(final RepositoryTreeNode node, final Url url) {
    final List<RepositoryTreeNode> children = node.getAlreadyLoadedChildren();
    for (RepositoryTreeNode child : children) {
      if (child.getURL().equals(url)) {
        return child;
      }
    }
    return null;
  }
}
