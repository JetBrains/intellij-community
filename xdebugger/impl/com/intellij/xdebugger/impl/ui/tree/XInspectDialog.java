package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author nik
 */
public class XInspectDialog extends DialogWrapper {
  private XDebuggerTreePanel myTreePanel;

  public XInspectDialog(final Project project, XDebuggerEditorsProvider editorsProvider, XSourcePosition sourcePosition, String nodeName, XValue value) {
    super(project, false);
    setTitle(XDebuggerBundle.message("inspect.value.dialog.title", nodeName));
    setModal(false);
    myTreePanel = new XDebuggerTreePanel(project, editorsProvider, sourcePosition, XDebuggerActions.INSPECT_TREE_POPUP_GROUP);
    XDebuggerTree tree = myTreePanel.getTree();
    tree.setRoot(new XValueNodeImpl(tree, null, value), true);
    init();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myTreePanel.getMainPanel();
  }

  @Nullable
  protected JComponent createSouthPanel() {
    return null;
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "#xdebugger.XInspectDialog";
  }

  protected void dispose() {
    myTreePanel.dispose();
    super.dispose();
  }
}
