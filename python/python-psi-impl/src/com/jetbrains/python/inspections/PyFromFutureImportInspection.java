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
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.quickfix.MoveFromFutureImportQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PyFromFutureImportInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  private static class Visitor extends PyInspectionVisitor {
    Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }
    @Override
    public void visitPyFromImportStatement(@NotNull PyFromImportStatement node) {
      PyReferenceExpression importSource = node.getImportSource();
      if (importSource != null && PyNames.FUTURE_MODULE.equals(importSource.getName())) {
        PsiFile file = importSource.getContainingFile();
        if (file instanceof PyFile) {
          final List<PyStatement> statementList = ((PyFile)file).getStatements();
          boolean skippedDocString = false;
          for (PyStatement statement : statementList) {
            if (statement instanceof PyExpressionStatement &&
                ((PyExpressionStatement) statement).getExpression() instanceof PyStringLiteralExpression &&
                !skippedDocString) {
              skippedDocString = true;
              continue;
            }
            if (statement instanceof PyFromImportStatement) {
              if (statement == node) {
                return;
              }
              PyReferenceExpression source = ((PyFromImportStatement)statement).getImportSource();
              if (source != null && PyNames.FUTURE_MODULE.equals(source.getName())) {
                continue;
              }
            }
            registerProblem(node, PyPsiBundle.message("INSP.from.future.import.from.future.imports.must.occur.at.beginning.file"),
                            new MoveFromFutureImportQuickFix());
            return;
          }
        }
      }
    }
  }
}
