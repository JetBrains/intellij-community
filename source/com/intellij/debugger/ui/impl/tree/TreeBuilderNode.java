package com.intellij.debugger.ui.impl.tree;

import com.intellij.openapi.diagnostic.Logger;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.Enumeration;

/**
 * User: lex
 * Date: Sep 10, 2003
 * Time: 7:01:02 PM
 */
public abstract class TreeBuilderNode extends DefaultMutableTreeNode{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.tree.TreeBuilderNode");
  private boolean                    myChildrenBuilt = false;

  public TreeBuilderNode(Object userObject) {
    super(userObject);
  }

  abstract protected TreeBuilder getTreeBuilder();

  public void removeAllChildren() {
    myChildrenBuilt = true;
    super.removeAllChildren();
  }

  public void add(MutableTreeNode newChild) {
    myChildrenBuilt = true;
    super.add(newChild);
  }

  public void remove(MutableTreeNode aChild) {
    myChildrenBuilt = true;
    super.remove(aChild);
  }

  public void insert(MutableTreeNode newChild, int childIndex) {
    myChildrenBuilt = true;
    super.insert(newChild, childIndex);
  }

  private void checkChildren() {
    if(!myChildrenBuilt) {
      myChildrenBuilt = true;
      if(getTreeBuilder().isExpandable(this)) {
        getTreeBuilder().buildChildren(this);
      }
    }
  }

  public void clear() {
    removeAllChildren();
    myChildrenBuilt = false;
  }

  //TreeNode interface
  public int getChildCount() {
    checkChildren();
    return super.getChildCount();
  }

  public boolean getAllowsChildren() {
    checkChildren();
    return super.getAllowsChildren();
  }

  public boolean isLeaf() {
    return !getTreeBuilder().isExpandable(this);
  }

  public Enumeration children() {
    checkChildren();
    return super.children();
  }

  public Enumeration rawChildren() {
    return super.children();
  }

  public TreeNode getChildAt(int childIndex) {
    checkChildren();
    return super.getChildAt(childIndex);
  }

  public int getIndex(TreeNode node) {
    checkChildren();
    return super.getIndex(node);
  }

  public boolean isChildrenBuilt() {
    return myChildrenBuilt;
  }
}
