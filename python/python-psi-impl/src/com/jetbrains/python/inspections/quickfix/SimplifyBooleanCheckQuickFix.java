// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

public class SimplifyBooleanCheckQuickFix extends PsiUpdateModCommandQuickFix {
  private final String myReplacementText;

  public SimplifyBooleanCheckQuickFix(PyBinaryExpression binaryExpression) {
    myReplacementText = createReplacementText(binaryExpression);
  }

  private static boolean isTrue(PyExpression expression) {
    return "True".equals(expression.getText());
  }

  private static boolean isFalse(PyExpression expression) {
    return "False".equals(expression.getText());
  }

  private static boolean isNull(PyExpression expression) {
    return "0".equals(expression.getText());
  }

  private static boolean isEmpty(PyExpression expression) {
    return "[]".equals(expression.getText());
  }

  @Override
  @NotNull
  public String getName() {
    return PyPsiBundle.message("QFIX.simplify.boolean.expression", myReplacementText);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.simplify.boolean.expression");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PyPsiUtils.assertValid(element);
    if (!element.isValid() || !(element instanceof PyBinaryExpression)) {
      return;
    }
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    element.replace(elementGenerator.createExpressionFromText(LanguageLevel.forElement(element), myReplacementText));
  }

  private static String createReplacementText(PyBinaryExpression expression) {
    PyExpression resultExpression;
    final PyExpression leftExpression = expression.getLeftExpression();
    final PyExpression rightExpression = expression.getRightExpression();
    boolean positiveCondition = !TokenSet.create(PyTokenTypes.NE, PyTokenTypes.NE_OLD).contains(expression.getOperator());
    positiveCondition ^= isFalse(leftExpression) || isFalse(rightExpression) || isNull(rightExpression) || isNull(leftExpression)
                         || isEmpty(rightExpression) || isEmpty(leftExpression);
    if (isTrue(leftExpression) || isFalse(leftExpression) || isNull(leftExpression) || isEmpty(leftExpression)) {
      resultExpression = rightExpression;
    } else {
      resultExpression = leftExpression;
    }
    return ((positiveCondition) ? "" : "not ") + resultExpression.getText();
  }
}
