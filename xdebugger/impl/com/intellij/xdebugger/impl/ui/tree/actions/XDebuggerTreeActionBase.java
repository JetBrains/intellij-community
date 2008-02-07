package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;

/**
 * @author nik
 */
public abstract class XDebuggerTreeActionBase extends AnAction {
  public void actionPerformed(final AnActionEvent e) {
    XValueNodeImpl node = getSelectedNode(e);
    if (node == null) return;

    String nodeName = node.getName();
    if (nodeName == null) return;

    perform(node, nodeName, e);
  }

  protected abstract void perform(final XValueNodeImpl node, @NotNull String nodeName, final AnActionEvent e);


  public void update(final AnActionEvent e) {
    XValueNodeImpl node = getSelectedNode(e);
    e.getPresentation().setEnabled(node != null && isEnabled(node));
  }

  protected boolean isEnabled(final XValueNodeImpl node) {
    return node.getName() != null;
  }

  @Nullable
  private static XValueNodeImpl getSelectedNode(AnActionEvent e) {
    XDebuggerTree tree = getTree(e);
    if (tree == null) return null;

    TreePath path = tree.getSelectionPath();
    if (path == null) return null;

    Object node = path.getLastPathComponent();
    if (node instanceof XValueNodeImpl) {
      return (XValueNodeImpl)node;
    }
    return null;
  }

  @Nullable
  private static XDebuggerTree getTree(final AnActionEvent e) {
    return e.getData(XDebuggerTree.XDEBUGGER_TREE_KEY);
  }
}
