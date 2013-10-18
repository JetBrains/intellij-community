package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexey.Ivanov
 */
public class PyRaisingNewStyleClassInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.raising.new.style.class");
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
    public void visitPyRaiseStatement(PyRaiseStatement node) {
      final VirtualFile virtualFile = node.getContainingFile().getVirtualFile();
      if (virtualFile == null) {
        return;
      }
      if (LanguageLevel.forFile(virtualFile).isAtLeast(LanguageLevel.PYTHON25)) {
        return;
      }
      final PyExpression[] expressions = node.getExpressions();
      if (expressions.length == 0) {
        return;
      }
      final PyExpression expression = expressions[0];
      if (expression instanceof PyCallExpression) {
        final PyExpression callee = ((PyCallExpression)expression).getCallee();
        if (callee instanceof PyReferenceExpression) {
          final PsiElement psiElement = ((PyReferenceExpression)callee).getReference(resolveWithoutImplicits()).resolve();
          if (psiElement instanceof PyClass) {
            if (((PyClass)psiElement).isNewStyleClass()) {
              registerProblem(expression, "Raising a new style class");
            }
          }
        }
      }
    }
  }
}
