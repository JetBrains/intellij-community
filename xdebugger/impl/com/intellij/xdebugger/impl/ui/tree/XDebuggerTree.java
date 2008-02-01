package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.util.ui.Tree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;

import javax.swing.tree.DefaultTreeModel;

/**
 * @author nik
 */
public class XDebuggerTree extends Tree {
  private DefaultTreeModel myTreeModel;

  public XDebuggerTree() {
    myTreeModel = new DefaultTreeModel(null);
    setModel(myTreeModel);
    setCellRenderer(new XDebuggerTreeRenderer());
    setRootVisible(false);
    setShowsRootHandles(true);
  }

  public void setRoot(XDebuggerTreeNode node) {
    myTreeModel.setRoot(node);
  }

  public DefaultTreeModel getTreeModel() {
    return myTreeModel;
  }
}
