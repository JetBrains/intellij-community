// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.BaseJavaRefactoringAction;
import org.jetbrains.annotations.NotNull;

public class WrapReturnValueAction extends BaseJavaRefactoringAction {

  @Override
  protected RefactoringActionHandler getHandler(@NotNull DataContext context){
        return new WrapReturnValueHandler();
    }

  @Override
  public boolean isAvailableInEditorOnly(){
      return false;
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element, @NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext context) {
    final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    if (psiMethod != null && !(psiMethod instanceof PsiCompiledElement)) {
      final PsiType returnType = psiMethod.getReturnType();
      return returnType != null && !PsiType.VOID.equals(returnType);
    }
    return false;
  }

  @Override
  public boolean isEnabledOnElements(PsiElement @NotNull [] elements) {
    if (elements.length != 1) {
        return false;
    }
    final PsiElement element = elements[0];
    final PsiMethod containingMethod =
            PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    return containingMethod != null;
  }
}
