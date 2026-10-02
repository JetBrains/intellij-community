// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.inspections.quickfix.MoveFromFutureImportQuickFix;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public final class PyFromFutureImportInspection extends PyInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
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
                ((PyExpressionStatement)statement).getExpression() instanceof PyStringLiteralExpression &&
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
