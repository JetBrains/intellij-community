// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RenameUnsupportedExpressionProcessor extends RenamePyElementProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return PsiTreeUtil.instanceOf(element, 
                                  PyStringLiteralExpression.class,
                                  PyNumericLiteralExpression.class,
                                  PyListLiteralExpression.class,
                                  PyDictLiteralExpression.class,
                                  PySetLiteralExpression.class,
                                  PyStarArgument.class,
                                  PyCallExpression.class,
                                  PyBinaryExpression.class,
                                  PySubscriptionExpression.class);
  }

  @Override
  public void renameElement(@NotNull PsiElement element, @NotNull String newName, UsageInfo @NotNull [] usages, @Nullable RefactoringElementListener listener)
    throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean isToSearchInComments(@NotNull PsiElement element) {
    return PyCodeInsightSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE;
  }

  @Override
  public void setToSearchInComments(@NotNull PsiElement element, boolean enabled) {
    PyCodeInsightSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE = enabled;
  }

  @Override
  public boolean isToSearchForTextOccurrences(@NotNull PsiElement element) {
    return PyCodeInsightSettings.getInstance().RENAME_SEARCH_NON_CODE_FOR_VARIABLE;
  }

  @Override
  public void setToSearchForTextOccurrences(@NotNull PsiElement element, boolean enabled) {
    PyCodeInsightSettings.getInstance().RENAME_SEARCH_NON_CODE_FOR_VARIABLE = enabled;
  }
}
