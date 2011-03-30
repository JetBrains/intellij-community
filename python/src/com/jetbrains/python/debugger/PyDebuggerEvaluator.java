package com.jetbrains.python.debugger;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.jetbrains.python.console.PyConsoleIndentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyDebuggerEvaluator extends XDebuggerEvaluator {

  private static final PyDebugValue NONE = new PyDebugValue("", "NoneType", "None", false, false, null, null);

  private final PyDebugProcess myDebugProcess;

  public PyDebuggerEvaluator(@NotNull final PyDebugProcess debugProcess) {
    myDebugProcess = debugProcess;
  }

  @Override
  public void evaluate(@NotNull String expression, XEvaluationCallback callback, @Nullable XSourcePosition expressionPosition) {
    doEvaluate(expression, callback, true);
  }

  private void doEvaluate(String expression, XEvaluationCallback callback, boolean doTrunc) {
    expression = expression.trim();
    if ("".equals(expression)) {
      callback.evaluated(NONE);
      return;
    }

    final Project project = myDebugProcess.getSession().getProject();
    final boolean isExpression = PyDebugSupportUtils.isExpression(project, expression);
    try {
      // todo: think on getting results from EXEC
      final PyDebugValue value = myDebugProcess.evaluate(expression, !isExpression, doTrunc);
      if (value.isErrorOnEval()) {
        callback.errorOccurred("{" + value.getType() + "}" + value.getValue());
      }
      else {
        callback.evaluated(value);
      }
    }
    catch (PyDebuggerException e) {
      callback.errorOccurred(e.getTracebackError());
    }
  }

  @Nullable
  @Override
  public TextRange getExpressionRangeAtOffset(final Project project, final Document document, final int offset, boolean sideEffectsAllowed) {
    return PyDebugSupportUtils.getExpressionRangeAtOffset(project, document, offset);
  }

  @NotNull
  @Override
  public String formatTextForEvaluation(@NotNull String text) {
    return PyConsoleIndentUtil.normalize(text);
  }
}
