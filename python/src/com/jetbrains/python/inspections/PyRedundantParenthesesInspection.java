package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.RedundantParenthesesQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * Inspection to detect redundant parentheses in if/while/except statement.
 */
public class PyRedundantParenthesesInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.redundant.parentheses");
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
    public void visitPyParenthesizedExpression(final PyParenthesizedExpression node) {
      if (node.getContainedExpression() instanceof PyReferenceExpression) {
        registerProblem(node, "Redundant parentheses", new RedundantParenthesesQuickFix());
      }
      else if (node.getParent() instanceof PyExceptPart ||
              node.getParent() instanceof PyIfPart || node.getParent() instanceof PyWhilePart) {
        registerProblem(node, "Redundant parentheses", new RedundantParenthesesQuickFix());
      }
    }

  }
}
