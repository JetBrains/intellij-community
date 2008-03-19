package com.intellij.xdebugger.impl.frame;

import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeInplaceEditor;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchesRootNode;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author nik
 */
public class WatchInplaceEditor extends XDebuggerTreeInplaceEditor {
  private final WatchesRootNode myRootNode;

  public WatchInplaceEditor(WatchesRootNode rootNode, final XDebuggerTreeNode node, @NonNls final String historyId) {
    super(node, historyId);
    myRootNode = rootNode;
    myExpressionEditor.setText("");
  }

  protected JComponent createInplaceEditorComponent() {
    return myExpressionEditor.getComponent();
  }

  public void cancelEditing() {
    super.cancelEditing();
    myRootNode.removeChildNode(getNode());
  }

  public void doOKAction() {
    String expression = myExpressionEditor.getText();
    myExpressionEditor.saveTextInHistory();
    super.doOKAction();
    myRootNode.removeChildNode(getNode());
    if (!StringUtil.isEmpty(expression)) {
      XDebugSessionTab tab = ((XDebugSessionImpl)myRootNode.getTree().getSession()).getSessionTab();
      tab.getWatchesView().addWatchExpression(expression);
    }
  }
}
