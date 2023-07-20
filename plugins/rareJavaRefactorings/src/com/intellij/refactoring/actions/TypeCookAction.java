// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.typeCook.TypeCookHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class TypeCookAction extends BaseJavaRefactoringAction {
  @Override
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element,
                                                        @NotNull Editor editor,
                                                        @NotNull PsiFile file,
                                                        @NotNull DataContext context,
                                                        @NotNull String place) {
    if (ActionPlaces.isPopupPlace(place) && !place.equals(ActionPlaces.REFACTORING_QUICKLIST)) {
      return element instanceof PsiClass || element instanceof PsiJavaFile;
    }
    return super.isAvailableOnElementInEditorAndFile(element, editor, file, context, place);
  }

  @Override
  public boolean isEnabledOnElements(PsiElement @NotNull [] elements) {
    return elements.length > 0 && Arrays.stream(elements).allMatch(
      e -> e instanceof PsiClass || e instanceof PsiJavaFile || e instanceof PsiDirectory || e instanceof PsiPackage);
  }

  @Override
  public RefactoringActionHandler getHandler(@NotNull DataContext dataContext) {
    return getHandler();
  }

  public RefactoringActionHandler getHandler() {
    return new TypeCookHandler();
  }
}