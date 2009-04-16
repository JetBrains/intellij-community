package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.ui.SimpleColoredText;
import com.intellij.util.enumeration.EmptyEnumeration;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

/**
 * @author nik
 */
public abstract class XDebuggerTreeNode implements TreeNode {
  protected final XDebuggerTree myTree;
  private final XDebuggerTreeNode myParent;
  private boolean myLeaf;
  protected final SimpleColoredText myText = new SimpleColoredText();
  private Icon myIcon;
  private TreePath myPath;

  protected XDebuggerTreeNode(final XDebuggerTree tree, final XDebuggerTreeNode parent, final boolean leaf) {
    myParent = parent;
    myLeaf = leaf;
    myTree = tree;
  }

  public TreeNode getChildAt(final int childIndex) {
    if (isLeaf()) return null;
    return getChildren().get(childIndex);
  }

  public int getChildCount() {
    return isLeaf() ? 0 : getChildren().size();
  }

  public TreeNode getParent() {
    return myParent;
  }

  public int getIndex(final TreeNode node) {
    if (isLeaf()) return -1;
    return getChildren().indexOf(node);
  }

  public boolean getAllowsChildren() {
    return true;
  }

  public boolean isLeaf() {
    return myLeaf;
  }

  public Enumeration children() {
    if (isLeaf()) {
      return EmptyEnumeration.INSTANCE;
    }
    return Collections.enumeration(getChildren());
  }

  protected abstract List<? extends TreeNode> getChildren();

  protected void setIcon(final Icon icon) {
    myIcon = icon;
  }

  public void setLeaf(final boolean leaf) {
    myLeaf = leaf;
  }

  @NotNull
  public SimpleColoredText getText() {
    return myText;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  protected void fireNodeChanged() {
    myTree.getTreeModel().nodeChanged(this);
  }

  protected void fireNodesRemoved(int[] indices, TreeNode[] nodes) {
    if (indices.length > 0) {
      myTree.getTreeModel().nodesWereRemoved(this, indices, nodes);
    }
  }

  protected void fireNodesInserted(Collection<? extends TreeNode> added) {
    if (!added.isEmpty()) {
      myTree.getTreeModel().nodesWereInserted(this, getNodesIndices(added));
    }
  }

  protected TreeNode[] getChildNodes(int[] indices) {
    final TreeNode[] children = new TreeNode[indices.length];
    for (int i = 0; i < indices.length; i++) {
      children[i] = getChildAt(indices[i]);
    }
    return children;
  }

  protected int[] getNodesIndices(@Nullable Collection<? extends TreeNode> children) {
    if (children == null) return new int[0];

    final int[] ints = new int[children.size()];
    int i = 0;
    for (TreeNode node : children) {
      ints[i++] = getIndex(node);
    }
    Arrays.sort(ints);
    return ints;
  }

  protected void fireNodeChildrenChanged() {
    myTree.getTreeModel().nodeStructureChanged(this);
  }

  public XDebuggerTree getTree() {
    return myTree;
  }

  public TreePath getPath() {
    if (myPath == null) {
      TreePath path;
      if (myParent == null) {
        path = new TreePath(this);
      }
      else {
        path = myParent.getPath().pathByAddingChild(this);
      }
      myPath = path;
    }
    return myPath;
  }

  @Nullable
  public abstract List<? extends XDebuggerTreeNode> getLoadedChildren();

  public abstract void clearChildren();
}
