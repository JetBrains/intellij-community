// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.removemiddleman.RemoveMiddlemanHandler;
import org.jetbrains.annotations.NotNull;

public class RemoveMiddlemanAction extends BaseJavaRefactoringAction{

  @Override
  protected RefactoringActionHandler getHandler(@NotNull DataContext context) {
    return new RemoveMiddlemanHandler();
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element, @NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext context) {
    return element instanceof PsiField;
  }

  @Override
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  public boolean isEnabledOnElements(PsiElement @NotNull [] elements) {
    return elements.length == 1 && PsiTreeUtil.getParentOfType(elements[0], PsiField.class, false) != null;
  }
}
