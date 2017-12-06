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
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 */
public class SimplifyBooleanCheckQuickFix implements LocalQuickFix {
  private String myReplacementText;

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

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.simplify.$0", myReplacementText);
  }

  @NotNull
  public String getFamilyName() {
    return "Simplify boolean expression";
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
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
