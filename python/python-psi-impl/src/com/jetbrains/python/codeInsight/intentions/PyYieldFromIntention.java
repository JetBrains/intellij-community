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
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
