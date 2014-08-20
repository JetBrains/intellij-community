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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.browserCache.*;
import org.tmatesoft.svn.core.SVNURL;

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

  public void setRoots(SVNURL[] urls) {
    final RepositoryTreeRootNode rootNode = new RepositoryTreeRootNode(this, urls);
    Disposer.register(this, rootNode);
    setRoot(rootNode);
  }

  public void setSingleRoot(SVNURL url) {
    final RepositoryTreeNode rootNode = new RepositoryTreeNode(this, null, url, url);
    Disposer.register(this, rootNode);
    setRoot(rootNode);
  }

  private boolean hasRoot(SVNURL url) {
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

  public void addRoot(SVNURL url) {
    if (!hasRoot(url)) {
      ((RepositoryTreeRootNode) getRoot()).addRoot(url);
    }
  }

  public void removeRoot(SVNURL url) {
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
  private RepositoryTreeNode getChild(final RepositoryTreeNode node, final SVNURL url) {
    final List<RepositoryTreeNode> children = node.getAlreadyLoadedChildren();
    for (RepositoryTreeNode child : children) {
      if (child.getURL().equals(url)) {
        return child;
      }
    }
    return null;
  }
}
