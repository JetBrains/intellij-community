package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.ui.SimpleColoredText;
import com.intellij.util.enumeration.EmptyEnumeration;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
 */
public abstract class XDebuggerTreeNode implements TreeNode {
  protected final XDebuggerTree myTree;
  private TreeNode myParent;
  private boolean myLeaf;
  protected final SimpleColoredText myText = new SimpleColoredText();
  private Icon myIcon;
  private TreeNode[] myPath;

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

  protected void fireNodeChildrenChanged() {
    myTree.getTreeModel().nodeStructureChanged(this);
  }

  public XDebuggerTree getTree() {
    return myTree;
  }

  public TreeNode[] getPath() {
    if (myPath == null) {
      List<TreeNode> nodes = new ArrayList<TreeNode>();
      TreeNode node = this;
      while (node != null) {
        nodes.add(node);
        node = node.getParent();
      }
      Collections.reverse(nodes);
      myPath = nodes.toArray(new TreeNode[nodes.size()]);
    }
    return myPath;
  }
}
