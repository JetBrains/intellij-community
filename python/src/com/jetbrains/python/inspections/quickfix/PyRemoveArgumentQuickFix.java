// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;

//TODO: Remove pydoc aswell
public class PyRemoveArgumentQuickFix implements LocalQuickFix {

  @Override
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("QFIX.NAME.remove.argument");
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (!(element instanceof PyExpression)) return;
    final PyExpression expression = (PyExpression)element;
    final PsiElement nextSibling = PsiTreeUtil.skipWhitespacesForward(expression);
    final PsiElement prevSibling = PsiTreeUtil.skipWhitespacesBackward(expression);
    expression.delete();
    if (nextSibling != null && nextSibling.getNode().getElementType().equals(PyTokenTypes.COMMA)) {
      nextSibling.delete();
      return;
    }
    if (prevSibling != null && prevSibling.getNode().getElementType().equals(PyTokenTypes.COMMA)) {
      prevSibling.delete();
    }
  }
}
