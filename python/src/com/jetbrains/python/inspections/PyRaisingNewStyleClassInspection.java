package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   10.03.2010
 * Time:   14:43:08
 */
public class PyRaisingNewStyleClassInspection extends LocalInspectionTool {
  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.raising.new.style.class");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "PyRaisingNewStyleClassInspection";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  private static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyRaiseStatement(PyRaiseStatement node) {
      final VirtualFile virtualFile = node.getContainingFile().getVirtualFile();
      if (virtualFile == null) {
        return;
      }
      if (LanguageLevel.forFile(virtualFile).isPy3K()) {
        return;
      }
      final PyExpression[] expressions = node.getExpressions();
      if (expressions == null || expressions.length == 0) {
        return;
      }
      final PyExpression expression = expressions[0];
      if (expression instanceof PyCallExpression) {
        final PyExpression callee = ((PyCallExpression)expression).getCallee();
        if (callee instanceof PyReferenceExpression) {
          final PsiElement psiElement = ((PyReferenceExpression)callee).getReference().resolve();
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
