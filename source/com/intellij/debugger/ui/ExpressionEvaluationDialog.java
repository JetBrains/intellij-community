package com.intellij.debugger.ui;

import com.intellij.debugger.actions.EvaluateAction;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public class ExpressionEvaluationDialog extends EvaluationDialog {
  private JPanel myPanel;
  private JPanel myExpressionComboPlace;
  private JPanel myWatchViewPlace;

  private final SwitchAction mySwitchAction = new SwitchAction();

  public ExpressionEvaluationDialog(Project project, TextWithImportsImpl defaultExpression) {
    super(project, makeOnLine(defaultExpression));
    setTitle("Expression Evaluation");

    myWatchViewPlace.setLayout(new BorderLayout());
    myWatchViewPlace.add(getEvaluationPanel());
    myExpressionComboPlace.setLayout(new BorderLayout());
    myExpressionComboPlace.add(getExpressionCombo());

    KeyStroke expressionStroke = KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.ALT_MASK);
    KeyStroke resultStroke = KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.ALT_MASK);


    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        getExpressionCombo().requestFocus();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(expressionStroke), getRootPane());

    new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        getEvaluationPanel().getWatchTree().requestFocus();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(resultStroke), getRootPane());

    this.init();
  }

  protected DebuggerExpressionComboBox createEditor() {
    return new DebuggerExpressionComboBox(getProject(), PositionUtil.getContextElement(getDebuggerContext()), "evaluation");
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private static TextWithImportsImpl makeOnLine(TextWithImportsImpl text) {
    String initialExpression = text.getText();
    if (initialExpression != null) {
      int size = initialExpression.length();
      StringBuffer buf = new StringBuffer(size);
      for (int idx = 0; idx < size; idx++) {
        char ch = initialExpression.charAt(idx);
        if (ch != '\n' && ch != '\r') {
          buf.append(ch);
        }
      }
      text = text.createText(initialExpression);
    }
    return text;
  }

  protected void initDialogData(TextWithImportsImpl text) {
    super.initDialogData(text);
    getExpressionCombo().selectAll();
  }

  private DebuggerExpressionComboBox getExpressionCombo() {
    return (DebuggerExpressionComboBox)getEditor();
  }

  protected Action[] createActions() {
    return new Action[] { getOKAction(), getCancelAction(), mySwitchAction} ;
  }

  private class SwitchAction extends AbstractAction {
    public SwitchAction() {
      putValue(Action.NAME, "Code Fragment Mode");
    }

    public void actionPerformed(ActionEvent e) {
      final TextWithImportsImpl text = (TextWithImportsImpl)getEditor().getText();
      doCancelAction();
      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
        public void run() {
          EvaluateAction.showEvaluationDialog(getProject(), text, DebuggerSettings.EVALUATE_FRAGMENT);
        }
      });
    }
  }

}
