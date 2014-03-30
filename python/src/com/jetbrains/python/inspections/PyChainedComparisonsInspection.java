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
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.inspections.quickfix.ChainedComparisonsQuickFix;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: catherine
 *
 * Inspection to detect chained comparisons which can be simplified
 * For instance, a < b and b < c  -->  a < b < c
 */
public class PyChainedComparisonsInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.chained.comparisons");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    boolean myIsLeft;
    boolean myIsRight;
    PyElementType myOperator;
    boolean getInnerRight;

    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyBinaryExpression(final PyBinaryExpression node){
      myIsLeft = false;
      myIsRight = false;
      myOperator = null;
      getInnerRight = false;

      PyExpression leftExpression = node.getLeftExpression();
      PyExpression rightExpression = node.getRightExpression();

      if (leftExpression instanceof PyBinaryExpression &&
                        rightExpression instanceof PyBinaryExpression) {
        if (node.getOperator() == PyTokenTypes.AND_KEYWORD) {
          if (isRightSimplified((PyBinaryExpression)leftExpression, (PyBinaryExpression)rightExpression) ||
              isLeftSimplified((PyBinaryExpression)leftExpression, (PyBinaryExpression)rightExpression))
            registerProblem(node, "Simplify chained comparison", new ChainedComparisonsQuickFix(myIsLeft, myIsRight,
                                                                                                getInnerRight));
        }
      }
    }

    private boolean isRightSimplified(@NotNull final PyBinaryExpression leftExpression,
                                      @NotNull final PyBinaryExpression rightExpression) {
      final PyExpression leftRight = leftExpression.getRightExpression();
      if (leftRight instanceof PyBinaryExpression && PyTokenTypes.AND_KEYWORD == leftExpression.getOperator()) {
        if (isRightSimplified((PyBinaryExpression)leftRight, rightExpression)) {
          getInnerRight = true;
          return true;
        }
      }

      if (leftRight instanceof PyBinaryExpression &&
          PyTokenTypes.RELATIONAL_OPERATIONS.contains(((PyBinaryExpression)leftRight).getOperator())){
        if (isRightSimplified((PyBinaryExpression)leftRight, rightExpression))
          return true;
      }

      myOperator = leftExpression.getOperator();
      if (PyTokenTypes.RELATIONAL_OPERATIONS.contains(myOperator)) {
        if (leftRight != null) {
          if (leftRight.getText().equals(getLeftExpression(rightExpression, true).getText())) {
            myIsLeft = false;
            myIsRight = true;
            return true;
          }

          final PyExpression right = getSmallestRight(rightExpression, true);
          if (right != null && leftRight.getText().equals(right.getText())) {
            myIsLeft = false;
            myIsRight = false;
            return true;
          }
        }
      }
      return false;
    }

    private static boolean isOpposite(final PyElementType op1, final PyElementType op2) {
      if ((op1 == PyTokenTypes.GT || op1 == PyTokenTypes.GE) && (op2 == PyTokenTypes.LT || op2 == PyTokenTypes.LE))
        return true;
      if ((op2 == PyTokenTypes.GT || op2 == PyTokenTypes.GE) && (op1 == PyTokenTypes.LT || op1 == PyTokenTypes.LE))
        return true;

      return false;
    }


    private boolean isLeftSimplified(PyBinaryExpression leftExpression, PyBinaryExpression rightExpression) {
      final PyExpression leftLeft = leftExpression.getLeftExpression();
      final PyExpression leftRight = leftExpression.getRightExpression();
      if (leftRight instanceof PyBinaryExpression
          && PyTokenTypes.AND_KEYWORD == leftExpression.getOperator()) {
        if (isLeftSimplified((PyBinaryExpression)leftRight, rightExpression)) {
          getInnerRight = true;
          return true;
        }
      }

      if (leftLeft instanceof PyBinaryExpression &&
        PyTokenTypes.RELATIONAL_OPERATIONS.contains(((PyBinaryExpression)leftLeft).getOperator())){
        if (isLeftSimplified((PyBinaryExpression)leftLeft, rightExpression))
          return true;
      }

      myOperator = leftExpression.getOperator();
      if (PyTokenTypes.RELATIONAL_OPERATIONS.contains(myOperator)) {
        if (leftLeft != null) {
          if (leftLeft.getText().equals(getLeftExpression(rightExpression, false).getText())) {
            myIsLeft = true;
            myIsRight = true;
            return true;
          }
          final PyExpression right = getSmallestRight(rightExpression, false);
          if (right != null && leftLeft.getText().equals(right.getText())) {
            myIsLeft = true;
            myIsRight = false;
            return true;
          }
        }
      }
      return false;
    }

    private PyExpression getLeftExpression(PyBinaryExpression expression, boolean isRight) {
      PyExpression result = expression;
      while (result instanceof PyBinaryExpression && (
        PyTokenTypes.RELATIONAL_OPERATIONS.contains(((PyBinaryExpression)result).getOperator())
        || PyTokenTypes.EQUALITY_OPERATIONS.contains(((PyBinaryExpression)result).getOperator()))) {

        final boolean opposite = isOpposite(((PyBinaryExpression)result).getOperator(), myOperator);
        if ((isRight && opposite) || (!isRight && !opposite))
          break;
        result = ((PyBinaryExpression)result).getLeftExpression();
      }
      return result;
    }

    @Nullable
    private PyExpression getSmallestRight(PyBinaryExpression expression, boolean isRight) {
      PyExpression result = expression;
      while (result instanceof PyBinaryExpression && (
        PyTokenTypes.RELATIONAL_OPERATIONS.contains(((PyBinaryExpression)result).getOperator())
        || PyTokenTypes.EQUALITY_OPERATIONS.contains(((PyBinaryExpression)result).getOperator()))) {

        final boolean opposite = isOpposite(((PyBinaryExpression)result).getOperator(), myOperator);
        if ((isRight && !opposite) || (!isRight && opposite))
          break;
        result = ((PyBinaryExpression)result).getRightExpression();
      }
      return result;
    }
  }
}
