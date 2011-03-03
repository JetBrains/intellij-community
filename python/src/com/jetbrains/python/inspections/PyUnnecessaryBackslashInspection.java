package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.Stack;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.RemoveUnnecessaryBackslashQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
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
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(ProblemsHolder holder) {
      super(holder);
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
      Stack<PsiElement> stack = new Stack<PsiElement>();
      stack.push(expression.getContainedExpression());
      while (!stack.isEmpty()) {
        PsiElement element = stack.pop();
        findProblem(element);
        for (PsiElement psiElement : element.getChildren()) {
          stack.push(psiElement);
        }
      }
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
          parent instanceof PySetLiteralExpression || parent instanceof PyKeyValueExpression)
        findProblem(stringLiteralExpression);
    }

    private void findProblem (final PsiElement expression) {
      PsiWhiteSpace[] children = PsiTreeUtil.getChildrenOfType(expression, PsiWhiteSpace.class);
      if (children != null) {
        for (PsiWhiteSpace ws : children) {
          if (ws.getText().contains("\\")) {
            registerProblem(ws, "Unnecessary backslash in expression.", new RemoveUnnecessaryBackslashQuickFix());
          }
        }
      }
    }
  }
}
