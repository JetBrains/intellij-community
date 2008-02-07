package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeInplaceEditor;
import com.intellij.xdebugger.impl.ui.tree.SetValueInplaceEditor;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class XSetValueAction extends XDebuggerTreeActionBase {
  protected void perform(final XValueNodeImpl node, @NotNull final String nodeName, final AnActionEvent e) {
    XDebuggerTreeInplaceEditor editor = new SetValueInplaceEditor(node, nodeName);
    editor.show();
  }
}
