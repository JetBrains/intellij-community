// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs.browserCache;

import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserComponent;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;

import javax.swing.tree.TreeNode;
import java.util.*;

public class KeepingExpandedExpander implements Expander {
  private final Expander mySelectionExpander;
  private final RepositoryBrowserComponent myBrowser;

  private Map<String, ChildrenData> myThirdLevel;
  private Collection<TreeNode> myExpanded;

  public KeepingExpandedExpander(final RepositoryBrowserComponent browser, final Expander selectionInstaller) {
    myBrowser = browser;
    mySelectionExpander = selectionInstaller;
  }

  public KeepingExpandedExpander(final RepositoryBrowserComponent browser) {
    this(browser, new KeepingSelectionExpander(browser));
  }

  @Override
  public void onBeforeRefresh(final RepositoryTreeNode node) {
    mySelectionExpander.onBeforeRefresh(node);

    myThirdLevel = new HashMap<>();
    myExpanded = new ArrayList<>();

    final Enumeration<TreeNode> children = node.children();
    while (children.hasMoreElements()) {
      final TreeNode treeNode = children.nextElement();
      if (treeNode instanceof RepositoryTreeNode) {
        final RepositoryTreeNode repositoryNode = (RepositoryTreeNode) treeNode;
        final List<TreeNode> thirdLevelChildren = repositoryNode.getAllAlreadyLoadedChildren();

        final String nodeName = repositoryNode.getSVNDirEntry().getName();

        if (! thirdLevelChildren.isEmpty()) {
          final boolean selfExpanded = myBrowser.isExpanded(repositoryNode);
          myThirdLevel.put(nodeName, new ChildrenData(selfExpanded, thirdLevelChildren, repositoryNode.getChildrenLoadState()));
          if (selfExpanded) {
            myExpanded.addAll(myBrowser.getExpandedSubTree(repositoryNode));
          }
        }
      }
    }
  }

  @Override
  public void onAfterRefresh(final RepositoryTreeNode node) {
    final Enumeration<TreeNode> children = node.children();
    while (children.hasMoreElements()) {
      final RepositoryTreeNode treeNode = (RepositoryTreeNode) children.nextElement();
      final String name = treeNode.getSVNDirEntry().getName();

      final ChildrenData thirdLevelLoaded = myThirdLevel.get(name);
      if (thirdLevelLoaded != null) {
        treeNode.setAlienChildren(thirdLevelLoaded.getChildren(), thirdLevelLoaded.getChildrenState());
        if (thirdLevelLoaded.isExpanded()) {
          myBrowser.expandNode(treeNode);
        }
      }
    }

    for (TreeNode expandedNode : myExpanded) {
      myBrowser.expandNode(expandedNode);
    }

    mySelectionExpander.onAfterRefresh(node);
  }

  public static class Factory implements NotNullFunction<RepositoryBrowserComponent, Expander> {
    @Override
    @NotNull
    public Expander fun(final RepositoryBrowserComponent repositoryBrowserComponent) {
      return new KeepingExpandedExpander(repositoryBrowserComponent);
    }
  }

  private static final class ChildrenData {
    private final boolean myExpanded;
    private final List<TreeNode> myChildren;
    private final NodeLoadState myChildrenState;

    private ChildrenData(final boolean expanded, final List<TreeNode> children, final NodeLoadState childrenState) {
      myExpanded = expanded;
      myChildren = children;
      myChildrenState = childrenState;
    }

    public List<TreeNode> getChildren() {
      return myChildren;
    }

    public NodeLoadState getChildrenState() {
      return myChildrenState;
    }

    public boolean isExpanded() {
      return myExpanded;
    }
  }
}
