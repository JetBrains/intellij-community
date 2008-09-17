package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNode;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeInplaceEditor;
import com.intellij.xdebugger.impl.ui.tree.SetValueInplaceEditor;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class XSetValueAction extends XDebuggerTreeActionBase {
  @Override
  public void update(final AnActionEvent e) {
    super.update(e);
    XValueNodeImpl node = getSelectedNode(e.getDataContext());
    Presentation presentation = e.getPresentation();
    if (node instanceof WatchNode) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
    }
    else {
      presentation.setVisible(true);
    }
  }

  protected void perform(final XValueNodeImpl node, @NotNull final String nodeName, final AnActionEvent e) {
    XDebuggerTreeInplaceEditor editor = new SetValueInplaceEditor(node, nodeName);
    editor.show();
  }
}
