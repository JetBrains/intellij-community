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
    public void visitPyIfStatement(final PyIfStatement node) {
      PyIfPart ifPart = node.getIfPart();
      RedundantParenthesesQuickFix quickFix = null;

      if (ifPart.getCondition() instanceof PyParenthesizedExpression) {
        quickFix = new RedundantParenthesesQuickFix(node);
      }

      PyIfPart[] elifParts = node.getElifParts();

      for (PyIfPart st : elifParts) {
        if (st.getCondition() instanceof PyParenthesizedExpression) {
          if (quickFix == null) {
            quickFix = new RedundantParenthesesQuickFix(node);
          }
          quickFix.addStatement(st);
        }
      }
      if (quickFix == null) {
        return;
      }
      registerProblem(node, "Redundant parentheses", quickFix);
    }

    @Override
    public void visitPyWhileStatement(final PyWhileStatement node) {
      PyWhilePart whilePart = node.getWhilePart();
      RedundantParenthesesQuickFix quickFix = null;

      if (whilePart.getCondition() instanceof PyParenthesizedExpression) {
        quickFix = new RedundantParenthesesQuickFix(node);
      }
      if (quickFix == null) {
        return;
      }
      registerProblem(node, "Redundant parentheses", quickFix);
    }

    @Override
    public void visitPyTryExceptStatement(final PyTryExceptStatement node) {
      PyExceptPart[] exceptParts = node.getExceptParts();
      RedundantParenthesesQuickFix quickFix = null;

      for (PyExceptPart except : exceptParts) {
        if (except.getExceptClass() instanceof PyParenthesizedExpression) {
          if (quickFix == null) {
            quickFix = new RedundantParenthesesQuickFix(node);
          }
          quickFix.addStatement(except);
        }
        if (quickFix == null) {
          return;
        }
        registerProblem(node, "Redundant parentheses", quickFix);

      }
    }
  }
}
