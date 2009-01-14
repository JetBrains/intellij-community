package com.intellij.xdebugger.impl.frame.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public abstract class XWatchesTreeActionBase extends AnAction {
  protected static <T extends XDebuggerTreeNode> List<? extends T> getSelectedNodes(final @NotNull XDebuggerTree tree, Class<T> nodeClass) {
    List<T> list = new ArrayList<T>();
    TreePath[] selectionPaths = tree.getSelectionPaths();
    if (selectionPaths != null) {
      for (TreePath selectionPath : selectionPaths) {
        Object element = selectionPath.getLastPathComponent();
        if (nodeClass.isInstance(element)) {
          list.add(nodeClass.cast(element));
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
