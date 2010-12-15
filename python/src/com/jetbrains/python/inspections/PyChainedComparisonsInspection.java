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
          if (checkOperator((PyBinaryExpression)leftExpression, (PyBinaryExpression)rightExpression))
            registerProblem(node, "Simplify chained comparison", new ChainedComparisonsQuickFix());
        }
      }
    }

    static private boolean checkOperator(PyBinaryExpression leftExpression,PyBinaryExpression rightExpression) {
      if (leftExpression.getRightExpression() instanceof PyBinaryExpression) {
        if (checkOperator((PyBinaryExpression)leftExpression.getRightExpression(), rightExpression))
          return true;
      }
      
      if (/*leftExpression.getOperator() == rightExpression.getOperator() && */
                  PyTokenTypes.RELATIONAL_OPERATIONS.contains(leftExpression.getOperator())) {
        PyExpression leftRight = leftExpression.getRightExpression();
        if (leftRight != null) {
          if (leftRight.getText().equals(getLeftExpression(rightExpression).getText()))
            return true;
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
  }
}
