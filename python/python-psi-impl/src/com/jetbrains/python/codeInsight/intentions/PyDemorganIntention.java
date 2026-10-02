// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyPrefixExpression;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Internal
public final class PyDemorganIntention extends PsiUpdateModCommandAction<PyBinaryExpression> {
  PyDemorganIntention() {
    super(PyBinaryExpression.class);
  }

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("INTN.NAME.demorgan.law");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PyBinaryExpression element) {
    final PyElementType op = element.getOperator();
    if (op == PyTokenTypes.AND_KEYWORD || op == PyTokenTypes.OR_KEYWORD) {
      return super.getPresentation(context, element);
    }
    return null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PyBinaryExpression element, @NotNull ModPsiUpdater updater) {
    final PyElementType op = element.getOperator();
    assert op != null;
    final String converted = convertConjunctionExpression(element, op);
    replaceExpression(converted, element);
  }

  private static void replaceExpression(String newExpression, PyBinaryExpression expression) {
    PsiElement expressionToReplace = expression;
    String expString = "not(" + newExpression + ')';
    final PsiElement parent = expression.getParent().getParent();
    if (isNegation(parent)) {
      expressionToReplace = parent;
      expString = newExpression;
    }
    final PyElementGenerator generator = PyElementGenerator.getInstance(expression.getProject());
    final PyExpression newCall = generator.createExpressionFromText(LanguageLevel.forElement(expression), expString);
    expressionToReplace.replace(newCall);
    // codeStyleManager = expression.getManager().getCodeStyleManager()
    // TODO codeStyleManager.reformat(insertedElement)
  }

  private static @NotNull String convertConjunctionExpression(@NotNull PyBinaryExpression exp, @NotNull PyElementType tokenType) {
    final PyExpression lhs = exp.getLeftExpression();
    final String lhsText;
    final String rhsText;
    if (isConjunctionExpression(lhs, tokenType)) {
      lhsText = convertConjunctionExpression((PyBinaryExpression)lhs, tokenType);
    }
    else {
      lhsText = convertLeafExpression(lhs);
    }

    final PyExpression rhs = exp.getRightExpression();
    if (isConjunctionExpression(rhs, tokenType)) {
      rhsText = convertConjunctionExpression((PyBinaryExpression)rhs, tokenType);
    }
    else {
      rhsText = convertLeafExpression(rhs);
    }

    final String flippedConjunction = (tokenType == PyTokenTypes.AND_KEYWORD) ? " or " : " and ";
    return lhsText + flippedConjunction + rhsText;
  }

  private static @NotNull String convertLeafExpression(@Nullable PyExpression condition) {
    if (condition == null) {
      return "";
    }
    else if (isNegation(condition)) {
      final PyExpression negated = getNegated(condition);
      if (negated == null) {
        return "";
      }
      return negated.getText();
    }
    else {
      if (condition instanceof PyBinaryExpression) {
        return "not(" + condition.getText() + ")";
      }
      return "not " + condition.getText();
    }
  }

  private static @Nullable PyExpression getNegated(@NotNull PyExpression expression) {
    return ((PyPrefixExpression)expression).getOperand();  // TODO strip ()
  }

  private static boolean isConjunctionExpression(@Nullable PyExpression expression, @NotNull PyElementType tokenType) {
    if (expression instanceof PyBinaryExpression) {
      final PyElementType operator = ((PyBinaryExpression)expression).getOperator();
      return operator == tokenType;
    }
    return false;
  }

  private static boolean isNegation(@Nullable PsiElement expression) {
    if (!(expression instanceof PyPrefixExpression)) {
      return false;
    }
    final PyElementType op = ((PyPrefixExpression)expression).getOperator();
    return op == PyTokenTypes.NOT_KEYWORD;
  }
}
