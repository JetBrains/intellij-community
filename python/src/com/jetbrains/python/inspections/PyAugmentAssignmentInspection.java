package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.actions.AugmentedAssignmentQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
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
      if (node.getAssignedValue() instanceof PyBinaryExpression) {
        PyExpression target = node.getLeftHandSideExpression();
        PyBinaryExpression expression = (PyBinaryExpression)node.getAssignedValue();
        PyExpression leftExpression = expression.getLeftExpression();
        PyExpression rightExpression = expression.getRightExpression();
        if (rightExpression != null) {
          if (rightExpression.getText().equals(target.getText())) {
            PyExpression tmp = rightExpression;
            rightExpression = leftExpression;
            leftExpression = tmp;
          }
          PyElementType op = expression.getOperator();
          if (PyTokenTypes.ADDITIVE_OPERATIONS.contains(op) ||
                PyTokenTypes.MULTIPLICATIVE_OPERATIONS.contains(op) ||
                  PyTokenTypes.SHIFT_OPERATIONS.contains(op) ||
                  PyTokenTypes.BITWISE_OPERATIONS.contains(op) ||
                  op == PyTokenTypes.EXP) {
            if (leftExpression != null
                && (leftExpression instanceof PyReferenceExpression || leftExpression instanceof PySubscriptionExpression)) {
              if (leftExpression.getText().equals(target.getText())) {
                if (rightExpression instanceof PyNumericLiteralExpression) {
                  AugmentedAssignmentQuickFix quickFix = new AugmentedAssignmentQuickFix();
                  registerProblem(node, "Assignment can be replaced with augmented assignment", quickFix);
                }
                else {
                  PyType type = rightExpression.getType(myTypeEvalContext);
                  if (type != null) {
                    if (type.getName().equals("int") || type.getName().equals("str"))
                      registerProblem(node, "Assignment can be replaced with augmented assignment", new AugmentedAssignmentQuickFix());
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
