package com.intellij.xdebugger.impl.evaluate;

import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionComboBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class XExpressionEvaluationDialog extends EvaluationDialogBase {
  private XDebuggerExpressionComboBox myExpressionComboBox;
  private JLabel myResultLabel;
  private final XSuspendContext mySuspendContext;

  public XExpressionEvaluationDialog(@NotNull final Project project, XDebuggerEditorsProvider editorsProvider, XSuspendContext suspendContext) {
    super(project, XDebuggerBundle.message("xdebugger.dialog.title.evaluate.expression"));
    mySuspendContext = suspendContext;
    getInputPanel().add(new JLabel(XDebuggerBundle.message("xdebugger.evaluate.label.expression")), BorderLayout.WEST);
    myExpressionComboBox = new XDebuggerExpressionComboBox(project, editorsProvider, "evaluateExpression");
    getInputPanel().add(myExpressionComboBox.getComponent(), BorderLayout.CENTER);
    myResultLabel = new JLabel("???");
    getResultPanel().add(myResultLabel, BorderLayout.NORTH);
    myExpressionComboBox.selectAll();
  }

  public JComponent getPreferredFocusedComponent() {
    return myExpressionComboBox.getPreferredFocusedComponent();
  }

  protected void doEvaluate() {
    myExpressionComboBox.saveTextInHistory();
    XDebuggerEvaluator evaluator = mySuspendContext.getEvaluator();
    String expression = myExpressionComboBox.getText();
    String result = evaluator.evaluateMessage(expression);
    myResultLabel.setText(expression + " = " + result);
    myResultLabel.invalidate();
  }
}
