// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.modcommand.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: catherine
 * Intention to transform conditional expression into if/else statement
 * For instance,
 *
 * x = a if cond else b
 *
 * into:
 *
 * if cond:
 *    x = a
 * else:
 *    x = b
 */
public final class PyTransformConditionalExpressionIntention extends PsiUpdateModCommandAction<PyAssignmentStatement> {
  PyTransformConditionalExpressionIntention() {
    super(PyAssignmentStatement.class);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("INTN.transform.into.if.else.statement");
  }


  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PyAssignmentStatement element, @NotNull ModPsiUpdater updater) {
    final PyExpression assignedValue = element.getAssignedValue();
    if (assignedValue instanceof PyConditionalExpression expression) {
      final PyExpression condition = expression.getCondition();
      final PyExpression falsePart = expression.getFalsePart();
      if (condition != null && falsePart != null) {
        final String truePartText = expression.getTruePart().getText();
        final PyExpression leftHandSideExpression = element.getLeftHandSideExpression();
        if (leftHandSideExpression != null) {
          final String targetText = leftHandSideExpression.getText();
          final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(context.project());
          final String text = "if " + condition.getText() + ":\n\t" + targetText + " = " + truePartText
                              + "\nelse:\n\t" + targetText + " = " + falsePart.getText();
          final PyIfStatement ifStatement =
            elementGenerator.createFromText(LanguageLevel.forElement(expression), PyIfStatement.class, text);
          element.replace(ifStatement);
        }
      }
    }
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PyAssignmentStatement element) {
    PsiFile file = element.getContainingFile();
    if (!(file instanceof PyFile)) {
      return null;
    }

    if (element.getAssignedValue() instanceof PyConditionalExpression) {
      return super.getPresentation(context, element);
    }
    return null;
  }
}
