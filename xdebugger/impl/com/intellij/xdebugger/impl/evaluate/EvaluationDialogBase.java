package com.intellij.xdebugger.impl.evaluate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreePanel;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public abstract class EvaluationDialogBase extends DialogWrapper {
  private JPanel myMainPanel;
  private JPanel myResultPanel;
  private JPanel myInputPanel;
  private XDebuggerTreePanel myTreePanel;
  private final XSuspendContext mySuspendContext;

  protected EvaluationDialogBase(@NotNull Project project, String title, final XSuspendContext suspendContext) {
    super(project, true);
    mySuspendContext = suspendContext;
    setModal(false);
    setTitle(title);
    setOKButtonText(XDebuggerBundle.message("xdebugger.button.evaluate"));
    setCancelButtonText(XDebuggerBundle.message("xdebugger.evaluate.dialog.close"));
    myTreePanel = new XDebuggerTreePanel();
    myTreePanel.getTree().setRootVisible(true);
    myResultPanel.add(myTreePanel.getMainPanel(), BorderLayout.CENTER);
    init();
  }

  protected JPanel getInputPanel() {
    return myInputPanel;
  }

  protected JPanel getResultPanel() {
    return myResultPanel;
  }

  protected void doOKAction() {
    evaluate();
  }

  protected void evaluate() {
    XValue result = doEvaluate();
    XDebuggerTree tree = myTreePanel.getTree();
    tree.setRoot(new XValueNodeImpl(tree, null, result));
    myResultPanel.invalidate();
  }

  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  protected XDebuggerEvaluator getEvaluator() {
    return mySuspendContext.getEvaluator();
  }

  protected abstract XValue doEvaluate();
}
