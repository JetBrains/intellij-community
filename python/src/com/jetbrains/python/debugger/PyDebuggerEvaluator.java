// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.jetbrains.python.console.PyConsoleIndentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyDebuggerEvaluator extends XDebuggerEvaluator {

  private final Project myProject;
  private final PyFrameAccessor myDebugProcess;

  public PyDebuggerEvaluator(@NotNull Project project, final @NotNull PyFrameAccessor debugProcess) {
    myProject = project;
    myDebugProcess = debugProcess;
  }

  @Override
  public void evaluate(@NotNull String expression, @NotNull XEvaluationCallback callback, @Nullable XSourcePosition expressionPosition) {
    doEvaluate(expression, callback, true);
  }

  private PyDebugValue getNone() {
    return new PyDebugValue("", "NoneType", null, "None", false, null, null, false, false, false, null, null, myDebugProcess);
  }

  private void doEvaluate(final String expr, final XEvaluationCallback callback, final boolean doTrunc) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      String expression = expr.trim();
      if (expression.isEmpty()) {
        callback.evaluated(getNone());
        return;
      }

      final boolean isExpression = PyDebugSupportUtils.isExpression(myProject, expression);
      try {
        // todo: think on getting results from EXEC
        final PyDebugValue value = myDebugProcess.evaluate(expression, !isExpression, doTrunc);
        if (value.isErrorOnEval()) {
          callback.errorOccurred("{" + value.getType() + "}" + value.getValue()); //NON-NLS
        }
        else {
          callback.evaluated(value);
        }
      }
      catch (PyDebuggerException e) {
        callback.errorOccurred(e.getTracebackError());
      }
    });
  }

  @Override
  public @Nullable TextRange getExpressionRangeAtOffset(final Project project,
                                                        final Document document,
                                                        final int offset,
                                                        boolean sideEffectsAllowed) {
    return PyDebugSupportUtils.getExpressionRangeAtOffset(project, document, offset);
  }

  @Override
  public @NotNull String formatTextForEvaluation(@NotNull String text) {
    return PyConsoleIndentUtil.normalize(text);
  }
}
