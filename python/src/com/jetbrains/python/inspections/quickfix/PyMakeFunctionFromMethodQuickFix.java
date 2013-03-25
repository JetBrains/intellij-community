package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * User: ktisha
 */
public class PyMakeFunctionFromMethodQuickFix implements LocalQuickFix {
  public PyMakeFunctionFromMethodQuickFix() {
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.NAME.make.function");
  }

  @NonNls
  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PyFunction problemFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (problemFunction == null) return;
    final PyClass containingClass = problemFunction.getContainingClass();
    if (containingClass == null) return;

    if (!PyUtil.deleteParameter(problemFunction, 0)) return;

    final PsiElement copy = problemFunction.copy();
    final PyStatementList classStatementList = containingClass.getStatementList();
    classStatementList.deleteChildRange(problemFunction, problemFunction);
    final PsiFile file = containingClass.getContainingFile();
    file.addAfter(copy, containingClass);
  }
}
