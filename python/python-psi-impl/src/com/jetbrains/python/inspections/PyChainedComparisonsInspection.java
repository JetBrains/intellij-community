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
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.UpdateInspectionOptionFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.inspections.quickfix.ChainedComparisonsQuickFix;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyLiteralExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.jetbrains.python.psi.PyUtil.as;

/**
 * User: catherine
 *
 * Inspection to detect chained comparisons which can be simplified
 * For instance, a < b and b < c  -->  a < b < c
 */
public final class PyChainedComparisonsInspection extends PyInspection {

  private static final String INSPECTION_SHORT_NAME = "PyChainedComparisonsInspection";
  public boolean ignoreConstantInTheMiddle = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreConstantInTheMiddle", PyPsiBundle.message("INSP.chained.comparisons.ignore.statements.with.constant.in.the.middle"))
    );
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, ignoreConstantInTheMiddle, PyInspectionVisitor.getContext(session));
  }

  private class Visitor extends PyInspectionVisitor {
    /**
     * @see ChainedComparisonsQuickFix#ChainedComparisonsQuickFix(boolean, boolean, boolean)
     */
    boolean myIsLeft;
    boolean myIsRight;
    PyElementType myOperator;
    boolean getInnerRight;
    boolean isConstantInTheMiddle;
    boolean ignoreConstantInTheMiddle;

    Visitor(@Nullable ProblemsHolder holder,
            boolean ignoreConstantInTheMiddle,
            @NotNull TypeEvalContext context) {
      super(holder, context);
      this.ignoreConstantInTheMiddle = ignoreConstantInTheMiddle;
    }

    @Override
    public void visitPyBinaryExpression(final @NotNull PyBinaryExpression node) {
      myIsLeft = false;
      myIsRight = false;
      myOperator = null;
      getInnerRight = false;

      final PyBinaryExpression leftExpression = as(node.getLeftExpression(), PyBinaryExpression.class);
      final PyBinaryExpression rightExpression = as(node.getRightExpression(), PyBinaryExpression.class);

      if (leftExpression != null && rightExpression != null && node.getOperator() == PyTokenTypes.AND_KEYWORD) {
        boolean applicable = false;
        if (leftExpression.getOperator() == PyTokenTypes.AND_KEYWORD) {
          final PyBinaryExpression leftLeft = as(leftExpression.getLeftExpression(), PyBinaryExpression.class);
          final PyBinaryExpression leftRight = as(leftExpression.getRightExpression(), PyBinaryExpression.class);
          if (leftLeft != null && (isRightSimplified(leftLeft, rightExpression) || isLeftSimplified(leftLeft, rightExpression))) {
            applicable = true;
            getInnerRight = false;
          }
          else if (leftRight != null && (isRightSimplified(leftRight, rightExpression) || isLeftSimplified(leftRight, rightExpression))) {
            applicable = true;
            getInnerRight = true;
          }
        }
        else if (isRightSimplified(leftExpression, rightExpression) || isLeftSimplified(leftExpression, rightExpression)) {
          applicable = true;
        }

        if (applicable) {
          if (isConstantInTheMiddle) {
            if (!ignoreConstantInTheMiddle) {
              registerProblem(node, PyPsiBundle.message("INSP.simplify.chained.comparison"),
                              new ChainedComparisonsQuickFix(myIsLeft, myIsRight, getInnerRight),
                              LocalQuickFix.from(new UpdateInspectionOptionFix(
                                PyChainedComparisonsInspection.this, "ignoreConstantInTheMiddle",
                                PyPsiBundle.message("INSP.chained.comparisons.ignore.statements.with.constant.in.the.middle"),
                                true)));
            }
          }
          else {
            registerProblem(node, PyPsiBundle.message("INSP.simplify.chained.comparison"), new ChainedComparisonsQuickFix(myIsLeft, myIsRight, getInnerRight));
          }
        }
      }
    }

    private boolean isRightSimplified(@NotNull final PyBinaryExpression leftExpression,
                                      @NotNull final PyBinaryExpression rightExpression) {
      final PyExpression leftRight = leftExpression.getRightExpression();
      if (leftRight instanceof PyBinaryExpression &&
          PyTokenTypes.RELATIONAL_OPERATIONS.contains(((PyBinaryExpression)leftRight).getOperator())) {
        if (isRightSimplified((PyBinaryExpression)leftRight, rightExpression)) {
          return true;
        }
      }

      myOperator = leftExpression.getOperator();
      if (PyTokenTypes.RELATIONAL_OPERATIONS.contains(myOperator)) {
        if (leftRight != null) {
          if (leftRight.getText().equals(getLeftExpression(rightExpression, true).getText())) {
            myIsLeft = false;
            myIsRight = true;
            isConstantInTheMiddle = leftRight instanceof PyLiteralExpression;
            return true;
          }

          final PyExpression right = getSmallestRight(rightExpression, true);
          if (right != null && leftRight.getText().equals(right.getText())) {
            myIsLeft = false;
            myIsRight = false;
            isConstantInTheMiddle = leftRight instanceof PyLiteralExpression;
            return true;
          }
        }
      }
      return false;
    }

    private static boolean isOpposite(final PyElementType op1, final PyElementType op2) {
      if ((op1 == PyTokenTypes.GT || op1 == PyTokenTypes.GE) && (op2 == PyTokenTypes.LT || op2 == PyTokenTypes.LE)) {
        return true;
      }
      if ((op2 == PyTokenTypes.GT || op2 == PyTokenTypes.GE) && (op1 == PyTokenTypes.LT || op1 == PyTokenTypes.LE)) {
        return true;
      }
      return false;
    }


    private boolean isLeftSimplified(PyBinaryExpression leftExpression, PyBinaryExpression rightExpression) {
      final PyExpression leftLeft = leftExpression.getLeftExpression();
      if (leftLeft instanceof PyBinaryExpression &&
          PyTokenTypes.RELATIONAL_OPERATIONS.contains(((PyBinaryExpression)leftLeft).getOperator())) {
        if (isLeftSimplified((PyBinaryExpression)leftLeft, rightExpression)) {
          return true;
        }
      }

      myOperator = leftExpression.getOperator();
      if (PyTokenTypes.RELATIONAL_OPERATIONS.contains(myOperator)) {
        if (leftLeft != null) {
          if (leftLeft.getText().equals(getLeftExpression(rightExpression, false).getText())) {
            myIsLeft = true;
            myIsRight = true;
            isConstantInTheMiddle = leftLeft instanceof PyLiteralExpression;
            return true;
          }
          final PyExpression right = getSmallestRight(rightExpression, false);
          if (right != null && leftLeft.getText().equals(right.getText())) {
            myIsLeft = true;
            myIsRight = false;
            isConstantInTheMiddle = leftLeft instanceof PyLiteralExpression;
            return true;
          }
        }
      }
      return false;
    }

    private PyExpression getLeftExpression(PyBinaryExpression expression, boolean isRight) {
      PyExpression result = expression;
      while (result instanceof PyBinaryExpression &&
             (PyTokenTypes.RELATIONAL_OPERATIONS.contains(((PyBinaryExpression)result).getOperator()) ||
              PyTokenTypes.EQUALITY_OPERATIONS.contains(((PyBinaryExpression)result).getOperator()))) {

        final boolean opposite = isOpposite(((PyBinaryExpression)result).getOperator(), myOperator);
        if ((isRight && opposite) || (!isRight && !opposite)) {
          break;
        }
        result = ((PyBinaryExpression)result).getLeftExpression();
      }
      return result;
    }

    @Nullable
    private PyExpression getSmallestRight(PyBinaryExpression expression, boolean isRight) {
      PyExpression result = expression;
      while (result instanceof PyBinaryExpression &&
             (PyTokenTypes.RELATIONAL_OPERATIONS.contains(((PyBinaryExpression)result).getOperator()) ||
              PyTokenTypes.EQUALITY_OPERATIONS.contains(((PyBinaryExpression)result).getOperator()))) {

        final boolean opposite = isOpposite(((PyBinaryExpression)result).getOperator(), myOperator);
        if ((isRight && !opposite) || (!isRight && opposite)) {
          break;
        }
        result = ((PyBinaryExpression)result).getRightExpression();
      }
      return result;
    }
  }
}
