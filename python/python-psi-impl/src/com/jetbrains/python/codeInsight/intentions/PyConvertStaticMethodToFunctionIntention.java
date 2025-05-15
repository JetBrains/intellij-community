// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class PyConvertStaticMethodToFunctionIntention extends PyBaseIntentionAction {
  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("INTN.convert.static.method.to.function");
  }

  @Override
  public @NotNull String getText() {
    return PyPsiBundle.message("INTN.convert.static.method.to.function");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (!(psiFile instanceof PyFile)) {
      return false;
    }
    final PsiElement element = PyUtil.findNonWhitespaceAtOffset(psiFile, editor.getCaretModel().getOffset());
    final PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (function == null) return false;
    final PyClass containingClass = function.getContainingClass();
    if (containingClass == null) return false;
    final PyDecoratorList decoratorList = function.getDecoratorList();
    if (decoratorList != null) {
      final PyDecorator staticMethod = decoratorList.findDecorator(PyNames.STATICMETHOD);
      if (staticMethod != null) return true;
    }
    return false;
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiElement element = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    PyFunction problemFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (problemFunction == null) return;
    final PyClass containingClass = problemFunction.getContainingClass();
    if (containingClass == null) return;
    final List<UsageInfo> usages = PyPsiIndexUtil.findUsages(problemFunction, false);
    final PyDecoratorList decoratorList = problemFunction.getDecoratorList();
    if (decoratorList != null) {
      final PyDecorator decorator = decoratorList.findDecorator(PyNames.STATICMETHOD);
      if (decorator != null) decorator.delete();
    }

    final PsiElement copy = problemFunction.copy();
    problemFunction.delete();
    file.addAfter(copy, containingClass);

    for (UsageInfo usage : usages) {
      final PsiElement usageElement = usage.getElement();
      if (usageElement instanceof PyReferenceExpression) {
        PyUtil.removeQualifier((PyReferenceExpression)usageElement);
      }
    }
  }
}
