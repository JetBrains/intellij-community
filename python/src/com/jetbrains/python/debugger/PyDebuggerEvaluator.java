package com.jetbrains.python.debugger;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyDebuggerEvaluator extends XDebuggerEvaluator {

  private static final PyDebugValue NONE = new PyDebugValue("", "NoneType", "None", false, null, null);

  private final PyDebugProcess myDebugProcess;

  public PyDebuggerEvaluator(@NotNull final PyDebugProcess debugProcess) {
    myDebugProcess = debugProcess;
  }

  @Override
  public void evaluate(@NotNull String expression, XEvaluationCallback callback, @Nullable XSourcePosition expressionPosition) {
    doEvaluate(expression, callback, true);
  }

  private void doEvaluate(String expression, XEvaluationCallback callback, boolean doTrim) {
    expression = expression.trim();
    if ("".equals(expression)) {
      callback.evaluated(NONE);
      return;
    }

    final Project project = myDebugProcess.getSession().getProject();
    final boolean isExpression = PyDebugSupportUtils.isExpression(project, expression);
    try {
      // todo: think on getting results from EXEC
      final PyDebugValue value = myDebugProcess.evaluate(expression, !isExpression, doTrim);
      callback.evaluated(value);
    }
    catch (PyDebuggerException e) {
      callback.errorOccurred(e.getTracebackError());
    }
  }

  @Override
  public boolean isEvaluateOnCopy(String name, String value) {
    if (value != null && value.length() >1000) {
      return true;
    }

    return false;
  }

  @Override
  public void evaluateFull(@NotNull String expression, XEvaluationCallback callback, @Nullable XSourcePosition expressionPosition) {
    doEvaluate(expression, callback, false);
  }

  @Override
  public TextRange getExpressionRangeAtOffset(final Project project, final Document document, final int offset) {
    return PyDebugSupportUtils.getExpressionRangeAtOffset(project, document, offset);
  }
}
