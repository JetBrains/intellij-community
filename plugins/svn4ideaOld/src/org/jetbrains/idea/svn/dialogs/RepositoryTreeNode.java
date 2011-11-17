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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.browserCache.Expander;
import org.jetbrains.idea.svn.dialogs.browserCache.NodeLoadState;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class RepositoryTreeNode implements TreeNode, Disposable {

  private TreeNode myParentNode;
  private List<TreeNode> myChildren;
  private final RepositoryTreeModel myModel;
  private final SVNURL myURL;
  private final Object myUserObject;

  private final NodeLoadState myLoadState;
  private NodeLoadState myChildrenLoadState;

  public RepositoryTreeNode(RepositoryTreeModel model, TreeNode parentNode,
                            @NotNull SVNURL url, Object userObject, final NodeLoadState state) {
    myParentNode = parentNode;

    myURL = url;
    myModel = model;
    myUserObject = userObject;

    myLoadState = state;
    myChildrenLoadState = NodeLoadState.EMPTY;
  }

  public RepositoryTreeNode(RepositoryTreeModel model, TreeNode parentNode, @NotNull SVNURL url, Object userObject) {
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
    return myUserObject instanceof SVNDirEntry ? ((SVNDirEntry) myUserObject).getKind() == SVNNodeKind.FILE : false;
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
    return SVNPathUtil.tail(myURL.getPath());
  }

  public void reload(final Expander expander, final boolean removeCurrentChildren) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (removeCurrentChildren || (myChildren == null)) {
      initChildren();
    }
    
    myModel.getCacheLoader().load(this, expander);
  }

  private void initChildren() {
    myChildren = new ArrayList<TreeNode>();
    myChildren.add(new DefaultMutableTreeNode("Loading"));
    myChildrenLoadState = NodeLoadState.LOADING;
  }

  private List getChildren() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    
    if (myChildren == null) {
      initChildren();
      myModel.getCacheLoader().load(this, myModel.getLazyLoadingExpander());
    }
    return myChildren;
  }

  public SVNURL getURL() {
    return myURL;
  }

  @Nullable
  public SVNDirEntry getSVNDirEntry() {
    if (myUserObject instanceof SVNDirEntry) {
      return (SVNDirEntry) myUserObject;
    }
    return null;
  }

  public void dispose() {
  }

  public TreeNode[] getSelfPath() {
    return myModel.getPathToRoot(this);
  }

  public boolean isRepositoryRoot() {
    return ! (myUserObject instanceof SVNDirEntry);
  }

  @NotNull
  public List<TreeNode> getAllAlreadyLoadedChildren() {
    if (myChildren != null) {
      final List<TreeNode> result = new ArrayList<TreeNode>(myChildren.size());
      for (TreeNode child : myChildren) {
        result.add(child);
      }
      return result;
    }
    return Collections.emptyList();
  }

  @NotNull
  public List<RepositoryTreeNode> getAlreadyLoadedChildren() {
    if (myChildren != null) {
      final List<RepositoryTreeNode> result = new ArrayList<RepositoryTreeNode>(myChildren.size());
      for (TreeNode child : myChildren) {
        if (child instanceof RepositoryTreeNode) {
          result.add((RepositoryTreeNode) child);
        }
      }
      return result;
    }
    return Collections.emptyList();
  }

  public boolean isDisposed() {
    return myModel.isDisposed();
  }

  public void setChildren(final List<SVNDirEntry> children, final NodeLoadState state) {
    final List<TreeNode> nodes = new ArrayList<TreeNode>();
    for (final SVNDirEntry entry : children) {
      if (!myModel.isShowFiles() && entry.getKind() != SVNNodeKind.DIR) {
        continue;
      }
      nodes.add(new RepositoryTreeNode(myModel, this, entry.getURL(), entry, state));
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
    if (myChildren == null) {
      myChildren = new ArrayList<TreeNode>();
    } else {
      myChildren.clear();
    }

    for (TreeNode child : children) {
      if (child instanceof RepositoryTreeNode) {
        ((RepositoryTreeNode) child).setParentNode(this);
        myChildren.add(child);
        myChildrenLoadState = oldState;
      } else if (child instanceof DefaultMutableTreeNode) {
        myChildren.add(new DefaultMutableTreeNode(((DefaultMutableTreeNode) child).getUserObject()));
        myChildrenLoadState = oldState;
      }
    }

    myModel.reload(this);
  }

  public void setErrorNode(final SVNErrorMessage text, final NodeLoadState state) {
    if (myChildren == null) {
      myChildren = new ArrayList<TreeNode>();
    }
    myChildren.clear();
    myChildren.add(new DefaultMutableTreeNode(text));

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

  public RepositoryTreeModel getModel() {
    return myModel;
  }

  public NodeLoadState getChildrenLoadState() {
    return myChildrenLoadState;
  }

  public void doOnSubtree(final NotNullFunction<RepositoryTreeNode, Object> function) {
    final SubTreeWalker walker = new SubTreeWalker(this, function);
    walker.execute();
  }

  private static class SubTreeWalker {
    private final RepositoryTreeNode myNode;
    private final NotNullFunction<RepositoryTreeNode, Object> myFunction;

    private SubTreeWalker(final RepositoryTreeNode node, final NotNullFunction<RepositoryTreeNode, Object> function) {
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
