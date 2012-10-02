package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.inspections.quickfix.ComparisonWithNoneQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexey.Ivanov
 */
public class PyComparisonWithNoneInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.comparison.with.none");
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
    public void visitPyBinaryExpression(PyBinaryExpression node) {
      final PyExpression rightExpression = node.getRightExpression();
      if ((rightExpression instanceof PyReferenceExpression && PyNames.NONE.equals(rightExpression.getText())) ||
          rightExpression instanceof PyNoneLiteralExpression) {
        final PyElementType operator = node.getOperator();
        if (operator == PyTokenTypes.EQEQ || operator == PyTokenTypes.NE || operator == PyTokenTypes.NE_OLD) {
          PsiReference reference = node.getReference();
          assert reference != null;
          PsiElement result = reference.resolve();
          if (result == null || PyBuiltinCache.getInstance(node).hasInBuiltins(result)) {
            registerProblem(node, "Comparison with None performed with equality operators", new ComparisonWithNoneQuickFix());
          }
        }
      }
    }
  }
}
