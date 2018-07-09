/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

  private final boolean myCommonIsInLeftLeft;
  private final boolean myCommonIsInRightLeft;
  private final boolean myUseRightChildOfLeft;

  /**
   * @param commonIsInLeftLeft  true if common expression is on the left hand side of the left comparison
   * @param commonIsInRightLeft true if common expression is on the left hand side of the right comparison
   * @param useRightChildOfLeft whether left comparison is deeper in PSI tree than the right comparison.
   *                            E.g. in {@code foo and x > 1 and x < 3} expressions {@code x > 1} and {@code x < 3} are targets for simplification but
   *                            because of associativity of {@code and} operator they are not siblings: {@code (foo and x > 1) and x < 3}
   */
  public ChainedComparisonsQuickFix(boolean commonIsInLeftLeft, boolean commonIsInRightLeft, boolean useRightChildOfLeft) {
    myCommonIsInLeftLeft = commonIsInLeftLeft;
    myCommonIsInRightLeft = commonIsInRightLeft;
    myUseRightChildOfLeft = useRightChildOfLeft;
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("QFIX.chained.comparison");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PyBinaryExpression expression = as(descriptor.getPsiElement(), PyBinaryExpression.class);
    if (isLogicalAndExpression(expression) && expression.isWritable()) {
      final PyBinaryExpression rightExpression = as(expression.getRightExpression(), PyBinaryExpression.class);
      PyBinaryExpression leftExpression = as(expression.getLeftExpression(), PyBinaryExpression.class);
      if (isLogicalAndExpression(leftExpression)) {
        final PyExpression nested = myUseRightChildOfLeft ? leftExpression.getRightExpression() : leftExpression.getLeftExpression();
        leftExpression = as(nested, PyBinaryExpression.class);
      }

      if (isComparisonExpression(leftExpression) && isComparisonExpression(rightExpression)) {
        applyFix(leftExpression, rightExpression, project);
      }
    }
  }

  private void applyFix(@NotNull PyBinaryExpression leftExpression, @NotNull PyBinaryExpression rightExpression, @NotNull Project project) {
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    final PyExpression newLeftExpression, newRightExpression;
    final String operator;
    if (myCommonIsInLeftLeft) {
      newLeftExpression = invertExpression(leftExpression, elementGenerator);
    }
    else {
      newLeftExpression = leftExpression;
    }
    
    if (myCommonIsInRightLeft) {
      operator = getLeftestOperator(rightExpression).getText();
      newRightExpression = getLargeRightExpression(rightExpression, project);
    }
    else {
      operator = invertOperator(assertNotNull(rightExpression.getPsiOperator()));
      final PyExpression rightLeftExpr = rightExpression.getLeftExpression();
      if (rightLeftExpr instanceof PyBinaryExpression) {
        newRightExpression = invertExpression((PyBinaryExpression)rightLeftExpr, elementGenerator);
      }
      else {
        newRightExpression = rightLeftExpr;
      }
    }
    final PyBinaryExpression binaryExpression = elementGenerator.createBinaryExpression(operator, newLeftExpression, newRightExpression);
    leftExpression.replace(binaryExpression);
    rightExpression.delete();
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
  private static PyExpression invertExpression(@NotNull PyBinaryExpression expression, @NotNull PyElementGenerator elementGenerator) {
    if (isComparisonExpression(expression)) {
      final PyExpression left = expression.getLeftExpression();
      final PyExpression right = expression.getRightExpression();

      final String newOperator = invertOperator(assertNotNull(expression.getPsiOperator()));
      final PyExpression newRight = isComparisonExpression(left) ? invertExpression((PyBinaryExpression)left, elementGenerator) : left;

      return elementGenerator.createBinaryExpression(newOperator, right, newRight);
    }
    else {
      return expression;
    }
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
