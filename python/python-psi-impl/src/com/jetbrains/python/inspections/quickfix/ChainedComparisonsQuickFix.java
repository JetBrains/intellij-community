// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Objects;

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

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.chained.comparison");
  }

  @Override
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
      operator = invertOperator(Objects.requireNonNull(rightExpression.getPsiOperator()));
      final PyExpression rightLeftExpr = rightExpression.getLeftExpression();
      if (rightLeftExpr instanceof PyBinaryExpression) {
        newRightExpression = invertExpression((PyBinaryExpression)rightLeftExpr, elementGenerator);
      }
      else {
        newRightExpression = rightLeftExpr;
      }
    }
    final PyBinaryExpression binaryExpression = elementGenerator.createBinaryExpression(operator, newLeftExpression, newRightExpression);
    leftExpression.replace(makePsiConsistentBinaryExpression(project, binaryExpression));
    rightExpression.delete();
  }

  @NotNull
  private static PyExpression makePsiConsistentBinaryExpression(@NotNull Project project, @NotNull PyBinaryExpression binaryExpression) {
    final ArrayList<PyExpression> elements = new ArrayList<>();
    final ArrayList<String> operators = new ArrayList<>();
    collectExpressionsDfs(elements, operators, binaryExpression);
    PyExpression resultExpression = buildResultExpression(project, elements, operators);
    return ObjectUtils.chooseNotNull(resultExpression, binaryExpression);
  }

  private static void collectExpressionsDfs(@NotNull ArrayList<PyExpression> elements,
                                            @NotNull ArrayList<String> operators,
                                            @NotNull PyBinaryExpression expression) {
    final PyExpression rightExpr = expression.getRightExpression();
    if (rightExpr instanceof PyBinaryExpression && isComparisonExpression(rightExpr)) {
      collectExpressionsDfs(elements, operators, (PyBinaryExpression)rightExpr);
    }
    else {
      elements.add(rightExpr);
    }
    if (expression.getPsiOperator() != null) {
      operators.add(expression.getPsiOperator().getText());
    }
    final PyExpression leftExpr = expression.getLeftExpression();
    if (leftExpr instanceof PyBinaryExpression && isComparisonExpression(leftExpr)) {
      collectExpressionsDfs(elements, operators, (PyBinaryExpression)leftExpr);
    }
    else {
      elements.add(leftExpr);
    }
  }

  @Nullable
  private static PyExpression buildResultExpression(@NotNull Project project,
                                                    @NotNull ArrayList<PyExpression> elements,
                                                    @NotNull ArrayList<String> operators) {
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    final int size = elements.size();
    if (elements.isEmpty()) return null;
    PyExpression currentResult = elements.get(size - 1);
    int i = size - 2;
    while (i >= 0) {
      PyExpression nextElement = elements.get(i);
      String nextOperator = operators.get(i);
      currentResult = elementGenerator.createBinaryExpression(nextOperator, currentResult, nextElement);
      i--;
    }
    return currentResult;
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

      final String newOperator = invertOperator(Objects.requireNonNull(expression.getPsiOperator()));
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
