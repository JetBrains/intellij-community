package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.actions.ChainedComparisonsQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

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
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  private static class Visitor extends PyInspectionVisitor {
    boolean myIsLeft;
    boolean myIsRight;

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyBinaryExpression(final PyBinaryExpression node){
      PyExpression leftExpression = node.getLeftExpression();
      PyExpression rightExpression = node.getRightExpression();

      if (leftExpression instanceof PyBinaryExpression &&
                        rightExpression instanceof PyBinaryExpression) {
        if (node.getOperator() == PyTokenTypes.AND_KEYWORD) {
          if (isRightSimplified((PyBinaryExpression)leftExpression, (PyBinaryExpression)rightExpression) ||
              isLeftSimplified((PyBinaryExpression)leftExpression, (PyBinaryExpression)rightExpression))
            registerProblem(node, "Simplify chained comparison", new ChainedComparisonsQuickFix(myIsLeft, myIsRight));
        }
      }
    }

    private boolean isRightSimplified(PyBinaryExpression leftExpression, PyBinaryExpression rightExpression) {
      if (leftExpression.getRightExpression() instanceof PyBinaryExpression &&
          PyTokenTypes.RELATIONAL_OPERATIONS.contains(((PyBinaryExpression)leftExpression.getRightExpression()).getOperator())){
        if (isRightSimplified((PyBinaryExpression)leftExpression.getRightExpression(), rightExpression))
          return true;
      }
      
      if (PyTokenTypes.RELATIONAL_OPERATIONS.contains(leftExpression.getOperator())) {
        PyExpression leftRight = leftExpression.getRightExpression();
        if (leftRight != null) {
          if (leftRight.getText().equals(getLeftExpression(rightExpression).getText())) {
            myIsLeft = false;
            myIsRight = true;
            return true;
          }

          PyExpression right = getSmallestRight(rightExpression);
          if (right != null && leftRight.getText().equals(right.getText())) {
            myIsLeft = false;
            myIsRight = false;
            return true;
          }
        }
      }
      return false;
    }

    private boolean isLeftSimplified(PyBinaryExpression leftExpression, PyBinaryExpression rightExpression) {
      if (leftExpression.getLeftExpression() instanceof PyBinaryExpression &&
        PyTokenTypes.RELATIONAL_OPERATIONS.contains(((PyBinaryExpression)leftExpression.getLeftExpression()).getOperator())){
        if (isLeftSimplified((PyBinaryExpression)leftExpression.getLeftExpression(), rightExpression))
          return true;
      }

      if (PyTokenTypes.RELATIONAL_OPERATIONS.contains(leftExpression.getOperator())) {
        PyExpression leftRight = leftExpression.getLeftExpression();
        if (leftRight != null) {
          if (leftRight.getText().equals(getLeftExpression(rightExpression).getText())) {
            myIsLeft = true;
            myIsRight = true;
            return true;
          }
          PyExpression right = getSmallestRight(rightExpression);
          if (right != null && leftRight.getText().equals(right.getText())) {
            myIsLeft = true;
            myIsRight = false;
            return true;
          }
        }
      }
      return false;
    }

    static private PyExpression getLeftExpression(PyBinaryExpression expression) {
      PyExpression result = expression;
      while (result instanceof PyBinaryExpression) {
        result = ((PyBinaryExpression)result).getLeftExpression();
      }
      return result;
    }

    static private PyExpression getSmallestRight(PyBinaryExpression expression) {
      PyExpression result = expression;
      while (result instanceof PyBinaryExpression) {
        result = ((PyBinaryExpression)result).getRightExpression();
      }
      return result;
    }
  }
}
