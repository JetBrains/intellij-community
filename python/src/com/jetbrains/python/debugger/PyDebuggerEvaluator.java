/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  public PyDebuggerEvaluator(@NotNull Project project, @NotNull final PyFrameAccessor debugProcess) {
    myProject = project;
    myDebugProcess = debugProcess;
  }

  @Override
  public void evaluate(@NotNull String expression, @NotNull XEvaluationCallback callback, @Nullable XSourcePosition expressionPosition) {
    doEvaluate(expression, callback, true);
  }

  private PyDebugValue getNone() {
    return new PyDebugValue("", "NoneType", null, "None", false, false, false, false, null, myDebugProcess);
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
          callback.errorOccurred("{" + value.getType() + "}" + value.getValue());
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

  @Nullable
  @Override
  public TextRange getExpressionRangeAtOffset(final Project project,
                                              final Document document,
                                              final int offset,
                                              boolean sideEffectsAllowed) {
    return PyDebugSupportUtils.getExpressionRangeAtOffset(project, document, offset);
  }

  @NotNull
  @Override
  public String formatTextForEvaluation(@NotNull String text) {
    return PyConsoleIndentUtil.normalize(text);
  }
}
