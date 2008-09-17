package com.intellij.xdebugger.impl.frame.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchesRootNode;

import java.util.List;

/**
 * @author nik
 */
public class XEditWatchAction extends XWatchesTreeActionBase {
  @Override
  public void update(final AnActionEvent e) {
    XDebuggerTree tree = XDebuggerTree.getTree(e);
    e.getPresentation().setVisible(tree != null && getSelectedWatches(tree).size() == 1);
    super.update(e);
  }

  protected boolean isEnabled(final AnActionEvent e) {
    return true;
  }

  public void actionPerformed(final AnActionEvent e) {
    XDebuggerTree tree = XDebuggerTree.getTree(e);
    if (tree == null) return;

    List<WatchNode> watchNodes = getSelectedWatches(tree);
    if (watchNodes.size() != 1) return;

    WatchNode node = watchNodes.get(0);
    XDebuggerTreeNode root = tree.getRoot();
    if (root instanceof WatchesRootNode) {
      ((WatchesRootNode)root).editWatch(node);
    }
  }
}
