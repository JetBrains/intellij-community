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
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.assertNotNull;
import static com.jetbrains.python.psi.PyUtil.as;

/**
 * User: catherine
 *
 * QuickFix to replace chained comparisons with more simple version
 * For instance, a < b and b < c  --> a < b < c
 */
public class ChainedComparisonsQuickFix implements LocalQuickFix {

  boolean myIsLeftLeft;
  boolean myIsRightLeft;
  boolean getInnerRight;

  /**
   * @param isLeft   true if common expression is on the left hand side of the left comparison
   * @param isRight  true if common expression is on the left hand side of the right comparison
   * @param getInner whether left comparison is deeper in PSI tree than the right comparison.
   *                 E.g. in {@code foo and x > 1 and x < 3} expressions {@code x > 1} and {@code x < 3} are targets for simplification but
   *                 because of associativity of {@code and} operator they are not siblings: {@code (foo and x > 1) and x < 3}
   */
  public ChainedComparisonsQuickFix(boolean isLeft, boolean isRight, boolean getInner) {
    myIsLeftLeft = isLeft;
    myIsRightLeft = isRight;
    getInnerRight = getInner;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.chained.comparison");
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PyBinaryExpression expression = as(descriptor.getPsiElement(), PyBinaryExpression.class);
    if (expression != null && expression.isWritable()) {
      final PyBinaryExpression rightExpression = as(expression.getRightExpression(), PyBinaryExpression.class);
      PyBinaryExpression leftExpression = as(expression.getLeftExpression(), PyBinaryExpression.class);
      if (rightExpression != null && leftExpression != null && isLogicalAndExpression(expression)) {
        if (getInnerRight && leftExpression.getRightExpression() instanceof PyBinaryExpression && isLogicalAndExpression(leftExpression)) {
          leftExpression = (PyBinaryExpression)leftExpression.getRightExpression();
        }
        checkOperator(assertNotNull(leftExpression), rightExpression, project);
      }
    }
  }

  private void checkOperator(@NotNull PyBinaryExpression leftExpression,
                             @NotNull PyBinaryExpression rightExpression,
                             @NotNull Project project) {
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    if (myIsLeftLeft) {
      final PyExpression newLeftExpression = invertExpression(leftExpression, elementGenerator);

      if (myIsRightLeft) {
        final PsiElement operator = getLeftestOperator(rightExpression);
        final PyBinaryExpression binaryExpression = elementGenerator.createBinaryExpression(
          operator.getText(), newLeftExpression, getLargeRightExpression(rightExpression, project));
        leftExpression.replace(binaryExpression);
        rightExpression.delete();
      }
      else {
        final String operator = invertOperator(assertNotNull(rightExpression.getPsiOperator()));
        final PyBinaryExpression binaryExpression = elementGenerator.createBinaryExpression(
          operator, newLeftExpression, rightExpression.getLeftExpression());
        leftExpression.replace(binaryExpression);
        rightExpression.delete();
      }
    }
    else {
      if (myIsRightLeft) {
        final PsiElement operator = getLeftestOperator(rightExpression);
        final PyBinaryExpression binaryExpression = elementGenerator.createBinaryExpression(
          operator.getText(), leftExpression, getLargeRightExpression(rightExpression, project));
        leftExpression.replace(binaryExpression);
        rightExpression.delete();
      }
      else {
        PyExpression expression = rightExpression.getLeftExpression();
        if (expression instanceof PyBinaryExpression) {
          expression = invertExpression((PyBinaryExpression)expression, elementGenerator);
        }
        final String operator = invertOperator(assertNotNull(rightExpression.getPsiOperator()));
        final PyBinaryExpression binaryExpression = elementGenerator.createBinaryExpression(
          operator, leftExpression, expression);
        leftExpression.replace(binaryExpression);
        rightExpression.delete();
      }
    }
  }

  @NotNull
  private static PsiElement getLeftestOperator(@NotNull PyBinaryExpression expression) {
    PsiElement op = expression.getPsiOperator();
    while (isComparisonExpression(expression.getLeftExpression())) {
      expression = (PyBinaryExpression)expression.getLeftExpression();
      op = expression.getPsiOperator();
    }
    assert op != null;
    return op;
  }

  @NotNull
  private static PyExpression invertExpression(@NotNull PyBinaryExpression leftExpression, @NotNull PyElementGenerator elementGenerator) {
    final PsiElement operator = leftExpression.getPsiOperator();
    final PyExpression right = leftExpression.getRightExpression();
    PyExpression left = leftExpression.getLeftExpression();
    if (isComparisonExpression(left)) {
      left = invertExpression((PyBinaryExpression)left, elementGenerator);
    }
    final String newOperator = invertOperator(assertNotNull(operator));
    return elementGenerator.createBinaryExpression(newOperator, right, left);
  }

  @NotNull
  private static String invertOperator(@NotNull PsiElement op) {
    if (op.getText().equals(">")) {
      return "<";
    }
    if (op.getText().equals("<")) {
      return ">";
    }
    if (op.getText().equals(">=")) {
      return "<=";
    }
    if (op.getText().equals("<=")) {
      return ">=";
    }
    return op.getText();
  }

  @Nullable
  static private PyExpression getLargeRightExpression(@NotNull PyBinaryExpression expression, @NotNull Project project) {
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PyExpression left = expression.getLeftExpression();
    PyExpression right = expression.getRightExpression();
    PsiElement operator = expression.getPsiOperator();
    while (isComparisonExpression(left)) {
      assert operator != null;
      right = elementGenerator.createBinaryExpression(operator.getText(), ((PyBinaryExpression)left).getRightExpression(), right);
      operator = ((PyBinaryExpression)left).getPsiOperator();
      left = ((PyBinaryExpression)left).getLeftExpression();
    }
    return right;
  }

  private static boolean isComparisonExpression(@Nullable PyExpression expression) {
    if (!(expression instanceof PyBinaryExpression)) {
      return false;
    }
    final PyElementType operator = ((PyBinaryExpression)expression).getOperator();
    return PyTokenTypes.RELATIONAL_OPERATIONS.contains(operator) || PyTokenTypes.EQUALITY_OPERATIONS.contains(operator);
  }

  private static boolean isLogicalAndExpression(@Nullable PyExpression expression) {
    return expression instanceof PyBinaryExpression && ((PyBinaryExpression)expression).getOperator() == PyTokenTypes.AND_KEYWORD;
  }
}
