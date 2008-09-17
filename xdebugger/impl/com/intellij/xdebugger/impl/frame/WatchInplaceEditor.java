package com.intellij.xdebugger.impl.frame;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeInplaceEditor;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchesRootNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNode;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class WatchInplaceEditor extends XDebuggerTreeInplaceEditor {
  private final WatchesRootNode myRootNode;
  @Nullable private final WatchNode myOldNode;

  public WatchInplaceEditor(WatchesRootNode rootNode, final XDebuggerTreeNode node, @NonNls final String historyId, final @Nullable WatchNode oldNode) {
    super(node, historyId);
    myRootNode = rootNode;
    myOldNode = oldNode;
    myExpressionEditor.setText(oldNode != null ? oldNode.getExpression() : "");
  }

  protected JComponent createInplaceEditorComponent() {
    return myExpressionEditor.getComponent();
  }

  public void cancelEditing() {
    super.cancelEditing();
    int index = myRootNode.removeChildNode(getNode());
    if (myOldNode != null) {
      getWatchesView().addWatchExpression(myOldNode.getExpression(), index);
    }
  }

  public void doOKAction() {
    String expression = myExpressionEditor.getText();
    myExpressionEditor.saveTextInHistory();
    super.doOKAction();
    int index = myRootNode.removeChildNode(getNode());
    if (!StringUtil.isEmpty(expression)) {
      getWatchesView().addWatchExpression(expression, index);
    }
  }

  private XWatchesView getWatchesView() {
    XDebugSessionTab tab = ((XDebugSessionImpl)myRootNode.getTree().getSession()).getSessionTab();
    return tab.getWatchesView();
  }
}
