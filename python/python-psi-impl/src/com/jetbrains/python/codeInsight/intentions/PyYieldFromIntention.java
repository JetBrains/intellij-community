// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyForPart;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyYieldExpression;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class PyYieldFromIntention extends PsiUpdateModCommandAction<PyForStatement> {
  PyYieldFromIntention() {
    super(PyForStatement.class);
  }

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("INTN.yield.from");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PyForStatement element) {
    if (!LanguageLevel.forElement(element).isPython2()) {
      final PyTargetExpression forTarget = findSingleForLoopTarget(element);
      final PyReferenceExpression yieldValue = findSingleYieldValue(element);
      if (forTarget != null && yieldValue != null) {
        final String targetName = forTarget.getName();
        if (targetName != null && targetName.equals(yieldValue.getName())) {
          return super.getPresentation(context, element);
        }
      }
    }
    return null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PyForStatement element, @NotNull ModPsiUpdater updater) {
    final PyExpression source = element.getForPart().getSource();
    if (source != null) {
      final PyElementGenerator generator = PyElementGenerator.getInstance(context.project());
      final String text = "yield from foo";
      final PyExpressionStatement exprStmt = generator.createFromText(LanguageLevel.forElement(element), PyExpressionStatement.class, text);
      final PyExpression expr = exprStmt.getExpression();
      if (expr instanceof PyYieldExpression) {
        final PyExpression yieldValue = ((PyYieldExpression)expr).getExpression();
        if (yieldValue != null) {
          yieldValue.replace(source);
          element.replace(exprStmt);
        }
      }
    }
  }

  private static @Nullable PyTargetExpression findSingleForLoopTarget(@NotNull PyForStatement forLoop) {
    final PyForPart forPart = forLoop.getForPart();
    final PyExpression forTarget = forPart.getTarget();
    if (forTarget instanceof PyTargetExpression) {
      return (PyTargetExpression)forTarget;
    }
    return null;
  }

  private static @Nullable PyReferenceExpression findSingleYieldValue(@NotNull PyForStatement forLoop) {
    final PyForPart forPart = forLoop.getForPart();
    if (forLoop.getElsePart() == null) {
      final PyStatement[] statements = forPart.getStatementList().getStatements();
      if (statements.length == 1) {
        final PyStatement firstStmt = statements[0];
        if (firstStmt instanceof PyExpressionStatement) {
          final PyExpression firstExpr = ((PyExpressionStatement)firstStmt).getExpression();
          if (firstExpr instanceof PyYieldExpression yieldExpr) {
            final PyExpression yieldValue = yieldExpr.getExpression();
            if (yieldValue instanceof PyReferenceExpression) {
              return (PyReferenceExpression)yieldValue;
            }
          }
        }
      }
    }
    return null;
  }
}
