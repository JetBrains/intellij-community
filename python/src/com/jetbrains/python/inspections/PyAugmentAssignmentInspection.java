package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.actions.AugmentedAssignmentQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyAssignmentStatement(final PyAssignmentStatement node) {
      if (node.getAssignedValue() instanceof PyBinaryExpression) {
        final PyExpression target = node.getLeftHandSideExpression();
        final PyBinaryExpression expression = (PyBinaryExpression)node.getAssignedValue();
        if (expression == null) return;
        PyExpression leftExpression = expression.getLeftExpression();
        PyExpression rightExpression = expression.getRightExpression();
        if (rightExpression instanceof PyParenthesizedExpression)
          rightExpression = ((PyParenthesizedExpression)rightExpression).getContainedExpression();
        if (rightExpression == null || target == null) return;
        boolean changedParts = false;
        final String targetText = target.getText();
        final String rightText = rightExpression.getText();
        if (rightText.equals(targetText)) {
          final PyExpression tmp = rightExpression;
          rightExpression = leftExpression;
          leftExpression = tmp;
          changedParts = true;
        }
        final PyElementType op = expression.getOperator();
        final TokenSet operations = TokenSet.create(PyTokenTypes.PLUS, PyTokenTypes.MINUS, PyTokenTypes.MULT,
                         PyTokenTypes.FLOORDIV, PyTokenTypes.DIV, PyTokenTypes.PERC, PyTokenTypes.AND, PyTokenTypes.OR,
                         PyTokenTypes.XOR, PyTokenTypes.LTLT, PyTokenTypes.GTGT, PyTokenTypes.EXP);
        final TokenSet commutativeOperations = TokenSet.create(PyTokenTypes.PLUS, PyTokenTypes.MULT);
        if ((operations.contains(op) && !changedParts) || (changedParts && commutativeOperations.contains(op))) {
          if ((leftExpression instanceof PyReferenceExpression || leftExpression instanceof PySubscriptionExpression)) {
            if (leftExpression.getText().equals(targetText)) {
              if (rightExpression instanceof PyNumericLiteralExpression) {
                final AugmentedAssignmentQuickFix quickFix = new AugmentedAssignmentQuickFix();
                registerProblem(node, "Assignment can be replaced with augmented assignment", quickFix);
              }
              else {
                final PyType type = rightExpression.getType(myTypeEvalContext);
                if (type != null) {
                  final PyBuiltinCache cache = PyBuiltinCache.getInstance(rightExpression);
                  if (PyTypeChecker.match(cache.getComplexType(), type, myTypeEvalContext) ||
                      PyTypeChecker.match(cache.getStringType(LanguageLevel.forElement(rightExpression)), type,
                                          myTypeEvalContext)) {
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
