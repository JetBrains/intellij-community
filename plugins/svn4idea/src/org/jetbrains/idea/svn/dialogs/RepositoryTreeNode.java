// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.CommonBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.browse.DirectoryEntry;
import org.jetbrains.idea.svn.dialogs.browserCache.Expander;
import org.jetbrains.idea.svn.dialogs.browserCache.NodeLoadState;

import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class RepositoryTreeNode implements TreeNode, Disposable {

  private TreeNode myParentNode;
  @NotNull private final List<TreeNode> myChildren;
  private final RepositoryTreeModel myModel;
  private final Url myURL;
  private final Object myUserObject;

  @NotNull private final NodeLoadState myLoadState;
  private NodeLoadState myChildrenLoadState;

  public RepositoryTreeNode(RepositoryTreeModel model, TreeNode parentNode,
                            @NotNull Url url, Object userObject, @NotNull NodeLoadState state) {
    myParentNode = parentNode;

    myURL = url;
    myModel = model;
    myUserObject = userObject;

    myLoadState = state;
    myChildren = ContainerUtil.newArrayList();
    myChildrenLoadState = NodeLoadState.EMPTY;
  }

  public RepositoryTreeNode(RepositoryTreeModel model, TreeNode parentNode, @NotNull Url url, Object userObject) {
    // created outside: only roots
    this(model, parentNode, url, userObject, NodeLoadState.REFRESHED);
  }

  public Object getUserObject() {
    return myUserObject;
  }

  public int getChildCount() {
    return getChildren().size();
  }

  public Enumeration children() {
    return Collections.enumeration(getChildren());
  }

  public TreeNode getChildAt(int childIndex) {
    return (TreeNode) getChildren().get(childIndex);
  }

  public int getIndex(TreeNode node) {
    return getChildren().indexOf(node);
  }

  public boolean getAllowsChildren() {
    return !isLeaf();
  }

  public boolean isLeaf() {
    return myUserObject instanceof DirectoryEntry && ((DirectoryEntry)myUserObject).isFile();
  }

  public TreeNode getParent() {
    return myParentNode;
  }

  public void reload(final boolean removeCurrentChildren) {
    // todo lazyLoading as explicit: keeping...
    reload(removeCurrentChildren ? myModel.getSelectionKeepingExpander() : myModel.getLazyLoadingExpander(), removeCurrentChildren);
  }

  @Nullable
  public TreeNode getNextChildByKey(final String key, final boolean isFolder) {
    final ByKeySelectedSearcher searcher = (isFolder) ? new FolderByKeySelectedSearcher(key, myChildren) :
                                                 new FileByKeySelectedSearcher(key, myChildren);
    return searcher.getNextSelectedByKey();
  }

  public String toString() {
    if (myParentNode instanceof RepositoryTreeRootNode) {
      return myURL.toString();
    }
    return myURL.getTail();
  }

  public void reload(@NotNull Expander expander, boolean removeCurrentChildren) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (removeCurrentChildren || NodeLoadState.EMPTY.equals(myChildrenLoadState)) {
      initChildren();
    }
    
    myModel.getCacheLoader().load(this, expander);
  }

  private void initChildren() {
    myChildren.clear();
    myChildren.add(new SimpleTextNode(CommonBundle.getLoadingTreeNodeText()));
    myChildrenLoadState = NodeLoadState.LOADING;
  }

  private List getChildren() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (NodeLoadState.EMPTY.equals(myChildrenLoadState)) {
      initChildren();
      myModel.getCacheLoader().load(this, myModel.getLazyLoadingExpander());
    }
    return myChildren;
  }

  public Url getURL() {
    return myURL;
  }

  @Nullable
  public DirectoryEntry getSVNDirEntry() {
    return myUserObject instanceof DirectoryEntry ? (DirectoryEntry)myUserObject : null;
  }

  public void dispose() {
  }

  public TreeNode[] getSelfPath() {
    return myModel.getPathToRoot(this);
  }

  public boolean isRepositoryRoot() {
    return ! (myUserObject instanceof DirectoryEntry);
  }

  @NotNull
  public List<TreeNode> getAllAlreadyLoadedChildren() {
    return ContainerUtil.newArrayList(myChildren);
  }

  @NotNull
  public List<RepositoryTreeNode> getAlreadyLoadedChildren() {
    return ContainerUtil.collect(myChildren.iterator(), FilteringIterator.instanceOf(RepositoryTreeNode.class));
  }

  public boolean isDisposed() {
    return myModel.isDisposed();
  }

  public void setChildren(@NotNull List<DirectoryEntry> children, @NotNull NodeLoadState state) {
    final List<TreeNode> nodes = new ArrayList<>();
    for (final DirectoryEntry entry : children) {
      if (!myModel.isShowFiles() && !entry.isDirectory()) {
        continue;
      }
      nodes.add(new RepositoryTreeNode(myModel, this, entry.getUrl(), entry, state));
    }

    myChildrenLoadState = state;
    myChildren.clear();
    myChildren.addAll(nodes);

    myModel.reload(this);
  }

  public void setParentNode(final TreeNode parentNode) {
    myParentNode = parentNode;
  }

  public void setAlienChildren(final List<TreeNode> children, final NodeLoadState oldState) {
    myChildren.clear();

    for (TreeNode child : children) {
      if (child instanceof RepositoryTreeNode) {
        ((RepositoryTreeNode) child).setParentNode(this);
        myChildren.add(child);
        myChildrenLoadState = oldState;
      }
      else if (child instanceof SimpleTextNode) {
        SimpleTextNode node = (SimpleTextNode)child;
        myChildren.add(new SimpleTextNode(node.getText(), node.isError()));
        myChildrenLoadState = oldState;
      }
    }

    myModel.reload(this);
  }

  public void setErrorNode(@NotNull String text) {
    myChildren.clear();
    myChildren.add(new SimpleTextNode(text, true));
    myChildrenLoadState = NodeLoadState.ERROR;
    myModel.reload(this);
  }

  public SvnVcs getVcs() {
    return myModel.getVCS();
  }

  public boolean isCached() {
    return NodeLoadState.CACHED.equals(myLoadState);
  }

  @Nullable
  public RepositoryTreeNode getNodeWithSamePathUnderModelRoot() {
    return myModel.findByUrl(this);
  }

  public NodeLoadState getChildrenLoadState() {
    return myChildrenLoadState;
  }

  public void doOnSubtree(@NotNull NotNullFunction<RepositoryTreeNode, Object> function) {
    new SubTreeWalker(this, function).execute();
  }

  private static class SubTreeWalker {

    @NotNull private final RepositoryTreeNode myNode;
    @NotNull private final NotNullFunction<RepositoryTreeNode, Object> myFunction;

    private SubTreeWalker(@NotNull RepositoryTreeNode node, @NotNull NotNullFunction<RepositoryTreeNode, Object> function) {
      myNode = node;
      myFunction = function;
    }

    public void execute() {
      executeImpl(myNode);
    }

    private void executeImpl(final RepositoryTreeNode node) {
      myFunction.fun(node);
      for (RepositoryTreeNode child : node.getAlreadyLoadedChildren()) {
        myFunction.fun(child);
      }
    }
  }
}
