package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.RemoveUnnecessaryBackslashQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: catherine
 * <p/>
 * Inspection to highlight backslashes in places where line continuation is implicit (inside (), [], {}).
 */
public class PyUnnecessaryBackslashInspection extends PyInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.unnecessary.backslash");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyParameterList(final PyParameterList list) {
      findProblem(list);
    }

    @Override
    public void visitPyArgumentList(final PyArgumentList list) {
      findProblem(list);
    }

    @Override
    public void visitPyParenthesizedExpression(final PyParenthesizedExpression expression) {
      findProblem(expression);
    }

    @Override
    public void visitPyDictLiteralExpression(final PyDictLiteralExpression expression) {
      findProblem(expression);
    }

    @Override
    public void visitPyListLiteralExpression(final PyListLiteralExpression expression) {
      findProblem(expression);
    }

    @Override
    public void visitPySetLiteralExpression(final PySetLiteralExpression expression) {
      findProblem(expression);
    }

    @Override
    public void visitPyStringLiteralExpression(final PyStringLiteralExpression stringLiteralExpression) {
      PsiElement parent = stringLiteralExpression.getParent();
      if (parent instanceof PyListLiteralExpression || parent instanceof PyParenthesizedExpression ||
          parent instanceof PySetLiteralExpression || parent instanceof PyKeyValueExpression ||
          parent instanceof PyNamedParameter || parent instanceof PyArgumentList) {
        findProblem(stringLiteralExpression);
      }
    }

    private void findProblem(@Nullable final PsiElement expression) {
      PsiTreeUtil.processElements(expression, new PsiElementProcessor<PsiElement>() {
        @Override
        public boolean execute(@NotNull PsiElement ws) {
          if (ws instanceof PsiWhiteSpace && ws.getText().contains("\\")) {
            registerProblem(ws, "Unnecessary backslash in expression.", new RemoveUnnecessaryBackslashQuickFix());
          }
          return true;
        }
      });
    }
  }
}
