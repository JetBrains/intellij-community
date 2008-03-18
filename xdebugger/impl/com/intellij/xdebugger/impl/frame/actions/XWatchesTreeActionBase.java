package com.intellij.xdebugger.impl.frame.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNode;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;

import javax.swing.tree.TreePath;
import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
 */
public abstract class XWatchesTreeActionBase extends AnAction {
  protected static List<WatchNode> getSelectedWatches(final XDebuggerTree tree) {
    ArrayList<WatchNode> list = new ArrayList<WatchNode>();
    TreePath[] selectionPaths = tree.getSelectionPaths();
    if (selectionPaths != null) {
      for (TreePath selectionPath : selectionPaths) {
        Object element = selectionPath.getLastPathComponent();
        if (element instanceof WatchNode) {
          list.add((WatchNode)element);
        }
      }
    }
    return list;
  }

  public void update(final AnActionEvent e) {
    boolean enabled = isEnabled(e);
    e.getPresentation().setEnabled(enabled);
  }

  protected abstract boolean isEnabled(AnActionEvent e);
}
