package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.actions.SimplifyBooleanCheckQuickFix;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyConditionalStatementPart;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexey.Ivanov
 */
public class PySimplifyBooleanCheckInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.check.can.be.simplified");
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
    public void visitPyConditionalStatementPart(PyConditionalStatementPart node) {
      super.visitPyConditionalStatementPart(node);
      final PyExpression condition = node.getCondition();
      if (condition != null) {
        condition.accept(new PyBinaryExpressionVisitor(getHolder()));
      }
    }
  }

  private static class PyBinaryExpressionVisitor extends PyInspectionVisitor {
    public PyBinaryExpressionVisitor(@Nullable final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyBinaryExpression(PyBinaryExpression node) {
      super.visitPyBinaryExpression(node);
      final PyElementType operator = node.getOperator();
      final PyExpression rightExpression = node.getRightExpression();
      if (rightExpression == null) {
        return;
      }
      final String leftExpressionText = node.getLeftExpression().getText();
      final String rightExpressionText = rightExpression.getText();
      if ("True".equals(leftExpressionText) ||
          "False".equals(leftExpressionText) ||
          "0".equals(leftExpressionText) ||
          "[]".equals(leftExpressionText) ||
          "True".equals(rightExpressionText) ||
          "False".equals(rightExpressionText) ||
          "0".equals(rightExpressionText) ||
          "[]".equals(rightExpressionText)) {
        if (PyTokenTypes.EQUALITY_OPERATIONS.contains(operator)) {
          registerProblem(node);
        }
      }
    }

    private void registerProblem(PyBinaryExpression binaryExpression) {
      registerProblem(binaryExpression, PyBundle.message("INSP.expression.can.be.simplified"), new SimplifyBooleanCheckQuickFix(binaryExpression));
    }
  }
}
