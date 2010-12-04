package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.actions.AugmentedAssignmentQuickFix;
import com.jetbrains.python.actions.RedundantParenthesesQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * Inspection to detect  assignment that can be replaced with augmented assignment.
 */
public class PyAugmentAssignmentInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.augment.assignment");
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
    public void visitPyAssignmentStatement(final PyAssignmentStatement node) {
      if (node.getLeftHandSideExpression() instanceof PyTargetExpression &&
                            node.getAssignedValue() instanceof PyBinaryExpression) {
        PyTargetExpression target = ((PyTargetExpression)node.getLeftHandSideExpression());
        PyBinaryExpression expression = (PyBinaryExpression)node.getAssignedValue();
        PyExpression leftExpression = expression.getLeftExpression();
        PyExpression rightExpression = expression.getRightExpression();
        if (PyTokenTypes.ADDITIVE_OPERATIONS.contains(expression.getOperator()) ||
              PyTokenTypes.MULTIPLICATIVE_OPERATIONS.contains(expression.getOperator())) {
          if (leftExpression != null && leftExpression instanceof PyReferenceExpression) {
            if (leftExpression.getText().equals(target.getText())) {
              if (rightExpression instanceof PyNumericLiteralExpression) {
                PyElementType op = expression.getOperator();
                if (op == PyTokenTypes.PLUS || op == PyTokenTypes.MINUS ||
                      op == PyTokenTypes.MULT || op == PyTokenTypes.DIV) {
                  AugmentedAssignmentQuickFix quickFix = new AugmentedAssignmentQuickFix();
                  registerProblem(node, "Assignment can be replaced with augmented assignment", quickFix);
                }
              }
            }
          }
        }
      }
    }
  }
}
