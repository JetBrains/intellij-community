package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.evaluate.XDebuggerEvaluationDialog;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("TestOnlyProblems")
public class EvaluateExpressionCompletionCommand extends CompletionCommand {
  public static final String NAME = "doCompleteInEvaluateExpression";
  public static final String PREFIX = CMD_PREFIX + NAME;
  public EvaluateExpressionCompletionCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  public Editor getEditor(Project project) {
    XDebuggerEvaluationDialog currentDialog = ShowEvaluateExpressionCommand.Companion.getCurrentDialog();
    if (currentDialog == null) {
      throw new IllegalStateException("XDebuggerEvaluationDialog is null, try to execute ShowEvaluateExpressionCommand");
    }
    Editor editor = currentDialog.getInputEditor().getEditor();
    if (editor == null) {
      throw new IllegalStateException("Editor is null for evaluate expression dialog");
    }
    return editor;
  }
}
