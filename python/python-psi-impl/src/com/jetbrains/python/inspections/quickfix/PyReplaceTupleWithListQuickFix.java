// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class PyReplaceTupleWithListQuickFix extends PsiUpdateModCommandQuickFix {
  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.make.list");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    assert element instanceof PyAssignmentStatement;
    PyExpression[] targets = ((PyAssignmentStatement)element).getTargets();
    if (targets.length == 1 && targets[0] instanceof PySubscriptionExpression subscriptionExpression) {
      if (subscriptionExpression.getOperand() instanceof PyReferenceExpression referenceExpression) {
        final TypeEvalContext context = TypeEvalContext.userInitiated(project, element.getContainingFile());
        final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        element = updater.getWritable(referenceExpression.followAssignmentsChain(resolveContext).getElement());
        if (element instanceof PyParenthesizedExpression) {
          final PyExpression expression = ((PyParenthesizedExpression)element).getContainedExpression();
          replaceWithListLiteral(element, (PyTupleExpression)expression);
        }
        else if (element instanceof PyTupleExpression) {
          replaceWithListLiteral(element, (PyTupleExpression)element);
        }
      }
    }
  }

  private static void replaceWithListLiteral(PsiElement element, PyTupleExpression expression) {
    final String expressionText = expression.isEmpty() ? "" : expression.getText();
    final PyExpression literal = PyElementGenerator.getInstance(element.getProject()).
      createExpressionFromText(LanguageLevel.forElement(element),
                               "[" + expressionText + "]");
    element.replace(literal);
  }
}
