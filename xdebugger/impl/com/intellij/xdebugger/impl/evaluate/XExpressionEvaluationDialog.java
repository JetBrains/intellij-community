package com.intellij.xdebugger.impl.evaluate;

import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionComboBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class XExpressionEvaluationDialog extends EvaluationDialogBase {
  private XDebuggerExpressionComboBox myExpressionComboBox;

  public XExpressionEvaluationDialog(@NotNull final Project project, XDebuggerEditorsProvider editorsProvider, XSuspendContext suspendContext,
                                     final @Nullable XSourcePosition sourcePosition) {
    super(project, XDebuggerBundle.message("xdebugger.dialog.title.evaluate.expression"), suspendContext);
    getInputPanel().add(new JLabel(XDebuggerBundle.message("xdebugger.evaluate.label.expression")), BorderLayout.WEST);
    myExpressionComboBox = new XDebuggerExpressionComboBox(project, editorsProvider, "evaluateExpression", sourcePosition);
    getInputPanel().add(myExpressionComboBox.getComponent(), BorderLayout.CENTER);
    myExpressionComboBox.selectAll();
  }

  public JComponent getPreferredFocusedComponent() {
    return myExpressionComboBox.getPreferredFocusedComponent();
  }

  protected XValue doEvaluate() {
    myExpressionComboBox.saveTextInHistory();
    String expression = myExpressionComboBox.getText();
    return getEvaluator().evaluate(expression);
  }

}
