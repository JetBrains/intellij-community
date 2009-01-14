package com.intellij.xdebugger.impl.frame.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;

import java.util.List;

/**
 * @author nik
 */
public class XRemoveWatchAction extends XWatchesTreeActionBase {

  protected boolean isEnabled(final AnActionEvent e) {
    XDebuggerTree tree = XDebuggerTree.getTree(e);
    return tree != null && !getSelectedNodes(tree, XDebuggerTreeNode.class).isEmpty();
  }


  public void actionPerformed(final AnActionEvent e) {
    XDebuggerTree tree = XDebuggerTree.getTree(e);
    if (tree == null) return;

    List<? extends XDebuggerTreeNode> nodes = getSelectedNodes(tree, XDebuggerTreeNode.class);
    XDebugSessionTab tab = ((XDebugSessionImpl)tree.getSession()).getSessionTab();
    tab.getWatchesView().removeWatches(nodes);
  }
}
