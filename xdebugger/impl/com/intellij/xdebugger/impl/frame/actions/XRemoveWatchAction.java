package com.intellij.xdebugger.impl.frame.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebugSessionTab;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNode;

import java.util.List;

/**
 * @author nik
 */
public class XRemoveWatchAction extends XWatchesTreeActionBase {

  protected boolean isEnabled(final AnActionEvent e) {
    XDebuggerTree tree = XDebuggerTree.getTree(e);
    return tree != null && !getSelectedWatches(tree).isEmpty();
  }


  public void actionPerformed(final AnActionEvent e) {
    XDebuggerTree tree = XDebuggerTree.getTree(e);
    if (tree == null) return;

    List<WatchNode> watchNodes = getSelectedWatches(tree);
    XDebugSessionTab tab = ((XDebugSessionImpl)tree.getSession()).getSessionTab();
    tab.getWatchesView().removeWatches(watchNodes);
  }
}
