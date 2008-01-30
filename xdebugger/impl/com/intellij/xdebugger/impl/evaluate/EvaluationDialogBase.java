package com.intellij.xdebugger.impl.evaluate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class EvaluationDialogBase extends DialogWrapper {
  private JPanel myMainPanel;
  private JPanel myResultPanel;
  private JPanel myInputPanel;

  protected EvaluationDialogBase(@NotNull Project project, String title) {
    super(project, true);
    setModal(false);
    setTitle(title);
    setOKButtonText(XDebuggerBundle.message("xdebugger.button.evaluate"));
    setCancelButtonText(XDebuggerBundle.message("xdebugger.evaluate.dialog.close"));
    init();
  }

  protected JPanel getInputPanel() {
    return myInputPanel;
  }

  protected JPanel getResultPanel() {
    return myResultPanel;
  }

  protected void doOKAction() {
    doEvaluate();
  }

  protected abstract void doEvaluate();

  protected JComponent createCenterPanel() {
    return myMainPanel;
  }
}
