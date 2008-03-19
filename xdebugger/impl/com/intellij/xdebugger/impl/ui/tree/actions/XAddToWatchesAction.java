package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class XAddToWatchesAction extends XDebuggerTreeActionBase {
  protected boolean isEnabled(final XValueNodeImpl node) {
    return super.isEnabled(node) && node.getValueContainer().getEvaluationExpression() != null;
  }

  protected void perform(final XValueNodeImpl node, @NotNull final String nodeName, final AnActionEvent e) {
    XDebugSession session = node.getTree().getSession();
    XDebugSessionTab sessionTab = ((XDebugSessionImpl)session).getSessionTab();
    sessionTab.getWatchesView().addWatchExpression(node.getValueContainer().getEvaluationExpression());
  }
}
