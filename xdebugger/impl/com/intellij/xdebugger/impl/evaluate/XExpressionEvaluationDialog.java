package com.intellij.xdebugger.impl.evaluate;

import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.ui.XDebuggerEditorBase;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionComboBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class XExpressionEvaluationDialog extends EvaluationDialogBase {
  private XDebuggerExpressionComboBox myExpressionComboBox;

  public XExpressionEvaluationDialog(@NotNull final XDebugSession session, XDebuggerEditorsProvider editorsProvider, @NotNull XStackFrame stackFrame) {
    super(session, XDebuggerBundle.message("xdebugger.dialog.title.evaluate.expression"), editorsProvider, stackFrame);
    getInputPanel().add(new JLabel(XDebuggerBundle.message("xdebugger.evaluate.label.expression")), BorderLayout.WEST);
    myExpressionComboBox = new XDebuggerExpressionComboBox(session.getProject(), editorsProvider, "evaluateExpression", stackFrame.getSourcePosition());
    myExpressionComboBox.getComboBox().setMinimumAndPreferredWidth(250);
    getInputPanel().add(myExpressionComboBox.getComponent(), BorderLayout.CENTER);
    myExpressionComboBox.selectAll();
  }

  protected XDebuggerEditorBase getInputEditor() {
    return myExpressionComboBox;
  }

  public void startEvaluation(final XDebuggerEvaluator.XEvaluationCallback callback) {
    myExpressionComboBox.saveTextInHistory();
    String expression = myExpressionComboBox.getText();
    getEvaluator().evaluate(expression, callback);
  }

}
