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
    public void visitPyAssignmentStatement(final PyAssignmentStatement node){
      if (node.getAssignedValue() instanceof PyBinaryExpression) {

        if (checkBinaryExpression((PyBinaryExpression)node.getAssignedValue()))
          registerProblem(node, "Simplify chained comparison", new ChainedComparisonsQuickFix());
      }
    }

    private static boolean checkBinaryExpression(PyBinaryExpression expression) {
      if (expression.getOperator() == PyTokenTypes.AND_KEYWORD) {
        PyExpression leftExpression = expression.getLeftExpression();
        PyExpression rightExpression = expression.getRightExpression();

        if (leftExpression instanceof PyBinaryExpression && rightExpression instanceof PyBinaryExpression) {
          if (((PyBinaryExpression)leftExpression).getOperator() == ((PyBinaryExpression)rightExpression).getOperator()) {
            PyExpression leftRight = ((PyBinaryExpression)leftExpression).getRightExpression();
            if (leftRight != null) {
              if (leftRight.getText().equals(getLeftExpression((PyBinaryExpression)rightExpression).getText()))
                return true;
            }
          }
        }
      }
      return false;
    }

    @Override
    public void visitPyIfStatement(final PyIfStatement node){
      if (node.getIfPart().getCondition() instanceof PyBinaryExpression) {
        if (checkBinaryExpression((PyBinaryExpression)node.getIfPart().getCondition()))
          registerProblem(node, "Simplify chained comparison", new ChainedComparisonsQuickFix());
      }
    }

    @Override
    public void visitPyWhileStatement(final PyWhileStatement node){
      if (node.getWhilePart().getCondition() instanceof PyBinaryExpression) {
        if (checkBinaryExpression((PyBinaryExpression)node.getWhilePart().getCondition()))
          registerProblem(node, "Simplify chained comparison", new ChainedComparisonsQuickFix());
      }
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
