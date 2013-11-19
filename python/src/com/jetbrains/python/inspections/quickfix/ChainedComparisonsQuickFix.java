/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    PsiElement expression = descriptor.getPsiElement();
    if (expression != null && expression.isWritable()) {
      if (expression instanceof PyBinaryExpression) {
        PyExpression leftExpression = ((PyBinaryExpression)expression).getLeftExpression();
        PyExpression rightExpression = ((PyBinaryExpression)expression).getRightExpression();
        if (rightExpression instanceof PyBinaryExpression && leftExpression instanceof PyBinaryExpression) {
          if (((PyBinaryExpression)expression).getOperator() == PyTokenTypes.AND_KEYWORD) {
            if (getInnerRight && ((PyBinaryExpression)leftExpression).getRightExpression() instanceof PyBinaryExpression
                && PyTokenTypes.AND_KEYWORD == ((PyBinaryExpression)leftExpression).getOperator()) {
              leftExpression = ((PyBinaryExpression)leftExpression).getRightExpression();
            }
            checkOperator((PyBinaryExpression)leftExpression, (PyBinaryExpression)rightExpression, project);
          }
        }
      }
    }
  }

  private void checkOperator(final PyBinaryExpression leftExpression,
                                                          final PyBinaryExpression rightExpression, final Project project) {
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
        final String operator = invertOperator(rightExpression.getPsiOperator());
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
        if (expression instanceof PyBinaryExpression)
          expression = invertExpression((PyBinaryExpression)expression, elementGenerator);
        final String operator = invertOperator(rightExpression.getPsiOperator());
        final PyBinaryExpression binaryExpression = elementGenerator.createBinaryExpression(
                    operator, leftExpression, expression);
        leftExpression.replace(binaryExpression);
        rightExpression.delete();
      }
    }

  }

  private PsiElement getLeftestOperator(PyBinaryExpression expression) {
    PsiElement op = expression.getPsiOperator();
    while (expression.getLeftExpression() instanceof PyBinaryExpression) {
      expression = (PyBinaryExpression)expression.getLeftExpression();
      op = expression.getPsiOperator();
    }
    assert op != null;
    return op;
  }

  private PyExpression invertExpression(PyBinaryExpression leftExpression, PyElementGenerator elementGenerator) {
    final PsiElement operator = leftExpression.getPsiOperator();
    final PyExpression right = leftExpression.getRightExpression();
    PyExpression left = leftExpression.getLeftExpression();
    if (left instanceof PyBinaryExpression){
      left = invertExpression((PyBinaryExpression)left, elementGenerator);
    }
    final String newOperator = invertOperator(operator);
    return elementGenerator.createBinaryExpression(
                newOperator, right, left);
  }

  private String invertOperator(PsiElement op) {
    if (op.getText().equals(">"))
      return "<";
    if (op.getText().equals("<"))
      return ">";
    if (op.getText().equals(">="))
      return "<=";
    if (op.getText().equals("<="))
      return ">=";
    return op.getText();
  }

  @Nullable
  static private PyExpression getLargeRightExpression(PyBinaryExpression expression, Project project) {
    final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PyExpression left = expression.getLeftExpression();
    PyExpression right = expression.getRightExpression();
    PsiElement operator = expression.getPsiOperator();
    while (left instanceof PyBinaryExpression) {
      assert operator != null;
      right = elementGenerator.createBinaryExpression(operator.getText(),
                                                      ((PyBinaryExpression)left).getRightExpression(),
                                                      right);
      operator = ((PyBinaryExpression)left).getPsiOperator();
      left = ((PyBinaryExpression)left).getLeftExpression();
    }
    return right;
  }

}
