
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.tempWithQuery.TempWithQueryHandler;
import org.jetbrains.annotations.NotNull;

public class TempWithQueryAction extends BaseJavaRefactoringAction{
  @Override
  public boolean isAvailableInEditorOnly() {
    return true;
  }

  @Override
  public boolean isEnabledOnElements(PsiElement @NotNull [] elements) {
    return false;
  }

  @Override
  public RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return new TempWithQueryHandler();
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(final @NotNull PsiElement element, final @NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext context) {
    return element instanceof PsiLocalVariable && ((PsiLocalVariable) element).getInitializer() != null;
  }
}